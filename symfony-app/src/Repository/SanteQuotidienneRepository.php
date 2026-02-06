<?php

namespace App\Repository;

use App\Entity\SanteQuotidienne;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SanteQuotidienne>
 */
class SanteQuotidienneRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SanteQuotidienne::class);
    }

    // --------------------------------------------------
    // Méthodes utiles très courantes dans ce genre de projet
    // --------------------------------------------------

    /**
     * Récupère l'entrée du jour pour l'utilisateur (s'il y en a une)
     */
    public function findTodayForUser(object $user): ?SanteQuotidienne
    {
        $today = (new \DateTime())->setTime(0, 0, 0);

        return $this->createQueryBuilder('s')
            ->andWhere('s.user = :user')
            ->andWhere('s.date >= :todayStart')
            ->andWhere('s.date < :tomorrow')
            ->setParameter('user', $user)
            ->setParameter('todayStart', $today)
            ->setParameter('tomorrow', (clone $today)->modify('+1 day'))
            ->getQuery()
            ->getOneOrNullResult();
    }

    /**
     * Toutes les entrées d'un utilisateur, triées par date descendante
     */
    public function findByUserOrdered(object $user): array
    {
        return $this->createQueryBuilder('s')
            ->where('s.user = :user')
            ->setParameter('user', $user)
            ->orderBy('s.date', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Moyenne du poids sur les 30 derniers jours
     */
    public function getAverageWeightLast30Days(object $user): ?float
    {
        return $this->createQueryBuilder('s')
            ->select('AVG(s.poids) as avg')
            ->where('s.user = :user')
            ->andWhere('s.date >= :start')
            ->setParameter('user', $user)
            ->setParameter('start', new \DateTime('-30 days'))
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Entrées d’un mois précis (ex: pour un rapport mensuel)
     */
    public function findByMonth(object $user, int $year, int $month): array
    {
        $start = (new \DateTime("$year-$month-01"))->setTime(0, 0, 0);
        $end = (clone $start)->modify('last day of this month')->setTime(23, 59, 59);

        return $this->createQueryBuilder('s')
            ->where('s.user = :user')
            ->andWhere('s.date BETWEEN :start AND :end')
            ->setParameter('user', $user)
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->orderBy('s.date', 'ASC')
            ->getQuery()
            ->getResult();
    }
}