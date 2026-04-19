<?php

namespace App\Repository;

use App\Entity\Like;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Like>
 */
class LikeRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Like::class);
    }

    /**
     * Find all Like entities ordered by ID desc
     * @return Like[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Like by ID
     */
    public function findById(int $id): ?Like
    {
        return $this->find($id);
    }

    public function findOneByUserAndContenu(int $userId, int $contenuId): ?Like
    {
        return $this->createQueryBuilder('l')
            ->andWhere('l.user = :uid')
            ->andWhere('l.contenu = :cid')
            ->setParameter('uid', $userId)
            ->setParameter('cid', $contenuId)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function countByContenu(int $contenuId): int
    {
        return (int) $this->createQueryBuilder('l')
            ->select('COUNT(l.id)')
            ->andWhere('l.contenu = :cid')
            ->setParameter('cid', $contenuId)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Save Like entity
     */
    public function save(Like $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Like entity
     */
    public function delete(Like $entity, bool $flush = true): void
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
}
