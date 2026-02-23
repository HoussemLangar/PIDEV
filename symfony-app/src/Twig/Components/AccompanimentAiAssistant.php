<?php

namespace App\Twig\Components;

use App\Service\Accompaniment\AccompanimentAiAdvisorService;
use Symfony\UX\LiveComponent\Attribute\AsLiveComponent;
use Symfony\UX\LiveComponent\Attribute\LiveAction;
use Symfony\UX\LiveComponent\Attribute\LiveProp;
use Symfony\UX\LiveComponent\ComponentToolsTrait;
use Symfony\UX\LiveComponent\DefaultActionTrait;

#[AsLiveComponent('AccompanimentAiAssistant')]
class AccompanimentAiAssistant
{
    use DefaultActionTrait;
    use ComponentToolsTrait;

    #[LiveProp(writable: true)]
    public string $goal = '';

    #[LiveProp(writable: true)]
    public string $dietStyle = 'standard';

    #[LiveProp(writable: true)]
    public string $allergies = '';

    #[LiveProp(writable: true)]
    public int $nutritionDays = 7;

    #[LiveProp(writable: true)]
    public string $level = 'intermediaire';

    #[LiveProp(writable: true)]
    public int $daysPerWeek = 3;

    #[LiveProp(writable: true)]
    public int $minutes = 30;

    #[LiveProp(writable: true)]
    public string $constraints = '';

    public array $result = [];

    public function __construct(
        private readonly AccompanimentAiAdvisorService $advisor,
    ) {
    }

    #[LiveAction]
    public function generate(): void
    {
        if (trim($this->goal) === '') {
            $this->result = [
                'summary' => 'Veuillez saisir un objectif global avant de générer.',
                'nutrition' => ['overview' => '', 'tips' => [], 'days' => []],
                'workout'   => ['overview' => '', 'safety' => [], 'sessions' => []],
                'suggestedTitle' => '',
                'suggestedObjectives' => '',
                'suggestedDescription' => '',
                '_error' => true,
            ];
            return;
        }

        try {
            $generated = $this->advisor->buildSuggestions(
                trim($this->goal),
                trim($this->dietStyle),
                trim($this->allergies),
                $this->nutritionDays,
                trim($this->level),
                $this->daysPerWeek,
                $this->minutes,
                trim($this->constraints)
            );

            $this->result = \is_array($generated) ? $generated : [
                'summary' => 'Erreur lors de la génération. Veuillez réessayer.',
                'nutrition' => ['overview' => '', 'tips' => [], 'days' => []],
                'workout'   => ['overview' => '', 'safety' => [], 'sessions' => []],
                'suggestedTitle' => '',
                'suggestedObjectives' => '',
                'suggestedDescription' => '',
                '_error' => true,
            ];
        } catch (\Throwable $e) {
            $this->result = [
                'summary' => 'Erreur lors de la génération. Veuillez réessayer.',
                'nutrition' => ['overview' => '', 'tips' => [], 'days' => []],
                'workout'   => ['overview' => '', 'safety' => [], 'sessions' => []],
                'suggestedTitle' => '',
                'suggestedObjectives' => '',
                'suggestedDescription' => '',
                '_error' => true,
            ];
        }
    }

    public function hasResult(): bool
    {
        return $this->result !== [];
    }
}
