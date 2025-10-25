package com.semasem.dto.response;

import com.semasem.repository.entity.UserRole;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String name;
    @Email
    private String email;
    private UserRole role;


}
