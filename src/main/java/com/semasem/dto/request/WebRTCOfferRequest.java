package com.semasem.dto.request;

import lombok.Data;

@Data
public class WebRTCOfferRequest {
    private String offer;
    private String targetUserId;
    private Boolean isBroadcast = true;
}