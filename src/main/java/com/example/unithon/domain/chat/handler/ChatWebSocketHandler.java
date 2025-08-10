package com.example.unithon.domain.chat.handler;

import com.example.unithon.domain.chat.service.ChatService;
import com.example.unithon.domain.chat.dto.MacroOrderData;
import com.example.unithon.domain.chat.dto.MacroTriggerEvent;
import com.example.unithon.domain.chat.dto.DialogState;
import com.example.unithon.domain.chat.dto.DialogStateEvent;
import com.example.unithon.domain.chat.dto.ServerErrorEvent;
import com.example.unithon.global.gcp.SttStreamingService;
import com.example.unithon.global.gcp.TtsStreamingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.event.EventListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    
    @Autowired(required = false)
    private SttStreamingService sttStreamingService;
    
    @Autowired(required = false)
    private TtsStreamingService ttsStreamingService;
    
    public ChatWebSocketHandler(ChatService chatService) {
        this.chatService = chatService;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${feature.tts:false}")
    private boolean ttsEnabled;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket 연결 성공: {}", sessionId);

        sendMessage(session, "connection", "채팅이 연결되었습니다. 주문하실 메뉴를 말씀해주세요.");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload().toString();
        
        log.info("수신 메시지 [{}]: {}", sessionId, payload);
        
        try {
            JsonNode messageNode = objectMapper.readTree(payload);
            String type = messageNode.get("type").asText();
            
            switch (type) {
                case "client.text":
                    handleTextMessage(session, messageNode);
                    break;
                case "client.command":
                    handleCommandMessage(session, messageNode);
                    break;
                case "audio.start":
                    handleAudioStart(session, messageNode);
                    break;
                case "audio.chunk":
                    handleAudioChunk(session, messageNode);
                    break;
                case "audio.end":
                    handleAudioEnd(session, messageNode);
                    break;
                default:
                    log.warn("알 수 없는 메시지 타입: {}", type);
            }
            
        } catch (Exception e) {
            log.error("메시지 처리 실패 [{}]: {}", sessionId, e.getMessage(), e);
            sendMessage(session, "server.error", "메시지 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 텍스트 메시지 처리 (1단계 MVP용)
     */
    private void handleTextMessage(WebSocketSession session, JsonNode messageNode) throws IOException {
        String userMessage = messageNode.get("message").asText();
        String sessionId = session.getId();
        
        log.info("텍스트 메시지 처리 [{}]: {}", sessionId, userMessage);

        String botResponse = chatService.processMessage(sessionId, userMessage);

        sendMessage(session, "bot.reply", botResponse);

        if (botResponse.contains("결제 해주시길 바랍니다")) {
            log.info("주문 완료로 인한 WebSocket 연결 종료 [{}]", sessionId);
            try {
                session.close();
            } catch (Exception e) {
                log.error("WebSocket 연결 종료 실패 [{}]: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 명령 메시지 처리
     */
    private void handleCommandMessage(WebSocketSession session, JsonNode messageNode) throws IOException {
        String action = messageNode.get("action").asText();
        String sessionId = session.getId();
        
        log.info("명령 메시지 처리 [{}]: {}", sessionId, action);
        
        switch (action) {
            case "confirm":
                String confirmResult = chatService.confirmOrder(sessionId);
                sendMessage(session, "macro.trigger", confirmResult);
                break;
            case "cancel":
                chatService.cancelOrder(sessionId);
                sendMessage(session, "bot.reply", "주문이 취소되었습니다.");
                break;
            case "repeat":
                String lastQuestion = chatService.getLastQuestion(sessionId);
                sendMessage(session, "bot.reply", lastQuestion);
                break;
            default:
                log.warn("알 수 없는 명령: {}", action);
        }
    }

    /**
     * 메시지 전송 헬퍼
     */
    private void sendMessage(WebSocketSession session, String type, String message) throws IOException {
        if (session.isOpen()) {
            try {
                Map<String, Object> messageData = new HashMap<>();
                messageData.put("type", type);
                messageData.put("message", message);
                messageData.put("timestamp", System.currentTimeMillis());
                
                String jsonMessage = objectMapper.writeValueAsString(messageData);
                
                session.sendMessage(new TextMessage(jsonMessage));
                log.debug("메시지 전송 [{}]: {}", session.getId(), type);
                
                // TTS가 활성화되고 bot.reply인 경우 음성 합성
                if (ttsEnabled && "bot.reply".equals(type)) {
                    startTtsSynthesis(session, message);
                }
                
            } catch (Exception e) {
                log.error("메시지 전송 실패 [{}]: {}", session.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        log.error("WebSocket 전송 오류 [{}]: {}", sessionId, exception.getMessage(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);

        // STT 세션 정리
        if (sttStreamingService != null) {
            sttStreamingService.stopStreaming(sessionId);
        }
        
        chatService.clearSession(sessionId);
        
        log.info("WebSocket 연결 종료 [{}]: {}", sessionId, closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * macro.trigger 이벤트 리스너
     */
    @EventListener
    public void handleMacroTriggerEvent(MacroTriggerEvent event) {
        sendMacroTrigger(event.getSessionId(), event.getOrderData());
    }

    /**
     * dialog.state 이벤트 리스너
     */
    @EventListener
    public void handleDialogStateEvent(DialogStateEvent event) {
        sendDialogState(event.getSessionId(), event.getDialogState());
    }

    /**
     * server.error 이벤트 리스너
     */
    @EventListener
    public void handleServerErrorEvent(ServerErrorEvent event) {
        sendServerError(event.getSessionId(), event);
    }

    /**
     * 매크로팀에게 macro.trigger 이벤트 발송
     */
    public void sendMacroTrigger(String sessionId, MacroOrderData orderData) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> macroEvent = new HashMap<>();
                macroEvent.put("type", "macro.trigger");
                macroEvent.put("orderData", orderData);
                
                String jsonMessage = objectMapper.writeValueAsString(macroEvent);
                session.sendMessage(new TextMessage(jsonMessage));
                
                log.info("macro.trigger 이벤트 발송 완료 [{}]", sessionId);
                
                // 주문 완료 후 대화 종료 신호 전송
                Map<String, Object> completeEvent = new HashMap<>();
                completeEvent.put("type", "conversation.complete");
                completeEvent.put("message", "주문이 완료되었습니다. 대화를 종료합니다.");
                
                String completeJsonMessage = objectMapper.writeValueAsString(completeEvent);
                session.sendMessage(new TextMessage(completeJsonMessage));
                
                log.info("conversation.complete 이벤트 발송 완료 [{}]", sessionId);
            } catch (Exception e) {
                log.error("macro.trigger 이벤트 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
            }
        } else {
            log.warn("WebSocket 세션을 찾을 수 없음: {}", sessionId);
        }
    }

    /**
     * 에이전트에게 dialog.state 메시지 발송
     */
    public void sendDialogState(String sessionId, DialogState dialogState) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> stateMessage = new HashMap<>();
                stateMessage.put("type", "dialog.state");
                stateMessage.put("state", dialogState);
                
                String jsonMessage = objectMapper.writeValueAsString(stateMessage);
                session.sendMessage(new TextMessage(jsonMessage));
                
                log.debug("dialog.state 발송 완료 [{}]: {}", sessionId, dialogState.getState());
            } catch (Exception e) {
                log.error("dialog.state 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
                         }
         }
     }

    /**
     * 에이전트에게 server.error 메시지 발송
     */
    public void sendServerError(String sessionId, ServerErrorEvent errorEvent) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("type", "server.error");
                errorMessage.put("errorCode", errorEvent.getErrorCode());
                errorMessage.put("message", errorEvent.getMessage());
                errorMessage.put("retryable", errorEvent.isRetryable());
                errorMessage.put("timestamp", errorEvent.getTimestamp().toString());
                
                String jsonMessage = objectMapper.writeValueAsString(errorMessage);
                session.sendMessage(new TextMessage(jsonMessage));
                
                log.warn("server.error 발송 완료 [{}]: {}", sessionId, errorEvent.getErrorCode());
            } catch (Exception e) {
                log.error("server.error 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
                         }
         }
     }

    /**
     * 오디오 스트리밍 시작 처리
     */
    private void handleAudioStart(WebSocketSession session, JsonNode messageNode) {
        String sessionId = session.getId();
        log.info("오디오 스트리밍 시작 [{}]", sessionId);
        
        if (sttStreamingService == null) {
            log.warn("STT 서비스가 비활성화됨 [{}]", sessionId);
            try {
                sendMessage(session, "server.error", "음성 인식 서비스가 비활성화되어 있습니다.");
            } catch (IOException e) {
                log.error("에러 메시지 전송 실패 [{}]: {}", sessionId, e.getMessage());
            }
            return;
        }
        
        // 기존 STT 세션이 있으면 먼저 정리
        sttStreamingService.stopStreaming(sessionId);
        
        // STT 스트리밍 세션 시작
        sttStreamingService.startStreaming(
            sessionId,
            // 중간 결과 처리 (transcript.partial)
            (partialTranscript) -> {
                try {
                    sendTranscriptPartial(session, partialTranscript);
                } catch (Exception e) {
                    log.error("중간 결과 전송 실패 [{}]: {}", sessionId, e.getMessage());
                }
            },
            // 최종 결과 처리 (transcript.final)
            (finalTranscript) -> {
                try {
                    sendTranscriptFinal(session, finalTranscript);
                    // 최종 결과를 ChatService로 전달
                    String botResponse = chatService.processMessage(sessionId, finalTranscript);
                    sendMessage(session, "bot.reply", botResponse);
                } catch (Exception e) {
                    log.error("최종 결과 처리 실패 [{}]: {}", sessionId, e.getMessage());
                }
            }
        );
    }

    /**
     * 오디오 청크 처리
     */
    private void handleAudioChunk(WebSocketSession session, JsonNode messageNode) {
        String sessionId = session.getId();
        
        try {
            String audioDataBase64 = messageNode.get("audioData").asText();
            byte[] audioData = java.util.Base64.getDecoder().decode(audioDataBase64);
            
            // STT 서비스로 오디오 청크 전송
            if (sttStreamingService != null) {
                sttStreamingService.sendAudioChunk(sessionId, audioData);
            }
            
            log.debug("오디오 청크 처리 [{}]: {} bytes", sessionId, audioData.length);
        } catch (Exception e) {
            log.error("오디오 청크 처리 실패 [{}]: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 오디오 스트리밍 종료 처리
     */
    private void handleAudioEnd(WebSocketSession session, JsonNode messageNode) {
        String sessionId = session.getId();
        log.info("오디오 스트리밍 종료 [{}]", sessionId);

        if (sttStreamingService != null) {
            // STT에게 오디오 스트림 종료 알림 (최종 인식 결과를 받기 위해)
            sttStreamingService.endAudioStream(sessionId);
        }
    }

    /**
     * STT 중간 결과 전송 (transcript.partial)
     */
    private void sendTranscriptPartial(WebSocketSession session, String transcript) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "transcript.partial");
        message.put("transcript", transcript);
        
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }

    /**
     * STT 최종 결과 전송 (transcript.final)
     */
    private void sendTranscriptFinal(WebSocketSession session, String transcript) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "transcript.final");
        message.put("transcript", transcript);
        
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }

    /**
     * TTS 음성 합성 시작
     */
    private void startTtsSynthesis(WebSocketSession session, String text) {
        String sessionId = session.getId();
        
        if (ttsStreamingService == null) {
            log.warn("TTS 서비스가 비활성화됨 [{}]", sessionId);
            return;
        }
        
        // 비동기로 TTS 처리
        new Thread(() -> {
            ttsStreamingService.synthesizeAndStream(
                sessionId,
                text,
                // 오디오 청크 콜백
                (audioChunk) -> {
                    try {
                        sendTtsChunk(session, audioChunk);
                    } catch (Exception e) {
                        log.error("TTS 청크 전송 실패 [{}]: {}", sessionId, e.getMessage());
                    }
                },
                // 완료 콜백
                (ignored) -> {
                    try {
                        sendTtsComplete(session);
                    } catch (Exception e) {
                        log.error("TTS 완료 전송 실패 [{}]: {}", sessionId, e.getMessage());
                    }
                }
            );
        }).start();
    }

    /**
     * TTS 오디오 청크 전송
     */
    private void sendTtsChunk(WebSocketSession session, byte[] audioChunk) throws Exception {
        if (session.isOpen()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "tts.chunk");
            message.put("audioData", java.util.Base64.getEncoder().encodeToString(audioChunk));
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        }
    }

    /**
     * TTS 완료 알림
     */
    private void sendTtsComplete(WebSocketSession session) throws Exception {
        if (session.isOpen()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "tts.complete");
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        }
    }

    /**
     * 마이크 중지 신호 전송
     */
    private void sendMicrophoneStop(WebSocketSession session, String reason) throws Exception {
        if (session.isOpen()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "mic.stop");
            message.put("reason", reason);
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
            log.info("마이크 중지 신호 전송 [{}]: {}", session.getId(), reason);
        }
    }
} 