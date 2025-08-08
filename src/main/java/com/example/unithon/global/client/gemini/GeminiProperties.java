package com.example.unithon.global.client.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini.api")
public record GeminiProperties(
	String key,
	String url,
	String path
) {
}