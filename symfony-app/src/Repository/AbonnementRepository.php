<?php

namespace App\Repository;

use App\Entity\Abonnement;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\ORM\QueryBuilder;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Abonnement>
 */
class AbonnementRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Abonnement::class);
    }

    /**
     * Find all Abonnement entities ordered by ID desc
     * @return Abonnement[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Abonnement by ID
     */
    public function findById(int $id): ?Abonnement
    {
        return $this->find($id);
    }

    /**
     * Save Abonnement entity
     */
    public function save(Abonnement $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Abonnement entity
     */
    public function delete(Abonnement $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->remove($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Flush pending changes
     */
    public function flush(): void
    {
        $this->getEntityManager()->flush();
    }

    public function findLatestForUser(User $user): ?Abonnement
    {
        return $this->createQueryBuilder('a')
            ->andWhere('a.user = :user')
            ->setParameter('user', $user)
            ->orderBy('a.dateDebut', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function findActiveForUserAndType(User $user, string $type): ?Abonnement
    {
        $today = new \DateTime('today');

        return $this->createQueryBuilder('a')
            ->andWhere('a.user = :user')
            ->andWhere('a.typeAbonnement = :type')
            ->andWhere('LOWER(a.statut) = :status')
            ->andWhere('a.dateDebut <= :today')
            ->andWhere('a.dateFin >= :today')
            ->setParameter('user', $user)
            ->setParameter('type', $type)
            ->setParameter('status', 'actif')
            ->setParameter('today', $today)
            ->orderBy('a.dateFin', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function findByPaymentSessionId(string $paymentSessionId): ?Abonnement
    {
        return $this->findOneBy(['paymentSessionId' => $paymentSessionId]);
    }

    public function createFilteredQueryBuilder(array $filters): QueryBuilder
    {
        $qb = $this->createQueryBuilder('a')
            ->leftJoin('a.user', 'u')
            ->addSelect('u');

        if (!empty($filters['q'])) {
            $qb->andWhere('u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q')
                ->setParameter('q', '%' . $filters['q'] . '%');
        }

        if (!empty($filters['status'])) {
            $qb->andWhere('a.statut = :status')->setParameter('status', $filters['status']);
        }

        if (!empty($filters['type'])) {
            $qb->andWhere('a.typeAbonnement = :type')->setParameter('type', $filters['type']);
        }

        if (!empty($filters['from'])) {
            try {
                $from = new \DateTime($filters['from'] . ' 00:00:00');
                $qb->andWhere('a.dateDebut >= :from')->setParameter('from', $from);
            } catch (\Throwable) {
            }
        }

        if (!empty($filters['to'])) {
            try {
                $to = new \DateTime($filters['to'] . ' 23:59:59');
                $qb->andWhere('a.dateFin <= :to')->setParameter('to', $to);
            } catch (\Throwable) {
            }
        }

        return $qb->orderBy('a.dateDebut', 'DESC');
    }

    public function getSubscriptionStats(): array
    {
        $total = (int) $this->createQueryBuilder('a')
            ->select('COUNT(a.id)')
            ->getQuery()
            ->getSingleScalarResult();

        $active = (int) $this->createQueryBuilder('a')
            ->select('COUNT(a.id)')
            ->andWhere('a.statut = :s')
            ->setParameter('s', 'actif')
            ->getQuery()
            ->getSingleScalarResult();

        $expired = (int) $this->createQueryBuilder('a')
            ->select('COUNT(a.id)')
            ->andWhere('a.statut = :s')
            ->setParameter('s', 'expire')
            ->getQuery()
            ->getSingleScalarResult();

        $cancelled = (int) $this->createQueryBuilder('a')
            ->select('COUNT(a.id)')
            ->andWhere('a.statut = :s')
            ->setParameter('s', 'annule')
            ->getQuery()
            ->getSingleScalarResult();

        $byType = $this->createQueryBuilder('a')
            ->select('a.typeAbonnement as type, COUNT(a.id) as count')
            ->groupBy('a.typeAbonnement')
            ->getQuery()
            ->getResult();

        return [
            'total' => $total,
            'active' => $active,
            'expired' => $expired,
            'cancelled' => $cancelled,
            'byType' => $byType,
        ];
    }
}
