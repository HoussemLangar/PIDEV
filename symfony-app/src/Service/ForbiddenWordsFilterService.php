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

        $matches = [];
        $lower = mb_strtolower($text);
        foreach ($this->forbiddenWords as $word) {
            $word = trim((string) $word);
            if ($word === '') {
                continue;
            }
            if (str_contains($lower, mb_strtolower($word))) {
                $matches[] = $word;
            }
        }

        return array_values(array_unique($matches));
    }
}
