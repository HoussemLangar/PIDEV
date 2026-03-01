<?php

namespace App\Service\Ai;

class WorkoutPlannerService
{
    public function __construct(
        private readonly AiGatewayService $aiGateway,
    ) {
    }

    public function generate(string $goal, string $level, int $daysPerWeek, int $minutes, string $constraints): array
    {
        $daysPerWeek = max(1, min(7, $daysPerWeek));
        $minutes = max(10, min(120, $minutes));

        $fallback = $this->buildFallback($goal, $level, $daysPerWeek, $minutes, $constraints);

        $ai = $this->aiGateway->askForJson(
            'Tu es coach sportif. Reponds uniquement en JSON: '
            . '{"overview":string,"sessions":[{"day":string,"focus":string,"durationMin":int,"plan":string[]}],"safety":string[]}.',
            sprintf(
                "Objectif: %s\nNiveau: %s\nJours/semaine: %d\nDuree/session: %d min\nContraintes: %s",
                $goal,
                $level,
                $daysPerWeek,
                $minutes,
                $constraints !== '' ? $constraints : 'aucune'
            )
        );

        if (!is_array($ai)) {
            return $fallback;
        }

        return [
            'overview' => (string)($ai['overview'] ?? $fallback['overview']),
            'sessions' => $this->normalizeSessions($ai['sessions'] ?? $fallback['sessions']),
            'safety' => $this->normalizeStringList($ai['safety'] ?? $fallback['safety']),
        ];
    }

    private function buildFallback(string $goal, string $level, int $daysPerWeek, int $minutes, string $constraints): array
    {
        $weekDays = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];
        $sessions = [];

        for ($i = 0; $i < $daysPerWeek; $i++) {
            $focus = $i % 2 === 0 ? 'Cardio + mobilité' : 'Renforcement global';
            $sessions[] = [
                'day' => $weekDays[$i],
                'focus' => $focus,
                'durationMin' => $minutes,
                'plan' => [
                    'Échauffement 8 min',
                    $focus === 'Cardio + mobilité' ? 'Cardio modéré 20 min' : 'Circuit force 20 min',
                    'Retour au calme 5 min',
                ],
            ];
        }

        return [
            'overview' => sprintf(
                'Programme %s (%s), %d sessions/semaine.',
                $goal !== '' ? $goal : 'remise en forme',
                $level !== '' ? $level : 'intermediaire',
                $daysPerWeek
            ),
            'sessions' => $sessions,
            'safety' => array_filter([
                'Respecter la technique avant l intensite.',
                'Arreter en cas de douleur aigue.',
                $constraints !== '' ? 'Adapter selon contraintes: ' . $constraints : null,
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

    private function normalizeSessions(mixed $items): array
    {
        if (!is_array($items)) {
            return [];
        }
        $out = [];
        foreach ($items as $item) {
            if (!is_array($item)) {
                continue;
            }
            $day = trim((string)($item['day'] ?? ''));
            if ($day === '') {
                continue;
            }

            $planSteps = [];
            if (isset($item['plan']) && is_array($item['plan'])) {
                foreach ($item['plan'] as $step) {
                    if (is_string($step) && trim($step) !== '') {
                        $planSteps[] = trim($step);
                    }
                }
            }

            $out[] = [
                'day' => $day,
                'focus' => trim((string)($item['focus'] ?? '')),
                'durationMin' => (int)($item['durationMin'] ?? 30),
                'plan' => $planSteps,
            ];
        }
        return $out;
    }
}

