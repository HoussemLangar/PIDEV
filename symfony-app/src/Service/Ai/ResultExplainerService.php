<?php

namespace App\Service\Ai;

class ResultExplainerService
{
    public function __construct(
        private readonly AiGatewayService $aiGateway,
    ) {
    }

    public function explain(string $testName, string $value, string $unit, string $referenceRange): array
    {
        $fallback = $this->buildFallback($testName, $value, $unit, $referenceRange);

        $ai = $this->aiGateway->askForJson(
            'Tu es assistant medical pedagogique. Reponds uniquement en JSON: '
            . '{"plainExplanation":string,"possibleMeaning":string[],"nextSteps":string[],"warning":string}. '
            . 'Regles: explication simple, prudente, basee sur la valeur + intervalle fourni, pas de diagnostic definitif, actions concretes.',
            sprintf(
                "Nom du test: %s\nValeur: %s %s\nIntervalle de reference: %s",
                $testName,
                $value,
                $unit,
                $referenceRange !== '' ? $referenceRange : 'non fourni'
            )
        );

        if (!is_array($ai)) {
            return $fallback;
        }

        $possibleMeaning = $this->normalizeStringList($ai['possibleMeaning'] ?? []);
        if ($possibleMeaning === []) {
            $possibleMeaning = $fallback['possibleMeaning'];
        }

        $nextSteps = $this->normalizeStringList($ai['nextSteps'] ?? []);
        if ($nextSteps === []) {
            $nextSteps = $fallback['nextSteps'];
        }

        return [
            'plainExplanation' => (string)($ai['plainExplanation'] ?? $fallback['plainExplanation']),
            'possibleMeaning' => $possibleMeaning,
            'nextSteps' => $nextSteps,
            'warning' => (string)($ai['warning'] ?? $fallback['warning']),
        ];
    }

    private function buildFallback(string $testName, string $value, string $unit, string $referenceRange): array
    {
        $label = trim($testName) !== '' ? $testName : 'ce resultat';
        $unitPart = trim($unit) !== '' ? ' ' . trim($unit) : '';

        $statusLine = $this->buildStatusLine($value, $referenceRange);
        $possibleMeaning = [];
        if ($statusLine !== null) {
            $possibleMeaning[] = $statusLine;
        }

        if ($referenceRange !== '') {
            $possibleMeaning[] = 'Comparer a l intervalle de reference: ' . $referenceRange;
        }
        $possibleMeaning[] = 'Une valeur isolee doit etre interpretee avec le contexte clinique et les symptomes.';

        $nextSteps = [
            'Montrer ce resultat a votre medecin traitant.',
        ];
        if ($statusLine !== null && (str_contains(mb_strtolower($statusLine), 'au-dessus') || str_contains(mb_strtolower($statusLine), 'en dessous'))) {
            $nextSteps[] = 'Verifier rapidement avec un professionnel si des symptomes sont presents.';
        }
        $nextSteps[] = 'Refaire le test si recommande par un professionnel.';

        return [
            'plainExplanation' => sprintf(
                '%s est mesure a %s%s. Cette interpretation reste informative et ne remplace pas un avis medical.',
                $label,
                trim($value) !== '' ? trim($value) : 'valeur non fournie',
                $unitPart
            ),
            'possibleMeaning' => $possibleMeaning,
            'nextSteps' => $nextSteps,
            'warning' => 'En cas de symptomes importants, consulter rapidement un professionnel de sante.',
        ];
    }

    private function buildStatusLine(string $value, string $referenceRange): ?string
    {
        $resultValue = $this->extractNumber($value);
        if ($resultValue === null) {
            return null;
        }

        [$min, $max] = $this->extractRangeBounds($referenceRange);
        if ($min === null && $max === null) {
            return null;
        }

        if ($min !== null && $resultValue < $min) {
            return sprintf('La valeur est en dessous de la reference (%.2f < %.2f).', $resultValue, $min);
        }
        if ($max !== null && $resultValue > $max) {
            return sprintf('La valeur est au-dessus de la reference (%.2f > %.2f).', $resultValue, $max);
        }

        if ($min !== null && $max !== null) {
            return sprintf('La valeur semble dans l intervalle de reference (%.2f entre %.2f et %.2f).', $resultValue, $min, $max);
        }

        return null;
    }

    private function extractNumber(string $text): ?float
    {
        if (!preg_match('/[-+]?\d+(?:[\.,]\d+)?/', $text, $m)) {
            return null;
        }

        $normalized = str_replace(',', '.', $m[0]);
        if (!is_numeric($normalized)) {
            return null;
        }

        return (float) $normalized;
    }

    /**
     * @return array{0:?float,1:?float}
     */
    private function extractRangeBounds(string $referenceRange): array
    {
        if (trim($referenceRange) === '') {
            return [null, null];
        }

        preg_match_all('/[-+]?\d+(?:[\.,]\d+)?/', $referenceRange, $m);
        $numbers = [];
        foreach ($m[0] ?? [] as $raw) {
            $normalized = str_replace(',', '.', (string) $raw);
            if (is_numeric($normalized)) {
                $numbers[] = (float) $normalized;
            }
        }

        if ($numbers === []) {
            return [null, null];
        }

        if (count($numbers) === 1) {
            $one = $numbers[0];
            if (preg_match('/<|inferieur|moins/i', $referenceRange)) {
                return [null, $one];
            }
            if (preg_match('/>|superieur|plus/i', $referenceRange)) {
                return [$one, null];
            }
            return [null, null];
        }

        $min = min($numbers[0], $numbers[1]);
        $max = max($numbers[0], $numbers[1]);

        return [$min, $max];
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
}

