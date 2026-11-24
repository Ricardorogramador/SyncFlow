package com.example.pasaas2.service;

import com.example.pasaas2.dto.InviteCodeRequest;
import com.example.pasaas2.dto.JoinOrganizationRequest;
import com.example.pasaas2.dto.OrganizationRequest;
import com.example.pasaas2.model.InviteCode;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.MembershipRole;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.InviteCodeRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.OrganizationRepository;
import com.example.pasaas2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Organization createOrganization(
            OrganizationRequest request,
            User currentUser
    ) {
        Organization org = Organization.builder()
                .name(request.getName())
                .owner(currentUser)
                .plan("FREE_TIER")
                .build();

        org = organizationRepository.save(org);

        Membership membership = Membership.builder()
                .user(currentUser)
                .organization(org)
                .role(MembershipRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        membershipRepository.save(membership);

        return org;
    }

    @Transactional
    public InviteCode generateInviteCode(
            UUID organizationId,
            InviteCodeRequest request,
            User currentUser
    ) {
        requireMembership(
                organizationId,
                currentUser,
                Set.of(
                        MembershipRole.OWNER,
                        MembershipRole.ADMIN
                )
        );

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organización no encontrada"
                                ));

        String uniqueCode =
                "ORG-" +
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 6)
                                .toUpperCase();

        InviteCode inviteCode = InviteCode.builder()
                .organization(organization)
                .code(uniqueCode)
                .maxUses(request.getMaxUses())
                .usesCount(0)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        return inviteCodeRepository.save(inviteCode);
    }

    @Transactional
    public Membership joinOrganization(
            JoinOrganizationRequest request,
            User currentUser
    ) {
        InviteCode inviteCode =
                inviteCodeRepository.findByCode(request.getCode())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Código de invitación inválido o no existe"
                                ));

        if (inviteCode.getUsesCount() >= inviteCode.getMaxUses()) {
            throw new RuntimeException(
                    "El código de invitación ha alcanzado su límite de usos"
            );
        }

        if (inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException(
                    "El código de invitación ha expirado"
            );
        }

        Organization organization = inviteCode.getOrganization();

        if (membershipRepository.existsByUserAndOrganization(
                currentUser,
                organization
        )) {
            throw new RuntimeException(
                    "Ya perteneces a esta organización"
            );
        }

        inviteCode.setUsesCount(inviteCode.getUsesCount() + 1);
        inviteCodeRepository.save(inviteCode);

        if ("ACTIVE".equals(
                currentUser.getSubscriptionStatus()
        ) && currentUser.getSubscriptionEndDate() != null) {

            currentUser.setSubscriptionStatus("PAUSED");

            long daysRemaining =
                    ChronoUnit.DAYS.between(
                            LocalDate.now(),
                            currentUser.getSubscriptionEndDate()
                    );

            currentUser.setPausedDaysRemaining(
                    (int) Math.max(0, daysRemaining)
            );

            userRepository.save(currentUser);
        }

        Membership membership = Membership.builder()
                .user(currentUser)
                .organization(organization)
                .role(MembershipRole.EMPLOYEE)
                .joinedAt(LocalDateTime.now())
                .build();

        return membershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public Membership requireMembership(
            UUID organizationId,
            User currentUser,
            Set<MembershipRole> allowedRoles
    ) {
        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organización no encontrada"
                                ));

        Membership membership =
                membershipRepository.findByUserAndOrganization(
                        currentUser,
                        organization
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "No perteneces a esta organización"
                        ));

        if (!allowedRoles.contains(membership.getRole())) {
            throw new IllegalArgumentException(
                    "No tienes permisos para realizar esta acción"
            );
        }

        return membership;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(
            UUID organizationId,
            User currentUser
    ) {
        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organización no encontrada"
                                ));

        return organization
                .getOwner()
                .getId()
                .equals(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public List<Membership> getMembers(
            UUID organizationId,
            User currentUser
    ) {
        requireMembership(
                organizationId,
                currentUser,
                Set.of(
                        MembershipRole.OWNER,
                        MembershipRole.ADMIN,
                        MembershipRole.EMPLOYEE
                )
        );

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organización no encontrada"
                                ));

        return membershipRepository.findAllByOrganization(
                organization
        );
    }
}