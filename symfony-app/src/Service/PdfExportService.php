<?php

namespace App\Service;

use Symfony\Component\HttpFoundation\Response;
use Twig\Environment;

class PdfExportService
{
    public function __construct(private readonly Environment $twig) {}

    /**
     * Renders a Twig template for PDF output.
     * This is intentionally HTML-only unless a real PDF engine is wired.
     */
    public function renderHtml(string $template, array $context = []): Response
    {
        $html = $this->twig->render($template, $context);

        return new Response($html, Response::HTTP_OK, [
            'Content-Type' => 'text/html; charset=UTF-8',
        ]);
    }
}
