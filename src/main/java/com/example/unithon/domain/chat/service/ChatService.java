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
                
                case OPTION_SELECTION:
                    response = handleOptionSelection(sessionId, message);
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
                
                // 특별한 동의어 처리 (옵션이 미리 포함된 경우)
                handleSpecialSynonyms(message, orderItem);
                
                session.setCurrentItem(orderItem);
                
                if (hasRequiredOptions(menu)) {
                    // 이미 모든 옵션이 설정되었는지 확인
                    if (isOptionComplete(orderItem)) {
                        session.setState(ConversationState.QUANTITY_SELECTION);
                        return String.format("%s %s 몇 개 드릴까요?", 
                            buildSelectedOptionsText(orderItem), menu.getDisplayName());
                    } else {
                        session.setState(ConversationState.OPTION_SELECTION);
                        return buildOptionSelectionMessage(menu, orderItem);
                    }
                } else {
                    session.setState(ConversationState.QUANTITY_SELECTION);
                    return String.format("%s 몇 개 드릴까요?", menu.getDisplayName());
                }
            
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
     * 특별한 동의어 처리 (옵션이 미리 포함된 경우)
     */
    private void handleSpecialSynonyms(String userInput, OrderItem orderItem) {
        Menu menu = orderItem.getMenu();
        String normalizedInput = userInput.replaceAll("\\s+", ""); // 공백 제거
        
        // "아아" 관련 = 아이스 아메리카노 (온도만 설정, 사이즈는 여전히 선택)
        if ((normalizedInput.contains("아아") || normalizedInput.contains("아이스아메리카노")) 
            && menu.getName().equals("americano")) {
            orderItem.setTemperature("ICE");
        }
        
        // "따아" 관련 = 핫 아메리카노 (온도만 설정)
        else if ((normalizedInput.contains("따아") || normalizedInput.contains("핫아메리카노")) 
                 && menu.getName().equals("americano")) {
            orderItem.setTemperature("HOT");
        }
        
        // "아이스라떼" 관련 = 카페라떼 + 아이스 (사이즈는 별도 선택)
        else if (normalizedInput.contains("아이스라떼") && menu.getName().equals("cafe_latte")) {
            orderItem.setTemperature("ICE");
        }
        // "핫라떼" 관련 = 카페라떼 + 핫 (사이즈는 별도 선택)
        else if (normalizedInput.contains("핫라떼") && menu.getName().equals("cafe_latte")) {
            orderItem.setTemperature("HOT");
        }
    }

    /**
     * 옵션 선택 처리
     */
    private String handleOptionSelection(String sessionId, String message) {
        ChatSession session = getSession(sessionId);
        OrderItem currentItem = session.getCurrentItem();
        
        if (currentItem == null) {
            session.setState(ConversationState.MENU_SELECTION);
            return "주문하실 메뉴를 다시 말씀해주세요.";
        }
        
        // 온도 옵션 처리
        if (currentItem.getMenu().getHasTemperature() && currentItem.getTemperature() == null) {
            if (message.contains("아이스") || message.contains("차가운") || message.contains("시원한")) {
                currentItem.setTemperature("ICE");
            } else if (message.contains("핫") || message.contains("따뜻한") || message.contains("뜨거운")) {
                currentItem.setTemperature("HOT");
            } else {
                return "아이스 또는 핫 중에서 선택해주세요.";
            }
        }

        if (currentItem.getMenu().getHasSize() && currentItem.getSize() == null) {
            if (message.contains("레귤러") || message.contains("R") || message.contains("작은")) {
                currentItem.setSize("REGULAR");
            } else if (message.contains("라지") || message.contains("L") || message.contains("큰")) {
                currentItem.setSize("LARGE");
            } else {
                return "레귤러 또는 라지 중에서 선택해주세요.";
            }
        }

        if (isOptionComplete(currentItem)) {
            session.setState(ConversationState.QUANTITY_SELECTION);
            return String.format("%s %s 몇 개 드릴까요?", 
                buildSelectedOptionsText(currentItem), currentItem.getMenu().getDisplayName());
        }

        return getNextOptionQuestion(currentItem);
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
        
        orderSummary.append(String.format("\n💰 총 금액: %,d원\n\n", totalPrice));
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
        log.info("💳 주문 완료 처리 완료 [{}]", sessionId);

        clearSession(sessionId);
        
        return orderSummary.toString();
    }

    /**
     * 필수 옵션 존재 여부 확인
     */
    private boolean hasRequiredOptions(Menu menu) {
        return menu.getHasTemperature() || menu.getHasSize();
    }

    /**
     * 옵션 선택 메시지 생성
     */
    private String buildOptionSelectionMessage(Menu menu, OrderItem orderItem) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("%s를 선택하셨습니다.\n", menu.getDisplayName()));
        
        if (menu.getHasTemperature()) {
            message.append("아이스 또는 핫 중에서 선택해주세요.");
        } else if (menu.getHasSize()) {
            message.append("레귤러 또는 라지 중에서 선택해주세요.");
        }
        
        return message.toString();
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
     * 옵션 완료 여부 확인
     */
    private boolean isOptionComplete(OrderItem item) {
        Menu menu = item.getMenu();
        
        if (menu.getHasTemperature() && item.getTemperature() == null) {
            return false;
        }
        
        if (menu.getHasSize() && item.getSize() == null) {
            return false;
        }
        
        return true;
    }

    /**
     * 다음 옵션 질문 생성
     */
    private String getNextOptionQuestion(OrderItem item) {
        Menu menu = item.getMenu();
        
        if (menu.getHasTemperature() && item.getTemperature() == null) {
            return "아이스 또는 핫 중에서 선택해주세요.";
        }
        
        if (menu.getHasSize() && item.getSize() == null) {
            return "레귤러 또는 라지 중에서 선택해주세요.";
        }
        
        return "옵션을 선택해주세요.";
    }

    /**
     * 메시지에서 수량 추출
     */
    private int extractQuantity(String message) {
        String numberStr = message.replaceAll("[^0-9]", "");
        if (numberStr.isEmpty()) {
            return 0; // 수량이 없으면 0 반환 (무조건 질문하게)
        }
        return Integer.parseInt(numberStr);
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
            case OPTION_SELECTION:
                OrderItem currentItem = session.getCurrentItem();
                if (currentItem != null && currentItem.getMenu().getHasTemperature() && currentItem.getTemperature() == null) {
                    return "아이스 또는 핫 중에서 선택해주세요";
                } else if (currentItem != null && currentItem.getMenu().getHasSize() && currentItem.getSize() == null) {
                    return "레귤러 또는 라지 중에서 선택해주세요";
                }
                return "옵션을 선택해주세요";
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
            "마치겠습니다", "완료", "주문마치기", "주문 마치기", "그만", "종료"
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
            case OPTION_SELECTION -> getNextOptionQuestion(session.getCurrentItem());
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
        OPTION_SELECTION,
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