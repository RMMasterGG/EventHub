package com.semasem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomJoinResponse {
    private UUID roomId;
    private String roomTitle;
    private String roomDescription;
    private boolean requiresAuth;
    private boolean allowGuests;
    private boolean canJoinDirectly;
    private String authUrl;
    private String guestJoinUrl;
    private String directJoinToken;
    private String errorMessage;
    private String visitorToken;
}
