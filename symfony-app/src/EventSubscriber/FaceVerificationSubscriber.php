<?php
// src/EventSubscriber/FaceVerificationSubscriber.php

namespace App\EventSubscriber;

use App\Entity\User;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Security;

class FaceVerificationSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private UrlGeneratorInterface $urlGenerator,
        private Security $security
    ) {}

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', 10],
        ];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();

        // Vérifier si l'utilisateur est banni (géré par BannedUserSubscriber avec priorité plus haute)
        $user = $this->security->getUser();
        if ($user instanceof User && $user->isBannedEffective()) {
            return;
        }
        
        // Routes à exclure de la vérification
        $excludedRoutes = [
            'admin_face_verification',
            'admin_register_face',
            'admin_verify_face',
            'app_logout',
            'login',
            'app_banned',
        ];

        $currentRoute = $request->attributes->get('_route');

        // Ne vérifier que les routes admin (préfixe admin_)
        if ($currentRoute === null || !str_starts_with($currentRoute, 'admin_')) {
            return;
        }

        if (in_array($currentRoute, $excludedRoutes, true)) {
            return;
        }

        // Vérifier si l'utilisateur est admin
        $user = $this->security->getUser();
        if (!$user instanceof User || !in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            return;
        }

        // Face verification is now optional for admins - no forced redirect
        // Users can voluntarily verify their face through the admin face verification page
        // if they choose to do so. The face_verified session flag is still set when they
        // complete verification, but is no longer required to access admin pages.
    }
}
