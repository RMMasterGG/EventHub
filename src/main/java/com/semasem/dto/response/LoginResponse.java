package com.semasem.dto.response;

import com.semasem.repository.entity.UserRole;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String name;
    @Email
    private String email;
    private UserRole role;


}
