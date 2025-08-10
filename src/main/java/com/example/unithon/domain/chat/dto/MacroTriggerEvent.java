package com.example.unithon.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MacroTriggerEvent {
    private String sessionId;
    private MacroOrderData orderData;
} 