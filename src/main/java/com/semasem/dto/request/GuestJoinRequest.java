package com.semasem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuestJoinRequest {
    @NotBlank(message = "Guest name is required")
    private String guestName;

    private String roomInviteLink;
}
