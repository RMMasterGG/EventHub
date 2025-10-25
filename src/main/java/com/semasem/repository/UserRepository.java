package com.semasem.repository;

import com.semasem.repository.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("unused")
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findByEmail(@Param("email") String email);

    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    Boolean existsByEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM User u WHERE u.isGuest = true AND u.guestExpiresAt < :now")
    void deleteExpiredGuests(@Param("now") Instant now);

    @Query("SELECT u FROM User u WHERE u.isGuest = true AND u.email = :email")
    Optional<User> findGuestByEmail(@Param("email") String email);

    Optional<User> findByUuid(UUID uuid);
}
