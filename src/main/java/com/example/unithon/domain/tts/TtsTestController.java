package com.example.unithon.domain.tts;

import com.example.unithon.domain.tts.dto.TtsRequest;
import com.example.unithon.global.gcp.TtsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/tts")
@RequiredArgsConstructor
public class TtsTestController {

    private final TtsService ttsService;

    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesizeSpeech(@RequestBody TtsRequest request) {
        byte[] audioBytes = ttsService.synthesizeText(request.getText());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "speech.mp3"); // 파일로 다운로드되도록 설정
        headers.setContentLength(audioBytes.length);

        return new ResponseEntity<>(audioBytes, headers, HttpStatus.OK);
    }
}
