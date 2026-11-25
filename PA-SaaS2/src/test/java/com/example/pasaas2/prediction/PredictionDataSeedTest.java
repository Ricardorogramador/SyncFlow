package com.example.pasaas2.prediction;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

class PredictionDataSeedTest {

    private static final int SYNTHETIC_MEMBER_COUNT = 20;
    private static final int TRAINING_EVENT_COUNT = 2000;
    private static final int PREDICTION_EVENT_COUNT = 250;

    private static final String TRAINING_HEADER =
            "start_time,end_time,assigned_at,is_recurring,group_size,"
                    + "daily_load_before,weekly_load_before,has_conflict";

    private static final String PREDICTION_HEADER =
            "start_time,end_time,assigned_at,is_recurring,group_size,"
                    + "daily_load_before,weekly_load_before";

    @Test
    void generateDataAndCsvFiles() throws Exception {
        String organizationValue = requiredEnvironmentVariable(
                "PREDICTION_ORGANIZATION_ID"
        );

        UUID organizationId = UUID.fromString(organizationValue);

        String databaseUrl = environmentOrDefault(
                "PREDICTION_DB_URL",
                "jdbc:postgresql://localhost:5432/saas_calendar"
        );
        String databaseUser = requiredEnvironmentVariable(
                "PREDICTION_DB_USER"
        );
        String databasePassword = requiredEnvironmentVariable(
                "PREDICTION_DB_PASSWORD"
        );

        Random random = new Random(42);
        LocalDate today = LocalDate.now();

        try (Connection connection = DriverManager.getConnection(
                databaseUrl,
                databaseUser,
                databasePassword
        )) {
            connection.setAutoCommit(false);

            try {
                UUID ownerId = findOrganizationOwner(
                        connection,
                        organizationId
                );

                List<UUID> memberIds = createSyntheticMembers(
                        connection,
                        organizationId
                );

                String marker =
                        "SYNCFLOW_SYNTHETIC_PREDICTION:" + organizationId;

                List<Candidate> trainingCandidates = createCandidates(
                        random,
                        memberIds,
                        TRAINING_EVENT_COUNT,
                        today.minusDays(180),
                        today.minusDays(1),
                        null
                );

                Map<UUID, List<PriorAssignment>> history = new HashMap<>();
                List<AssignmentRow> trainingRows = buildRows(
                        trainingCandidates,
                        history,
                        true
                );

                long conflicts = trainingRows.stream()
                        .filter(row -> Boolean.TRUE.equals(row.hasConflict()))
                        .count();
                long nonConflicts = trainingRows.size() - conflicts;

                if (conflicts < 4 || nonConflicts < 4) {
                    throw new IllegalStateException(
                            "El CSV de entrenamiento necesita al menos "
                                    + "4 casos de cada clase. Conflictos: "
                                    + conflicts + ", sin conflicto: "
                                    + nonConflicts
                    );
                }

                List<Candidate> predictionCandidates = createCandidates(
                        random,
                        memberIds,
                        PREDICTION_EVENT_COUNT,
                        today.plusDays(1),
                        today.plusDays(31),
                        today.atStartOfDay()
                );

                List<AssignmentRow> predictionRows = buildRows(
                        predictionCandidates,
                        history,
                        false
                );

                deletePreviousGeneratedEvents(
                        connection,
                        organizationId,
                        marker
                );

                insertEvents(
                        connection,
                        ownerId,
                        organizationId,
                        marker,
                        trainingCandidates,
                        predictionCandidates
                );

                insertAssignments(connection, trainingRows);
                insertAssignments(connection, predictionRows);

                connection.commit();

                Path outputRoot = Path.of(
                        environmentOrDefault(
                                "PREDICTION_OUTPUT_DIR",
                                "../data/prediction"
                        )
                ).resolve(organizationId.toString());

                writeCsv(
                        outputRoot.resolve("training_labeled.csv"),
                        TRAINING_HEADER,
                        trainingRows,
                        true
                );

                writeCsv(
                        outputRoot.resolve("prediction_unlabeled.csv"),
                        PREDICTION_HEADER,
                        predictionRows,
                        false
                );

                System.out.println("Datos insertados en PostgreSQL.");
                System.out.println("Miembros sintéticos: " + memberIds.size());
                System.out.println("Asignaciones etiquetadas: " + trainingRows.size());
                System.out.println("Conflictos: " + conflicts);
                System.out.println("Sin conflicto: " + nonConflicts);
                System.out.println("Asignaciones futuras: " + predictionRows.size());
                System.out.println("CSV: " + outputRoot.toAbsolutePath());

            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static UUID findOrganizationOwner(
            Connection connection,
            UUID organizationId
    ) throws SQLException {
        String sql = """
                SELECT owner_id
                FROM organizations
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, organizationId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                            "No existe la organización " + organizationId
                    );
                }

                return result.getObject("owner_id", UUID.class);
            }
        }
    }

    private static List<UUID> createSyntheticMembers(
            Connection connection,
            UUID organizationId
    ) throws SQLException {
        List<UUID> members = new ArrayList<>();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String userSql = """
                INSERT INTO users
                    (id, email, password_hash, name,
                     subscription_status, paused_days_remaining)
                VALUES (?, ?, ?, ?, 'INACTIVE', 0)
                ON CONFLICT (email)
                DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """;

        String membershipSql = """
                INSERT INTO memberships
                    (id, user_id, organization_id, role, joined_at, left_at)
                VALUES (?, ?, ?, 'EMPLOYEE', NOW(), NULL)
                ON CONFLICT (user_id, organization_id)
                DO UPDATE SET role = 'EMPLOYEE', left_at = NULL
                """;

        for (int index = 1; index <= SYNTHETIC_MEMBER_COUNT; index++) {
            String email = String.format(
                    Locale.ROOT,
                    "predictive-%s-%02d@syncflow.test",
                    organizationId,
                    index
            );

            UUID userId;

            try (PreparedStatement statement =
                         connection.prepareStatement(userSql)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, email);
                statement.setString(
                        3,
                        encoder.encode(UUID.randomUUID().toString())
                );
                statement.setString(
                        4,
                        String.format(
                                Locale.ROOT,
                                "Predictive Test Employee %02d",
                                index
                        )
                );

                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    userId = result.getObject("id", UUID.class);
                }
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(membershipSql)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, userId);
                statement.setObject(3, organizationId);
                statement.executeUpdate();
            }

            members.add(userId);
        }

        return members;
    }

    private static List<Candidate> createCandidates(
            Random random,
            List<UUID> members,
            int eventCount,
            LocalDate firstDate,
            LocalDate lastDate,
            LocalDateTime minimumAssignedAt
    ) {
        List<Candidate> candidates = new ArrayList<>();
        int dayRange = Math.toIntExact(
                ChronoUnit.DAYS.between(firstDate, lastDate)
        );

        int[] hours = {8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
        int[] hourWeights = {2, 10, 14, 11, 3, 3, 8, 11, 8, 2};

        for (int index = 0; index < eventCount; index++) {
            LocalDate eventDate = randomBusinessDay(
                    random,
                    firstDate,
                    dayRange
            );

            int hour = weightedChoice(random, hours, hourWeights);
            int minute = random.nextInt(4) * 15;
            LocalDateTime start = eventDate.atTime(hour, minute);

            int[] durations = {30, 45, 60, 90, 120};
            int[] durationWeights = {2, 2, 5, 3, 1};
            int durationMinutes = weightedChoice(
                    random,
                    durations,
                    durationWeights
            );
            LocalDateTime end = start.plusMinutes(durationMinutes);

            int[] leadHours = {1, 2, 4, 8, 24, 48, 72, 168};
            int[] leadWeights = {1, 2, 3, 4, 5, 4, 3, 2};
            int lead = weightedChoice(random, leadHours, leadWeights);

            LocalDateTime assignedAt = start.minusHours(lead);

            if (minimumAssignedAt != null
                    && assignedAt.isBefore(minimumAssignedAt)) {
                assignedAt = minimumAssignedAt.plusNanos(index * 1000L);
            }

            int[] groupSizes = {1, 2, 3, 4, 5, 6};
            int[] groupWeights = {3, 5, 6, 5, 3, 1};
            int groupSize = Math.min(
                    weightedChoice(random, groupSizes, groupWeights),
                    members.size()
            );

            List<UUID> shuffled = new ArrayList<>(members);
            java.util.Collections.shuffle(shuffled, random);

            candidates.add(new Candidate(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    index,
                    start,
                    end,
                    assignedAt,
                    random.nextDouble() < 0.25,
                    shuffled.subList(0, groupSize),
                    groupSize
            ));
        }

        candidates.sort(
                Comparator.comparing(Candidate::assignedAt)
                        .thenComparing(Candidate::start)
        );

        return candidates;
    }

    private static LocalDate randomBusinessDay(
            Random random,
            LocalDate firstDate,
            int dayRange
    ) {
        for (int attempt = 0; attempt < 50; attempt++) {
            LocalDate candidate = firstDate.plusDays(
                    random.nextInt(dayRange + 1)
            );

            if (candidate.getDayOfWeek().getValue() <= 5) {
                return candidate;
            }
        }

        LocalDate candidate = firstDate;
        while (candidate.getDayOfWeek().getValue() > 5) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static int weightedChoice(
            Random random,
            int[] values,
            int[] weights
    ) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }

        int selected = random.nextInt(total);
        for (int index = 0; index < values.length; index++) {
            selected -= weights[index];
            if (selected < 0) {
                return values[index];
            }
        }

        throw new IllegalStateException("No se pudo seleccionar un valor.");
    }

    private static List<AssignmentRow> buildRows(
            List<Candidate> candidates,
            Map<UUID, List<PriorAssignment>> history,
            boolean includeLabels
    ) {
        List<AssignmentRow> rows = new ArrayList<>();

        for (Candidate candidate : candidates) {
            List<AssignmentRow> eventRows = new ArrayList<>();

            for (UUID userId : candidate.memberIds()) {
                List<PriorAssignment> previous = history.computeIfAbsent(
                        userId,
                        ignored -> new ArrayList<>()
                );

                int dailyLoad = 0;
                int weeklyLoad = 0;
                boolean conflict = false;

                for (PriorAssignment assignment : previous) {
                    if (!assignment.assignedAt().isBefore(candidate.assignedAt())) {
                        continue;
                    }

                    if (assignment.start().toLocalDate()
                            .equals(candidate.start().toLocalDate())) {
                        dailyLoad++;
                    }

                    if (sameIsoWeek(
                            assignment.start().toLocalDate(),
                            candidate.start().toLocalDate()
                    )) {
                        weeklyLoad++;
                    }

                    if (assignment.start().isBefore(candidate.end())
                            && assignment.end().isAfter(candidate.start())) {
                        conflict = true;
                    }
                }

                eventRows.add(new AssignmentRow(
                        candidate,
                        userId,
                        dailyLoad,
                        weeklyLoad,
                        includeLabels ? conflict : null
                ));
            }

            rows.addAll(eventRows);

            for (AssignmentRow row : eventRows) {
                history.get(row.userId()).add(
                        new PriorAssignment(
                                candidate.start(),
                                candidate.end(),
                                candidate.assignedAt()
                        )
                );
            }
        }

        return rows;
    }

    private static boolean sameIsoWeek(LocalDate first, LocalDate second) {
        WeekFields iso = WeekFields.ISO;

        return first.get(iso.weekBasedYear())
                == second.get(iso.weekBasedYear())
                && first.get(iso.weekOfWeekBasedYear())
                == second.get(iso.weekOfWeekBasedYear());
    }

    private static void deletePreviousGeneratedEvents(
            Connection connection,
            UUID organizationId,
            String marker
    ) throws SQLException {
        List<UUID> eventIds = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id
                FROM events
                WHERE organization_id = ? AND description = ?
                """
        )) {
            statement.setObject(1, organizationId);
            statement.setString(2, marker);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    eventIds.add(result.getObject("id", UUID.class));
                }
            }
        }

        for (UUID eventId : eventIds) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM event_assignments WHERE event_id = ?"
            )) {
                statement.setObject(1, eventId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE events SET recurrence_rule_id = NULL WHERE id = ?"
            )) {
                statement.setObject(1, eventId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM recurrence_rules WHERE event_id = ?"
            )) {
                statement.setObject(1, eventId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM events WHERE id = ?"
            )) {
                statement.setObject(1, eventId);
                statement.executeUpdate();
            }
        }
    }

    private static void insertEvents(
            Connection connection,
            UUID ownerId,
            UUID organizationId,
            String marker,
            List<Candidate> training,
            List<Candidate> prediction
    ) throws SQLException {
        String eventSql = """
                INSERT INTO events
                    (id, created_by, organization_id, scope, title,
                     description, start_time, end_time, created_at,
                     recurrence_rule_id)
                VALUES (?, ?, ?, 'CORPORATE', ?, ?, ?, ?, ?, NULL)
                """;

        List<Candidate> all = new ArrayList<>(training);
        all.addAll(prediction);

        try (PreparedStatement statement =
                     connection.prepareStatement(eventSql)) {
            for (Candidate candidate : all) {
                statement.setObject(1, candidate.eventId());
                statement.setObject(2, ownerId);
                statement.setObject(3, organizationId);
                statement.setString(
                        4,
                        "Predicción sintética " + candidate.index()
                );
                statement.setString(5, marker);
                statement.setObject(6, candidate.start());
                statement.setObject(7, candidate.end());
                statement.setObject(8, candidate.assignedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }

        String recurrenceSql = """
                INSERT INTO recurrence_rules
                    (id, event_id, frequency, interval_count, until_date)
                VALUES (?, ?, 'WEEKLY', 1, ?)
                """;

        try (PreparedStatement insert =
                     connection.prepareStatement(recurrenceSql);
             PreparedStatement update =
                     connection.prepareStatement(
                             "UPDATE events SET recurrence_rule_id = ? WHERE id = ?"
                     )) {
            for (Candidate candidate : all) {
                if (!candidate.recurring()) {
                    continue;
                }

                insert.setObject(1, candidate.recurrenceId());
                insert.setObject(2, candidate.eventId());
                insert.setObject(
                        3,
                        candidate.start().toLocalDate().plusDays(56)
                );
                insert.addBatch();

                update.setObject(1, candidate.recurrenceId());
                update.setObject(2, candidate.eventId());
                update.addBatch();
            }

            insert.executeBatch();
            update.executeBatch();
        }
    }

    private static void insertAssignments(
            Connection connection,
            List<AssignmentRow> rows
    ) throws SQLException {
        String sql = """
                INSERT INTO event_assignments
                    (id, event_id, user_id, has_conflict, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (AssignmentRow row : rows) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, row.candidate().eventId());
                statement.setObject(3, row.userId());

                if (row.hasConflict() == null) {
                    statement.setNull(
                            4,
                            java.sql.Types.BOOLEAN
                    );
                } else {
                    statement.setBoolean(4, row.hasConflict());
                }

                statement.setObject(
                        5,
                        row.candidate().assignedAt()
                );
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private static void writeCsv(
            Path path,
            String header,
            List<AssignmentRow> rows,
            boolean includeLabel
    ) throws Exception {
        Files.createDirectories(path.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(header);
            writer.newLine();

            for (AssignmentRow row : rows) {
                Candidate candidate = row.candidate();

                String line = String.join(
                        ",",
                        candidate.start().toString(),
                        candidate.end().toString(),
                        candidate.assignedAt().toString(),
                        Boolean.toString(candidate.recurring()),
                        Integer.toString(candidate.groupSize()),
                        Integer.toString(row.dailyLoad()),
                        Integer.toString(row.weeklyLoad())
                );

                if (includeLabel) {
                    line += "," + Boolean.toString(row.hasConflict());
                }

                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Configura la variable de entorno " + name
            );
        }

        return value;
    }

    private static String environmentOrDefault(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record Candidate(
            UUID eventId,
            UUID recurrenceId,
            int index,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime assignedAt,
            boolean recurring,
            List<UUID> memberIds,
            int groupSize
    ) {
    }

    private record PriorAssignment(
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime assignedAt
    ) {
    }

    private record AssignmentRow(
            Candidate candidate,
            UUID userId,
            int dailyLoad,
            int weeklyLoad,
            Boolean hasConflict
    ) {
    }
}