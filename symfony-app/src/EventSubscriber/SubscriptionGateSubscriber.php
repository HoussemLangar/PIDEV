<?php

namespace App\EventSubscriber;

use App\Entity\User;
use App\Repository\AbonnementRepository;
use App\Service\UserAiScoreService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Session\Session;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\HttpFoundation\RequestStack;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Security;

class SubscriptionGateSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private Security $security,
        private UrlGeneratorInterface $urlGenerator,
        private EntityManagerInterface $em,
        private RequestStack $requestStack,
        private AbonnementRepository $abonnementRepository,
        private UserAiScoreService $userAiScoreService
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

        $session = $this->requestStack->getSession();
        $mustFlush = false;

        if ($this->userAiScoreService->grantFreeMonthIfEligible($user)) {
            $mustFlush = true;
            if ($session instanceof Session) {
                $session->getFlashBag()->add('success', 'Félicitations ! Vous avez atteint un score IA de 80+ et gagné 1 mois d\'utilisation gratuit.');
            }
        }

        if ($this->userAiScoreService->recordDailyHistory($user)) {
            $mustFlush = true;
        }

        if ($user->getSubscriptionStatus() === 'ACTIVE' && $this->isExpiredByData($user)) {
            $this->revokeExpiredSubscriptionAccess($user);
            $mustFlush = true;

            if ($session instanceof Session && !$session->get('subscription_expired_notice_shown', false)) {
                $session->getFlashBag()->add('warning', 'Votre abonnement est terminé. Vos droits d\'accès ont été retirés.');
                $session->set('subscription_expired_notice_shown', true);
            }
        }

        if ($user->getSubscriptionStatus() !== 'ACTIVE' && $this->normalizeInactiveUserAccess($user)) {
            $mustFlush = true;
        }

        if ($mustFlush) {
            $this->em->flush();
        }

        $allowedRoutes = [
            'app_home',
            'app_subscription',
            'app_subscription_overview',
            'app_subscription_checkout',
            'app_subscription_payment',
            'app_subscription_payment_success',
            'app_subscription_payment_cancel',
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
            if ($session) {
                $session->remove('subscription_expired_notice_shown');
            }
            return;
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
        if ($user->getSubscriptionStatus() === 'EXPIRED') {
            if ($session instanceof Session && !$session->get('subscription_expired_notice_shown', false)) {
                $session->getFlashBag()->add('warning', 'Votre abonnement est terminé. Merci de renouveler pour récupérer l\'accès.');
                $session->set('subscription_expired_notice_shown', true);
            }
        }

        $event->setResponse(new RedirectResponse($this->urlGenerator->generate('app_subscription')));
    }

    private function revokeExpiredSubscriptionAccess(User $user): void
    {
        $user->setRole('ROLE_USER');
        $user->setSubscriptionStatus('EXPIRED');
        $user->setSubscriptionType(null);
        $user->setSubscriptionEndAt(null);
        $user->setUpdatedAt(new \DateTimeImmutable());
    }

    private function normalizeInactiveUserAccess(User $user): bool
    {
        $changed = false;

        if ($user->getRole() !== 'ROLE_USER') {
            $user->setRole('ROLE_USER');
            $changed = true;
        }
        if ($user->getSubscriptionType() !== null) {
            $user->setSubscriptionType(null);
            $changed = true;
        }
        if ($user->getSubscriptionEndAt() !== null && $user->getSubscriptionStatus() !== 'ACTIVE') {
            $user->setSubscriptionEndAt(null);
            $changed = true;
        }

        if ($changed) {
            $user->setUpdatedAt(new \DateTimeImmutable());
        }

        return $changed;
    }

    private function isExpiredByData(User $user): bool
    {
        if ($user->isSubscriptionExpired()) {
            return true;
        }

        $hasFreeMonth = $user->getAiFreeMonthGrantedAt() !== null
            && $user->getSubscriptionEndAt() !== null
            && $user->getSubscriptionEndAt() > new \DateTimeImmutable();

        if ($hasFreeMonth) {
            return false;
        }

        if ($user->getSubscriptionStatus() !== 'ACTIVE') {
            return false;
        }

        $latest = $this->abonnementRepository->findLatestForUser($user);
        if ($latest === null) {
            return true;
        }

        $endDate = $latest->getDateFin();
        $today = new \DateTimeImmutable('today');
        $endDay = \DateTimeImmutable::createFromInterface($endDate)->setTime(0, 0);

        return $endDay < $today;
    }
}
