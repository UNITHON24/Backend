package com.example.unithon.global.client.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(
	List<Candidate> candidates
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Candidate(
		Content content
	) {
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Content(
			List<Part> parts,
			String role
		) {
			@JsonIgnoreProperties(ignoreUnknown = true)
			public record Part(
				String text
			) {
			}
		}
	}
	public String getFirstResponseText() {
		if (candidates != null && !candidates.isEmpty()) {
			Candidate firstCandidate = candidates.get(0);
			if (firstCandidate.content() != null &&
				firstCandidate.content().parts() != null &&
				!firstCandidate.content().parts().isEmpty()) {
				return firstCandidate.content().parts().get(0).text();
			}
		}
		return "";
	}
}