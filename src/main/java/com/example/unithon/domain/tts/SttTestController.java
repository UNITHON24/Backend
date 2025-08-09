package com.example.unithon.domain.tts;

import com.example.unithon.global.gcp.SttService;
import com.example.unithon.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/stt")
@RequiredArgsConstructor
public class SttTestController {

    private final SttService sttService;

    @PostMapping("/transcribe")
    public ResponseEntity<ApiResponse<String>> performStt(@RequestParam("file") MultipartFile file) throws IOException {
        String transcription = sttService.transcribe(file);
        return ResponseEntity.ok(ApiResponse.success("음성 변환이 완료되었습니다.", transcription));
    }
}
