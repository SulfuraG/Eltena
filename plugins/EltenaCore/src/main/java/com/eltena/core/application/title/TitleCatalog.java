package com.eltena.core.application.title;

import com.eltena.core.domain.title.TitleDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TitleCatalog {

    private final Map<String, TitleDefinition> titles = new LinkedHashMap<>();

    public synchronized TitleDefinition find(String titleId) {
        return titles.get(titleId);
    }

    public synchronized List<TitleDefinition> list() {
        return List.copyOf(titles.values());
    }

    public synchronized void replaceAll(Map<String, TitleDefinition> definitions) {
        titles.clear();
        titles.putAll(definitions);
    }

    public synchronized boolean isEmpty() {
        return titles.isEmpty();
    }
}
