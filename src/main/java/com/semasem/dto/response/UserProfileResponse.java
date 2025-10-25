package com.semasem.dto.response;

import com.semasem.repository.entity.UserRole;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class UserProfileResponse {
    private String userId;
    private String avatarLink;
    private String name;
    @Email
    private String email;
    private String phoneNumber;
    private LocalDate createdAt;
    private boolean emailVerified;
    private UserRole role;
}
