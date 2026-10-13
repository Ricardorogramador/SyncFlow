package com.example.pasaas2.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TimelineResponse {
    private UUID id;
    private String content;
    private String entryType;
    private String authorName;
    private LocalDateTime createdAt;
}