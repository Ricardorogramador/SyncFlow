package com.example.pasaas2.repository;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.EventAssignment;
import com.example.pasaas2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventAssignmentRepository extends JpaRepository<EventAssignment, UUID> {

    @Query("SELECT COUNT(ea) > 0 FROM EventAssignment ea JOIN ea.event e " +
            "WHERE ea.user = :user AND " +
            "(e.startTime < :endTime AND e.endTime > :startTime)")
    boolean hasOverlappingEvents(@Param("user") User user,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    void deleteByEvent(Event event);
    List<EventAssignment> findAllByUser(User currentUser);
    Optional<EventAssignment> findByEventAndUser(Event event, User user);
}
