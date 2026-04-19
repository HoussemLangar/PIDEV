<?php

namespace App\Repository;

use App\Entity\HealthRiskPrediction;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<HealthRiskPrediction>
 */
class HealthRiskPredictionRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, HealthRiskPrediction::class);
    }

    public function findLatestForUser(User $user): ?HealthRiskPrediction
    {
        return $this->createQueryBuilder('p')
            ->andWhere('p.user = :user')
            ->setParameter('user', $user)
            ->orderBy('p.predictionDate', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function findForUserAndDate(User $user, \DateTimeImmutable $date): ?HealthRiskPrediction
    {
        return $this->findOneBy([
            'user' => $user,
            'predictionDate' => $date,
        ]);
    }
}

