package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndStatus(UserRole role, AccountStatus status);
}
