package com.semasem.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateRoomRequest {
    @NotBlank(message = "Room title is required")
    @Size(min = 1, max = 100, message = "Title must be between 1 and 100 characters")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private boolean isPublic = true;
    private boolean allowGuests = true;

    @Min(value = 2, message = "Maximum participants must be at least 2")
    @Max(value = 50, message = "Maximum participants cannot exceed 50")
    private int maxParticipants = 10;
}
