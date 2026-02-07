<?php

namespace App\Repository;

use App\Entity\Teleconsultation;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Teleconsultation>
 */
class TeleconsultationRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Teleconsultation::class);
    }

    /**
     * Find upcoming consultations for a user
     */
    public function findUpcoming(User $user, int $limit = 20): array
    {
        return $this->createQueryBuilder('t')
            ->where('(t.initiator = :user OR t.recipient = :user)')
            ->andWhere('t.status = :status')
            ->andWhere('t.scheduledAt >= :now')
            ->setParameter('user', $user)
            ->setParameter('status', 'pending')
            ->setParameter('now', new \DateTimeImmutable())
            ->orderBy('t.scheduledAt', 'ASC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Find past consultations for a user
     */
    public function findPast(User $user, int $limit = 50): array
    {
        return $this->createQueryBuilder('t')
            ->where('(t.initiator = :user OR t.recipient = :user)')
            ->andWhere('t.status IN (:statuses)')
            ->setParameter('user', $user)
            ->setParameter('statuses', ['completed', 'cancelled'])
            ->orderBy('t.endedAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Find ongoing consultations for a user
     */
    public function findOngoing(User $user): array
    {
        return $this->createQueryBuilder('t')
            ->where('(t.initiator = :user OR t.recipient = :user)')
            ->andWhere('t.status = :status')
            ->setParameter('user', $user)
            ->setParameter('status', 'ongoing')
            ->getQuery()
            ->getResult();
    }

    /**
     * Get consultations statistics for a user
     */
    public function getStatistics(User $user): array
    {
        $stats = $this->createQueryBuilder('t')
            ->select("SUM(CASE WHEN t.status = 'completed' THEN 1 ELSE 0 END) as completed")
            ->addSelect("SUM(CASE WHEN t.status = 'pending' THEN 1 ELSE 0 END) as pending")
            ->addSelect("SUM(CASE WHEN t.status = 'cancelled' THEN 1 ELSE 0 END) as cancelled")
            ->addSelect('AVG(t.durationSeconds) as avgDuration')
            ->addSelect('SUM(t.durationSeconds) as totalDuration')
            ->where('(t.initiator = :user OR t.recipient = :user)')
            ->setParameter('user', $user)
            ->getQuery()
            ->getSingleResult();

        return [
            'completed' => (int)$stats['completed'],
            'pending' => (int)$stats['pending'],
            'cancelled' => (int)$stats['cancelled'],
            'avgDuration' => $stats['avgDuration'] ? (int)$stats['avgDuration'] : 0,
            'totalDuration' => $stats['totalDuration'] ? (int)$stats['totalDuration'] : 0,
        ];
    }
}
