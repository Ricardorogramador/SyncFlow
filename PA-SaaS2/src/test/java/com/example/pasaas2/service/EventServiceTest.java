package com.example.pasaas2.service;

import com.example.pasaas2.dto.EventRequest;
import com.example.pasaas2.dto.EventResponse;
import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.EventAssignment;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.EventAssignmentRepository;
import com.example.pasaas2.repository.EventRepository;
import com.example.pasaas2.repository.RecurrenceRuleRepository;
import com.example.pasaas2.repository.TimelineEntryRepository;
import com.example.pasaas2.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Mock
    private GeminiAiService geminiAiService;

    @Mock
    private TimelineEntryRepository timelineEntryRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void shouldCreatePersonalEventSuccessfully() {
        UUID eventId = UUID.randomUUID();

        User currentUser = User.builder()
                .id(UUID.randomUUID())
                .name("Ricardo")
                .email("ricardo@example.com")
                .build();

        LocalDateTime startTime = LocalDateTime.of(2026, 10, 25, 10, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 10, 25, 11, 0);

        EventRequest request = new EventRequest();
        request.setTitle("Reunión de equipo");
        request.setDescription("Revisión semanal");
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        Event savedEvent = Event.builder()
                .id(eventId)
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(startTime)
                .endTime(endTime)
                .createdBy(currentUser)
                .build();

        when(eventRepository.save(any(Event.class)))
                .thenReturn(savedEvent);

        when(eventAssignmentRepository.hasOverlappingEvents(
                eq(currentUser),
                eq(startTime),
                eq(endTime)
        )).thenReturn(false);

        EventResponse response = eventService.createPersonalEvent(request, currentUser);

        assertNotNull(response);
        assertEquals(eventId, response.getId());
        assertEquals("Reunión de equipo", response.getTitle());
        assertEquals(startTime, response.getStartTime());
        assertEquals(endTime, response.getEndTime());
        assertFalse(response.getHasConflict());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event eventToSave = eventCaptor.getValue();

        assertEquals("Reunión de equipo", eventToSave.getTitle());
        assertEquals("Revisión semanal", eventToSave.getDescription());
        assertEquals(currentUser, eventToSave.getCreatedBy());

        verify(eventAssignmentRepository).save(any(EventAssignment.class));
        verify(recurrenceRuleRepository, never()).save(any());
    }

    @Test
    void shouldRejectEventWhenEndTimeIsBeforeStartTime() {
        User currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("ricardo@example.com")
                .build();

        EventRequest request = new EventRequest();
        request.setTitle("Evento inválido");
        request.setStartTime(LocalDateTime.of(2026, 10, 25, 12, 0));
        request.setEndTime(LocalDateTime.of(2026, 10, 25, 11, 0));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createPersonalEvent(request, currentUser)
        );

        assertEquals(
                "La fecha de fin no puede ser anterior a la fecha de inicio",
                exception.getMessage()
        );

        verify(eventRepository, never()).save(any(Event.class));
        verify(eventAssignmentRepository, never()).save(any(EventAssignment.class));
    }

    @Test
    void shouldReturnConflictStatusWhenCreatingOverlappingEvent() {
        User currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("ricardo@example.com")
                .build();

        LocalDateTime startTime = LocalDateTime.of(2026, 10, 25, 10, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 10, 25, 11, 0);

        EventRequest request = new EventRequest();
        request.setTitle("Evento con conflicto");
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        Event savedEvent = Event.builder()
                .id(UUID.randomUUID())
                .title("Evento con conflicto")
                .startTime(startTime)
                .endTime(endTime)
                .createdBy(currentUser)
                .build();

        when(eventRepository.save(any(Event.class)))
                .thenReturn(savedEvent);

        when(eventAssignmentRepository.hasOverlappingEvents(
                eq(currentUser),
                eq(startTime),
                eq(endTime)
        )).thenReturn(true);

        EventResponse response = eventService.createPersonalEvent(request, currentUser);

        assertTrue(response.getHasConflict());

        ArgumentCaptor<EventAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(EventAssignment.class);

        verify(eventAssignmentRepository).save(assignmentCaptor.capture());

        EventAssignment assignment = assignmentCaptor.getValue();

        assertEquals(currentUser, assignment.getUser());
        assertEquals(savedEvent, assignment.getEvent());
        assertTrue(assignment.getHasConflict());
    }

    @Test
    void shouldRejectAssignmentWhenEventDoesNotExist() {
        UUID eventId = UUID.randomUUID();

        User currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("ricardo@example.com")
                .build();

        com.example.pasaas2.dto.EventAssignRequest request =
                new com.example.pasaas2.dto.EventAssignRequest();

        request.setEventId(eventId);
        request.setUserIds(java.util.List.of(UUID.randomUUID()));

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.assignEventToUsers(request, currentUser)
        );

        assertEquals("Evento no encontrado", exception.getMessage());

        verify(eventAssignmentRepository, never()).saveAll(any());
    }
}