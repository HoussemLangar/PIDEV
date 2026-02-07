<?php

namespace App\Service;

use App\Entity\Notification;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

class NotificationService
{
    public function __construct(private EntityManagerInterface $em) {}

    public function create(User $user, string $type, string $titre, string $message, ?string $link = null, string $priority = 'normal'): Notification
    {
        $notification = new Notification();
        $notification->setUser($user);
        $notification->setType($type);
        $notification->setTitre($titre);
        $notification->setMessage($message);
        $notification->setLien($link);
        $notification->setPrioritee($priority);

        $this->em->persist($notification);
        $this->em->flush();

        return $notification;
    }
}
