package com.semasem.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "rooms")
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    private String title;

    private String description;

    private UUID ownerUuid;

    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    private boolean isPublic;

    private String inviteLink;

    private int maxParticipants;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @OneToMany(mappedBy = "room")
    private List<RoomParticipant> participants;

    @Column(name = "allow_guests")
    private boolean allowGuests = true;

    public Room(String title, String description, UUID ownerUuid, boolean isPublic, String inviteLink, int maxParticipants) {
        this.title = title;
        this.description = description;
        this.ownerUuid = ownerUuid;
        this.isPublic = isPublic;
        this.inviteLink = inviteLink;
        this.maxParticipants = maxParticipants;
    }

}
