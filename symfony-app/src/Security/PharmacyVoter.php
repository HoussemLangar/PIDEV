<?php

namespace App\Security;

use App\Entity\User;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Authorization\Voter\Voter;

class PharmacyVoter extends Voter
{
    public const VIEW = 'PHARMACY_VIEW';
    public const RESERVE = 'PHARMACY_RESERVE';
    public const MANAGE = 'PHARMACY_MANAGE';
    public const STOCK = 'PHARMACY_STOCK';
    public const ORDERS = 'PHARMACY_ORDERS';

    protected function supports(string $attribute, mixed $subject): bool
    {
        return in_array($attribute, [self::VIEW, self::RESERVE, self::MANAGE, self::STOCK, self::ORDERS], true);
    }

    protected function voteOnAttribute(string $attribute, mixed $subject, TokenInterface $token): bool
    {
        $user = $token->getUser();
        if (!$user instanceof User) {
            return false;
        }

        if (in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            return true;
        }

        if ($user->isBannedEffective()) {
            return false;
        }

        $effectiveRole = $user->getSubscriptionType() ?? $user->getRole();

        return match ($attribute) {
            self::VIEW => $user->isSubscriptionActive(),
            self::RESERVE => $user->isSubscriptionActive() && in_array($effectiveRole, ['ROLE_PATIENT', 'ROLE_MEDECIN'], true),
            self::MANAGE, self::STOCK, self::ORDERS => $user->isSubscriptionActive() && $effectiveRole === 'ROLE_PHARMACIEN',
            default => false,
        };
    }
}
