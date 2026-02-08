<?php
// src/Security/LoginSuccessHandler.php

namespace App\Security;

use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\RouterInterface;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Http\Authentication\AuthenticationSuccessHandlerInterface;

class LoginSuccessHandler implements AuthenticationSuccessHandlerInterface
{
    public function __construct(private RouterInterface $router)
    {
    }

    public function onAuthenticationSuccess(Request $request, TokenInterface $token): RedirectResponse
    {
        // ✅ Récupérer l'utilisateur depuis le token
        $user = $token->getUser();

        if (method_exists($user, 'isBannedEffective') && $user->isBannedEffective()) {
            return new RedirectResponse($this->router->generate('app_banned'));
        }
        
        // Vérifier si l'utilisateur a le role ADMIN
        if (in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            // Forcer la vérification faciale avant tout accès admin
            return new RedirectResponse($this->router->generate('admin_face_verification'));
        }

        // Redirection par défaut pour les utilisateurs normaux
        return new RedirectResponse($this->router->generate('app_home'));
    }
}
