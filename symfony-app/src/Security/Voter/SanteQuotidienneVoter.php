<?php

namespace App\Security\Voter;

use App\Entity\SanteQuotidienne;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Authorization\Voter\Voter;
use Symfony\Component\Security\Core\User\UserInterface;

class SanteQuotidienneVoter extends Voter
{
    public const EDIT = 'SANTE_EDIT';
    public const VIEW = 'SANTE_VIEW';
    public const DELETE = 'SANTE_DELETE';

    public function __construct(private Security $security)
    {
    }

    protected function supports(string $attribute, mixed $subject): bool
    {
        // if the attribute is not one we support, return false
        if (!in_array($attribute, [self::EDIT, self::VIEW, self::DELETE])) {
            return false;
        }

        // only vote on `SanteQuotidienne` objects
        if (!$subject instanceof SanteQuotidienne) {
            return false;
        }

        return true;
    }

    protected function voteOnAttribute(string $attribute, mixed $subject, TokenInterface $token): bool
    {
        $user = $token->getUser();

        // if the user is anonymous, do not grant access
        if (!$user instanceof UserInterface) {
            return false;
        }

        // ... (check conditions and return true to grant permission)
        switch ($attribute) {
            case self::EDIT:
                return $this->canEdit($subject, $user);
            case self::VIEW:
                return $this->canView($subject, $user);
            case self::DELETE:
                return $this->canDelete($subject, $user);
        }

        return false;
    }

    private function canEdit(SanteQuotidienne $sante, UserInterface $user): bool
    {
        // L'utilisateur peut éditer sa propre entrée
        return $sante->getUser() === $user;
    }

    private function canView(SanteQuotidienne $sante, UserInterface $user): bool
    {
        // L'utilisateur peut voir sa propre entrée
        return $sante->getUser() === $user;
    }

    private function canDelete(SanteQuotidienne $sante, UserInterface $user): bool
    {
        // L'utilisateur peut supprimer sa propre entrée
        return $sante->getUser() === $user;
    }
}
