package com.example.unithon.domain.chat.handler;

import com.example.unithon.domain.chat.service.ChatService;
import com.example.unithon.domain.chat.dto.MacroOrderData;
import com.example.unithon.domain.chat.dto.MacroTriggerEvent;

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
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            } catch (Exception e) {
                log.error("macro.trigger 이벤트 발송 실패 [{}]: {}", sessionId, e.getMessage(), e);
            }
        } else {
            log.warn("WebSocket 세션을 찾을 수 없음: {}", sessionId);
        }
    }
} 