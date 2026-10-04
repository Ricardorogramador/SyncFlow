package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class EventAssignRequest {
    @NotNull(message = "El ID del evento es obligatorio")
    private UUID eventId;

    @NotEmpty(message = "Debe especificar al menos un usuario para asignar")
    private List<UUID> userIds;
}
