<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class CommentSentimentScoringService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient
    ) {}

    /**
     * @return array{score:int,label:string,confidence:float,source:string}
     */
    public function analyze(string $text): array
    {
        $normalized = trim($text);
        if ($normalized === '') {
            return [
                'score' => 50,
                'label' => 'NEUTRAL',
                'confidence' => 0.0,
                'source' => 'empty',
            ];
        }

        $remote = $this->analyzeWithRobertaXml($normalized);
        if ($remote !== null) {
            return $remote;
        }

        return $this->fallbackAnalyze($normalized);
    }

    /**
     * Convertit le résultat d'analyse en score signé [-1, 1].
     *
     * @param array{score?:int|float,label?:string,confidence?:float} $analysis
     */
    public function toSignedScore(array $analysis): float
    {
        $label = strtoupper(trim((string) ($analysis['label'] ?? 'NEUTRAL')));
        $confidenceRaw = $analysis['confidence'] ?? null;
        $scoreRaw = $analysis['score'] ?? 0;

        if (is_numeric($confidenceRaw)) {
            $confidence = (float) $confidenceRaw;
        } elseif (is_numeric($scoreRaw)) {
            $scoreFloat = (float) $scoreRaw;
            $confidence = $scoreFloat > 1.0 ? ($scoreFloat / 100.0) : $scoreFloat;
        } else {
            $confidence = 0.0;
        }

        $confidence = max(0.0, min(1.0, $confidence));

        if ($label === 'POSITIVE') {
            return $confidence;
        }
        if ($label === 'NEGATIVE') {
            return -$confidence;
        }

        return 0.0;
    }

    /**
     * @return array{score:int,label:string,confidence:float,source:string}|null
     */
    private function analyzeWithRobertaXml(string $text): ?array
    {
        $endpoint = trim((string) ($_ENV['ROBERTA_XML_ENDPOINT'] ?? $_SERVER['ROBERTA_XML_ENDPOINT'] ?? ''));
        if ($endpoint === '') {
            return null;
        }

        $payload = [
            'text' => $text,
            'model' => (string) ($_ENV['ROBERTA_XML_MODEL'] ?? $_SERVER['ROBERTA_XML_MODEL'] ?? 'roberta-xml'),
        ];

        try {
            $response = $this->httpClient->request('POST', $endpoint, [
                'json' => $payload,
                'timeout' => 4.0,
            ]);
            $data = $response->toArray(false);
        } catch (TransportExceptionInterface|\Throwable) {
            return null;
        }

        return $this->normalizeRobertaResponse($data);
    }

    /**
     * @param mixed $data
     * @return array{score:int,label:string,confidence:float,source:string}|null
     */
    private function normalizeRobertaResponse(mixed $data): ?array
    {
        if (!is_array($data)) {
            return null;
        }

        $label = null;
        $confidence = null;
        $score = null;

        if (isset($data['score']) && is_numeric($data['score'])) {
            $score = (float) $data['score'];
        }
        if (isset($data['label']) && is_string($data['label'])) {
            $label = strtoupper(trim($data['label']));
        }
        if (isset($data['confidence']) && is_numeric($data['confidence'])) {
            $confidence = (float) $data['confidence'];
        }

        if (isset($data['result']) && is_array($data['result'])) {
            $nested = $this->normalizeRobertaResponse($data['result']);
            if ($nested !== null) {
                return $nested;
            }
        }

        if (array_is_list($data) && isset($data[0]) && is_array($data[0])) {
            $nested = $this->normalizeRobertaResponse($data[0]);
            if ($nested !== null) {
                return $nested;
            }
        }

        if ($score === null && $confidence !== null) {
            $score = $confidence <= 1.0 ? $confidence * 100.0 : $confidence;
        }

        if ($score === null || $label === null) {
            return null;
        }

        $scoreInt = (int) max(0, min(100, round($score)));
        $confidenceValue = $confidence ?? ($scoreInt / 100);
        $confidenceValue = max(0.0, min(1.0, $confidenceValue));

        return [
            'score' => $scoreInt,
            'label' => $this->normalizeLabel($label),
            'confidence' => $confidenceValue,
            'source' => 'roberta_xml',
        ];
    }

    /**
     * @return array{score:int,label:string,confidence:float,source:string}
     */
    private function fallbackAnalyze(string $text): array
    {
        $positiveWords = [
            'excellent', 'super', 'merci', 'utile', 'clair', 'parfait', 'top', 'bon', 'bien', 'aide',
            'bravo', 'genial', 'satisfait', 'recommande', 'helpful', 'great', 'love', 'nice', 'good',
        ];
        $negativeWords = [
            'nul', 'mauvais', 'horrible', 'arnaque', 'faux', 'inutile', 'déçu', 'decu', 'lent',
            'bug', 'erreur', 'grave', 'dangereux', 'haine', 'violence', 'spam', 'bad',
        ];

        $lower = mb_strtolower($text);
        $positive = 0;
        $negative = 0;

        foreach ($positiveWords as $word) {
            if (str_contains($lower, $word)) {
                $positive++;
            }
        }
        foreach ($negativeWords as $word) {
            if (str_contains($lower, $word)) {
                $negative++;
            }
        }

        $raw = 50 + ($positive * 12) - ($negative * 12);
        $score = (int) max(0, min(100, $raw));
        $label = $score >= 55 ? 'POSITIVE' : ($score <= 45 ? 'NEGATIVE' : 'NEUTRAL');
        $confidence = min(1.0, 0.45 + (abs($positive - $negative) * 0.1));

        return [
            'score' => $score,
            'label' => $label,
            'confidence' => $confidence,
            'source' => 'fallback',
        ];
    }

    private function normalizeLabel(string $label): string
    {
        if (preg_match('/label[_\s-]?2/i', $label)) {
            return 'POSITIVE';
        }
        if (preg_match('/label[_\s-]?0/i', $label)) {
            return 'NEGATIVE';
        }
        if (preg_match('/label[_\s-]?1/i', $label)) {
            return 'NEUTRAL';
        }
        if (str_contains($label, 'POS')) {
            return 'POSITIVE';
        }
        if (str_contains($label, 'NEG')) {
            return 'NEGATIVE';
        }

        return 'NEUTRAL';
    }
}
