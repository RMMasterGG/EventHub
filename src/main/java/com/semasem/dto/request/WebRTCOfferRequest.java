package com.semasem.dto.request;

import lombok.Data;

@Data
public class WebRTCOfferRequest {
    private String offer;
    private String targetUserId; // Для прямого соединения
    private Boolean isBroadcast = true; // Для широковещательной рассылки
}