package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.UserSocialIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSocialIdentityRepository extends JpaRepository<UserSocialIdentity, UUID> {

    Optional<UserSocialIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserSocialIdentity> findByUserIdAndProvider(UUID userId, String provider);

    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);

    boolean existsByUserIdAndProvider(UUID userId, String provider);
}
