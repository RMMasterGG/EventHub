package com.semasem.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CreateRoomRequest {
    String title;
    String description;
    boolean isPublic;
    int maxParticipants;
}
