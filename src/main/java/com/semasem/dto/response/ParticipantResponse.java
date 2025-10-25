package com.semasem.dto.response;

import com.semasem.repository.entity.ParticipantRole;
import com.semasem.repository.entity.ParticipantStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class ParticipantResponse {
    private String userEmail;
    private String userName;
    private ParticipantRole role;
    private ParticipantStatus status;
    private boolean isAudioEnabled;
    private boolean isVideoEnabled;
    private Instant joinedAt;
    private String sessionId;
}