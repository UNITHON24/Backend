package com.example.unithon.global.client.gemini;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {
    
    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    
    public String generateText(String prompt) {
        try {
            log.info("Calling Gemini API with prompt: {}", prompt);
            
            GeminiRequest request = GeminiRequest.of(prompt);
            GeminiResponse response = geminiClient.generateContent(
                geminiProperties.key(), 
                request
            );
            
            String result = response.getFirstResponseText();
            log.info("Received response from Gemini API: {}", result);
            
            return result;
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to generate text from Gemini API", e);
        }
    }
} 