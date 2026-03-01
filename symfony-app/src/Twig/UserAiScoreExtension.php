<?php

namespace App\Twig;

use App\Entity\User;
use App\Service\UserAiScoreService;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

class UserAiScoreExtension extends AbstractExtension
{
    public function __construct(private UserAiScoreService $userAiScoreService)
    {
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('user_ai_score', [$this, 'getScore']),
        ];
    }

    public function getScore(mixed $user): int
    {
        if (!$user instanceof User) {
            return 0;
        }

        return $this->userAiScoreService->calculateScore($user);
    }
}
