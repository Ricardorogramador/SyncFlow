package com.example.pasaas2.controller;

import com.example.pasaas2.dto.*;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> createOrganization(
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal User currentUser) {

        var org = organizationService.createOrganization(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Organización creada exitosamente", org));
    }

    @PostMapping("/{organizationId}/invite-codes")
    public ResponseEntity<ApiResponse<Object>> generateInviteCode(
            @PathVariable UUID organizationId,
            @Valid @RequestBody InviteCodeRequest request) {

        var inviteCode = organizationService.generateInviteCode(organizationId, request);
        return ResponseEntity.ok(ApiResponse.success("Código generado", inviteCode));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<MembershipResponse>> joinOrganization(
            @Valid @RequestBody JoinOrganizationRequest request,
            @AuthenticationPrincipal User currentUser) {

        Membership membership = organizationService.joinOrganization(request, currentUser);

        MembershipResponse response = MembershipResponse.builder()
                .id(membership.getId())
                .role(membership.getRole())
                .joinedAt(membership.getJoinedAt())
                .organizationName(membership.getOrganization().getName())
                .userEmail(membership.getUser().getEmail())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Te has unido a la organización exitosamente", response));
    }
}
