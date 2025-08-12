package com.example.unithon.domain.chat.handler;

import com.example.unithon.domain.chat.dto.*;
import com.example.unithon.domain.chat.service.ChatService;
import com.example.unithon.global.gcp.SttStreamingService;
import com.example.unithon.global.gcp.TtsStreamingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    // WebSocket 메시지 타입 상수 정의
    private static final class MessageType {
        // Client -> Server
        static final String CLIENT_TEXT = "client.text";
        static final String CLIENT_COMMAND = "client.command";
        static final String AUDIO_START = "audio.start";
        static final String AUDIO_CHUNK = "audio.chunk";
        static final String AUDIO_END = "audio.end";

        // Server -> Client
        static final String CONNECTION_SUCCESS = "connection";
        static final String BOT_REPLY = "bot.reply";
        static final String MACRO_TRIGGER = "macro.trigger";
        static final String DIALOG_STATE = "dialog.state";
        static final String SERVER_ERROR = "server.error";
        static final String TRANSCRIPT_PARTIAL = "transcript.partial";
        static final String TRANSCRIPT_FINAL = "transcript.final";
        static final String TTS_CHUNK = "tts.chunk";
        static final String TTS_COMPLETE = "tts.complete";
        static final String CONVERSATION_COMPLETE = "conversation.complete";
    }

    // 세션 상태 관리 enum
    private enum SessionState {
        IDLE, LISTENING, PROCESSING, ENDED
    }

    private final ChatService chatService;
    private final SttStreamingService sttStreamingService;
    private final TtsStreamingService ttsStreamingService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${feature.tts:false}")
    private boolean ttsEnabled;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    @Autowired
    public ChatWebSocketHandler(ChatService chatService,
                                @Autowired(required = false) SttStreamingService sttStreamingService,
                                @Autowired(required = false) TtsStreamingService ttsStreamingService,
                                TaskExecutor taskExecutor) {
        this.chatService = chatService;
        this.sttStreamingService = sttStreamingService;
        this.ttsStreamingService = ttsStreamingService;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionStates.put(sessionId, SessionState.IDLE);
        log.info("WebSocket 연결 성공: {}", sessionId);
        sendMessage(session, MessageType.CONNECTION_SUCCESS, "채팅이 연결되었습니다. 주문하실 메뉴를 말씀해주세요.");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload().toString();
        log.debug("수신 메시지 [{}]: {}", sessionId, payload);

        try {
            JsonNode messageNode = objectMapper.readTree(payload);
            String type = messageNode.get("type").asText();

            switch (type) {
                case MessageType.CLIENT_TEXT -> handleTextMessage(session, messageNode);
                case MessageType.CLIENT_COMMAND -> handleCommandMessage(session, messageNode);
                case MessageType.AUDIO_START -> handleAudioStart(session);
                case MessageType.AUDIO_CHUNK -> handleAudioChunk(session, messageNode);
                case MessageType.AUDIO_END -> handleAudioEnd(session);
                default -> log.warn("알 수 없는 메시지 타입: {}", type);
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패 [{}]: {}", sessionId, e.getMessage(), e);
            sendMessage(session, MessageType.SERVER_ERROR, "메시지 처리 중 오류가 발생했습니다.");
        }
    }

    private void handleTextMessage(WebSocketSession session, JsonNode messageNode) throws IOException {
        String userMessage = messageNode.get("message").asText();
        String sessionId = session.getId();
        log.info("텍스트 메시지 처리 [{}]: {}", sessionId, userMessage);

        String botResponse = chatService.processMessage(sessionId, userMessage);
        sendMessage(session, MessageType.BOT_REPLY, botResponse);
    }

    private void handleCommandMessage(WebSocketSession session, JsonNode messageNode) throws IOException {
        String action = messageNode.get("action").asText();
        String sessionId = session.getId();
        log.info("명령 메시지 처리 [{}]: {}", sessionId, action);

        switch (action) {
            case "confirm" -> {
                String confirmResult = chatService.confirmOrder(sessionId);
                sendMessage(session, MessageType.MACRO_TRIGGER, confirmResult);
            }
            case "cancel" -> {
                chatService.cancelOrder(sessionId);
                sendMessage(session, MessageType.BOT_REPLY, "주문이 취소되었습니다.");
            }
            case "repeat" -> {
                String lastQuestion = chatService.getLastQuestion(sessionId);
                sendMessage(session, MessageType.BOT_REPLY, lastQuestion);
            }
            default -> log.warn("알 수 없는 명령: {}", action);
        }
    }

    private void handleAudioStart(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("오디오 스트리밍 시작 [{}]", sessionId);

        if (sttStreamingService == null) {
            handleServiceDisabled(session, "STT");
            return;
        }

        sessionStates.put(sessionId, SessionState.LISTENING);
        sttStreamingService.startStreaming(
                sessionId,
                (partialTranscript) -> sendTranscript(session, MessageType.TRANSCRIPT_PARTIAL, partialTranscript),
                (finalTranscript) -> taskExecutor.execute(() -> {
                    if (finalTranscript.isBlank()) {
                        log.warn("STT 최종 결과가 비어있어 처리를 건너뜁니다. [{}]", sessionId);
                        return;
                    }
                    sessionStates.put(sessionId, SessionState.PROCESSING);
                    sendTranscript(session, MessageType.TRANSCRIPT_FINAL, finalTranscript);

                    // 이 블록은 이제 별도의 스레드에서 실행됩니다.
                    String botResponse = chatService.processMessage(sessionId, finalTranscript);
                    try {
                        sendMessage(session, MessageType.BOT_REPLY, botResponse);
                    } catch (IOException e) {
                        log.error("봇 응답 전송 실패 [{}]: {}", sessionId, e.getMessage());
                    }
                })
        );
    }

    private void handleAudioChunk(WebSocketSession session, JsonNode messageNode) {
        String sessionId = session.getId();

        if (sessionStates.get(sessionId) != SessionState.LISTENING) {
            return;
        }

        try {
            String audioDataBase64 = messageNode.get("audioData").asText();
            byte[] audioData = Base64.getDecoder().decode(audioDataBase64);
            if (sttStreamingService != null) {
                sttStreamingService.sendAudioChunk(sessionId, audioData);
            }
        } catch (Exception e) {
            log.error("오디오 청크 처리 실패 [{}]: {}", sessionId, e.getMessage(), e);
        }
    }

    private void handleAudioEnd(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("오디오 스트리밍 종료 [{}]", sessionId);

        sessionStates.put(sessionId, SessionState.PROCESSING);

        if (sttStreamingService != null) {
            sttStreamingService.endAudioStream(sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 전송 오류 [{}]: {}", session.getId(), exception.getMessage(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String sessionId = session.getId();

        sessionStates.remove(sessionId);
        sessions.remove(sessionId);

        try {
            if (sttStreamingService != null) {
                sttStreamingService.stopStreaming(sessionId);
            }
        } catch (Exception e) {
            log.error("STT 스트리밍 정리 실패 [{}]: {}", sessionId, e.getMessage());
        }

        try {
            chatService.clearSession(sessionId);
        } catch (Exception e) {
            log.error("채팅 세션 정리 실패 [{}]: {}", sessionId, e.getMessage());
        }

        log.info("WebSocket 연결 종료 [{}]: {}", sessionId, closeStatus);
    }

    private void sendMessage(WebSocketSession session, String type, String message) throws IOException {
        if (session.isOpen()) {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("type", type);
            messageData.put("message", message);
            String jsonMessage = objectMapper.writeValueAsString(messageData);
            session.sendMessage(new TextMessage(jsonMessage));

            if (ttsEnabled && MessageType.BOT_REPLY.equals(type)) {
                startTtsSynthesis(session, message);
            }
        }
    }

    private void sendTranscript(WebSocketSession session, String type, String transcript) {
        if (session.isOpen()) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", type);
                message.put("transcript", transcript);
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("Transcript 전송 실패 [{}]: {}", session.getId(), e.getMessage());
            }
        }
    }

    private void startTtsSynthesis(WebSocketSession session, String text) {
        String sessionId = session.getId();
        if (ttsStreamingService == null) {
            handleServiceDisabled(session, "TTS");
            return;
        }

        taskExecutor.execute(() -> ttsStreamingService.synthesizeAndStream(
                sessionId, text,
                (audioChunk) -> sendTtsChunk(session, audioChunk),
                (ignored) -> sendTtsComplete(session)
        ));
    }

    private void sendTtsChunk(WebSocketSession session, byte[] audioChunk) {
        if (session.isOpen()) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", MessageType.TTS_CHUNK);
                message.put("audioData", Base64.getEncoder().encodeToString(audioChunk));
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("TTS 청크 전송 실패 [{}]: {}", session.getId(), e.getMessage());
            }
        }
    }

    private void sendTtsComplete(WebSocketSession session) {
        if (session.isOpen()) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", MessageType.TTS_COMPLETE);
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("TTS 완료 전송 실패 [{}]: {}", session.getId(), e.getMessage());
            }
        }
    }

    private void handleServiceDisabled(WebSocketSession session, String serviceName) {
        log.warn("{} 서비스가 비활성화됨 [{}]", serviceName, session.getId());
        try {
            sendMessage(session, MessageType.SERVER_ERROR, serviceName + " 서비스가 비활성화되어 있습니다.");
        } catch (IOException e) {
            log.error("에러 메시지 전송 실패 [{}]: {}", session.getId(), e.getMessage());
        }
    }

    @EventListener
    public void handleMacroTriggerEvent(MacroTriggerEvent event) {
        sendMacroTrigger(event.getSessionId(), event.getOrderData());
    }

    public void sendMacroTrigger(String sessionId, MacroOrderData orderData) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("type", MessageType.MACRO_TRIGGER);
                eventData.put("orderData", orderData);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(eventData)));
                log.info("macro.trigger 이벤트 발송 완료 [{}]", sessionId);

                Map<String, Object> completeEvent = new HashMap<>();
                completeEvent.put("type", MessageType.CONVERSATION_COMPLETE);
                completeEvent.put("message", "주문이 완료되었습니다. 대화를 종료합니다.");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(completeEvent)));
                log.info("conversation.complete 이벤트 발송 완료 [{}]", sessionId);
            } catch (Exception e) {
                log.error("macro.trigger 이벤트 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
            }
        }
    }

    @EventListener
    public void handleDialogStateEvent(DialogStateEvent event) {
        sendDialogState(event.getSessionId(), event.getDialogState());
    }

    public void sendDialogState(String sessionId, DialogState dialogState) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> stateMessage = new HashMap<>();
                stateMessage.put("type", MessageType.DIALOG_STATE);
                stateMessage.put("state", dialogState);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(stateMessage)));
            } catch (Exception e) {
                log.error("dialog.state 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
            }
        }
    }

    @EventListener
    public void handleServerErrorEvent(ServerErrorEvent event) {
        sendServerError(event.getSessionId(), event);
    }

    public void sendServerError(String sessionId, ServerErrorEvent errorEvent) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("type", MessageType.SERVER_ERROR);
                errorMessage.put("errorCode", errorEvent.getErrorCode());
                errorMessage.put("message", errorEvent.getMessage());
                errorMessage.put("retryable", errorEvent.isRetryable());
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
            } catch (Exception e) {
                log.error("server.error 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
