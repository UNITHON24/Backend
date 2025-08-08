package com.example.unithon.global.client.gemini;


import java.util.List;

public record GeminiRequest(
	List<Content> contents
) {
	public record Content(
		List<Part> parts
	) {
		public record Part(
			String text
		) {
		}
	}
	public static GeminiRequest of(String prompt) {
		return new GeminiRequest(
			List.of(
				new Content(
					List.of(new Content.Part(prompt))
				)
			)
		);
	}
}