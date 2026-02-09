<?php
// src/EventSubscriber/FaceVerificationSubscriber.php

namespace App\EventSubscriber;

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
        $session = $request->getSession();
        
        // Vérifier si l'utilisateur est banni (géré par BannedUserSubscriber avec priorité plus haute)
        $user = $this->security->getUser();
        if ($user && method_exists($user, 'isBannedEffective') && $user->isBannedEffective()) {
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
        if (!$user || !in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            return;
        }

        // Vérifier si la reconnaissance faciale a été validée
        $faceVerified = $session->get('face_verified', false);

        if (!$faceVerified) {
            // Rediriger vers la page de vérification faciale
            $response = new RedirectResponse(
                $this->urlGenerator->generate('admin_face_verification')
            );
            $event->setResponse($response);
        }
    }
}
