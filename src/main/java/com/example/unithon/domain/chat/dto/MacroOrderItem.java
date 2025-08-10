package com.example.unithon.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MacroOrderItem {
    private String menuName;        // 내부 메뉴 코드 (e.g., "americano")
    private String displayName;     // 사용자 표시명 (e.g., "아메리카노")
    private String temperature;     // ICE, HOT, null
    private String size;            // REGULAR, LARGE, null
    private int quantity;           // 수량
    private int unitPrice;          // 단가
    private int totalPrice;         // 총 가격 (단가 × 수량)
} 