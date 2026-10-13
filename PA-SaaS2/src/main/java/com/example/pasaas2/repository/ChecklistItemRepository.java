package com.example.pasaas2.repository;

import com.example.pasaas2.model.ChecklistItem;
import com.example.pasaas2.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {
    List<ChecklistItem> findAllByEvent(Event event);
}