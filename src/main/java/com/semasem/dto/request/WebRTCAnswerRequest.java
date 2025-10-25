package com.semasem.dto.request;

import lombok.Data;

@Data
public class WebRTCAnswerRequest {
    private String answer;
    private String targetUserId;
}