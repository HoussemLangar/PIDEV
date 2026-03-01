<?php

namespace App\Tests\Controller;

use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

final class AdminControllerTest extends WebTestCase
{
    /** @testdox Vérifier l'accès à la route admin content */
    public function testIndex(): void
    {
        $client = static::createClient(['environment' => 'test', 'debug' => true]);
        $client->catchExceptions(true);
        $client->request('GET', '/admin/content');

        self::assertContains(
            $client->getResponse()->getStatusCode(),
            [200, 302, 403]
        );
    }
}
