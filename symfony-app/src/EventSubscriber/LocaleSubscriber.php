<?php

namespace App\EventSubscriber;

use App\Entity\User;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\Security\Core\Authentication\Token\Storage\TokenStorageInterface;

#[AsEventListener(event: 'kernel.request', priority: 20)]
class LocaleSubscriber
{
    public function __construct(private TokenStorageInterface $tokenStorage) {}

    public function __invoke(RequestEvent $event): void
    {
        $request = $event->getRequest();
        $session = $request->hasSession() ? $request->getSession() : null;

        $locale = null;
        if ($session && $session->has('_locale')) {
            $locale = $session->get('_locale');
        } else {
            $token = $this->tokenStorage->getToken();
            $user = $token ? $token->getUser() : null;
            if ($user instanceof User) {
                $locale = $user->getLocale();
                if ($session) {
                    $session->set('_locale', $locale);
                }
            }
        }

        if ($locale) {
            $request->setLocale($locale);
        }
    }
}
