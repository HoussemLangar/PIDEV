<?php

namespace App\Service\Ai;

use Symfony\Contracts\HttpClient\HttpClientInterface;

class AiGatewayService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $baseUrl,
        private readonly string $apiKey,
        private readonly string $model,
    ) {
    }

    public function isEnabled(): bool
    {
        return trim($this->apiKey) !== '';
    }

    public function askForJson(string $systemPrompt, string $userPrompt): ?array
    {
        if (!$this->isEnabled()) {
            return null;
        }

        $url = rtrim($this->baseUrl, '/') . '/chat/completions';

        try {
            $response = $this->httpClient->request('POST', $url, [
                'headers' => [
                    'Authorization' => 'Bearer ' . $this->apiKey,
                    'Content-Type' => 'application/json',
                ],
                'json' => [
                    'model' => $this->model,
                    'temperature' => 0.2,
                    'messages' => [
                        ['role' => 'system', 'content' => $systemPrompt],
                        ['role' => 'user', 'content' => $userPrompt],
                    ],
                ],
                'timeout' => 8,
                'max_duration' => 10,
            ]);

            $payload = $response->toArray(false);
            $content = $payload['choices'][0]['message']['content'] ?? null;

            if (!is_string($content) || trim($content) === '') {
                return null;
            }

            return $this->decodeJsonFromText($content);
        } catch (\Throwable) {
            return null;
        }
    }

    private function decodeJsonFromText(string $text): ?array
    {
        $decoded = json_decode($text, true);
        if (is_array($decoded)) {
            return $decoded;
        }

        $start = strpos($text, '{');
        $end = strrpos($text, '}');
        if ($start === false || $end === false || $end <= $start) {
            return null;
        }

        $candidate = substr($text, $start, $end - $start + 1);
        $decoded = json_decode($candidate, true);

        return is_array($decoded) ? $decoded : null;
    }
}

