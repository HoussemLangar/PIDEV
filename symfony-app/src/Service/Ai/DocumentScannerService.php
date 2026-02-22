<?php

namespace App\Service\Ai;

class DocumentScannerService
{
    public function __construct(
        private readonly AiGatewayService $aiGateway,
    ) {
    }

    public function scan(string $content): array
    {
        $content = trim($content);
        if ($content === '') {
            return [
                'summary' => 'Aucun contenu fourni.',
                'keyPoints' => [],
                'values' => [],
                'suggestedActions' => [],
            ];
        }

        $fallback = $this->buildFallback($content);

        $ai = $this->aiGateway->askForJson(
            'Tu es un assistant OCR médical. Reponds uniquement en JSON: '
            . '{"summary":string,"keyPoints":string[],"values":[{"label":string,"value":string,"unit":string}],"suggestedActions":string[]}.',
            "Analyse ce document medical:\n\n" . $content
        );

        if (!is_array($ai)) {
            return $fallback;
        }

        return [
            'summary' => (string)($ai['summary'] ?? $fallback['summary']),
            'keyPoints' => $this->normalizeStringList($ai['keyPoints'] ?? $fallback['keyPoints']),
            'values' => $this->normalizeValues($ai['values'] ?? $fallback['values']),
            'suggestedActions' => $this->normalizeStringList($ai['suggestedActions'] ?? $fallback['suggestedActions']),
        ];
    }

    private function buildFallback(string $content): array
    {
        $summary = mb_substr(preg_replace('/\s+/', ' ', $content) ?? '', 0, 240);

        preg_match_all('/\b\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}\b/', $content, $dateMatches);
        $dates = array_values(array_unique($dateMatches[0] ?? []));

        preg_match_all(
            '/([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \-_]{1,40})\s*[:\-]?\s*([0-9]+(?:[.,][0-9]+)?)\s*([A-Za-z%\/µ]+)?/u',
            $content,
            $valueMatches,
            \PREG_SET_ORDER
        );

        $values = [];
        foreach (array_slice($valueMatches, 0, 12) as $match) {
            $label = trim((string)($match[1] ?? ''));
            $value = trim((string)($match[2] ?? ''));
            $unit = trim((string)($match[3] ?? ''));
            if ($label === '' || $value === '') {
                continue;
            }

            $values[] = [
                'label' => $label,
                'value' => $value,
                'unit' => $unit,
            ];
        }

        $keyPoints = [];
        if ($dates !== []) {
            $keyPoints[] = 'Dates détectées: ' . implode(', ', array_slice($dates, 0, 5));
        }
        if ($values !== []) {
            $keyPoints[] = sprintf('%d mesures numériques détectées.', count($values));
        }

        return [
            'summary' => $summary !== '' ? $summary : 'Document analysé.',
            'keyPoints' => $keyPoints,
            'values' => $values,
            'suggestedActions' => [
                'Vérifier les valeurs anormales avec un professionnel de santé.',
                'Conserver ce document dans votre dossier médical personnel.',
            ],
        ];
    }

    private function normalizeStringList(mixed $items): array
    {
        if (!is_array($items)) {
            return [];
        }

        $out = [];
        foreach ($items as $item) {
            if (is_string($item) && trim($item) !== '') {
                $out[] = trim($item);
            }
        }
        return array_values(array_unique($out));
    }

    private function normalizeValues(mixed $items): array
    {
        if (!is_array($items)) {
            return [];
        }

        $out = [];
        foreach ($items as $item) {
            if (!is_array($item)) {
                continue;
            }
            $label = trim((string)($item['label'] ?? ''));
            $value = trim((string)($item['value'] ?? ''));
            $unit = trim((string)($item['unit'] ?? ''));
            if ($label === '' || $value === '') {
                continue;
            }
            $out[] = ['label' => $label, 'value' => $value, 'unit' => $unit];
        }
        return $out;
    }
}

