package com.semasem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebRTCSignal {
    private String type;
    private String roomId;
    private String sender;
    private String target;
    private Object data;
    private String signalType;
}
