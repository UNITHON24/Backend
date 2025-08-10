package com.example.unithon.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class DialogState {
    private String state;              // GREETING, MENU_SELECTION, OPTION_SELECTION, etc.
    private String currentMenu;        // 현재 선택 중인 메뉴
    private Map<String, String> selectedOptions; // 현재 선택된 옵션들
    private List<CartItem> cart;       // 장바구니 아이템들
    private String nextAction;         // 다음에 해야 할 행동
    private int cartItemCount;         // 장바구니 아이템 개수
    private int totalPrice;            // 현재 장바구니 총 금액

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CartItem {
        private String menu;
        private String options;
        private int quantity;
        private int price;
    }
} 