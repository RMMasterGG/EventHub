package com.semasem.repository.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
@NoArgsConstructor
@SuppressWarnings("unused")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(length = 12)
    private String phoneNumber;

    @Column(nullable = false)
    private String password;

    @CreatedDate
    @Column(updatable = false)
    private LocalDate createdAt;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.ROLE_USER;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    @Column
    private String avatarLink;

    @Column(name = "is_guest", nullable = false)
    private boolean isGuest = false;

    @Column(name = "guest_expires_at")
    private Instant guestExpiresAt;

    public User(UUID uuid, String name, String email, String phoneNumber, String password, UserRole role) {
        this.uuid = uuid;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.password = password;
        this.role = role;
    }
}
