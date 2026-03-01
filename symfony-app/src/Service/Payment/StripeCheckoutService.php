<?php

namespace App\Service\Payment;

use Psr\Log\LoggerInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class StripeCheckoutService
{
    private const STRIPE_API_BASE = 'https://api.stripe.com/v1';

    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly LoggerInterface $logger,
        private readonly string $secretKey,
        private readonly string $webhookSecret = '',
        private readonly string $currency = 'usd',
        private readonly float $tndToStripeRate = 0.32
    ) {
    }

    public function createCheckoutSession(
        string $planLabel,
        float $amount,
        string $successUrl,
        string $cancelUrl,
        string $customerEmail,
        array $metadata = []
    ): ?string {
        if (!$this->isConfigured()) {
            return null;
        }

        $amountForStripe = $this->convertFromTnd($amount);
        $unitAmount = (int) round($amountForStripe * 100);
        if ($unitAmount <= 0) {
            return null;
        }

        $payload = [
            'mode' => 'payment',
            'success_url' => $successUrl,
            'cancel_url' => $cancelUrl,
            'customer_email' => $customerEmail,
            'line_items[0][price_data][currency]' => strtolower($this->currency),
            'line_items[0][price_data][unit_amount]' => (string) $unitAmount,
            'line_items[0][price_data][product_data][name]' => $planLabel,
            'line_items[0][quantity]' => '1',
            'metadata[local_currency]' => 'TND',
            'metadata[local_amount]' => number_format($amount, 2, '.', ''),
        ];

        foreach ($metadata as $key => $value) {
            $payload[sprintf('metadata[%s]', (string) $key)] = (string) $value;
        }

        try {
            $response = $this->httpClient->request('POST', self::STRIPE_API_BASE . '/checkout/sessions', [
                'headers' => [
                    'Authorization' => 'Bearer ' . $this->secretKey,
                    'Content-Type' => 'application/x-www-form-urlencoded',
                ],
                'body' => http_build_query($payload),
            ]);

            $status = $response->getStatusCode();
            $data = $response->toArray(false);

            if ($status >= 400) {
                $this->logger->error('Stripe checkout session creation failed.', ['status' => $status, 'data' => $data]);
                return null;
            }

            return isset($data['url']) && is_string($data['url']) ? $data['url'] : null;
        } catch (\Throwable $exception) {
            $this->logger->error('Stripe checkout session creation exception.', ['exception' => $exception]);
            return null;
        }
    }

    public function fetchSession(string $sessionId): ?array
    {
        if (!$this->isConfigured() || trim($sessionId) === '') {
            return null;
        }

        try {
            $response = $this->httpClient->request('GET', self::STRIPE_API_BASE . '/checkout/sessions/' . rawurlencode($sessionId), [
                'headers' => [
                    'Authorization' => 'Bearer ' . $this->secretKey,
                ],
            ]);

            $status = $response->getStatusCode();
            $data = $response->toArray(false);

            if ($status >= 400 || !is_array($data)) {
                $this->logger->warning('Stripe session fetch failed.', ['status' => $status, 'data' => $data]);
                return null;
            }

            return $data;
        } catch (\Throwable $exception) {
            $this->logger->error('Stripe session fetch exception.', ['exception' => $exception]);
            return null;
        }
    }

    public function isConfigured(): bool
    {
        return trim($this->secretKey) !== '';
    }

    public function getCurrency(): string
    {
        return strtolower($this->currency);
    }

    public function convertFromTnd(float $amount): float
    {
        $currency = strtolower($this->currency);

        if ($currency === 'tnd') {
            return round($amount, 2);
        }

        $rate = $this->tndToStripeRate > 0 ? $this->tndToStripeRate : 1.0;

        return round($amount * $rate, 2);
    }

    public function verifyWebhookSignature(string $payload, string $signatureHeader, int $toleranceSeconds = 300): bool
    {
        if (trim($this->webhookSecret) === '' || trim($payload) === '' || trim($signatureHeader) === '') {
            return false;
        }

        $parts = [];
        foreach (explode(',', $signatureHeader) as $segment) {
            $kv = explode('=', trim($segment), 2);
            if (count($kv) !== 2) {
                continue;
            }

            $parts[$kv[0]][] = $kv[1];
        }

        $timestamp = isset($parts['t'][0]) ? (int) $parts['t'][0] : 0;
        $signatures = $parts['v1'] ?? [];

        if ($timestamp <= 0 || $signatures === []) {
            return false;
        }

        if (abs(time() - $timestamp) > $toleranceSeconds) {
            return false;
        }

        $expected = hash_hmac('sha256', $timestamp . '.' . $payload, $this->webhookSecret);

        foreach ($signatures as $signature) {
            if (is_string($signature) && hash_equals($expected, $signature)) {
                return true;
            }
        }

        return false;
    }

    public function decodeWebhookEvent(string $payload): ?array
    {
        if (trim($payload) === '') {
            return null;
        }

        $decoded = json_decode($payload, true);
        if (!is_array($decoded)) {
            return null;
        }

        return $decoded;
    }
}
