<?php

namespace App\Service;

class ForbiddenWordsFilterService
{
    /**
     * @param string[] $forbiddenWords
     */
    public function __construct(private array $forbiddenWords = [])
    {
    }

    /**
     * @return string[] matched forbidden words
     */
    public function findForbiddenWords(string $text): array
    {
        if ($text === '' || $this->forbiddenWords === []) {
            return [];
        }

        $cleaned = $this->cleanText($text);
        $matches = [];
        foreach ($this->forbiddenWords as $word) {
            $word = trim((string) $word);
            if ($word === '') {
                continue;
            }
            if ($this->matchesWord($cleaned, $word)) {
                $matches[] = $word;
            }
        }

        return array_values(array_unique($matches));
    }

    public function hasBadWord(string $text): bool
    {
        return $this->findForbiddenWords($text) !== [];
    }

    public function cleanText(string $text): string
    {
        $text = trim($text);
        $text = preg_replace('/\s+/u', ' ', $text) ?? $text;
        $text = preg_replace('/[^\p{L}\p{N}\s]/u', '', $text) ?? $text;
        return mb_strtolower($text);
    }

    private function matchesWord(string $cleanedText, string $word): bool
    {
        $normalizedWord = $this->normalizeLeetToken($word);
        if ($normalizedWord === '') {
            return false;
        }

        $tokens = preg_split('/\s+/u', $cleanedText, -1, PREG_SPLIT_NO_EMPTY) ?: [];
        foreach ($tokens as $token) {
            if ($this->normalizeLeetToken($token) === $normalizedWord) {
                return true;
            }
        }

        return false;
    }

    private function normalizeLeetToken(string $value): string
    {
        $value = mb_strtolower($value);
        $value = strtr($value, [
            '0' => 'o',
            '1' => 'i',
            '2' => 'e',
            '3' => 'e',
            '4' => 'a',
            '5' => 's',
            '6' => 'g',
            '7' => 't',
            '8' => 'b',
            '9' => 'g',
        ]);

        return preg_replace('/[^\p{L}\p{N}]/u', '', $value) ?? '';
    }
}
