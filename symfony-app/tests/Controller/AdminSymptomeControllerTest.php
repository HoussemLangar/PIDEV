<?php

namespace App\Tests\Controller;

use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;

final class AdminSymptomeControllerTest extends KernelTestCase
{
    /** @testdox Vérifier le mapping de la route admin symptômes */
    public function testIndex(): void
    {
        self::bootKernel(['environment' => 'test', 'debug' => true]);
        $router = self::$kernel->getContainer()->get('router');
        $matched = $router->match('/admin/symptomes');

        self::assertSame('app_symptome_liste_index', $matched['_route'] ?? null);
    }
}
