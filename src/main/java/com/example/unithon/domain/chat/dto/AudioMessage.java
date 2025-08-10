package com.example.unithon.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AudioMessage {
    private String type;        // "audio.start", "audio.chunk", "audio.end"
    private String sessionId;   // WebSocket 세션 ID
    private String audioData;   // Base64 인코딩된 오디오 데이터 (chunk인 경우)
    private AudioConfig config; // 오디오 설정 (start인 경우)
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioConfig {
        private int sampleRate;     // 샘플링 레이트 (예: 16000)
        private int channels;       // 채널 수 (1=모노, 2=스테레오)
        private String encoding;    // 인코딩 형식 (예: "PCM", "FLAC")
    }
} 