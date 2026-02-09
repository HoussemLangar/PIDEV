<?php
// src/EventSubscriber/LoginSuccessListener.php

namespace App\EventSubscriber;

use App\Entity\SuspiciousLogin;
use App\Entity\User;
use App\Entity\UserSession;
use App\Repository\SuspiciousLoginRepository;
use App\Repository\UserSessionRepository;
use App\Security\GeoIpService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Authentication\Token\Storage\TokenStorageInterface;
use Symfony\Component\Security\Http\Event\LoginSuccessEvent;

#[AsEventListener(event: LoginSuccessEvent::class)]
class LoginSuccessListener
{
    public function __construct(
        private UrlGeneratorInterface $urlGenerator,
        private EntityManagerInterface $em,
        private UserSessionRepository $userSessionRepository,
        private SuspiciousLoginRepository $suspiciousLoginRepository,
        private GeoIpService $geoIpService,
        private MailerInterface $mailer,
        private TokenStorageInterface $tokenStorage
    ) {}

    public function __invoke(LoginSuccessEvent $event): void
    {
        $user = $event->getUser();
        if (!$user instanceof User) {
            return;
        }

        $request = $event->getRequest();
        $session = $request->getSession();
        $sessionId = $session ? $session->getId() : null;
        $ip = (string) $request->getClientIp();
        $userAgent = (string) $request->headers->get('User-Agent', '');
        $country = $this->geoIpService->getCountry($ip);

        if (method_exists($user, 'isBannedEffective') && $user->isBannedEffective()) {
            $response = new RedirectResponse(
                $this->urlGenerator->generate('app_banned')
            );
            $event->setResponse($response);
            return;
        }

        $hasAnySession = $this->userSessionRepository->hasAnySession($user);
        $newBrowser = $hasAnySession && $userAgent !== '' && !$this->userSessionRepository->hasUserAgent($user, $userAgent);
        $newCountry = $hasAnySession && $country !== null && $country !== 'UNKNOWN' && !$this->userSessionRepository->hasCountry($user, $country);

        if (($newBrowser || $newCountry) && !$this->suspiciousLoginRepository->hasBlockedForUser($user, $ip, $userAgent)) {
            $reasons = [];
            if ($newCountry) {
                $reasons[] = 'Nouveau pays';
            }
            if ($newBrowser) {
                $reasons[] = 'Nouveau navigateur';
            }

            $suspicious = new SuspiciousLogin();
            $suspicious->setUser($user);
            $suspicious->setIpAddress($ip ?: null);
            $suspicious->setUserAgent($userAgent ?: null);
            $suspicious->setCountry($country);
            $suspicious->setReason(implode(', ', $reasons));
            $suspicious->setBlocked(true);
            $suspicious->setNotified(true);
            $this->em->persist($suspicious);
            $this->em->flush();

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA Security'))
                ->to($user->getEmail())
                ->subject('Connexion suspecte détectée')
                ->htmlTemplate('emails/suspicious_login.html.twig')
                ->context([
                    'user' => $user,
                    'ip' => $ip,
                    'country' => $country,
                    'userAgent' => $userAgent,
                    'createdAt' => new \DateTimeImmutable(),
                    'reasons' => $reasons,
                ]);
            $this->mailer->send($email);

            if ($session) {
                $session->getFlashBag()->add('error', 'Connexion suspecte détectée. Un email de sécurité vous a été envoyé.');
                $session->invalidate();
            }
            $this->tokenStorage->setToken(null);
            $event->setResponse(new RedirectResponse($this->urlGenerator->generate('login')));
            return;
        }

        if ($sessionId !== null && $sessionId !== '') {
            $existing = $this->userSessionRepository->findBySessionId($sessionId);
            if ($existing === null) {
                $existing = new UserSession();
                $existing->setUser($user);
                $existing->setSessionId($sessionId);
            }
            $existing->setIpAddress($ip ?: null);
            $existing->setUserAgent($userAgent ?: null);
            $existing->setCountry($country);
            $existing->setLastActivityAt(new \DateTimeImmutable());
            $this->em->persist($existing);
            $this->em->flush();
        }
        
        // Vérifier si l'utilisateur a le rôle ADMIN
        if (in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            // Rediriger vers la page de vérification faciale
            $response = new RedirectResponse(
                $this->urlGenerator->generate('admin_face_verification')
            );
            $event->setResponse($response);
            return;
        }
        
        // Rediriger les utilisateurs sans abonnement actif vers la page de souscription
        if (!$user->isSubscriptionActive() && $user->getSubscriptionStatus() !== 'SKIPPED') {
            $response = new RedirectResponse(
                $this->urlGenerator->generate('app_subscription')
            );
            $event->setResponse($response);
        }
    }
}
