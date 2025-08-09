package com.example.unithon.domain.chat.service;

import com.example.unithon.domain.menu.service.MenuService;
import com.example.unithon.domain.menu.service.MenuSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MenuService menuService;

    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 메시지 처리 (1단계 MVP - 단순 메뉴 검색)
     */
    public String processMessage(String sessionId, String userMessage) {
        log.info("메시지 처리 시작 [{}]: {}", sessionId, userMessage);
        
        try {
            MenuSearchResult searchResult = menuService.searchMenu(userMessage);

            String response = generateResponse(searchResult);

            updateSession(sessionId, response);
            
            return response;
            
        } catch (Exception e) {
            log.error("메시지 처리 실패 [{}]: {}", sessionId, e.getMessage(), e);
            return "죄송합니다. 처리 중 오류가 발생했습니다. 다시 말씀해 주세요.";
        }
    }

    /**
     * 검색 결과에 따른 응답 생성
     */
    private String generateResponse(MenuSearchResult result) {
        switch (result.getType()) {
            case DIRECT_MATCH:
                // DB에서 바로 찾은 경우
                return String.format("%s 맞나요? 사이즈는 어떻게 하시겠어요?", 
                                   result.getMenu().getDisplayName());
            
            case GEMINI_SUGGESTION:
                // Gemini 추천인 경우
                if (result.getSuggestions() != null && !result.getSuggestions().isEmpty()) {
                    String menuNames = result.getSuggestions().stream()
                        .map(menu -> menu.getDisplayName())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                    return String.format("혹시 %s 중에서 찾으시는 건가요?", menuNames);
                } else {
                    // NO_MATCH 케이스
                    return result.getGeminiResponse();
                }
            
            case NO_MATCH:
            default:
                return "죄송합니다. 찾으시는 메뉴를 찾을 수 없습니다. 다른 메뉴를 말씀해 주세요.";
        }
    }

    /**
     * 주문 확정
     */
    public String confirmOrder(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            return "주문 정보를 찾을 수 없습니다.";
        }
        
        // TODO: 실제 주문 정보를 매크로 시스템으로 전송
        log.info("주문 확정 [{}]", sessionId);
        
        // 매크로 트리거 시뮬레이션
        return "{\"order\":\"confirmed\",\"items\":[]}";
    }

    /**
     * 주문 취소
     */
    public void cancelOrder(String sessionId) {
        sessions.remove(sessionId);
        log.info("주문 취소 [{}]", sessionId);
    }

    /**
     * 마지막 질문 반복
     */
    public String getLastQuestion(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null && session.getLastResponse() != null) {
            return session.getLastResponse();
        }
        return "이전 질문이 없습니다. 주문하실 메뉴를 말씀해 주세요.";
    }

    /**
     * 세션 정리
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("세션 정리 [{}]", sessionId);
    }

    /**
     * 세션 정보 업데이트
     */
    private void updateSession(String sessionId, String lastResponse) {
        sessions.computeIfAbsent(sessionId, k -> new ChatSession()).setLastResponse(lastResponse);
    }

    /**
     * 간단한 채팅 세션 정보
     */
    private static class ChatSession {
        private String lastResponse;
        
        public String getLastResponse() {
            return lastResponse;
        }
        
        public void setLastResponse(String lastResponse) {
            this.lastResponse = lastResponse;
        }
    }
} 