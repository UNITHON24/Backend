package com.example.unithon.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DialogStateEvent {
    private String sessionId;
    private DialogState dialogState;
} 