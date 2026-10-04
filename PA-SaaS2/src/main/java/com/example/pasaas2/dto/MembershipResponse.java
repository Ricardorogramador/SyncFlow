package com.example.pasaas2.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MembershipResponse {
    private UUID id;
    private String role;
    private LocalDateTime joinedAt;
    private String organizationName;
    private String userEmail;
}
