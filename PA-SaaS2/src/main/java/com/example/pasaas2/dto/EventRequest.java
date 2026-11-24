package com.example.pasaas2.dto;

import com.example.pasaas2.model.EventScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class EventRequest {

    @NotBlank(message = "El título es obligatorio")
    private String title;

    private String description;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDateTime startTime;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDateTime endTime;

    private RecurrenceRequest recurrence;

    @NotNull(message = "Debe especificar si el evento es personal o corporativo")
    private EventScope scope;

    private UUID organizationId;

    private List<UUID> assignedUserIds = new ArrayList<>();
}