package com.example.pasaas2.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurrence_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Ej: "DAILY", "WEEKLY", "MONTHLY"
    @Column(nullable = false)
    private String frequency;

    // Intervalo de repetición (Ej: 1 = cada semana, 2 = cada 2 semanas)
    @Column(nullable = false)
    private Integer intervalCount;

    // Fecha en la que deja de repetirse
    private LocalDate untilDate;
}