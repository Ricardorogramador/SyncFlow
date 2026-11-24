package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrganizationUpdateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;
}