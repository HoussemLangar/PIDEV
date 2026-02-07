<?php

namespace App\Repository;

use App\Entity\SuspiciousLogin;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SuspiciousLogin>
 */
class SuspiciousLoginRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SuspiciousLogin::class);
    }

    public function findRecent(int $limit = 10): array
    {
        return $this->createQueryBuilder('s')
            ->orderBy('s.createdAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    public function countBlocked(): int
    {
        return (int) $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->andWhere('s.blocked = true')
            ->getQuery()
            ->getSingleScalarResult();
    }

    public function hasBlockedForUser(User $user, string $ip, string $userAgent): bool
    {
        return (int) $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->andWhere('s.user = :user')
            ->andWhere('s.ipAddress = :ip OR s.userAgent = :ua')
            ->andWhere('s.blocked = true')
            ->setParameter('user', $user)
            ->setParameter('ip', $ip)
            ->setParameter('ua', $userAgent)
            ->getQuery()
            ->getSingleScalarResult() > 0;
    }

    public function createAdminQueryBuilder(array $filters)
    {
        $qb = $this->createQueryBuilder('s')
            ->leftJoin('s.user', 'u')
            ->addSelect('u');

        if (!empty($filters['q'])) {
            $qb->andWhere('u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q')
                ->setParameter('q', '%' . $filters['q'] . '%');
        }

        if (!empty($filters['status'])) {
            if ($filters['status'] === 'blocked') {
                $qb->andWhere('s.blocked = true');
            } elseif ($filters['status'] === 'allowed') {
                $qb->andWhere('s.blocked = false');
            }
        }

        if (!empty($filters['country'])) {
            $qb->andWhere('s.country = :country')->setParameter('country', $filters['country']);
        }

        return $qb->orderBy('s.createdAt', 'DESC');
    }

    public function findForUser(User $user, int $limit = 5): array
    {
        return $this->createQueryBuilder('s')
            ->andWhere('s.user = :user')
            ->setParameter('user', $user)
            ->orderBy('s.createdAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }
}
