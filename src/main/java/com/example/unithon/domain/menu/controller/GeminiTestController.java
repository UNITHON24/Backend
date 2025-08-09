package com.example.unithon.domain.menu.controller;

import com.example.unithon.global.client.gemini.GeminiService;
import com.example.unithon.global.client.gemini.GeminiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gemini")
@RequiredArgsConstructor
public class GeminiTestController {
    
    private final GeminiService geminiService;
    private final GeminiProperties geminiProperties;
    
    @PostMapping("/generate")
    public String generateText(@RequestBody String prompt) {
        return geminiService.generateText(prompt);
    }
    
    @GetMapping("/test")
    public String testGemini(@RequestParam(defaultValue = "안녕하세요! 자기소개를 해주세요.") String prompt) {
        return geminiService.generateText(prompt);
    }
    
    @GetMapping("/debug")
    public String debugConfig() {
        return String.format("API Key: %s, URL: %s, Path: %s", 
            geminiProperties.key().substring(0, Math.min(10, geminiProperties.key().length())) + "...",
            geminiProperties.url(),
            geminiProperties.path()
        );
    }
} 