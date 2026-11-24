package com.example.pasaas2.dto;

import com.example.pasaas2.model.MembershipRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MembershipResponse {

    private UUID id;
    private MembershipRole role;
    private LocalDateTime joinedAt;
    private String organizationName;
    private String userEmail;
    private boolean owner;
    private boolean canManageOrganization;
    private boolean canManageEvents;
}