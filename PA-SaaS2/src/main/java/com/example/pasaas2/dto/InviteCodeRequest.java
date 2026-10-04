package com.example.pasaas2.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteCodeRequest {
    @NotNull(message = "Debe especificar el límite de usos")
    @Min(value = 1, message = "El código debe tener al menos 1 uso")
    private Integer maxUses;
}
