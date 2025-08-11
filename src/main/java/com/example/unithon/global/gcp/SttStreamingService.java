package com.example.unithon.global.gcp;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "feature.stt", havingValue = "true")
public class SttStreamingService {

    private final SpeechClient speechClient;
    private final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    /**
     * STT 스트리밍 세션 시작
     */
    public void startStreaming(String sessionId, Consumer<String> onPartialResult, Consumer<String> onFinalResult) {
        try {
            ResponseObserver<StreamingRecognizeResponse> responseObserver = new ResponseObserver<>() {
                @Override
                public void onStart(StreamController controller) {
                    log.debug("STT 스트리밍 시작됨 [{}]", sessionId);
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    StreamingSession session = streamingSessions.get(sessionId);
                    // 세션이 이미 종료된 후 도착하는 응답은 무시
                    if (session == null) return;

                    for (StreamingRecognitionResult result : response.getResultsList()) {
                        if (result.getAlternativesCount() > 0) {
                            SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                            String transcript = alternative.getTranscript();

                            if (result.getIsFinal()) {
                                log.info("STT 최종 결과 [{}]: {}", sessionId, transcript);
                                session.cancelTimeoutTask(); // 최종 결과를 받았으므로 타임아웃 취소
                                onFinalResult.accept(transcript);
                            } else {
                                log.debug("STT 중간 결과 [{}]: {}", sessionId, transcript);
                                onPartialResult.accept(transcript);
                            }
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("STT 스트리밍 오류 [{}]: {}", sessionId, t.getMessage());
                    // 오류 발생 시에도 리소스 정리
                    cleanupSession(sessionId);
                }

                @Override
                public void onComplete() {
                    log.info("STT 스트리밍 완료 (onComplete) [{}]", sessionId);
                    // Google 서버가 스트림을 닫았을 때 호출됨. 리소스만 정리.
                    cleanupSession(sessionId);
                }
            };

            ClientStream<StreamingRecognizeRequest> clientStream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("ko-KR")
                    .setEnableAutomaticPunctuation(true)
                    .setModel("latest_short")
                    .build();

            StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(true)
                    .setSingleUtterance(true)
                    .build();

            StreamingRecognizeRequest configRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build();
            clientStream.send(configRequest);

            // 10초 후 자동 종료 스케줄링
            ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
                log.warn("STT 세션 타임아웃 [{}] - 10초 경과로 종료", sessionId);
                onFinalResult.accept(""); // 타임아웃 시 빈 최종 결과 전송
                endAudioStream(sessionId); // 스트림을 정상적으로 종료 시도
            }, 10, TimeUnit.SECONDS);

            streamingSessions.put(sessionId, new StreamingSession(clientStream, timeoutTask));

        } catch (Exception e) {
            log.error("STT 스트리밍 시작 실패 [{}]: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 오디오 청크 전송
     */
    public void sendAudioChunk(String sessionId, byte[] audioData) {
        StreamingSession session = streamingSessions.get(sessionId);
        if (session != null && !session.isClosing()) {
            try {
                StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(audioData))
                        .build();
                session.clientStream.send(audioRequest);
            } catch (Exception e) {
                log.error("오디오 청크 전송 실패 [{}]: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 오디오 스트림 끝 알림 (최종 인식 결과를 위해)
     */
    public void endAudioStream(String sessionId) {
        StreamingSession session = streamingSessions.get(sessionId);
        if (session != null && session.setClosing()) { // setClosing()이 true를 반환할 때만 실행 (최초 1회)
            try {
                log.info("STT 오디오 스트림 종료 신호 전송 [{}]", sessionId);
                session.clientStream.closeSend();
            } catch (Exception e) {
                log.error("STT 오디오 스트림 종료 실패 [{}]: {}", sessionId, e.getMessage());
                cleanupSession(sessionId); // 실패 시에도 리소스 정리
            }
        }
    }

    /**
     * STT 스트리밍 세션 종료
     */
    public void stopStreaming(String sessionId) {
        StreamingSession session = streamingSessions.get(sessionId);
        if (session != null) {
            if (session.setClosing()) { // 아직 닫는 중이 아닐 경우에만 closeSend() 호출
                try {
                    session.clientStream.closeSend();
                    log.info("STT 스트리밍 강제 종료 [{}]", sessionId);
                } catch (Exception e) {
                    log.error("STT 세션 강제 종료 중 오류 [{}]: {}", sessionId, e.getMessage());
                }
            }
            cleanupSession(sessionId);
        }
    }

    private void cleanupSession(String sessionId) {
        StreamingSession session = streamingSessions.remove(sessionId);
        if (session != null) {
            session.cancelTimeoutTask();
            log.info("STT 세션 리소스 정리 완료 [{}]", sessionId);
        }
    }

    private static class StreamingSession {
        final ClientStream<StreamingRecognizeRequest> clientStream;
        final ScheduledFuture<?> timeoutTask;
        private boolean closing = false;

        StreamingSession(ClientStream<StreamingRecognizeRequest> clientStream, ScheduledFuture<?> timeoutTask) {
            this.clientStream = clientStream;
            this.timeoutTask = timeoutTask;
        }

        /**
         * 스트림이 닫히고 있는지 확인
         */
        synchronized boolean isClosing() {
            return closing;
        }

        /**
         * 스트림을 닫는 상태로 설정.
         * @return 성공적으로 상태를 변경했으면 true (최초 1회), 이미 닫는 중이면 false
         */
        synchronized boolean setClosing() {
            if (closing) {
                return false;
            }
            this.closing = true;
            return true;
        }

        void cancelTimeoutTask() {
            if (this.timeoutTask != null && !this.timeoutTask.isDone()) {
                this.timeoutTask.cancel(false);
            }
        }
    }
} 