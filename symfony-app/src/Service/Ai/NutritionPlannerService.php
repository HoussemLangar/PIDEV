<?php

namespace App\Service\Ai;

class NutritionPlannerService
{
    public function __construct(
        private readonly AiGatewayService $aiGateway,
    ) {
    }

    public function generate(string $goal, string $dietStyle, string $allergies, int $days): array
    {
        $days = max(1, min(14, $days));
        $fallback = $this->buildFallback($goal, $dietStyle, $allergies, $days);

        $ai = $this->aiGateway->askForJson(
            'Tu es nutritionniste. Reponds uniquement en JSON: '
            . '{"overview":string,"days":[{"day":int,"breakfast":string,"lunch":string,"dinner":string,"snack":string}],"tips":string[]}.',
            sprintf(
                "Objectif: %s\nStyle alimentaire: %s\nAllergies/intolerances: %s\nNombre de jours: %d",
                $goal,
                $dietStyle,
                $allergies !== '' ? $allergies : 'aucune',
                $days
            )
        );

        if (!is_array($ai)) {
            return $fallback;
        }

        return [
            'overview' => (string)($ai['overview'] ?? $fallback['overview']),
            'days' => $this->normalizeDays($ai['days'] ?? $fallback['days']),
            'tips' => $this->normalizeStringList($ai['tips'] ?? $fallback['tips']),
        ];
    }

    private function buildFallback(string $goal, string $dietStyle, string $allergies, int $days): array
    {
        $baseBreakfast = $dietStyle === 'vegetarien' ? 'Porridge avoine + fruits + noix' : 'Omelette légumes + pain complet';
        $baseLunch = $dietStyle === 'vegetarien' ? 'Bowl quinoa pois chiches légumes' : 'Poulet grillé + quinoa + salade';
        $baseDinner = $dietStyle === 'vegetarien' ? 'Soupe lentilles + légumes vapeur' : 'Poisson au four + légumes + riz complet';

        $plan = [];
        for ($i = 1; $i <= $days; $i++) {
            $plan[] = [
                'day' => $i,
                'breakfast' => $baseBreakfast,
                'lunch' => $baseLunch,
                'dinner' => $baseDinner,
                'snack' => 'Yaourt nature / fruit de saison',
            ];
        }

        return [
            'overview' => sprintf(
                'Plan nutrition %d jours orienté "%s" (%s).',
                $days,
                $goal !== '' ? $goal : 'equilibre',
                $dietStyle !== '' ? $dietStyle : 'standard'
            ),
            'days' => $plan,
            'tips' => array_filter([
                'Hydratation: 1.5 a 2L d eau par jour.',
                'Prioriser les aliments peu transformes.',
                $allergies !== '' ? 'Eviter: ' . $allergies : null,
            ]),
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

    private function normalizeDays(mixed $items): array
    {
        if (!is_array($items)) {
            return [];
        }

        $out = [];
        foreach ($items as $item) {
            if (!is_array($item)) {
                continue;
            }
            $day = (int)($item['day'] ?? 0);
            if ($day <= 0) {
                continue;
            }
            $out[] = [
                'day' => $day,
                'breakfast' => (string)($item['breakfast'] ?? ''),
                'lunch' => (string)($item['lunch'] ?? ''),
                'dinner' => (string)($item['dinner'] ?? ''),
                'snack' => (string)($item['snack'] ?? ''),
            ];
        }
        return $out;
    }
}

