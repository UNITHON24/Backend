package com.example.unithon.global.config;

import com.example.unithon.global.client.gemini.GeminiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value = {GeminiProperties.class})
public class PropertiesConfig {
}