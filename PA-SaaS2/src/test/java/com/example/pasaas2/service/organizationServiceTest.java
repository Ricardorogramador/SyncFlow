package com.example.pasaas2.service;

import com.example.pasaas2.dto.InviteCodeRequest;
import com.example.pasaas2.dto.OrganizationRequest;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.InviteCodeRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.OrganizationRepository;
import com.example.pasaas2.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void shouldCreateOrganizationAndAssignAdminRole() {
        User currentUser = User.builder()
                .id(UUID.randomUUID())
                .name("Ricardo")
                .email("ricardo@example.com")
                .build();

        OrganizationRequest request = new OrganizationRequest();
        request.setName("SyncFlow Team");

        Organization savedOrganization = Organization.builder()
                .id(UUID.randomUUID())
                .name("SyncFlow Team")
                .plan("FREE_TIER")
                .build();

        when(organizationRepository.save(any(Organization.class)))
                .thenReturn(savedOrganization);

        Organization result = organizationService.createOrganization(
                request,
                currentUser
        );

        assertNotNull(result);
        assertEquals("SyncFlow Team", result.getName());
        assertEquals("FREE_TIER", result.getPlan());

        ArgumentCaptor<Organization> organizationCaptor =
                ArgumentCaptor.forClass(Organization.class);

        verify(organizationRepository).save(organizationCaptor.capture());

        Organization organizationToSave = organizationCaptor.getValue();

        assertEquals("SyncFlow Team", organizationToSave.getName());
        assertEquals("FREE_TIER", organizationToSave.getPlan());

        ArgumentCaptor<Membership> membershipCaptor =
                ArgumentCaptor.forClass(Membership.class);

        verify(membershipRepository).save(membershipCaptor.capture());

        Membership membership = membershipCaptor.getValue();

        assertEquals(currentUser, membership.getUser());
        assertEquals(savedOrganization, membership.getOrganization());
        assertEquals("ADMIN", membership.getRole());
        assertNotNull(membership.getJoinedAt());
    }

    @Test
    void shouldRejectInviteCodeWhenOrganizationDoesNotExist() {
        UUID organizationId = UUID.randomUUID();

        InviteCodeRequest request = new InviteCodeRequest();
        request.setMaxUses(5);

        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> organizationService.generateInviteCode(
                        organizationId,
                        request
                )
        );

        assertEquals("Organización no encontrada", exception.getMessage());

        verify(inviteCodeRepository, never()).save(any());
    }

    @Test
    void shouldCreateInviteCodeWithSevenDayExpiration() {
        UUID organizationId = UUID.randomUUID();

        Organization organization = Organization.builder()
                .id(organizationId)
                .name("SyncFlow Team")
                .build();

        InviteCodeRequest request = new InviteCodeRequest();
        request.setMaxUses(10);

        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.of(organization));

        when(inviteCodeRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = organizationService.generateInviteCode(
                organizationId,
                request
        );

        assertNotNull(result);
        assertEquals(organization, result.getOrganization());
        assertEquals(10, result.getMaxUses());
        assertEquals(0, result.getUsesCount());
        assertTrue(result.getCode().startsWith("ORG-"));
        assertNotNull(result.getExpiresAt());

        ArgumentCaptor<com.example.pasaas2.model.InviteCode> captor =
                ArgumentCaptor.forClass(com.example.pasaas2.model.InviteCode.class);

        verify(inviteCodeRepository).save(captor.capture());

        assertEquals(organization, captor.getValue().getOrganization());
    }
}