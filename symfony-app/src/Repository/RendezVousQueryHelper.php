<?php

namespace App\Repository;

use Doctrine\ORM\EntityManagerInterface;

class RendezVousQueryHelper
{
    public function __construct(private EntityManagerInterface $em) {}

    public function findUpcomingForUser(int $userId): array
    {
        $now = new \DateTimeImmutable();
        return $this->em->createQueryBuilder()
            ->select('r')
            ->from('App\Entity\RendezVous', 'r')
            ->innerJoin('r.patient', 'p')
            ->innerJoin('p.user', 'u')
            ->andWhere('u.id = :uid')
            ->andWhere('r.statut = :statut')
            ->andWhere('r.dateRdv >= :today')
            ->setParameter('uid', $userId)
            ->setParameter('statut', 'confirme')
            ->setParameter('today', $now->format('Y-m-d'))
            ->orderBy('r.dateRdv', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
