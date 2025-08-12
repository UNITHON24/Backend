package com.example.unithon.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.unithon.domain.menu.entity.Menu;
import com.example.unithon.domain.menu.service.MenuSearchResult;
import com.example.unithon.domain.menu.service.MenuService;
import com.example.unithon.domain.chat.dto.MacroOrderData;
import com.example.unithon.domain.chat.dto.MacroOrderItem;
import com.example.unithon.domain.chat.dto.MacroTriggerEvent;
import com.example.unithon.domain.chat.dto.DialogState;
import com.example.unithon.domain.chat.dto.DialogStateEvent;
import com.example.unithon.domain.chat.dto.ServerErrorEvent;
import com.example.unithon.global.error.ErrorCode;

import org.springframework.context.ApplicationEventPublisher;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MenuService menuService;
    private final ApplicationEventPublisher eventPublisher;
    private final MacroWebhookService macroWebhookService;

    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 사용자 메시지 처리
     */
    public String processMessage(String sessionId, String message) {
        log.info("메시지 처리 [{}]: {}", sessionId, message);
        
        ChatSession session = getSession(sessionId);

        String response;
        
        if (isOrderComplete(message)) {
            response = completeOrder(sessionId);
        } else if (isAddMore(message)) {
            session.setState(ConversationState.MENU_SELECTION);
            session.setCurrentItem(null);
            response = "추가로 주문하실 메뉴를 말씀해주세요.";
        } else {
            switch (session.getState()) {
                case GREETING:
                    response = handleGreeting(sessionId, message);
                    break;
                
                case MENU_SELECTION:
                    response = handleMenuSelection(sessionId, message);
                    break;
                
                case QUANTITY_SELECTION:
                    response = handleQuantitySelection(sessionId, message);
                    break;
                
                case ORDER_CONFIRMATION:
                    response = handleOrderConfirmation(sessionId, message);
                    break;
                
                default:
                    response = "안녕하세요! 주문하실 메뉴를 말씀해주세요.";
            }
        }

        // 응답 후 dialog.state 이벤트 발송 (주문 완료 시 제외)
        if (!response.contains("결제 해주시길 바랍니다")) {
            DialogState dialogState = buildDialogState(sessionId);
            eventPublisher.publishEvent(new DialogStateEvent(sessionId, dialogState));
        }
        
        return response;
    }

    /**
     * 인사 처리
     */
    private String handleGreeting(String sessionId, String message) {
        ChatSession session = getSession(sessionId);
        
        if (message.contains("안녕") || message.contains("하이") || message.contains("주문")) {
            session.setState(ConversationState.MENU_SELECTION);
            return "안녕하세요! 주문하실 메뉴를 말씀해주세요.";
        }

        session.setState(ConversationState.MENU_SELECTION);
        return handleMenuSelection(sessionId, message);
    }

    /**
     * 메뉴 선택 처리
     */
    private String handleMenuSelection(String sessionId, String message) {
        ChatSession session = getSession(sessionId);
        
        MenuSearchResult result = menuService.searchMenu(message);
        
        switch (result.getType()) {
            case DIRECT_MATCH:
                Menu menu = result.getMenu();
                OrderItem orderItem = new OrderItem(menu);
                
                session.setCurrentItem(orderItem);
                
                session.setState(ConversationState.QUANTITY_SELECTION);
                return String.format("%s 몇 개 드릴까요?", menu.getDisplayName());

            case AMBIGUOUS_MATCH:
                List<Menu> menus = result.getAmbiguousMenus();
                return String.format("혹시 다음 메뉴 중에서 찾으시는 게 있나요?\n%s",
                    menus.stream()
                        .map(Menu::getDisplayName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));

            case GEMINI_SUGGESTION:
                if (result.getSuggestions() == null || result.getSuggestions().isEmpty()) {
                    return result.getGeminiResponse();
                }
                return String.format("혹시 다음 메뉴 중에서 찾으시는 게 있나요?\n%s", 
                    result.getSuggestions().stream()
                        .map(Menu::getDisplayName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));

            case NO_MATCH:
                return "죄송합니다. 해당 메뉴를 찾을 수 없습니다. 다른 메뉴로 말씀해주세요.";
            
            default:
                return "주문하실 메뉴를 말씀해주세요.";
        }
    }

    /**
     * 수량 선택 처리
     */
    private String handleQuantitySelection(String sessionId, String message) {
        ChatSession session = getSession(sessionId);
        OrderItem currentItem = session.getCurrentItem();
        
        if (currentItem == null) {
            session.setState(ConversationState.MENU_SELECTION);
            return "주문하실 메뉴를 다시 말씀해주세요.";
        }
        
        try {

            int quantity = extractQuantity(message);
            if (quantity <= 0) {
                return handleError(sessionId, ErrorCode.INVALID_QUANTITY);
            }
            
            currentItem.setQuantity(quantity);

            session.getCart().add(currentItem);

            session.setState(ConversationState.ORDER_CONFIRMATION);
            session.setCurrentItem(null);
            
            return String.format("%s %s %d개가 담겼습니다.\n\n메뉴를 더 담겠습니까? 주문을 마치겠습니까?", 
                buildSelectedOptionsText(currentItem), 
                currentItem.getMenu().getDisplayName(), 
                quantity);
            
        } catch (Exception e) {
            return handleError(sessionId, ErrorCode.INVALID_QUANTITY, e);
        }
    }

    /**
     * 주문 확인 처리
     */
    private String handleOrderConfirmation(String sessionId, String message) {
        if (isAddMore(message)) {
            return processMessage(sessionId, "메뉴 추가");
        }
        
        if (isOrderComplete(message)) {
            return completeOrder(sessionId);
        }
        
        return "메뉴를 더 담겠습니까? 주문을 마치겠습니까?";
    }

    /**
     * 주문 완료 처리
     */
    private String completeOrder(String sessionId) {
        ChatSession session = getSession(sessionId);
        
        if (session.getCart().isEmpty()) {
            return "장바구니가 비어있습니다. 메뉴를 주문해주세요.";
        }

        StringBuilder orderSummary = new StringBuilder("주문 내역:\n");
        int totalPrice = 0;

        List<MacroOrderItem> macroItems = new ArrayList<>();
        
        for (OrderItem item : session.getCart()) {
            String optionText = buildSelectedOptionsText(item);
            int itemTotalPrice = item.getMenu().getBasePrice().intValue() * item.getQuantity();

            orderSummary.append(String.format("- %s %s %d개\n", 
                optionText, item.getMenu().getDisplayName(), item.getQuantity()));
            totalPrice += itemTotalPrice;

            macroItems.add(new MacroOrderItem(
                item.getMenu().getName(),
                item.getMenu().getDisplayName(),
                item.getTemperature(),
                item.getSize(),
                item.getQuantity(),
                item.getMenu().getBasePrice().intValue(),
                itemTotalPrice
            ));
        }
        
        orderSummary.append(String.format("\n총 금액: %,d원\n\n", totalPrice));
        orderSummary.append("결제 해주시길 바랍니다.");
		
        MacroOrderData macroData = new MacroOrderData(
            sessionId,
            macroItems,
            totalPrice,
            java.time.LocalDateTime.now().toString()
        );
        
        // 매크로팀에게 주문 데이터 전송 (HTTP Webhook)
        macroWebhookService.sendOrderToMacro(macroData);
        
        // WebSocket으로도 macro.trigger 이벤트 발송 (에이전트용)
        eventPublisher.publishEvent(new MacroTriggerEvent(sessionId, macroData));
        log.info("주문 완료 처리 완료 [{}]", sessionId);

        clearSession(sessionId);
        
        return orderSummary.toString();
    }

    /**
     * 선택된 옵션 텍스트 생성
     */
    private String buildSelectedOptionsText(OrderItem item) {
        StringBuilder text = new StringBuilder();
        
        if (item.getTemperature() != null) {
            text.append(item.getTemperature().equals("ICE") ? "아이스 " : "핫 ");
        }
        
        if (item.getSize() != null) {
            text.append(item.getSize().equals("LARGE") ? "라지 " : "");
        }
        
        return text.toString().trim();
    }

    /**
     * 메시지에서 수량 추출
     */
    private int extractQuantity(String message) {
        // 1단계: 기본 숫자 패턴 확인
        String numberStr = message.replaceAll("[^0-9]", "");
        if (!numberStr.isEmpty()) {
            return Integer.parseInt(numberStr);
        }
        
        // 2단계: 간단한 한국어 패턴 확인
        message = message.toLowerCase();
        if (message.contains("하나") || message.contains("한개") || message.contains("한 개") || message.contains("한잔") || message.contains("한 잔")) {
            return 1;
        } else if (message.contains("둘") || message.contains("두개") || message.contains("두 개") || message.contains("두잔") || message.contains("두 잔")) {
            return 2;
        } else if (message.contains("셋") || message.contains("세개") || message.contains("세 개") || message.contains("세잔") || message.contains("세 잔")) {
            return 3;
        }
        
        // 3단계: MenuService의 Gemini로 고급 수량 추출
        try {
            String quantityPrompt = String.format("""
                다음 텍스트에서 수량을 추출해주세요:
                "%s"
                
                규칙:
                - 숫자나 한국어 수량 표현 (하나, 둘, 셋, 한잔, 두잔, 많이, 조금 등)을 찾아주세요
                - "많이"는 3개, "조금"은 1개로 해석해주세요
                - 수량이 명확하지 않으면 0을 반환해주세요
                - 숫자만 답변해주세요 (1, 2, 3, 0 등)
                """, message);
                
            String response = menuService.callGeminiForIntent(quantityPrompt);
            return Integer.parseInt(response.trim());
        } catch (Exception e) {
            log.warn("Gemini 수량 추출 실패: {}", e.getMessage());
            return 0; // 실패 시 0 반환 (질문하게)
        }
    }

    /**
     * 현재 대화 상태를 DialogState로 변환
     */
    private DialogState buildDialogState(String sessionId) {
        ChatSession session = getSession(sessionId);

        List<DialogState.CartItem> cartItems = new ArrayList<>();
        int totalPrice = 0;
        
        for (OrderItem item : session.getCart()) {
            String optionText = buildSelectedOptionsText(item);
            int itemPrice = item.getMenu().getBasePrice().intValue() * item.getQuantity();
            
            cartItems.add(DialogState.CartItem.builder()
                .menu(item.getMenu().getDisplayName())
                .options(optionText)
                .quantity(item.getQuantity())
                .price(itemPrice)
                .build());
                
            totalPrice += itemPrice;
        }

        Map<String, String> selectedOptions = new HashMap<>();
        OrderItem currentItem = session.getCurrentItem();
        if (currentItem != null) {
            if (currentItem.getTemperature() != null) {
                selectedOptions.put("temperature", currentItem.getTemperature());
            }
            if (currentItem.getSize() != null) {
                selectedOptions.put("size", currentItem.getSize());
            }
        }
		
        String nextAction = determineNextAction(session);
        
        return DialogState.builder()
            .state(session.getState().name())
            .currentMenu(currentItem != null ? currentItem.getMenu().getDisplayName() : null)
            .selectedOptions(selectedOptions)
            .cart(cartItems)
            .nextAction(nextAction)
            .cartItemCount(session.getCart().size())
            .totalPrice(totalPrice)
            .build();
    }

    /**
     * 다음에 해야 할 행동 결정
     */
    private String determineNextAction(ChatSession session) {
        switch (session.getState()) {
            case GREETING:
                return "주문하실 메뉴를 말씀해주세요";
            case MENU_SELECTION:
                return "메뉴 이름을 말씀해주세요";
            case QUANTITY_SELECTION:
                return "수량을 말씀해주세요";
            case ORDER_CONFIRMATION:
                return "메뉴를 더 담을지 주문을 마칠지 선택해주세요";
            default:
                return "말씀해주세요";
        }
    }

    /**
     * 서버 에러 발생 시 에러 이벤트 발송 및 사용자 친화적 메시지 반환
     */
    private String handleError(String sessionId, ErrorCode errorCode, Exception e) {
        log.error("에러 발생 [{}]: {} - {}", sessionId, errorCode.getCode(), e.getMessage(), e);
        
        // 에러 이벤트 발송
        ServerErrorEvent errorEvent = ServerErrorEvent.of(sessionId, errorCode);
        eventPublisher.publishEvent(errorEvent);
        
        return errorCode.getMessage();
    }

    /**
     * 서버 에러 발생 시 에러 이벤트 발송 및 사용자 친화적 메시지 반환 (Exception 없는 버전)
     */
    private String handleError(String sessionId, ErrorCode errorCode) {
        log.error("에러 발생 [{}]: {}", sessionId, errorCode.getCode());
        
        // 에러 이벤트 발송
        ServerErrorEvent errorEvent = ServerErrorEvent.of(sessionId, errorCode);
        eventPublisher.publishEvent(errorEvent);
        
        return errorCode.getMessage();
    }

    /**
     * 주문 완료 동의어 체크
     */
    private boolean isOrderComplete(String message) {
        String[] completeKeywords = {
            "주문완료", "주문 완료", "결제", "끝", "마무리", "이게다", "이게 다", 
            "마치겠습니다", "완료", "주문마치기", "주문 마치기", "그만", "종료", "종료하겠습니다",
                "마칠게", "마치겠", "마칠게여", "마치겠음"
        };
        
        for (String keyword : completeKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 메뉴 추가 의사 체크
     */
    private boolean isAddMore(String message) {
        String[] addKeywords = {
            "더", "추가", "더주문", "더 주문", "메뉴추가", "메뉴 추가", "하나더", "하나 더"
        };
        
        for (String keyword : addKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 주문 확인
     */
    public String confirmOrder(String sessionId) {
        return completeOrder(sessionId);
    }

    /**
     * 주문 취소
     */
    public String cancelOrder(String sessionId) {
        clearSession(sessionId);
        return "주문이 취소되었습니다.";
    }

    /**
     * 마지막 질문 반복
     */
    public String getLastQuestion(String sessionId) {
        ChatSession session = getSession(sessionId);
        return switch (session.getState()) {
            case GREETING -> "안녕하세요! 주문하실 메뉴를 말씀해주세요.";
            case MENU_SELECTION -> "주문하실 메뉴를 말씀해주세요.";
            case QUANTITY_SELECTION -> "몇 개 드릴까요?";
            case ORDER_CONFIRMATION -> "메뉴를 더 담겠습니까? 주문을 마치겠습니까?";
        };
    }

    /**
     * 세션 정리
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("세션 정리 완료 [{}]", sessionId);
    }

    /**
     * 세션 가져오기 (없으면 생성)
     */
    private ChatSession getSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new ChatSession());
    }

    public enum ConversationState {
        GREETING,
        MENU_SELECTION,
        QUANTITY_SELECTION,
        ORDER_CONFIRMATION
    }


    @Data
    public static class OrderItem {
        private Menu menu;
        private String temperature; // ICE, HOT
        private String size; // REGULAR, LARGE
        private int quantity;
        
        public OrderItem(Menu menu) {
            this.menu = menu;
        }
    }
    @Data
    private static class ChatSession {
        private ConversationState state = ConversationState.GREETING;
        private OrderItem currentItem;
        private List<OrderItem> cart = new ArrayList<>();
    }
} 