package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.dto.AssignmentResultResponse;
import com.example.pasaas2.dto.EventAssignRequest;
import com.example.pasaas2.dto.EventRequest;
import com.example.pasaas2.dto.EventResponse;
import com.example.pasaas2.dto.TimelineRequest;
import com.example.pasaas2.dto.TimelineResponse;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>>
    getUserEvents(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Eventos obtenidos exitosamente",
                        eventService.getEventsForUser(
                                currentUser
                        )
                )
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>>
    createEvent(
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Evento creado exitosamente",
                        eventService.createEvent(
                                request,
                                currentUser
                        )
                )
        );
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Evento actualizado",
                        eventService.updateEvent(
                                eventId,
                                request,
                                currentUser
                        )
                )
        );
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User currentUser
    ) {
        eventService.deleteEvent(
                eventId,
                currentUser
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Evento eliminado",
                        null
                )
        );
    }

    @PostMapping("/assign")
    public ResponseEntity<ApiResponse<List<AssignmentResultResponse>>>
    assignEventToUsers(
            @Valid @RequestBody EventAssignRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Proceso de asignación completado",
                        eventService.assignEventToUsers(
                                request,
                                currentUser
                        )
                )
        );
    }

    @PostMapping("/{eventId}/timeline")
    public ResponseEntity<ApiResponse<TimelineResponse>>
    addTimelineEntry(
            @PathVariable UUID eventId,
            @Valid @RequestBody TimelineRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Entrada agregada",
                        eventService.addTimelineEntry(
                                eventId,
                                request,
                                currentUser
                        )
                )
        );
    }

    @GetMapping("/{eventId}/timeline")
    public ResponseEntity<ApiResponse<List<TimelineResponse>>>
    getTimeline(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Contexto obtenido",
                        eventService.getTimelineForEvent(
                                eventId,
                                currentUser
                        )
                )
        );
    }
}