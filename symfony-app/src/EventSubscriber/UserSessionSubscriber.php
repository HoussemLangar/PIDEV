<?php

namespace App\EventSubscriber;

use App\Entity\User;
use App\Entity\UserSession;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\Persistence\ManagerRegistry;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Authentication\Token\Storage\TokenStorageInterface;

#[AsEventListener(event: 'kernel.request')]
class UserSessionSubscriber
{
    public function __construct(
        private EntityManagerInterface $em,
        private ManagerRegistry $registry,
        private TokenStorageInterface $tokenStorage,
        private UrlGeneratorInterface $urlGenerator
    ) {}

    public function __invoke(RequestEvent $event): void
    {
        $request = $event->getRequest();
        $session = $request->hasSession() ? $request->getSession() : null;
        if ($session === null || !$session->isStarted()) {
            return;
        }

        $token = $this->tokenStorage->getToken();
        $user = $token ? $token->getUser() : null;
        if (!$user instanceof User) {
            return;
        }

        $sessionId = $session->getId();
        if ($sessionId === '') {
            return;
        }

        if (!$this->em->isOpen()) {
            $resetManager = $this->registry->resetManager();
            if (!$resetManager instanceof EntityManagerInterface) {
                return;
            }
            $this->em = $resetManager;
        }

        $sessionRepository = $this->registry->getRepository(UserSession::class);
        $sessionEntity = $sessionRepository->findOneBy(['sessionId' => $sessionId]);
        if ($sessionEntity && $sessionEntity->isRevoked()) {
            $session->invalidate();
            $this->tokenStorage->setToken(null);
            $event->setResponse(new RedirectResponse($this->urlGenerator->generate('login')));
            return;
        }

        if ($sessionEntity === null) {
            $sessionEntity = new UserSession();
            $sessionEntity->setUser($user);
            $sessionEntity->setSessionId($sessionId);
        }

        $sessionEntity->markLastActivityAt(new \DateTimeImmutable());
        $this->em->persist($sessionEntity);
        $this->em->flush();
    }
}
