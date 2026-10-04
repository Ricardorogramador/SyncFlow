package com.example.pasaas2.controller;

import com.example.pasaas2.dto.*;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;


    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>>
    getUserEvents(@AuthenticationPrincipal User currentUser) {
        List<EventResponse> events = eventService.getEventsForUser(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Eventos obtenidos exitosamente", events));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal User currentUser) {

        EventResponse response = eventService.updateEvent(eventId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Evento actualizado exitosamente", response));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User currentUser) {

        eventService.deleteEvent(eventId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Evento eliminado exitosamente", null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>>
    createPersonalEvent(
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal User currentUser) {

        EventResponse response =
                eventService.createPersonalEvent(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Evento creado exitosamente", response));
    }

    @PostMapping("/assign")
    public ResponseEntity<ApiResponse<List<AssignmentResultResponse>>>
    assignEventToUsers(
            @Valid @RequestBody EventAssignRequest request,
            @AuthenticationPrincipal User currentUser) {

        List<AssignmentResultResponse> results =
                eventService.assignEventToUsers(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Proceso de asignación completado", results));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<EventRequest>> previewEvent(
            @Valid @RequestBody EventPromptRequest request,
            @AuthenticationPrincipal User currentUser) {

        EventRequest draft = eventService.previewEventFromText(request);
        return ResponseEntity.ok(ApiResponse.success("Borrador generado exitosamente", draft));
    }
}