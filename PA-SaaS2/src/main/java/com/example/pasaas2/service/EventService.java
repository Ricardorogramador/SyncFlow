package com.example.pasaas2.service;

import com.example.pasaas2.dto.*;
import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.EventAssignment;
import com.example.pasaas2.model.RecurrenceRule;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.EventAssignmentRepository;
import com.example.pasaas2.repository.EventRepository;
import com.example.pasaas2.repository.RecurrenceRuleRepository;
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
    private final GeminiAiService geminiAiService;

    @Transactional
    public EventResponse createPersonalEvent(EventRequest request, User currentUser) {

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdBy(currentUser)
                .build();

        event = eventRepository.save(event);

        boolean conflict = eventAssignmentRepository.hasOverlappingEvents(
                currentUser,
                request.getStartTime(),
                request.getEndTime()
        );

        EventAssignment assignment = EventAssignment.builder()
                .event(event)
                .user(currentUser)
                .hasConflict(conflict)
                .build();

        eventAssignmentRepository.save(assignment);

        if (request.getRecurrence() != null) {
            RecurrenceRule rule = RecurrenceRule.builder()
                    .event(event)
                    .frequency(request.getRecurrence().getFrequency())
                    .intervalCount(request.getRecurrence().getIntervalCount())
                    .untilDate(request.getRecurrence().getUntilDate())
                    .build();

            recurrenceRuleRepository.save(rule);
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .hasConflict(conflict)
                .build();
    }

    @Transactional
    public List<AssignmentResultResponse> assignEventToUsers(EventAssignRequest request, User currentUser) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));

        if (!event.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("No tienes permisos para asignar usuarios a este evento");
        }

        List<AssignmentResultResponse> results = new ArrayList<>();
        List<EventAssignment> assignmentsToSave = new ArrayList<>();

        for (UUID targetUserId : request.getUserIds()) {
            User targetUser = userRepository.findById(targetUserId).orElse(null);

            if (targetUser == null) {
                results.add(AssignmentResultResponse.builder()
                        .userId(targetUserId)
                        .success(false)
                        .build());
                continue;
            }

            boolean conflict = eventAssignmentRepository.hasOverlappingEvents(
                    targetUser,
                    event.getStartTime(),
                    event.getEndTime()
            );

            EventAssignment assignment = EventAssignment.builder()
                    .event(event)
                    .user(targetUser)
                    .hasConflict(conflict)
                    .build();

            assignmentsToSave.add(assignment);

            results.add(AssignmentResultResponse.builder()
                    .userId(targetUser.getId())
                    .userEmail(targetUser.getEmail())
                    .hasConflict(conflict)
                    .success(true)
                    .build());
        }
        eventAssignmentRepository.saveAll(assignmentsToSave);
        return results;
    }

    public List<EventResponse> getEventsForUser(User currentUser) {
        List<EventAssignment> assignments = eventAssignmentRepository.findAllByUser(currentUser);
        List<EventResponse> responses = new ArrayList<>();

        for (EventAssignment assignment : assignments) {
            Event event = assignment.getEvent();

            String frequency = null;
            if (event.getRecurrenceRule() != null) {
                frequency = event.getRecurrenceRule().getFrequency();
            }

            responses.add(EventResponse.builder()
                    .id(event.getId())
                    .title(event.getTitle())
                    .description(event.getDescription())
                    .startTime(event.getStartTime())
                    .endTime(event.getEndTime())
                    .hasConflict(assignment.getHasConflict())
                    .recurrenceFrequency(frequency)
                    .build());
        }

        return responses;
    }

    @Transactional
    public EventResponse updateEvent(UUID eventId, EventRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        EventAssignment assignment = eventAssignmentRepository.findByEventAndUser(event, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("No tienes permisos sobre este evento"));

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event = eventRepository.save(event);

        boolean conflict = eventAssignmentRepository.hasOverlappingEvents(
                currentUser,
                request.getStartTime(),
        request.getEndTime()
        );

        assignment.setHasConflict(conflict);
        eventAssignmentRepository.save(assignment);

        String frequency = null;
        if (event.getRecurrenceRule() != null) {
            frequency = event.getRecurrenceRule().getFrequency();
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .hasConflict(conflict)
                .recurrenceFrequency(frequency)
                .build();
    }

    @Transactional
    public void deleteEvent(UUID eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));

        EventAssignment assignment = eventAssignmentRepository.findByEventAndUser(event, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("No tienes permisos para eliminar este evento"));


        if (event.getCreatedBy().getId().equals(currentUser.getId())) {

            eventAssignmentRepository.deleteByEvent(event);
            recurrenceRuleRepository.deleteByEvent(event);
            eventRepository.delete(event);
        } else {
            eventAssignmentRepository.delete(assignment);
        }
    }

    public EventRequest previewEventFromText(EventPromptRequest request) {
        return geminiAiService.parseEventFromText(request.getPrompt());
    }
}
