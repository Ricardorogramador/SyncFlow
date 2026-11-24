package com.example.pasaas2.service;

import com.example.pasaas2.dto.AssignmentResultResponse;
import com.example.pasaas2.dto.EventAssignRequest;
import com.example.pasaas2.dto.EventRequest;
import com.example.pasaas2.dto.EventResponse;
import com.example.pasaas2.dto.TimelineRequest;
import com.example.pasaas2.dto.TimelineResponse;
import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.EventAssignment;
import com.example.pasaas2.model.EventScope;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.MembershipRole;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.RecurrenceRule;
import com.example.pasaas2.model.TimelineEntry;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.EventAssignmentRepository;
import com.example.pasaas2.repository.EventRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.OrganizationRepository;
import com.example.pasaas2.repository.RecurrenceRuleRepository;
import com.example.pasaas2.repository.TimelineEntryRepository;
import com.example.pasaas2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final UserRepository userRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final TimelineEntryRepository timelineEntryRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    @Transactional
    public EventResponse createEvent(
            EventRequest request,
            User currentUser
    ) {
        if (
                request.getEndTime()
                        .isBefore(request.getStartTime())
        ) {
            throw new IllegalArgumentException(
                    "La fecha de fin no puede ser anterior "
                            + "a la fecha de inicio"
            );
        }

        Organization organization = null;

        if (
                request.getScope()
                        == EventScope.CORPORATE
        ) {
            if (request.getOrganizationId() == null) {
                throw new IllegalArgumentException(
                        "El evento corporativo requiere una organización"
                );
            }

            organization =
                    organizationRepository.findById(
                                    request.getOrganizationId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Organización no encontrada"
                                    ));

            Membership membership =
                    membershipRepository
                            .findByUserAndOrganization(
                                    currentUser,
                                    organization
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "No perteneces a esta organización"
                                    ));

            if (
                    membership.getRole()
                            != MembershipRole.OWNER
                            && membership.getRole()
                            != MembershipRole.ADMIN
            ) {
                throw new IllegalArgumentException(
                        "Solo el propietario o un administrador "
                                + "puede crear eventos corporativos"
                );
            }
        }

        Event event =
                Event.builder()
                        .title(request.getTitle())
                        .description(request.getDescription())
                        .startTime(request.getStartTime())
                        .endTime(request.getEndTime())
                        .createdBy(currentUser)
                        .scope(request.getScope())
                        .organization(organization)
                        .build();

        event = eventRepository.save(event);

        List<User> usersToAssign =
                new ArrayList<>();

        if (
                event.getScope()
                        == EventScope.PERSONAL
        ) {
            usersToAssign.add(currentUser);
        } else {
            usersToAssign.add(currentUser);

            for (
                    UUID assignedUserId
                    : request.getAssignedUserIds()
            ) {
                User assignedUser =
                        userRepository.findById(
                                        assignedUserId
                                )
                                .orElse(null);

                if (assignedUser == null) {
                    continue;
                }

                boolean member =
                        membershipRepository
                                .existsByUserAndOrganization(
                                        assignedUser,
                                        organization
                                );

                if (
                        member
                                && usersToAssign
                                .stream()
                                .noneMatch(
                                        user ->
                                                user.getId()
                                                        .equals(
                                                                assignedUserId
                                                        )
                                )
                ) {
                    usersToAssign.add(
                            assignedUser
                    );
                }
            }
        }

        for (User assignedUser : usersToAssign) {
            boolean conflict =
                    eventAssignmentRepository
                            .hasOverlappingEvents(
                                    assignedUser,
                                    request.getStartTime(),
                                    request.getEndTime()
                            );

            EventAssignment assignment =
                    EventAssignment.builder()
                            .event(event)
                            .user(assignedUser)
                            .hasConflict(conflict)
                            .build();

            eventAssignmentRepository.save(
                    assignment
            );
        }

        if (request.getRecurrence() != null) {
            RecurrenceRule recurrence =
                    RecurrenceRule.builder()
                            .event(event)
                            .frequency(
                                    request
                                            .getRecurrence()
                                            .getFrequency()
                            )
                            .intervalCount(
                                    request
                                            .getRecurrence()
                                            .getIntervalCount()
                            )
                            .untilDate(
                                    request
                                            .getRecurrence()
                                            .getUntilDate()
                            )
                            .build();

            recurrenceRuleRepository.save(
                    recurrence
            );
        }

        boolean currentUserConflict =
                eventAssignmentRepository
                        .hasOverlappingEvents(
                                currentUser,
                                request.getStartTime(),
                                request.getEndTime()
                        );

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .hasConflict(currentUserConflict)
                .scope(event.getScope())
                .organizationId(
                        organization == null
                                ? null
                                : organization.getId()
                )
                .build();
    }

    public List<EventResponse> getEventsForUser(
            User currentUser
    ) {
        List<EventAssignment> assignments =
                eventAssignmentRepository
                        .findAllByUser(currentUser);

        List<EventResponse> result =
                new ArrayList<>();

        for (EventAssignment assignment : assignments) {
            Event event =
                    assignment.getEvent();

            String recurrenceFrequency =
                    null;

            if (
                    event.getRecurrenceRule()
                            != null
            ) {
                recurrenceFrequency =
                        event
                                .getRecurrenceRule()
                                .getFrequency();
            }

            result.add(
                    EventResponse.builder()
                            .id(event.getId())
                            .title(event.getTitle())
                            .description(
                                    event.getDescription()
                            )
                            .startTime(
                                    event.getStartTime()
                            )
                            .endTime(
                                    event.getEndTime()
                            )
                            .hasConflict(
                                    assignment
                                            .getHasConflict()
                            )
                            .recurrenceFrequency(
                                    recurrenceFrequency
                            )
                            .scope(event.getScope())
                            .organizationId(
                                    event.getOrganization()
                                            == null
                                            ? null
                                            : event
                                            .getOrganization()
                                            .getId()
                            )
                            .build()
            );
        }

        return result;
    }

    @Transactional
    public EventResponse updateEvent(
            UUID eventId,
            EventRequest request,
            User currentUser
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Evento no encontrado"
                                ));

        if (
                event.getScope()
                        == EventScope.PERSONAL
        ) {
            if (
                    event.getCreatedBy()
                            .getId()
                            .equals(currentUser.getId())
                            == false
            ) {
                throw new IllegalArgumentException(
                        "No puedes modificar este evento personal"
                );
            }
        } else {
            Organization organization =
                    event.getOrganization();

            if (organization == null) {
                throw new IllegalArgumentException(
                        "El evento corporativo no tiene organización"
                );
            }

            Membership membership =
                    membershipRepository
                            .findByUserAndOrganization(
                                    currentUser,
                                    organization
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "No tienes permisos sobre este evento"
                                    ));

            boolean isCreator =
                    event.getCreatedBy()
                            .getId()
                            .equals(currentUser.getId());

            boolean canManage =
                    membership.getRole()
                            == MembershipRole.OWNER
                            || membership.getRole()
                            == MembershipRole.ADMIN;

            if (!isCreator && !canManage) {
                throw new IllegalArgumentException(
                        "No tienes permisos sobre este evento"
                );
            }
        }

        if (
                request.getEndTime()
                        .isBefore(request.getStartTime())
        ) {
            throw new IllegalArgumentException(
                    "La fecha de fin no puede ser anterior "
                            + "a la fecha de inicio"
            );
        }

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());

        event = eventRepository.save(event);

        EventAssignment assignment =
                eventAssignmentRepository
                        .findByEventAndUser(
                                event,
                                currentUser
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No tienes una asignación sobre este evento"
                                ));

        boolean conflict =
                eventAssignmentRepository
                        .hasOverlappingEvents(
                                currentUser,
                                request.getStartTime(),
                                request.getEndTime()
                        );

        assignment.setHasConflict(conflict);
        eventAssignmentRepository.save(
                assignment
        );

        String frequency = null;

        if (
                event.getRecurrenceRule()
                        != null
        ) {
            frequency =
                    event
                            .getRecurrenceRule()
                            .getFrequency();
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .hasConflict(conflict)
                .recurrenceFrequency(frequency)
                .scope(event.getScope())
                .organizationId(
                        event.getOrganization()
                                == null
                                ? null
                                : event
                                .getOrganization()
                                .getId()
                )
                .build();
    }

    @Transactional
    public void deleteEvent(
            UUID eventId,
            User currentUser
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Evento no encontrado"
                                ));

        if (
                event.getScope()
                        == EventScope.PERSONAL
        ) {
            if (
                    event.getCreatedBy()
                            .getId()
                            .equals(currentUser.getId())
                            == false
            ) {
                throw new IllegalArgumentException(
                        "No puedes eliminar este evento personal"
                );
            }
        } else {
            Membership membership =
                    membershipRepository
                            .findByUserAndOrganization(
                                    currentUser,
                                    event.getOrganization()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "No tienes permisos sobre este evento"
                                    ));

            boolean isCreator =
                    event.getCreatedBy()
                            .getId()
                            .equals(currentUser.getId());

            boolean canManage =
                    membership.getRole()
                            == MembershipRole.OWNER
                            || membership.getRole()
                            == MembershipRole.ADMIN;

            if (!isCreator && !canManage) {
                throw new IllegalArgumentException(
                        "No tienes permisos sobre este evento"
                );
            }
        }

        eventAssignmentRepository.deleteByEvent(
                event
        );

        if (
                event.getRecurrenceRule()
                        != null
        ) {
            recurrenceRuleRepository.deleteByEvent(
                    event
            );
        }

        eventRepository.delete(event);
    }

    @Transactional
    public List<AssignmentResultResponse> assignEventToUsers(
            EventAssignRequest request,
            User currentUser
    ) {
        Event event =
                eventRepository.findById(
                                request.getEventId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Evento no encontrado"
                                ));

        if (
                event.getScope()
                        != EventScope.CORPORATE
        ) {
            throw new IllegalArgumentException(
                    "Solo los eventos corporativos pueden asignarse"
            );
        }

        Membership membership =
                membershipRepository
                        .findByUserAndOrganization(
                                currentUser,
                                event.getOrganization()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No perteneces a esta organización"
                                ));

        boolean canManage =
                membership.getRole()
                        == MembershipRole.OWNER
                        || membership.getRole()
                        == MembershipRole.ADMIN;

        boolean isCreator =
                event.getCreatedBy()
                        .getId()
                        .equals(currentUser.getId());

        if (!canManage && !isCreator) {
            throw new IllegalArgumentException(
                    "No tienes permisos para asignar este evento"
            );
        }

        List<AssignmentResultResponse> result =
                new ArrayList<>();

        for (
                UUID targetUserId
                : request.getUserIds()
        ) {
            User targetUser =
                    userRepository.findById(
                                    targetUserId
                            )
                            .orElse(null);

            if (targetUser == null) {
                result.add(
                        AssignmentResultResponse.builder()
                                .userId(targetUserId)
                                .success(false)
                                .build()
                );
                continue;
            }

            boolean isMember =
                    membershipRepository
                            .existsByUserAndOrganization(
                                    targetUser,
                                    event.getOrganization()
                            );

            if (!isMember) {
                result.add(
                        AssignmentResultResponse.builder()
                                .userId(targetUserId)
                                .success(false)
                                .build()
                );
                continue;
            }

            boolean conflict =
                    eventAssignmentRepository
                            .hasOverlappingEvents(
                                    targetUser,
                                    event.getStartTime(),
                                    event.getEndTime()
                            );

            EventAssignment assignment =
                    EventAssignment.builder()
                            .event(event)
                            .user(targetUser)
                            .hasConflict(conflict)
                            .build();

            eventAssignmentRepository.save(
                    assignment
            );

            result.add(
                    AssignmentResultResponse.builder()
                            .userId(targetUser.getId())
                            .userEmail(targetUser.getEmail())
                            .hasConflict(conflict)
                            .success(true)
                            .build()
            );
        }

        return result;
    }

    @Transactional
    public TimelineResponse addTimelineEntry(
            UUID eventId,
            TimelineRequest request,
            User currentUser
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Evento no encontrado"
                                ));

        boolean isCreator =
                event.getCreatedBy()
                        .getId()
                        .equals(currentUser.getId());

        boolean isAssigned =
                eventAssignmentRepository
                        .findByEventAndUser(
                                event,
                                currentUser
                        )
                        .isPresent();

        if (!isCreator && !isAssigned) {
            throw new IllegalArgumentException(
                    "No tienes permisos para participar "
                            + "en este evento"
            );
        }

        TimelineEntry entry =
                TimelineEntry.builder()
                        .event(event)
                        .author(currentUser)
                        .entryType(request.getEntryType())
                        .content(request.getContent())
                        .build();

        timelineEntryRepository.save(entry);

        return TimelineResponse.builder()
                .id(entry.getId())
                .content(entry.getContent())
                .entryType(entry.getEntryType())
                .authorName(currentUser.getName())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    public List<TimelineResponse> getTimelineForEvent(
            UUID eventId,
            User currentUser
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Evento no encontrado"
                                ));

        boolean isCreator =
                event.getCreatedBy()
                        .getId()
                        .equals(currentUser.getId());

        boolean isAssigned =
                eventAssignmentRepository
                        .findByEventAndUser(
                                event,
                                currentUser
                        )
                        .isPresent();

        if (!isCreator && !isAssigned) {
            throw new IllegalArgumentException(
                    "No tienes permisos para ver "
                            + "el contexto de este evento"
            );
        }

        return timelineEntryRepository
                .findByEventOrderByCreatedAtDesc(event)
                .stream()
                .map(entry ->
                        TimelineResponse.builder()
                                .id(entry.getId())
                                .content(entry.getContent())
                                .entryType(entry.getEntryType())
                                .authorName(
                                        entry
                                                .getAuthor()
                                                .getName()
                                )
                                .createdAt(
                                        entry.getCreatedAt()
                                )
                                .build()
                )
                .toList();
    }
}