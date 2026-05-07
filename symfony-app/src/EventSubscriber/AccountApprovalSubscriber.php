<?php

namespace App\EventSubscriber;

use App\Entity\User;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Security;

class AccountApprovalSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private Security $security,
        private UrlGeneratorInterface $urlGenerator
    ) {}

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', 18],
        ];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $user = $this->security->getUser();
        if (!$user instanceof User) {
            return;
        }

        // Les utilisateurs bannis sont gérés par BannedUserSubscriber (priorité plus haute)
        if (method_exists($user, 'isBannedEffective') && $user->isBannedEffective()) {
            return;
        }

        if ($user->isEmailVerified() && $user->isAdminApproved()) {
            return;
        }

        $route = $event->getRequest()->attributes->get('_route');
        if ($route === null) {
            return;
        }

        $allowedRoutes = [
            'app_pending_approval',
            'app_verify_email',
            'login',
            'app_logout',
            'app_register',
            'app_banned',
        ];

        if (in_array($route, $allowedRoutes, true)) {
            return;
        }

        $event->setResponse(new RedirectResponse($this->urlGenerator->generate('app_pending_approval')));
    }
}
