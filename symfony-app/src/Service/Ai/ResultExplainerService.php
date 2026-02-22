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
            . '{"plainExplanation":string,"possibleMeaning":string[],"nextSteps":string[],"warning":string}.',
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

        return [
            'plainExplanation' => (string)($ai['plainExplanation'] ?? $fallback['plainExplanation']),
            'possibleMeaning' => $this->normalizeStringList($ai['possibleMeaning'] ?? $fallback['possibleMeaning']),
            'nextSteps' => $this->normalizeStringList($ai['nextSteps'] ?? $fallback['nextSteps']),
            'warning' => (string)($ai['warning'] ?? $fallback['warning']),
        ];
    }

    private function buildFallback(string $testName, string $value, string $unit, string $referenceRange): array
    {
        $label = trim($testName) !== '' ? $testName : 'ce resultat';
        $unitPart = trim($unit) !== '' ? ' ' . trim($unit) : '';

        return [
            'plainExplanation' => sprintf(
                '%s est mesure a %s%s. Cette interpretation reste informative et ne remplace pas un avis medical.',
                $label,
                trim($value) !== '' ? trim($value) : 'valeur non fournie',
                $unitPart
            ),
            'possibleMeaning' => array_filter([
                $referenceRange !== '' ? 'Comparer a l intervalle de reference: ' . $referenceRange : null,
                'Une valeur isolee doit etre interpretee avec le contexte clinique.',
            ]),
            'nextSteps' => [
                'Montrer ce resultat a votre medecin traitant.',
                'Refaire le test si recommande par un professionnel.',
            ],
            'warning' => 'En cas de symptomes importants, consulter rapidement un professionnel de sante.',
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
}

