package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.dto.InviteCodeRequest;
import com.example.pasaas2.dto.JoinOrganizationRequest;
import com.example.pasaas2.dto.MembershipResponse;
import com.example.pasaas2.dto.OrganizationRequest;
import com.example.pasaas2.dto.OrganizationUpdateRequest;
import com.example.pasaas2.dto.UpdateMembershipRoleRequest;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.MembershipRole;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> createOrganization(
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        var organization =
                organizationService.createOrganization(
                        request,
                        currentUser
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Organización creada exitosamente",
                        organization
                )
        );
    }

    @PatchMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<Object>> updateOrganization(
            @PathVariable UUID organizationId,
            @Valid @RequestBody OrganizationUpdateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        var organization =
                organizationService.updateOrganization(
                        organizationId,
                        request,
                        currentUser
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Organización actualizada",
                        organization
                )
        );
    }

    @PostMapping("/{organizationId}/invite-codes")
    public ResponseEntity<ApiResponse<Object>> generateInviteCode(
            @PathVariable UUID organizationId,
            @Valid @RequestBody InviteCodeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        var inviteCode =
                organizationService.generateInviteCode(
                        organizationId,
                        request,
                        currentUser
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Código generado",
                        inviteCode
                )
        );
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<MembershipResponse>>
    joinOrganization(
            @Valid @RequestBody JoinOrganizationRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Membership membership =
                organizationService.joinOrganization(
                        request,
                        currentUser
                );

        MembershipRole role =
                membership.getRole();

        MembershipResponse response =
                MembershipResponse.builder()
                        .id(membership.getId())
                        .organizationId(
                                membership
                                        .getOrganization()
                                        .getId()
                        )
                        .role(role)
                        .owner(
                                role
                                        == MembershipRole.OWNER
                        )
                        .canManageOrganization(
                                role
                                        == MembershipRole.OWNER
                                        || role
                                        == MembershipRole.ADMIN
                        )
                        .canManageEvents(
                                role
                                        == MembershipRole.OWNER
                                        || role
                                        == MembershipRole.ADMIN
                        )
                        .joinedAt(
                                membership.getJoinedAt()
                        )
                        .organizationName(
                                membership
                                        .getOrganization()
                                        .getName()
                        )
                        .userEmail(
                                membership
                                        .getUser()
                                        .getEmail()
                        )
                        .build();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Te has unido a la organización exitosamente",
                        response
                )
        );
    }

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<Object>> getMembers(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Miembros obtenidos",
                        organizationService.getMembers(
                                organizationId,
                                currentUser
                        )
                )
        );
    }

    @PatchMapping(
            "/{organizationId}/members/{membershipId}"
    )
    public ResponseEntity<ApiResponse<Object>> updateMemberRole(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody
            UpdateMembershipRoleRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        organizationService.updateMemberRole(
                organizationId,
                membershipId,
                request.getRole(),
                currentUser
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Rol actualizado",
                        null
                )
        );
    }

    @DeleteMapping(
            "/{organizationId}/members/{membershipId}"
    )
    public ResponseEntity<ApiResponse<Object>> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @AuthenticationPrincipal User currentUser
    ) {
        organizationService.removeMember(
                organizationId,
                membershipId,
                currentUser
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Miembro eliminado",
                        null
                )
        );
    }

    @GetMapping("/{organizationId}/membership")
    public ResponseEntity<ApiResponse<MembershipResponse>>
    getMyMembership(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal User currentUser
    ) {
        Membership membership =
                organizationService.getCurrentMembership(
                        organizationId,
                        currentUser
                );

        MembershipRole role = membership.getRole();

        MembershipResponse response =
                MembershipResponse.builder()
                        .id(membership.getId())
                        .organizationId(
                                membership.getOrganization().getId()
                        )
                        .role(role)
                        .joinedAt(membership.getJoinedAt())
                        .organizationName(
                                membership.getOrganization().getName()
                        )
                        .userEmail(membership.getUser().getEmail())
                        .owner(role == MembershipRole.OWNER)
                        .canManageOrganization(
                                role == MembershipRole.OWNER ||
                                        role == MembershipRole.ADMIN
                        )
                        .canManageEvents(
                                role == MembershipRole.OWNER ||
                                        role == MembershipRole.ADMIN
                        )
                        .build();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Membresía obtenida",
                        response
                )
        );
    }
}