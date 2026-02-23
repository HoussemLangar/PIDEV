<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class CommentModerationService
{
    /** @var array<string, array{blocked:bool, score:float, label:string, source:string}> */
    private array $cache = [];

    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly ForbiddenWordsFilterService $fallbackFilter
    ) {}

    /**
     * @return array{blocked:bool, score:float, label:string, source:string}
     */
    public function moderate(string $text): array
    {
        $normalized = trim($text);
        if ($normalized === '') {
            return [
                'blocked' => true,
                'score' => 1.0,
                'label' => 'EMPTY',
                'source' => 'empty',
            ];
        }

        $cacheKey = sha1(mb_strtolower($normalized));
        if (isset($this->cache[$cacheKey])) {
            return $this->cache[$cacheKey];
        }

        $remote = $this->moderateWithModel($normalized);
        if ($remote !== null) {
            return $this->cache[$cacheKey] = $remote;
        }

        $cleaned = $this->fallbackFilter->cleanText($normalized);
        $blocked = $cleaned === '' || $this->fallbackFilter->hasBadWord($cleaned);

        return $this->cache[$cacheKey] = [
            'blocked' => $blocked,
            'score' => $blocked ? 0.9 : 0.1,
            'label' => $blocked ? 'TOXIC_FALLBACK' : 'CLEAN_FALLBACK',
            'source' => 'fallback_words',
        ];
    }

    public function isInappropriate(string $text): bool
    {
        return $this->moderate($text)['blocked'];
    }

    /**
     * @return array{blocked:bool, score:float, label:string, source:string}|null
     */
    private function moderateWithModel(string $text): ?array
    {
        $endpoint = trim((string) ($_ENV['TOXICITY_MODEL_ENDPOINT'] ?? $_SERVER['TOXICITY_MODEL_ENDPOINT'] ?? ''));
        if ($endpoint === '') {
            return null;
        }

        $thresholdRaw = $_ENV['TOXICITY_THRESHOLD'] ?? $_SERVER['TOXICITY_THRESHOLD'] ?? '0.60';
        $threshold = is_numeric($thresholdRaw) ? (float) $thresholdRaw : 0.60;
        $threshold = max(0.0, min(1.0, $threshold));

        $payload = [
            'text' => $text,
            'model' => (string) ($_ENV['TOXICITY_MODEL_NAME'] ?? $_SERVER['TOXICITY_MODEL_NAME'] ?? 'unitary/toxic-bert'),
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

        $normalized = $this->normalizeModelResponse($data);
        if ($normalized === null) {
            return null;
        }

        $label = $normalized['label'];
        $score = $normalized['score'];
        $blocked = $this->isToxicLabel($label) && $score >= $threshold;

        return [
            'blocked' => $blocked,
            'score' => $score,
            'label' => $label,
            'source' => 'toxicity_model',
        ];
    }

    /**
     * @param mixed $data
     * @return array{label:string, score:float}|null
     */
    private function normalizeModelResponse(mixed $data): ?array
    {
        if (!is_array($data)) {
            return null;
        }

        if (isset($data['result']) && is_array($data['result'])) {
            $nested = $this->normalizeModelResponse($data['result']);
            if ($nested !== null) {
                return $nested;
            }
        }

        if (array_is_list($data) && isset($data[0]) && is_array($data[0])) {
            $nested = $this->normalizeModelResponse($data[0]);
            if ($nested !== null) {
                return $nested;
            }
        }

        $label = isset($data['label']) && is_string($data['label'])
            ? strtoupper(trim($data['label']))
            : null;

        $score = null;
        if (isset($data['score']) && is_numeric($data['score'])) {
            $score = (float) $data['score'];
        } elseif (isset($data['confidence']) && is_numeric($data['confidence'])) {
            $score = (float) $data['confidence'];
        }

        if ($label === null || $score === null) {
            return null;
        }

        $score = $score > 1.0 ? ($score / 100.0) : $score;
        $score = max(0.0, min(1.0, $score));

        return ['label' => $label, 'score' => $score];
    }

    private function isToxicLabel(string $label): bool
    {
        if (str_contains($label, 'NON_TOXIC') || str_contains($label, 'NOT_TOXIC') || str_contains($label, 'CLEAN')) {
            return false;
        }

        $toxicityHints = ['TOXIC', 'INSULT', 'OBSCENE', 'PROFAN', 'HATE', 'ABUSE', 'OFFENSIVE', 'LABEL_1'];
        foreach ($toxicityHints as $hint) {
            if (str_contains($label, $hint)) {
                return true;
            }
        }

        return false;
    }

}
