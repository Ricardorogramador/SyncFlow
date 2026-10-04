package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EventPromptRequest {
    @NotBlank(message = "El texto de la petición es obligatorio")
    private String prompt;
}
