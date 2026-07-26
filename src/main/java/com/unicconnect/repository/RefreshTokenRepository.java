package com.unicconnect.repository;

import com.unicconnect.model.RefreshToken;
import com.unicconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserAndRevokedFalse(User user);
    void deleteByUserAndRevokedTrue(User user);
    void deleteByExpiresAtBefore(java.time.LocalDateTime date);
}
