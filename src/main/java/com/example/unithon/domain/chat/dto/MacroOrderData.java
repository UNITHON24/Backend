package com.example.unithon.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MacroOrderData {
    private String sessionId;
    private List<MacroOrderItem> items;
    private int totalPrice;
    private String timestamp;
} 