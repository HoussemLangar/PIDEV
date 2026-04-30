package com.santea.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filtre mots interdits (fallback modération) — aligné sur symfony-app ForbiddenWordsFilterService.
 */
public class ForbiddenWordsFilterService {
    private final List<String> forbiddenWords;

    public ForbiddenWordsFilterService(List<String> forbiddenWords) {
        this.forbiddenWords = forbiddenWords == null ? List.of() : List.copyOf(forbiddenWords);
    }

    public List<String> findForbiddenWords(String text) {
        if (text == null || text.isEmpty() || forbiddenWords.isEmpty()) {
            return List.of();
        }

        String cleaned = cleanText(text);
        Set<String> matches = new LinkedHashSet<>();
        for (String word : forbiddenWords) {
            String w = word == null ? "" : word.trim();
            if (w.isEmpty()) {
                continue;
            }
            if (matchesWord(cleaned, w)) {
                matches.add(w);
            }
        }
        return new ArrayList<>(matches);
    }

    public boolean hasBadWord(String text) {
        return !findForbiddenWords(text).isEmpty();
    }

    public String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        t = t.replaceAll("[^\\p{L}\\p{N}\\s]", "");
        return t.toLowerCase(Locale.ROOT);
    }

    private boolean matchesWord(String cleanedText, String word) {
        String normalizedWord = normalizeLeetToken(word);
        if (normalizedWord.isEmpty()) {
            return false;
        }

        String[] tokens = cleanedText.split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (normalizeLeetToken(token).equals(normalizedWord)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeLeetToken(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('2', 'e')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('6', 'g')
                .replace('7', 't')
                .replace('8', 'b')
                .replace('9', 'g');
        return normalized.replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
