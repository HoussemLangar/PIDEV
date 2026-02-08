<?php

namespace App\Security;

use App\Entity\User;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Authorization\Voter\Voter;

class ContentVoter extends Voter
{
    public const VIEW = 'CONTENT_VIEW';
    public const INTERACT = 'CONTENT_INTERACT';
    public const CREATE = 'CONTENT_CREATE';
    public const MODERATE = 'CONTENT_MODERATE';

    protected function supports(string $attribute, mixed $subject): bool
    {
        return in_array($attribute, [self::VIEW, self::INTERACT, self::CREATE, self::MODERATE], true);
    }

    protected function voteOnAttribute(string $attribute, mixed $subject, TokenInterface $token): bool
    {
        $user = $token->getUser();
        if (!$user instanceof User) {
            return false;
        }

        $roles = $user->getRoles();
        if (in_array('ROLE_ADMIN', $roles, true)) {
            return true;
        }

        if ($user->isBannedEffective()) {
            return false;
        }

        return match ($attribute) {
            self::VIEW, self::INTERACT => $user->isSubscriptionActive(),
            self::CREATE => $user->isSubscriptionActive()
                && in_array($user->getRole(), ['ROLE_MEDECIN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE'], true),
            self::MODERATE => false,
            default => false,
        };
    }
}
