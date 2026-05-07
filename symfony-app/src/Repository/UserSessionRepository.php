<?php

namespace App\Repository;

use App\Entity\User;
use App\Entity\UserSession;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<UserSession>
 */
class UserSessionRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, UserSession::class);
    }

    public function findBySessionId(string $sessionId): ?UserSession
    {
        return $this->findOneBy(['sessionId' => $sessionId]);
    }

    public function hasAnySession(User $user): bool
    {
        return (int) $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->andWhere('s.user = :user')
            ->setParameter('user', $user)
            ->getQuery()
            ->getSingleScalarResult() > 0;
    }

    public function hasUserAgent(User $user, string $userAgent): bool
    {
        return (int) $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->andWhere('s.user = :user')
            ->andWhere('s.userAgent = :ua')
            ->setParameter('user', $user)
            ->setParameter('ua', $userAgent)
            ->getQuery()
            ->getSingleScalarResult() > 0;
    }

    public function hasCountry(User $user, string $country): bool
    {
        return (int) $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->andWhere('s.user = :user')
            ->andWhere('s.country = :country')
            ->setParameter('user', $user)
            ->setParameter('country', $country)
            ->getQuery()
            ->getSingleScalarResult() > 0;
    }

    public function findActiveSessions(User $user, int $idleMinutes): array
    {
        $since = new \DateTimeImmutable('-' . $idleMinutes . ' minutes');
        return $this->createQueryBuilder('s')
            ->andWhere('s.user = :user')
            ->andWhere('s.revokedAt IS NULL')
            ->andWhere('s.lastActivityAt >= :since')
            ->setParameter('user', $user)
            ->setParameter('since', $since)
            ->orderBy('s.lastActivityAt', 'DESC')
            ->getQuery()
            ->getResult();
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
            if ($filters['status'] === 'active') {
                $qb->andWhere('s.revokedAt IS NULL');
            } elseif ($filters['status'] === 'revoked') {
                $qb->andWhere('s.revokedAt IS NOT NULL');
            }
        }

        if (!empty($filters['ip'])) {
            $qb->andWhere('s.ipAddress LIKE :ip')->setParameter('ip', '%' . $filters['ip'] . '%');
        }

        if (!empty($filters['country'])) {
            $qb->andWhere('s.country = :country')->setParameter('country', $filters['country']);
        }

        return $qb->orderBy('s.lastActivityAt', 'DESC');
    }
}
