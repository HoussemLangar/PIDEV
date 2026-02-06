<?php
// src/EventSubscriber/LoginSuccessListener.php

namespace App\EventSubscriber;

use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Http\Event\LoginSuccessEvent;

#[AsEventListener(event: LoginSuccessEvent::class)]
class LoginSuccessListener
{
    public function __construct(
        private UrlGeneratorInterface $urlGenerator
    ) {}

    public function __invoke(LoginSuccessEvent $event): void
    {
        $user = $event->getUser();

        if (method_exists($user, 'isBannedEffective') && $user->isBannedEffective()) {
            $response = new RedirectResponse(
                $this->urlGenerator->generate('app_banned')
            );
            $event->setResponse($response);
            return;
        }
        
        // Vérifier si l'utilisateur a le rôle ADMIN
        if (in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            // Rediriger vers la page de vérification faciale
            $response = new RedirectResponse(
                $this->urlGenerator->generate('admin_face_verification')
            );
            $event->setResponse($response);
        }
    }
}
