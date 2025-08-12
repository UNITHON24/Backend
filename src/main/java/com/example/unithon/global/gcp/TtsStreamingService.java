package com.example.unithon.global.gcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "feature.tts", havingValue = "true")
public class TtsStreamingService {

    private final TtsService ttsService;

    /**
     * 텍스트를 음성으로 변환하고 청크 단위로 스트리밍
     */
    public void synthesizeAndStream(String sessionId, String text, Consumer<byte[]> onAudioChunk, Consumer<Void> onComplete) {
        try {
            log.info("TTS 시작 [{}]: {}", sessionId, text);
            
            // TTS 서비스로 전체 오디오 생성
            byte[] audioData = ttsService.synthesizeText(text);
            
            // 청크 단위로 분할해서 스트리밍 (1KB씩)
            int chunkSize = 1024;
            int totalChunks = (int) Math.ceil((double) audioData.length / chunkSize);
            
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, audioData.length);
                
                byte[] chunk = new byte[end - start];
                System.arraycopy(audioData, start, chunk, 0, chunk.length);
                
                // 청크 전송
                onAudioChunk.accept(chunk);
                
                log.debug("TTS 청크 전송 [{}]: {}/{} ({} bytes)", sessionId, i + 1, totalChunks, chunk.length);
                
                // 스트리밍 시뮬레이션을 위한 짧은 지연
                try {
                    Thread.sleep(20); // 50ms 지연
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // 완료 콜백
            onComplete.accept(null);
            log.info("TTS 완료 [{}]: {} 청크 전송 완료", sessionId, totalChunks);
            
        } catch (Exception e) {
            log.error("TTS 스트리밍 실패 [{}]: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 단순 TTS 변환 (스트리밍 없이)
     */
    public byte[] synthesize(String text) {
        return ttsService.synthesizeText(text);
    }
} 