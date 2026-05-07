<?php

namespace App\EventSubscriber;

use App\Entity\User;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\Security\Core\Authentication\Token\Storage\TokenStorageInterface;

#[AsEventListener(event: 'kernel.request', priority: 20)]
class LocaleSubscriber
{
    private const DEFAULT_LOCALE = 'fr';
    private const ALLOWED_LOCALES = ['fr', 'en', 'ar'];

    public function __construct(private TokenStorageInterface $tokenStorage) {}

    public function __invoke(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();
        $session = $request->hasSession() ? $request->getSession() : null;

        $locale = null;

        // 1. Session takes priority (explicit user choice)
        if ($session && $session->has('_locale')) {
            $locale = $session->get('_locale');
        }

        // 2. Authenticated user's saved preference
        if (!$locale) {
            $token = $this->tokenStorage->getToken();
            $user = $token ? $token->getUser() : null;
            if ($user instanceof User) {
                $locale = $user->getLocale();
                // Persist to session so subsequent requests skip DB lookup
                if ($session && $locale) {
                    $session->set('_locale', $locale);
                }
            }
        }

        // 3. Fallback to French
        if (!$locale || !in_array($locale, self::ALLOWED_LOCALES, true)) {
            $locale = self::DEFAULT_LOCALE;
        }

        $request->setLocale($locale);
    }
}
