package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinOrganizationRequest {
    @NotBlank(message = "El código de invitación es obligatorio")
    private String code;
}
