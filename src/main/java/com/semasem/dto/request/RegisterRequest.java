package com.semasem.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterRequest {
    private String name;
    @Email
    private String email;
    @Size(min = 8, max = 255, message = "The password size should be from 8 to 10")
    private String password;
}
