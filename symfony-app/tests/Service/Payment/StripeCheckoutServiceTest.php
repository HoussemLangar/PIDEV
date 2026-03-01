<?php

namespace App\Tests\Service\Payment;

use App\Service\Payment\StripeCheckoutService;
use PHPUnit\Framework\TestCase;
use Psr\Log\NullLogger;
use Symfony\Contracts\HttpClient\HttpClientInterface;

final class StripeCheckoutServiceTest extends TestCase
{
    /** @testdox Accepter une signature webhook Stripe valide */
    public function testVerifyWebhookSignatureReturnsTrueForValidPayload(): void
    {
        $service = new StripeCheckoutService(
            $this->createMock(HttpClientInterface::class),
            new NullLogger(),
            'sk_test_demo',
            'whsec_test_secret',
            'usd',
            0.36
        );

        $payload = '{"id":"evt_1","type":"checkout.session.completed"}';
        $timestamp = time();
        $signature = hash_hmac('sha256', $timestamp . '.' . $payload, 'whsec_test_secret');
        $header = sprintf('t=%d,v1=%s', $timestamp, $signature);

        self::assertTrue($service->verifyWebhookSignature($payload, $header));
    }

    /** @testdox Rejeter une signature webhook Stripe invalide */
    public function testVerifyWebhookSignatureReturnsFalseForInvalidSignature(): void
    {
        $service = new StripeCheckoutService(
            $this->createMock(HttpClientInterface::class),
            new NullLogger(),
            'sk_test_demo',
            'whsec_test_secret',
            'usd',
            0.36
        );

        $payload = '{"id":"evt_1","type":"checkout.session.completed"}';
        $timestamp = time();
        $header = sprintf('t=%d,v1=%s', $timestamp, 'invalid');

        self::assertFalse($service->verifyWebhookSignature($payload, $header));
    }

    /** @testdox Convertir les montants TND avec le taux configuré */
    public function testConvertFromTndUsesConfiguredRate(): void
    {
        $service = new StripeCheckoutService(
            $this->createMock(HttpClientInterface::class),
            new NullLogger(),
            'sk_test_demo',
            'whsec_test_secret',
            'usd',
            0.50
        );

        self::assertSame(5.0, $service->convertFromTnd(10.0));
    }
}
