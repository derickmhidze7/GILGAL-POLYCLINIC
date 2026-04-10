package com.adags.hospital.repository.user;

import com.adags.hospital.domain.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    /** Fetches the user AND their linked Staff record in a single join. */
    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.staff WHERE u.username = :username")
    Optional<AppUser> findByUsernameWithStaff(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
