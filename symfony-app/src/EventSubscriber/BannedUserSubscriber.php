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

class BannedUserSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private Security $security,
        private UrlGeneratorInterface $urlGenerator,
        private EntityManagerInterface $em
    ) {}

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', 20],
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

        // Auto-déban si la période est expirée
        if ($user->unbanIfExpired()) {
            $this->em->flush();
            return;
        }

        if (!$user->isBannedEffective()) {
            return;
        }

        $request = $event->getRequest();
        $route = $request->attributes->get('_route');

        // Liste stricte des routes autorisées pour les utilisateurs bannis
        $excludedRoutes = [
            'app_banned',              // Page d'information du bannissement
            'app_logout',              // Déconnexion
            'login',                   // Page de connexion (pour pouvoir se reconnecter plus tard)
            'app_login',               // Alternative route de connexion
            '_wdt',                    // Web Debug Toolbar (dev only)
            '_profiler',               // Profiler (dev only)
            '_profiler_search',        // Profiler search (dev only)
            '_profiler_search_bar',    // Profiler search bar (dev only)
            '_profiler_search_results',// Profiler search results (dev only)
            '_profiler_router',        // Profiler router (dev only)
            '_profiler_exception',     // Profiler exception (dev only)
            '_profiler_exception_css', // Profiler exception CSS (dev only)
        ];

        // Bloquer toutes les autres routes
        if ($route === null || !in_array($route, $excludedRoutes, true)) {
            // Log l'accès bloqué pour traçabilité (optionnel)
            // $this->logger->info('Blocked access for banned user', ['user' => $user->getEmail(), 'route' => $route]);
            
            $event->setResponse(new RedirectResponse(
                $this->urlGenerator->generate('app_banned')
            ));
        }
    }
}
