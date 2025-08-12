package com.example.unithon.domain.menu.service;

import com.example.unithon.domain.menu.entity.Menu;
import com.example.unithon.domain.menu.repository.MenuRepository;
import com.example.unithon.domain.menu.repository.MenuSynonymRepository;
import com.example.unithon.global.client.gemini.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuSynonymRepository menuSynonymRepository;
    private final GeminiService geminiService;

    /**
     * DB 동의어 우선 -> Gemini 보완
     */
    public MenuSearchResult searchMenu(String userInput) { //아메리카노 한잔줘
        log.info("메뉴 검색 시작: {}", userInput);

        // 사용자 입력에서 검색 키워드들을 추출
        List<String> keywords = extractKeywords(userInput); //아메리카노

        // 중복을 허용하지 않는 Set으로 검색 결과를 통합
        Set<Menu> foundMenus = new LinkedHashSet<>();

        for (String keyword : keywords) {
            // 1. 메뉴 display_name 직접 검색 (Containing -> EqualsIgnoreCase로 변경하여 정확도 향상)
            menuRepository.findByDisplayNameContaining(keyword).forEach(foundMenus::add);

            // 2. 동의어 테이블 검색
            menuSynonymRepository.findMenuBySynonym(keyword).forEach(foundMenus::add);
        }

        // 만약 정확한 매칭 결과가 없다면, Containing 으로 넓게 다시 검색
        if (foundMenus.isEmpty()) {
            for (String keyword : keywords) {
                menuRepository.findByDisplayNameContaining(keyword).forEach(foundMenus::add);
            }
        }

        List<Menu> resultList = new ArrayList<>(foundMenus);

        if (resultList.size() == 1) {
            // 정확히 하나의 메뉴를 찾은 경우
            log.info("DB 직접 매칭 성공: {}", resultList.get(0).getDisplayName());
            return MenuSearchResult.directMatch(resultList.get(0));
        } else if (resultList.size() > 1) {
            // 여러 개의 메뉴가 검색된 경우 (모호한 경우)
            log.info("DB 모호한 매칭: {} 개의 결과 발견", resultList.size());
            return MenuSearchResult.ambiguousMatch(resultList);
        } else {
            // DB에서 메뉴를 찾지 못한 경우, Gemini로 검색
            log.info("DB 매칭 실패. Gemini 검색으로 전환.");
            return searchWithGemini(userInput);
        }
    }

    /**
     * Gemini를 활용한 메뉴 추천
     */
    private MenuSearchResult searchWithGemini(String userInput) {
        try {
            List<Menu> allMenus = menuRepository.findAll();
            
            String prompt = buildMenuRecommendationPrompt(userInput);
            String geminiResponse = geminiService.generateText(prompt);
            
            log.info("Gemini 응답: {}", geminiResponse);
            
            if (isGeneralQuestion(userInput)) {
                return MenuSearchResult.geminiSuggestion(geminiResponse, new ArrayList<>());
            }
            List<Menu> recommendedMenus = parseGeminiResponse(geminiResponse, allMenus);
            
            if (!recommendedMenus.isEmpty()) {
                return MenuSearchResult.geminiSuggestion(geminiResponse, recommendedMenus);
            } else {
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


    private String buildMenuRecommendationPrompt(String userInput) {
        if (isGeneralQuestion(userInput)) {
            return buildRAGPrompt(userInput);
        }

        // 카페 메뉴 추천 프롬프트
        StringBuilder prompt = new StringBuilder();
        prompt.append("사용자가 '").append(userInput).append("'를 주문했습니다.\n");
        prompt.append("이것은 카페 매장입니다. 다음은 현재 판매 중인 메뉴입니다:\n\n");
        
        // 카테고리별로 메뉴 정리
        prompt.append("커피: 아메리카노, 에스프레소, 카페라떼, 바닐라라떼, 카라멜마키아토, 카푸치노, 카페모카, 플랫화이트, 헤이즐넛라떼, 콜드브루, 디카페인 등\n");
        prompt.append("음료: 딸기스무디, 망고스무디, 블루베리스무디, 오렌지주스, 레몬에이드, 자몽에이드, 청포도에이드, 초코라떼, 녹차라떼, 말차라떼, 복숭아아이스티, 유자차, 밀크티 등\n");
        prompt.append("디저트: 뉴욕치즈케이크, 초코칩쿠키, 초콜릿브라우니, 레드벨벳케이크, 크루아상, 에그타르트, 블루베리머핀, 마카롱세트, 시나몬롤 등\n\n");
        
        prompt.append("판단 기준:\n");
        prompt.append("1. 요청한 음식이 카페에서 파는 종류의 음식인가?\n");
        prompt.append("2. 위 메뉴와 유사한 것이 있는가?\n\n");
        
        prompt.append("응답 규칙:\n");
        prompt.append("- 카페와 전혀 관련 없는 음식(짜장면, 김치찌개, 초밥, 햄버거 등)이면 → NO_MATCH\n");
        prompt.append("- 유사한 메뉴가 있으면 → 다음 기준으로 추천:\n");
        prompt.append("  1. 카테고리가 같은 메뉴를 우선 추천 (커피→커피, 음료→음료, 디저트→디저트)\n");
        prompt.append("  2. 맛, 재료, 특성이 유사한 메뉴를 선택\n");
        prompt.append("  3. 가격대가 비슷한 메뉴를 우선 고려\n");
        prompt.append("  4. 반드시 위 메뉴 목록에 있는 정확한 메뉴명만 추천\n\n");
        
        prompt.append("예시:\n");
        prompt.append("'바닐라라떼' → '바닐라 라떼', '카라멜 마키아토' 추천\n");
        prompt.append("'딸기음료' → '딸기 스무디', '딸기 에이드' 추천\n");
        prompt.append("'치즈케익' → '뉴욕 치즈 케이크' 추천\n");
        prompt.append("'짜장면' → NO_MATCH (카페 음식이 아님)\n");
        prompt.append("'햄버거' → NO_MATCH (카페 음식이 아님)\n\n");
        
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

            if (geminiResponse.contains("NO_MATCH")) {
                log.info("Gemini 응답: 카페 메뉴와 관련 없는 요청으로 판단됨");
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


    /**
     * 사용자 입력에서 메뉴 키워드 추출
     */
    private List<String> extractKeywords(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return new ArrayList<>();
        }

        // 1. DB에서 모든 메뉴명과 동의어 목록을 가져옵니다.
        // 중복을 미리 제거하여 성능을 개선합니다.
        Set<String> allMenuKeywordsSet = new HashSet<>();
        menuRepository.findAll().forEach(menu -> {
            allMenuKeywordsSet.add(menu.getDisplayName());
            menu.getSynonyms().forEach(synonym -> allMenuKeywordsSet.add(synonym.getSynonym()));
        });

        // 2. 키워드를 글자 길이의 역순으로 정렬합니다. (매우 중요!)
        List<String> allMenuKeywords = allMenuKeywordsSet.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        Set<String> foundKeywords = new LinkedHashSet<>();
        String remainingInput = userInput;

        // 3. 정렬된 키워드 목록으로 사용자의 문장을 확인합니다.
        for (String keyword : allMenuKeywords) {
            // ★★★ 핵심 수정 부분 ★★★
            // 사용자 입력(remainingInput)에 키워드가 포함되어 있는지 확인합니다.
            if (remainingInput.contains(keyword)) {
                foundKeywords.add(keyword);
                // 찾은 키워드를 문장에서 제거하여 중복 인식을 방지합니다.
                remainingInput = remainingInput.replace(keyword, "");
            }
        }

        if (foundKeywords.isEmpty()) {
            log.warn("입력 '{}'에서 메뉴 키워드를 찾지 못했습니다. Gemini로 처리를 시도합니다.", userInput);
            return List.of(userInput);
        }

        log.info("입력: '{}', 추출된 키워드: {}", userInput, foundKeywords);
        return new ArrayList<>(foundKeywords);
    }

    private String normalizeInput(String input) {
        if (input == null) return "";
        
        return input.trim()
                   .toLowerCase()
                   .replaceAll("\\s+", "")  // 공백 제거
                   .replaceAll("[.,!?]", ""); // 구두점 제거
    }

    /**
     * RAG용 메뉴 데이터 텍스트 변환
     */
    private String getAllMenuDataAsText() {
        StringBuilder data = new StringBuilder();
        List<Menu> menus = menuRepository.findAll();
        
        data.append("=== 우리 카페 메뉴 ===\n");
        
        data.append("\n[커피 메뉴]\n");
        menus.stream()
            .filter(menu -> menu.getCategory().getName().equals("coffee"))
            .forEach(menu -> data.append(String.format("- %s: %,d원 (%s)\n", 
                menu.getDisplayName(), 
                menu.getBasePrice().intValue(),
                menu.getDescription() != null ? menu.getDescription() : "")));

        data.append("\n[음료 메뉴]\n");
        menus.stream()
            .filter(menu -> menu.getCategory().getName().equals("beverage"))
            .forEach(menu -> data.append(String.format("- %s: %,d원 (%s)\n", 
                menu.getDisplayName(), 
                menu.getBasePrice().intValue(),
                menu.getDescription() != null ? menu.getDescription() : "")));

        data.append("\n[디저트 메뉴]\n");
        menus.stream()
            .filter(menu -> menu.getCategory().getName().equals("dessert"))
            .forEach(menu -> data.append(String.format("- %s: %,d원 (%s)\n", 
                menu.getDisplayName(), 
                menu.getBasePrice().intValue(),
                menu.getDescription() != null ? menu.getDescription() : "")));

        data.append("\n=== 카테고리별 요약 ===\n");
        data.append("- 커피: 아메리카노, 라떼, 에스프레소, 카푸치노, 모카 등 20종\n");
        data.append("- 음료: 스무디, 주스, 에이드, 차류, 밀크티 등 23종\n");
        data.append("- 디저트: 케이크, 쿠키, 브라우니, 마카롱 등 10종\n");

        return data.toString();
    }

    /**
     * RAG 기반 일반 질문 처리 프롬프트
     */
    private String buildRAGPrompt(String userInput) {
        StringBuilder prompt = new StringBuilder();
        
        // 메뉴 데이터 포함
        prompt.append(getAllMenuDataAsText());
        
        prompt.append("\n\n=== 사용자 질문 ===\n");
        prompt.append(userInput);
        
        prompt.append("\n\n=== 응답 규칙 ===\n");
        prompt.append("위의 메뉴 데이터를 정확히 참고해서 사용자의 질문에 답변해주세요.\n");
        prompt.append("- 가격 정보는 정확한 금액을 명시해주세요\n");
        prompt.append("- 메뉴명은 정확히 표기해주세요\n");
        prompt.append("- 카테고리별로 분류해서 답변하면 더 좋습니다\n");
        prompt.append("- 친근하고 자연스러운 톤으로 답변해주세요\n");
        prompt.append("- 카페 종업원처럼 응답해주세요\n\n");
        
        prompt.append("응답 형식: 일반 텍스트 (JSON 아님)");
        
        return prompt.toString();
    }

    /**
     * 일반적인 질문인지 판단
     */
    private boolean isGeneralQuestion(String userInput) {
        String[] questionKeywords = {
            "뭐", "무엇", "어떤", "얼마", "가격", "비싼", "싼", "저렴", "제일", "가장", 
            "추천", "인기", "맛있는", "몇개", "몇 개", "얼마나", "어디", "언제", 
            "왜", "어떻게", "몇시", "몇 시", "정보", "알려", "궁금", "문의"
        };
        
        for (String keyword : questionKeywords) {
            if (userInput.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 의도 판단을 위한 간단한 Gemini 호출
     */
    public String callGeminiForIntent(String prompt) {
        try {
            return geminiService.generateText(prompt);
        } catch (Exception e) {
            log.error("Gemini 의도 판단 호출 실패: {}", e.getMessage(), e);
            return null;
        }
    }


} 