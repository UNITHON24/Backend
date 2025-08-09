package com.example.unithon.domain.menu.service;

import com.example.unithon.domain.menu.entity.Menu;
import com.example.unithon.domain.menu.entity.MenuOption;
import com.example.unithon.domain.menu.repository.MenuOptionRepository;
import com.example.unithon.domain.menu.repository.MenuRepository;
import com.example.unithon.domain.menu.repository.MenuSynonymRepository;
import com.example.unithon.global.client.gemini.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuSynonymRepository menuSynonymRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final GeminiService geminiService;

    /**
     * DB 동의어 우선 -> Gemini 보완
     */
    public MenuSearchResult searchMenu(String userInput) {
        log.info("메뉴 검색 시작: {}", userInput);

        String normalizedInput = normalizeInput(userInput);

        Optional<Menu> directMatch = menuSynonymRepository.findMenuBySynonym(normalizedInput);
        if (directMatch.isPresent()) {
            log.info("DB 동의어 매칭 성공: {} → {}", userInput, directMatch.get().getDisplayName());
            return MenuSearchResult.directMatch(directMatch.get());
        }

        return searchWithGemini(userInput);
    }

    /**
     * Gemini를 활용한 메뉴 추천
     */
    private MenuSearchResult searchWithGemini(String userInput) {
        try {
            List<Menu> allMenus = menuRepository.findActiveMenus();

            String prompt = buildMenuRecommendationPrompt(userInput, allMenus);
            String geminiResponse = geminiService.generateText(prompt);
            
            log.info("Gemini 응답: {}", geminiResponse);

            List<Menu> recommendedMenus = parseGeminiResponse(geminiResponse, allMenus);
            
            if (!recommendedMenus.isEmpty()) {
                return MenuSearchResult.geminiSuggestion(geminiResponse, recommendedMenus);
            } else {
                // NO_MATCH인 경우 사용자 친화적 메시지로 변경
                if (geminiResponse.contains("NO_MATCH")) {
                    String userMessage = "죄송합니다. '" + userInput + "'은(는) 저희 매장에서 판매하지 않는 메뉴입니다. 다른 메뉴를 말씀해 주세요.";
                    return MenuSearchResult.geminiSuggestion(userMessage, new ArrayList<>());
                }
                return MenuSearchResult.noMatch();
            }
            
        } catch (Exception e) {
            log.error("Gemini 메뉴 검색 실패: {}", e.getMessage(), e);
            return MenuSearchResult.noMatch();
        }
    }


    private String buildMenuRecommendationPrompt(String userInput, List<Menu> menus) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("사용자가 '").append(userInput).append("'를 주문했습니다.\n");
        prompt.append("이것은 맥도날드 매장입니다. 다음은 현재 판매 중인 메뉴입니다:\n\n");
        
        // 카테고리별로 메뉴 정리
        prompt.append("버거/세트: 빅맥, 쿼터파운더, 맥스파이시 상하이 버거, 불고기 버거\n");
        prompt.append("커피: 아메리카노, 카페라떼, 카푸치노, 캐러멜 마키아토\n");
        prompt.append("음료: 코카콜라, 코카콜라 제로, 스프라이트, 오렌지 주스\n");
        prompt.append("사이드: 프렌치프라이, 치킨 너겟, 애플파이\n");
        prompt.append("디저트: 맥플러리 오레오, 소프트 콘\n\n");
        
        prompt.append("판단 기준:\n");
        prompt.append("1. 요청한 음식이 맥도날드에서 파는 종류의 음식인가?\n");
        prompt.append("2. 위 메뉴와 유사한 것이 있는가?\n\n");
        
        prompt.append("응답 규칙:\n");
        prompt.append("- 맥도날드와 전혀 관련 없는 음식(짜장면, 김치찌개, 초밥 등)이면 → NO_MATCH\n");
        prompt.append("- 유사한 메뉴가 있으면 → 다음 기준으로 추천:\n");
        prompt.append("  1. 카테고리가 같은 메뉴를 우선 추천 (커피→커피, 버거→버거)\n");
        prompt.append("  2. 맛, 재료, 특성이 유사한 메뉴를 선택\n");
        prompt.append("  3. 가격대가 비슷한 메뉴를 우선 고려\n");
        prompt.append("  4. 반드시 위 메뉴 목록에 있는 정확한 메뉴명만 추천\n\n");
        
        prompt.append("예시:\n");
        prompt.append("바닐라 라떼' → '카페라떼', '캐러멜 마키아토' 추천\n");
        prompt.append("'치즈버거' → '빅맥', '불고기 버거' 추천\n");
        prompt.append("'짜장면' → NO_MATCH (맥도날드 음식이 아님)\n");
        prompt.append("'김치찌개' → NO_MATCH (맥도날드 음식이 아님)\n\n");
        
        prompt.append("응답 형식: JSON\n");
        prompt.append("{\n");
        prompt.append("  \"recommended\": [\"메뉴명1\", \"메뉴명2\"] 또는 \"NO_MATCH\",\n");
        prompt.append("  \"reason\": \"판단 이유\"\n");
        prompt.append("}");
        
        return prompt.toString();
    }


    private List<Menu> parseGeminiResponse(String geminiResponse, List<Menu> allMenus) {
        try {
            List<Menu> recommendedMenus = new ArrayList<>();
            
            // NO_MATCH 체크 먼저
            if (geminiResponse.contains("NO_MATCH")) {
                log.info("Gemini 응답: 맥도날드 메뉴와 관련 없는 요청으로 판단됨");
                return new ArrayList<>(); // 빈 리스트 반환
            }
            
            if (geminiResponse.contains("recommended")) {
                String[] lines = geminiResponse.split("\n");
                for (String line : lines) {
                    if (line.contains("recommended") && !line.contains("NO_MATCH")) {
                        String menuPart = line.substring(line.indexOf("["), line.indexOf("]") + 1);
                        String[] menuNames = menuPart.replaceAll("[\\[\\]\"]", "").split(",");
                        
                        for (String menuName : menuNames) {
                            String trimmedName = menuName.trim();
                            allMenus.stream()
                                   .filter(menu -> menu.getDisplayName().equals(trimmedName) || 
                                                 menu.getName().equals(trimmedName))
                                   .findFirst()
                                   .ifPresent(recommendedMenus::add);
                        }
                        break;
                    }
                }
            }

            // 파싱 실패시 텍스트에서 직접 찾기 (NO_MATCH가 아닌 경우만)
            if (recommendedMenus.isEmpty() && !geminiResponse.contains("NO_MATCH")) {
                for (Menu menu : allMenus) {
                    if (geminiResponse.contains(menu.getDisplayName())) {
                        recommendedMenus.add(menu);
                        if (recommendedMenus.size() >= 2) break; // 최대 2개까지
                    }
                }
            }
            
            log.info("Gemini 응답에서 파싱된 추천 메뉴: {}", 
                    recommendedMenus.stream().map(Menu::getDisplayName).toList());
            
            return recommendedMenus;
            
        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }


    private String normalizeInput(String input) {
        if (input == null) return "";
        
        return input.trim()
                   .toLowerCase()
                   .replaceAll("\\s+", "")  // 공백 제거
                   .replaceAll("[.,!?]", ""); // 구두점 제거
    }

    public List<MenuOption> getMenuOptions(Long menuId, MenuOption.OptionType optionType) {
        return menuOptionRepository.findActiveOptionsByMenuIdAndType(menuId, optionType);
    }

    public List<Menu> getAllActiveMenus() {
        return menuRepository.findActiveMenus();
    }


} 