package io.github.heorhipuhachou.bmuz.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByConfirmTokenHash(String confirmTokenHash);

    Optional<AppUser> findByResetTokenHash(String resetTokenHash);

    Optional<AppUser> findByEmailChangeTokenHash(String emailChangeTokenHash);

    Optional<AppUser> findByRole(Role role);

    Optional<AppUser> findByDisplayNameIgnoreCase(String displayName);
}
