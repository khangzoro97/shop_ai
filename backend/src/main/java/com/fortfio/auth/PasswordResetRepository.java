package com.fortfio.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

	Optional<PasswordReset> findByTokenHash(String tokenHash);
}
