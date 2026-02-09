<?php

namespace App\EventSubscriber;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Security;

class SubscriptionGateSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private Security $security,
        private UrlGeneratorInterface $urlGenerator,
        private EntityManagerInterface $em
    ) {}

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', 15],
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

        // Admins restent libres de l'abonnement
        if (in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            return;
        }

        $route = $event->getRequest()->attributes->get('_route');
        if ($route === null) {
            return;
        }

        $allowedRoutes = [
            'app_home',
            'app_subscription',
            'app_subscription_overview',
            'app_subscription_checkout',
            'app_subscription_payment',
            'app_subscription_subscribe',
            'app_subscription_skip',
            'login',
            'app_logout',
            'app_register',
            'app_banned',
        ];

        if (in_array($route, $allowedRoutes, true)) {
            return;
        }

        if ($user->isSubscriptionActive()) {
            return;
        }

        // Si l'abonnement actif a expiré, marquer EXPIRED
        if ($user->getSubscriptionStatus() === 'ACTIVE' && $user->isSubscriptionExpired()) {
            $user->setSubscriptionStatus('EXPIRED');
            $user->setSubscriptionEndAt(null);
            $user->setUpdatedAt(new \DateTimeImmutable());
            $this->em->flush();
        }

        // Mode découverte: autoriser uniquement le front, pas l'admin ni le contenu premium
        if ($user->getSubscriptionStatus() === 'SKIPPED') {
            $restrictedInDiscovery = [
                'front_content_',
                'api_content_',
                'front_pharmacy_',
                'api_pharmacies_',
                'front_pharmacien_',
                'api_pharmacien_',
                'admin_',
            ];
            foreach ($restrictedInDiscovery as $prefix) {
                if (str_starts_with($route, $prefix)) {
                    $event->setResponse(new RedirectResponse($this->urlGenerator->generate('app_subscription')));
                    return;
                }
            }
            return;
        }

        // PENDING / EXPIRED => forcer la page d'abonnement
        $event->setResponse(new RedirectResponse($this->urlGenerator->generate('app_subscription')));
    }
}
