package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TimelineRequest {
    @NotBlank(message = "El contenido no puede estar vacío")
    private String content;

    @NotBlank(message = "El tipo de entrada es obligatorio (ej. COMMENT, FILE_URL)")
    private String entryType;
}