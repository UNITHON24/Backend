package com.example.unithon.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.unithon.domain.menu.entity.Menu;
import com.example.unithon.domain.menu.entity.MenuOption;
import com.example.unithon.domain.menu.service.MenuSearchResult;
import com.example.unithon.domain.menu.service.MenuSearchResultType;
import com.example.unithon.domain.menu.service.MenuService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MenuService menuService;

    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 사용자 메시지 처리
     */
    public String processMessage(String sessionId, String message) {
        log.info("메시지 처리 [{}]: {}", sessionId, message);
        
        ChatSession session = getSession(sessionId);

        if (isOrderComplete(message)) {
            return completeOrder(sessionId);
        }
        if (isAddMore(message)) {
            session.setState(ConversationState.MENU_SELECTION);
            session.setCurrentItem(null);
            return "추가로 주문하실 메뉴를 말씀해주세요.";
        }

        switch (session.getState()) {
            case GREETING:
                return handleGreeting(sessionId, message);
            
            case MENU_SELECTION:
                return handleMenuSelection(sessionId, message);
            
            case OPTION_SELECTION:
                return handleOptionSelection(sessionId, message);
            
            case QUANTITY_SELECTION:
                return handleQuantitySelection(sessionId, message);
            
            case ORDER_CONFIRMATION:
                return handleOrderConfirmation(sessionId, message);
            
            default:
                return "안녕하세요! 주문하실 메뉴를 말씀해주세요.";
        }
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
                session.setCurrentItem(new OrderItem(menu));

                if (hasRequiredOptions(menu)) {
                    session.setState(ConversationState.OPTION_SELECTION);
                    return buildOptionSelectionMessage(menu);
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
                return "올바른 수량을 입력해주세요. (예: 1개, 2잔)";
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
            return "올바른 수량을 입력해주세요. (예: 1개, 2잔)";
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

        StringBuilder orderSummary = new StringBuilder("🧾 주문 내역:\n");
        int totalPrice = 0;
        
        for (OrderItem item : session.getCart()) {
            String optionText = buildSelectedOptionsText(item);
            orderSummary.append(String.format("- %s %s %d개\n", 
                optionText, item.getMenu().getDisplayName(), item.getQuantity()));
            totalPrice += item.getMenu().getBasePrice().intValue() * item.getQuantity();
        }
        
        orderSummary.append(String.format("\n💰 총 금액: %,d원\n\n", totalPrice));
        orderSummary.append("결제 해주시길 바랍니다.");

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
    private String buildOptionSelectionMessage(Menu menu) {
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
     * 수량 추출
     */
    private int extractQuantity(String message) {
        // 숫자 추출 로직
        String numberStr = message.replaceAll("[^0-9]", "");
        if (numberStr.isEmpty()) {
            return 1; // 기본값
        }
        return Integer.parseInt(numberStr);
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