<?php

namespace App\Service;

use App\Entity\User;
use App\Repository\UserSessionRepository;

class UserAiScoreService
{
    private const PREMIUM_THRESHOLD = 70;

    public function __construct(private UserSessionRepository $userSessionRepository)
    {
    }

    public function calculateScore(User $user): int
    {
        $breakdown = $this->getBreakdown($user);

        return max(0, min(100,
            $breakdown['activity']
            + $breakdown['seniority']
            + $breakdown['ruleCompliance']
            + $breakdown['sanctionsHistory']
        ));
    }

    public function getBreakdown(User $user): array
    {
        return [
            'activity' => $this->computeActivityScore($user),
            'seniority' => $this->computeSeniorityScore($user),
            'ruleCompliance' => $this->computeRuleComplianceScore($user),
            'sanctionsHistory' => $this->computeSanctionsHistoryScore($user),
        ];
    }

    public function getSupportPriority(User $user): string
    {
        $score = $this->calculateScore($user);

        if ($score >= 85) {
            return 'high';
        }

        if ($score >= 55) {
            return 'normal';
        }

        return 'low';
    }

    public function getSupportPriorityLabel(User $user): string
    {
        return match ($this->getSupportPriority($user)) {
            'high' => 'Haute',
            'normal' => 'Normale',
            default => 'Standard',
        };
    }

    public function isPremiumEligible(User $user): bool
    {
        return $this->calculateScore($user) >= self::PREMIUM_THRESHOLD;
    }

    public function getBenefitsMessage(User $user): string
    {
        $score = $this->calculateScore($user);

        if ($score >= 85) {
            return 'Excellent profil : priorité support haute et accès premium IA activé.';
        }

        if ($score >= self::PREMIUM_THRESHOLD) {
            return 'Très bon profil : accès premium IA activé et traitement support prioritaire.';
        }

        if ($score >= 55) {
            return 'Bon profil : priorité support normale. Continuez votre activité pour débloquer les offres premium.';
        }

        return 'Profil en progression : augmentez votre activité et le respect des règles pour obtenir des offres premium.';
    }

    public function getProfileSummary(User $user): array
    {
        return [
            'score' => $this->calculateScore($user),
            'breakdown' => $this->getBreakdown($user),
            'supportPriority' => $this->getSupportPriority($user),
            'supportPriorityLabel' => $this->getSupportPriorityLabel($user),
            'premiumEligible' => $this->isPremiumEligible($user),
            'benefitsMessage' => $this->getBenefitsMessage($user),
            'premiumThreshold' => self::PREMIUM_THRESHOLD,
        ];
    }

    private function computeActivityScore(User $user): int
    {
        $score = 0;

        $updatedAt = $user->getUpdatedAt();
        if ($updatedAt instanceof \DateTimeInterface) {
            $lastUpdateDays = (new \DateTimeImmutable())->diff(
                \DateTimeImmutable::createFromInterface($updatedAt)
            )->days;

            if ($lastUpdateDays <= 3) {
                $score += 20;
            } elseif ($lastUpdateDays <= 14) {
                $score += 14;
            } elseif ($lastUpdateDays <= 30) {
                $score += 8;
            } else {
                $score += 4;
            }
        }

        $recentSessions = (int) $this->userSessionRepository->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->andWhere('s.user = :user')
            ->andWhere('s.lastActivityAt >= :since')
            ->setParameter('user', $user)
            ->setParameter('since', new \DateTimeImmutable('-30 days'))
            ->getQuery()
            ->getSingleScalarResult();

        if ($recentSessions >= 20) {
            $score += 15;
        } elseif ($recentSessions >= 8) {
            $score += 10;
        } elseif ($recentSessions >= 3) {
            $score += 6;
        } elseif ($recentSessions >= 1) {
            $score += 3;
        }

        return min(35, $score);
    }

    private function computeSeniorityScore(User $user): int
    {
        $createdAt = $user->getCreatedAt();
        if (!$createdAt instanceof \DateTimeInterface) {
            return 0;
        }

        $months = (new \DateTimeImmutable())
            ->diff(\DateTimeImmutable::createFromInterface($createdAt))->m
            + ((new \DateTimeImmutable())
            ->diff(\DateTimeImmutable::createFromInterface($createdAt))->y * 12);

        if ($months >= 24) {
            return 25;
        }
        if ($months >= 12) {
            return 20;
        }
        if ($months >= 6) {
            return 14;
        }
        if ($months >= 3) {
            return 9;
        }

        return 5;
    }

    private function computeRuleComplianceScore(User $user): int
    {
        $score = 0;

        if ($user->isEmailVerified()) {
            $score += 10;
        }
        if ($user->isMfaEnabled()) {
            $score += 8;
        }
        if ($user->isReminderEnabled()) {
            $score += 4;
        }
        if (!$user->isDeleted()) {
            $score += 3;
        }

        return min(25, $score);
    }

    private function computeSanctionsHistoryScore(User $user): int
    {
        if ($user->isBannedEffective()) {
            return 0;
        }

        $score = 15;

        if ($user->isBanned()) {
            $score -= 10;
        }

        if ($user->getBanReason() !== null && trim($user->getBanReason()) !== '') {
            $score -= 3;
        }

        if ($user->getBanUntil() !== null) {
            $score -= 2;
        }

        return max(0, $score);
    }
}
