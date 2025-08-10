package com.example.unithon.global.gcp;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Slf4j
public class SttStreamingService {

    private final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();

    /**
     * STT 스트리밍 세션 시작
     */
    public void startStreaming(String sessionId, Consumer<String> onTranscript, Consumer<String> onFinalTranscript) {
        try {
            SpeechClient speechClient = SpeechClient.create();

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)  // WebSocket에서 많이 사용
                .setSampleRateHertz(16000)
                .setLanguageCode("ko-KR")
                .setEnableAutomaticPunctuation(true)
                .setModel("latest_short")
                .build();

            StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                .setConfig(recognitionConfig)
                .setInterimResults(true)  // 중간 결과도 받기
                .setSingleUtterance(false)  // 연속 인식
                .build();

            ResponseObserver<StreamingRecognizeResponse> responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {
                @Override
                public void onStart(StreamController controller) {
                    log.info("STT 스트리밍 시작 [{}]", sessionId);
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    for (StreamingRecognitionResult result : response.getResultsList()) {
                        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                        String transcript = alternative.getTranscript();
                        
                        if (result.getIsFinal()) {
                            log.info("STT 최종 결과 [{}]: {}", sessionId, transcript);
                            onFinalTranscript.accept(transcript);
                        } else {
                            log.debug("STT 중간 결과 [{}]: {}", sessionId, transcript);
                            onTranscript.accept(transcript);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("STT 스트리밍 오류 [{}]: {}", sessionId, t.getMessage(), t);
                    stopStreaming(sessionId);
                }

                @Override
                public void onComplete() {
                    log.info("STT 스트리밍 완료 [{}]", sessionId);
                    stopStreaming(sessionId);
                }
            };

            ClientStream<StreamingRecognizeRequest> clientStream = speechClient.streamingRecognizeCallable()
                .splitCall(responseObserver);

            StreamingRecognizeRequest configRequest = StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(streamingConfig)
                .build();
            clientStream.send(configRequest);

            StreamingSession session = new StreamingSession(speechClient, clientStream);
            streamingSessions.put(sessionId, session);

        } catch (Exception e) {
            log.error("STT 스트리밍 시작 실패 [{}]: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 오디오 청크 전송
     */
    public void sendAudioChunk(String sessionId, byte[] audioData) {
        StreamingSession session = streamingSessions.get(sessionId);
        if (session != null && session.clientStream != null) {
            try {
                StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(audioData))
                    .build();
                session.clientStream.send(audioRequest);
                
                log.debug("오디오 청크 전송 [{}]: {} bytes", sessionId, audioData.length);
            } catch (Exception e) {
                log.error("오디오 청크 전송 실패 [{}]: {}", sessionId, e.getMessage(), e);
            }
        } else {
            log.warn("STT 세션을 찾을 수 없음: {}", sessionId);
        }
    }

    /**
     * STT 스트리밍 세션 종료
     */
    public void stopStreaming(String sessionId) {
        StreamingSession session = streamingSessions.remove(sessionId);
        if (session != null) {
            try {
                if (session.clientStream != null) {
                    session.clientStream.closeSend();
                }
                if (session.speechClient != null) {
                    session.speechClient.close();
                }
                log.info("STT 스트리밍 세션 종료 [{}]", sessionId);
            } catch (Exception e) {
                log.error("STT 세션 종료 중 오류 [{}]: {}", sessionId, e.getMessage(), e);
            }
        }
    }

    /**
     * 스트리밍 세션 정보
     */
    private static class StreamingSession {
        final SpeechClient speechClient;
        final ClientStream<StreamingRecognizeRequest> clientStream;

        StreamingSession(SpeechClient speechClient, ClientStream<StreamingRecognizeRequest> clientStream) {
            this.speechClient = speechClient;
            this.clientStream = clientStream;
        }
    }
} 