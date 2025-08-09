package com.example.unithon.global.gcp;

import com.example.unithon.global.error.exception.BusinessException;
import com.example.unithon.global.error.exception.GlobalExceptionMessage;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SttService {

    /**
     * 오디오 파일을 텍스트로 변환합니다.
     * @param audioFile 변환할 오디오 파일
     * @return 변환된 텍스트
     */
    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(GlobalExceptionMessage.AUDIO_FILE_INVALID);
        }
        
        try (SpeechClient speechClient = SpeechClient.create()) {
            // 오디오 파일을 ByteString 으로 읽어오기
            ByteString audioBytes = ByteString.copyFrom(audioFile.getBytes());

            // 인식기(Recognizer) 설정 구성
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.FLAC) // 오디오 파일 형식에 맞게 설정 (예: FLAC, MP3)
                            //.setSampleRateHertz(16000) // 오디오 파일의 샘플링 레이트
                            .setAudioChannelCount(2) // 오디오 채널이 2개(스테레오)임을 명시
                            .setEnableSeparateRecognitionPerChannel(true) // 채널별로 음성을 분리하여 인식
                            .setLanguageCode("ko-KR") // 한국어 설정
                            .build();

            // 인식할 오디오 설정
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

            // STT 요청 보내기
            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            // 변환 결과 추출
            StringBuilder transcription = new StringBuilder();
            for (SpeechRecognitionResult result : results) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                transcription.append(alternative.getTranscript());
            }

            if (transcription.length() == 0) {
                return "음성을 인식하지 못했습니다.";
            }

            return transcription.toString();
        } catch (IOException e) {
            log.error("STT 서비스 오류: {}", e.getMessage(), e);
            throw new BusinessException(GlobalExceptionMessage.STT_SERVICE_ERROR);
        } catch (Exception e) {
            log.error("STT 서비스 예상치 못한 오류: {}", e.getMessage(), e);
            throw new BusinessException(GlobalExceptionMessage.STT_SERVICE_ERROR);
        }
    }
}