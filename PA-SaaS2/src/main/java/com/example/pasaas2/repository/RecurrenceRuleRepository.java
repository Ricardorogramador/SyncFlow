package com.example.pasaas2.repository;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
    void deleteByEvent(Event event);
}