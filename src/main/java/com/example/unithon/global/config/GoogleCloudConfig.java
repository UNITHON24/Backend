package com.example.unithon.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
@Slf4j
public class GoogleCloudConfig {

    @Bean
    @ConditionalOnProperty(name = "feature.stt", havingValue = "true")
    public SpeechClient speechClient() {
        try {
            log.info("SpeechClient 생성 중... (JSON 파일 사용)");
            
            GoogleCredentials credentials;

            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                log.info("환경변수에서 인증 파일 사용: {}", credentialsPath);
                credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
            } else {
                log.info("클래스패스에서 인증 파일 사용");
                ClassPathResource resource = new ClassPathResource("unithon-4b2df7873498.json");
                try (InputStream inputStream = resource.getInputStream()) {
                    credentials = GoogleCredentials.fromStream(inputStream);
                }
            }
            
            SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
                
            log.info("SpeechClient 생성 완료");
            return SpeechClient.create(settings);
        } catch (Exception e) {
            log.error("SpeechClient 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create SpeechClient", e);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "feature.tts", havingValue = "true")
    public TextToSpeechClient textToSpeechClient() {
        try {
            log.info("TextToSpeechClient 생성 중... (JSON 파일 사용)");
            
            GoogleCredentials credentials;

            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                log.info("환경변수에서 인증 파일 사용: {}", credentialsPath);
                credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
            } else {
                log.info("클래스패스에서 인증 파일 사용");
                ClassPathResource resource = new ClassPathResource("unithon-4b2df7873498.json");
                try (InputStream inputStream = resource.getInputStream()) {
                    credentials = GoogleCredentials.fromStream(inputStream);
                }
            }
            
            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
                
            log.info("TextToSpeechClient 생성 완료");
            return TextToSpeechClient.create(settings);
        } catch (Exception e) {
            log.error("TextToSpeechClient 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create TextToSpeechClient", e);
        }
    }
}
