<?php

namespace App\Service\Accompaniment;

use App\Service\Ai\NutritionPlannerService;
use App\Service\Ai\WorkoutPlannerService;

class AccompanimentAiAdvisorService
{
    public function __construct(
        private readonly NutritionPlannerService $nutritionPlanner,
        private readonly WorkoutPlannerService $workoutPlanner,
    ) {
    }

    public function buildSuggestions(
        string $goal,
        string $dietStyle,
        string $allergies,
        int $nutritionDays,
        string $level,
        int $daysPerWeek,
        int $minutes,
        string $constraints,
    ): array {
        $goal = trim($goal);
        $constraints = trim($constraints);
        $nutritionDays = max(1, min(14, $nutritionDays));
        $daysPerWeek = max(1, min(7, $daysPerWeek));
        $minutes = max(10, min(120, $minutes));

        $nutrition = $this->nutritionPlanner->generate(
            $goal,
            $dietStyle,
            trim($allergies),
            $nutritionDays
        );

        $workout = $this->workoutPlanner->generate(
            $goal,
            trim($level),
            $daysPerWeek,
            $minutes,
            $constraints
        );

        return [
            'summary' => $this->buildSummary($nutrition, $workout),
            'nutrition' => $nutrition,
            'workout' => $workout,
            'suggestedTitle' => $this->buildSuggestedTitle($goal, $workout),
            'suggestedObjectives' => $this->buildSuggestedObjectives($goal, $nutrition, $workout),
            'suggestedDescription' => $this->buildSuggestedDescription($constraints, $nutrition, $workout),
        ];
    }

    private function buildSuggestedTitle(string $goal, array $workout): string
    {
        $goal = trim($goal);
        $title = $goal !== '' ? 'Plan ' . mb_substr($goal, 0, 60) : 'Plan d\'accompagnement personnalisé';

        $sessions = $workout['sessions'] ?? [];
        if (is_array($sessions) && $sessions !== []) {
            $firstSession = $sessions[0];
            $duration = (int) ($firstSession['durationMin'] ?? 0);
            if ($duration > 0) {
                $title .= sprintf(' (%d min/séance)', $duration);
            }
        }

        return $title;
    }

    private function buildSummary(array $nutrition, array $workout): string
    {
        $nutritionOverview = trim((string) ($nutrition['overview'] ?? ''));
        $workoutOverview = trim((string) ($workout['overview'] ?? ''));

        if ($nutritionOverview !== '' && $workoutOverview !== '') {
            return $nutritionOverview . ' ' . $workoutOverview;
        }

        return $nutritionOverview !== '' ? $nutritionOverview : $workoutOverview;
    }

    private function buildSuggestedObjectives(string $goal, array $nutrition, array $workout): string
    {
        $lines = [];

        if ($goal !== '') {
            $lines[] = 'Objectif principal: ' . $goal . '.';
        }

        $sessions = $workout['sessions'] ?? [];
        if (is_array($sessions) && $sessions !== []) {
            $firstSession = $sessions[0];
            $focus = trim((string) ($firstSession['focus'] ?? ''));
            $duration = (int) ($firstSession['durationMin'] ?? 0);
            if ($focus !== '' && $duration > 0) {
                $lines[] = sprintf('Activité physique régulière: %s (%d min par séance).', $focus, $duration);
            }
        }

        $tips = $nutrition['tips'] ?? [];
        if (is_array($tips) && isset($tips[0]) && is_string($tips[0]) && trim($tips[0]) !== '') {
            $lines[] = 'Objectif nutritionnel: ' . trim($tips[0]);
        }

        return implode("\n", array_filter($lines));
    }

    private function buildSuggestedDescription(string $constraints, array $nutrition, array $workout): string
    {
        $parts = [];

        $nutritionOverview = trim((string) ($nutrition['overview'] ?? ''));
        if ($nutritionOverview !== '') {
            $parts[] = 'Nutrition: ' . $nutritionOverview;
        }

        $workoutOverview = trim((string) ($workout['overview'] ?? ''));
        if ($workoutOverview !== '') {
            $parts[] = 'Exercice: ' . $workoutOverview;
        }

        if ($constraints !== '') {
            $parts[] = 'Contraintes patient: ' . $constraints;
        }

        $safety = $workout['safety'] ?? [];
        if (is_array($safety) && isset($safety[0]) && is_string($safety[0]) && trim($safety[0]) !== '') {
            $parts[] = 'Sécurité: ' . trim($safety[0]);
        }

        return implode("\n", $parts);
    }
}
