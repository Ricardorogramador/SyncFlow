package com.example.pasaas2.dto;

import com.example.pasaas2.model.MembershipRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMembershipRoleRequest {

    @NotNull(message = "El rol es obligatorio")
    private MembershipRole role;
}