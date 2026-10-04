package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

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
}
