<?php

namespace App\Service;

use App\Entity\Notification;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

class NotificationService
{
    public function __construct(private EntityManagerInterface $em) {}

    public function notify(
        User $user,
        string $title,
        string $message,
        string $type = 'rdv',
        ?string $link = null,
        string $priority = 'normal'
    ): Notification {
        $notification = new Notification();
        $notification->setUser($user);
        $notification->setTitre($title);
        $notification->setMessage($message);
        $notification->setType($type);
        $notification->setLien($link);
        $notification->setPrioritee($priority);
        $notification->setDateEnvoi(new \DateTime());

        $this->em->persist($notification);
        $this->em->flush();

        return $notification;
    }
}
