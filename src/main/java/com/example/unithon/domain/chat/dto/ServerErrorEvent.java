package com.example.unithon.domain.chat.dto;

import com.example.unithon.global.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ServerErrorEvent {
    private String sessionId;
    private String errorCode;
    private String message;
    private boolean retryable;
    private LocalDateTime timestamp;
    
    public static ServerErrorEvent of(String sessionId, ErrorCode errorCode) {
        return ServerErrorEvent.builder()
            .sessionId(sessionId)
            .errorCode(errorCode.getCode())
            .message(errorCode.getMessage())
            .retryable(errorCode.isRetryable())
            .timestamp(LocalDateTime.now())
            .build();
    }
} 