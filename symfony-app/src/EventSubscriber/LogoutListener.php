<?php
// src/EventSubscriber/LogoutListener.php

namespace App\EventSubscriber;

use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\Security\Http\Event\LogoutEvent;

#[AsEventListener(event: LogoutEvent::class)]
class LogoutListener
{
    public function __invoke(LogoutEvent $event): void
    {
        // Nettoyer la session de vérification faciale
        $request = $event->getRequest();
        $session = $request->getSession();
        
        if ($session->has('face_verified')) {
            $session->remove('face_verified');
        }
    }
}
