package com.example.unithon.global.gcp;

import com.example.unithon.global.error.exception.BusinessException;
import com.example.unithon.global.error.exception.GlobalExceptionMessage;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Service
@Slf4j
public class TtsService {

    public byte[] synthesizeText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(GlobalExceptionMessage.TEXT_EMPTY);
        }
        
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            // 1. 변환할 텍스트 설정
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build();

            // 2. 목소리 선택 설정 (한국어, 여성, 표준 목소리)
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("ko-KR")
                    .setSsmlGender(SsmlVoiceGender.FEMALE) // 또는 NEUTRAL, MALE
                    .setName("ko-KR-Standard-A") // 상세 목소리 선택
                    .build();

            // 3. 오디오 출력 형식 설정 (MP3)
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            // 4. TTS 요청 보내기
            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(
                    input, voice, audioConfig
            );

            // 5. 응답에서 오디오 콘텐츠(ByteString)를 추출하여 byte 배열로 변환
            ByteString audioContents = response.getAudioContent();
            return audioContents.toByteArray();
        } catch (IOException e) {
            log.error("TTS 서비스 오류: {}", e.getMessage(), e);
            throw new BusinessException(GlobalExceptionMessage.TTS_SERVICE_ERROR);
        } catch (Exception e) {
            log.error("TTS 서비스 예상치 못한 오류: {}", e.getMessage(), e);
            throw new BusinessException(GlobalExceptionMessage.TTS_SERVICE_ERROR);
        }
    }
}
