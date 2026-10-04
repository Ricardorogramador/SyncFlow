package com.example.pasaas2.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RecurrenceRequest {
    private String frequency;
    private Integer intervalCount;
    private LocalDate untilDate;
}