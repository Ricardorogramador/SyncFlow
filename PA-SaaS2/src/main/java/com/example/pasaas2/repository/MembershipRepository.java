package com.example.pasaas2.repository;

import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    boolean existsByUserAndOrganization(User user, Organization organization);
}
