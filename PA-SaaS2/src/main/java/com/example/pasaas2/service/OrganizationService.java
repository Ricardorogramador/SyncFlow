package com.example.pasaas2.service;

import com.example.pasaas2.dto.InviteCodeRequest;
import com.example.pasaas2.dto.OrganizationRequest;
import com.example.pasaas2.model.InviteCode;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.InviteCodeRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.OrganizationRepository;
import com.example.pasaas2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.pasaas2.dto.JoinOrganizationRequest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Organization createOrganization(OrganizationRequest request, User currentUser) {
        // 1. Crear la organización
        Organization org = Organization.builder()
                .name(request.getName())
                .plan("FREE_TIER") // Valor por defecto para el MVP
                .build();
        org = organizationRepository.save(org);

        // 2. Asignar al creador como Administrador
        Membership membership = Membership.builder()
                .user(currentUser)
                .organization(org)
                .role("ADMIN")
                .joinedAt(LocalDateTime.now())
                .build();
        membershipRepository.save(membership);

        return org;
    }

    @Transactional
    public InviteCode generateInviteCode(UUID organizationId, InviteCodeRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organización no encontrada"));

        // Generar un código alfanumérico corto (ej. ORG-A1B2C3)
        String uniqueCode = "ORG-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        InviteCode inviteCode = InviteCode.builder()
                .organization(org)
                .code(uniqueCode)
                .maxUses(request.getMaxUses())
                .usesCount(0)
                .expiresAt(LocalDateTime.now().plusDays(7)) // Expira en 7 días por defecto
                .build();

        return inviteCodeRepository.save(inviteCode);
    }

    @Transactional
    public Membership joinOrganization(JoinOrganizationRequest request, User currentUser) {
        InviteCode inviteCode = inviteCodeRepository.findByCode(request.getCode())
                .orElseThrow(() -> new RuntimeException("Código de invitación inválido o no existe"));

        if (inviteCode.getUsesCount() >= inviteCode.getMaxUses()) {
            throw new RuntimeException("El código de invitación ha alcanzado su límite de usos");
        }

        if (inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El código de invitación ha expirado");
        }

        Organization org = inviteCode.getOrganization();

        if (membershipRepository.existsByUserAndOrganization(currentUser, org)) {
            throw new RuntimeException("Ya perteneces a esta organización");
        }

        // 1. Incrementar uso del código
        inviteCode.setUsesCount(inviteCode.getUsesCount() + 1);
        inviteCodeRepository.save(inviteCode);

        // 2. Regla de negocio: Pausa de suscripción individual
        if ("ACTIVE".equals(currentUser.getSubscriptionStatus()) && currentUser.getSubscriptionEndDate() != null) {
            currentUser.setSubscriptionStatus("PAUSED");

            long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), currentUser.getSubscriptionEndDate());
            currentUser.setPausedDaysRemaining((int) Math.max(0, daysRemaining));

            userRepository.save(currentUser);
        }

        // 3. Crear membresía como empleado
        Membership membership = Membership.builder()
                .user(currentUser)
                .organization(org)
                .role("EMPLOYEE")
                .joinedAt(LocalDateTime.now())
                .build();

        return membershipRepository.save(membership);
    }
}