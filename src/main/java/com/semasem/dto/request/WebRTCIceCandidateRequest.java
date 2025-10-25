package com.semasem.dto.request;

import lombok.Data;

@Data
public class WebRTCIceCandidateRequest {
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
    private String targetUserId;
}