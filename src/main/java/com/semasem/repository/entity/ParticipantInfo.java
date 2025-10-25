package com.semasem.repository.entity;


import java.time.Instant;
import java.util.UUID;

public class ParticipantInfo {
    private UUID userId;
    private String name;
    private String email;
    private ParticipantRole role;
    private boolean isGuest;
    private Instant joinedAt;
    private Instant lastActiveAt;

    public ParticipantInfo(UUID userId, String name, String email, ParticipantRole role,
                           boolean isGuest, Instant joinedAt, Instant lastActiveAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.isGuest = isGuest;
        this.joinedAt = joinedAt;
        this.lastActiveAt = lastActiveAt;
    }

    // Геттеры
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public ParticipantRole getRole() { return role; }
    public boolean isGuest() { return isGuest; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
}