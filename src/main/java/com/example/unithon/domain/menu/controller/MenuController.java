package com.example.unithon.domain.menu.controller;

import com.example.unithon.domain.menu.entity.Menu;
import com.example.unithon.domain.menu.service.MenuSearchResult;
import com.example.unithon.domain.menu.service.MenuService;
import com.example.unithon.global.response.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 테스트용 controller
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
@Slf4j
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<MenuSearchResponse>> searchMenu(@RequestParam String query) {
        log.info("메뉴 검색 요청: {}", query);
        
        MenuSearchResult result = menuService.searchMenu(query);
        
        log.info("검색 결과 - 타입: {}, 메뉴: {}", 
                result.getType(), 
                result.getMenu() != null ? result.getMenu().getDisplayName() : "없음");
        
        MenuSearchResponse response = MenuSearchResponse.from(result);
        return ResponseEntity.ok(ApiResponse.success("메뉴 검색이 완료되었습니다.", response));
    }

    @Getter
    @Setter
    public static class MenuSearchResponse {
        private String resultType;
        private String menuName;
        private String geminiResponse;
        private List<String> suggestions;

        public static MenuSearchResponse from(MenuSearchResult result) {
            MenuSearchResponse response = new MenuSearchResponse();
            response.resultType = result.getType().name();
            
            if (result.getMenu() != null) {
                response.menuName = result.getMenu().getDisplayName();
            }
            
            response.geminiResponse = result.getGeminiResponse();
            
            if (result.getSuggestions() != null) {
                response.suggestions = result.getSuggestions().stream()
                        .map(Menu::getDisplayName)
                        .toList();
            }
            
            return response;
        }
    }
    @GetMapping("/search/simple")
    public ResponseEntity<ApiResponse<MenuSearchResponse>> searchMenuSimple(@RequestParam String query) {
        MenuSearchResult result = menuService.searchMenu(query);
        MenuSearchResponse response = MenuSearchResponse.from(result);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
} 