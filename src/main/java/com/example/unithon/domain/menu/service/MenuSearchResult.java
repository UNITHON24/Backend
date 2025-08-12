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
    private final List<Menu> ambiguousMenus;

    private MenuSearchResult(MenuSearchResultType type, Menu menu, String geminiResponse, List<Menu> suggestions, List<Menu> ambiguousMenus) {
        this.type = type;
        this.menu = menu;
        this.geminiResponse = geminiResponse;
        this.suggestions = suggestions;
        this.ambiguousMenus = ambiguousMenus;
    }

    public static MenuSearchResult directMatch(Menu menu) {
        return new MenuSearchResult(MenuSearchResultType.DIRECT_MATCH, menu, null, null, null);
    }

    public static MenuSearchResult ambiguousMatch(List<Menu> menus) {
        return new MenuSearchResult(MenuSearchResultType.AMBIGUOUS_MATCH, null, null, null, menus);
    }

    public static MenuSearchResult geminiSuggestion(String response, List<Menu> suggestions) {
        return new MenuSearchResult(MenuSearchResultType.GEMINI_SUGGESTION, null, response, suggestions, null);
    }

    public static MenuSearchResult noMatch() {
        return new MenuSearchResult(MenuSearchResultType.NO_MATCH, null, null, null, null);
    }
} 