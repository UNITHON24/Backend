package com.example.unithon.domain.menu.service;

import com.example.unithon.domain.menu.entity.Menu;
import lombok.Getter;

import java.util.List;

@Getter
public class MenuSearchResult {
    private final MenuSearchResultType type;
    private final Menu menu;
    private final String geminiResponse;
    private final List<Menu> suggestions;

    private MenuSearchResult(MenuSearchResultType type, Menu menu, String geminiResponse, List<Menu> suggestions) {
        this.type = type;
        this.menu = menu;
        this.geminiResponse = geminiResponse;
        this.suggestions = suggestions;
    }

    public static MenuSearchResult directMatch(Menu menu) {
        return new MenuSearchResult(MenuSearchResultType.DIRECT_MATCH, menu, null, null);
    }

    public static MenuSearchResult geminiSuggestion(String response, List<Menu> suggestions) {
        return new MenuSearchResult(MenuSearchResultType.GEMINI_SUGGESTION, null, response, suggestions);
    }

    public static MenuSearchResult noMatch() {
        return new MenuSearchResult(MenuSearchResultType.NO_MATCH, null, null, null);
    }
} 