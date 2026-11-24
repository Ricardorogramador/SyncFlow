package com.example.pasaas2.dto;

import com.example.pasaas2.model.EventScope;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EventResponse {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean hasConflict;
    private String recurrenceFrequency;
    private EventScope scope;
    private UUID organizationId;
}