package com.semasem.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "room_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role; // HOST, PARTICIPANT, GUEST

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status; // JOINED, LEFT, KICKED, BANNED

    @Column(name = "is_audio_enabled")
    private boolean isAudioEnabled;

    @Column(name = "is_video_enabled")
    private boolean isVideoEnabled;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "session_id")
    private String sessionId; // WebSocket session ID

    // Метод для проверки, активен ли участник
    public boolean isActive() {
        return status == ParticipantStatus.JOINED && leftAt == null;
    }

    // Метод для отметки выхода
    public void markAsLeft() {
        this.status = ParticipantStatus.LEFT;
        this.leftAt = Instant.now();
    }
}
