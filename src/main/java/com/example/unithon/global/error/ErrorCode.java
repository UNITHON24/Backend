package com.example.unithon.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Gemini API 관련
    GEMINI_API_FAILED("GEMINI_001", "메뉴 검색 중 오류가 발생했습니다. 다시 시도해주세요.", true),
    GEMINI_TIMEOUT("GEMINI_002", "응답 시간이 초과되었습니다. 다시 시도해주세요.", true),
    
    // 데이터베이스 관련
    DB_CONNECTION_FAILED("DB_001", "데이터베이스 연결에 실패했습니다.", true),
    MENU_NOT_FOUND("DB_002", "메뉴 정보를 찾을 수 없습니다.", false),
    
    // 사용자 입력 관련
    INVALID_USER_INPUT("INPUT_001", "올바르지 않은 입력입니다. 다시 말씀해주세요.", false),
    INVALID_QUANTITY("INPUT_002", "올바른 수량을 입력해주세요.", false),
    
    // 세션 관련
    SESSION_EXPIRED("SESSION_001", "세션이 만료되었습니다. 다시 시작해주세요.", false),
    SESSION_NOT_FOUND("SESSION_002", "세션을 찾을 수 없습니다.", false),
    
    // 매크로 연동 관련
    MACRO_WEBHOOK_FAILED("MACRO_001", "주문 처리 중 오류가 발생했습니다.", true),
    
    // 일반적인 서버 오류
    INTERNAL_SERVER_ERROR("SERVER_001", "서버 내부 오류가 발생했습니다.", true),
    WEBSOCKET_ERROR("SERVER_002", "연결 중 오류가 발생했습니다.", true);
    
    private final String code;
    private final String message;
    private final boolean retryable;
} 