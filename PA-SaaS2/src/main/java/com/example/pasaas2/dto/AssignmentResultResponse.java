package com.example.pasaas2.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class AssignmentResultResponse {
    private UUID userId;
    private String userEmail;
    private Boolean hasConflict;
    private Boolean success;
}
