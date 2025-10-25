package com.semasem.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NewPasswordRequest {
    private String currentPassword;
    private String newPassword;

}
