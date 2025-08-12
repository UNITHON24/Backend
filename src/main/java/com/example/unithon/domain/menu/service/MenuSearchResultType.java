package com.example.unithon.domain.menu.service;
 
public enum MenuSearchResultType {
    DIRECT_MATCH,      // DB 동의어 직접 매칭
    AMBIGUOUS_MATCH,   // 따아, 아아, 디카아
    GEMINI_SUGGESTION, // Gemini 추천
    NO_MATCH          // 매칭 실패
} 