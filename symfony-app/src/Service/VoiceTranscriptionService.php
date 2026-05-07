<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\HttpClientInterface;

class VoiceTranscriptionService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
    ) {
    }

    public function transcribe(string $filePath, string $fileName = 'audio.wav', ?string $language = null): ?string
    {
        if (!is_file($filePath)) {
            return null;
        }

        $endpoint = $this->resolveEndpoint();
        if ($endpoint === '') {
            return null;
        }

        $apiKey = $this->resolveApiKey();
        $model = $this->resolveModel();
        $language = $language ?? $this->resolveLanguage();

        $handle = fopen($filePath, 'r');
        if (!$handle) {
            return null;
        }

        $headers = [];
        if ($apiKey !== '') {
            $headers['Authorization'] = 'Bearer ' . $apiKey;
        }

        $body = [
            'model' => $model,
            'file' => $handle,
        ];
        if ($language !== null && trim($language) !== '') {
            $body['language'] = trim($language);
        }

        try {
            $response = $this->httpClient->request('POST', $endpoint, [
                'headers' => $headers,
                'body' => $body,
                'timeout' => 30,
                'max_duration' => 60,
            ]);

            if ($response->getStatusCode() < 200 || $response->getStatusCode() >= 300) {
                return null;
            }

            $payload = $response->toArray(false);
            $text = $payload['text'] ?? ($payload['transcript'] ?? '');
            $text = is_string($text) ? trim($text) : '';
            return $text !== '' ? $text : null;
        } catch (\Throwable) {
            return null;
        } finally {
            if (is_resource($handle)) {
                fclose($handle);
            }
        }
    }

    private function resolveEndpoint(): string
    {
        $endpoint = $this->env('WHISPER_API_URL');
        if ($endpoint !== '') {
            return $endpoint;
        }

        $apiKey = $this->resolveApiKey();
        if ($apiKey === '') {
            return '';
        }

        return 'https://api.openai.com/v1/audio/transcriptions';
    }

    private function resolveApiKey(): string
    {
        $key = $this->env('WHISPER_API_KEY');
        if ($key !== '') {
            return $key;
        }
        return $this->env('OPENAI_API_KEY');
    }

    private function resolveModel(): string
    {
        $model = $this->env('WHISPER_MODEL');
        return $model !== '' ? $model : 'whisper-1';
    }

    private function resolveLanguage(): ?string
    {
        $language = $this->env('WHISPER_LANGUAGE');
        return $language !== '' ? $language : null;
    }

    private function env(string $key): string
    {
        $value = $_ENV[$key] ?? $_SERVER[$key] ?? '';
        return is_string($value) ? trim($value) : '';
    }
}
