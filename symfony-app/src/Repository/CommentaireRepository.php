<?php

namespace App\Repository;

use App\Entity\Commentaire;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Commentaire>
 */
class CommentaireRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Commentaire::class);
    }

    /**
     * Find all Commentaire entities ordered by ID desc
     * @return Commentaire[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Commentaire by ID
     */
    public function findById(int $id): ?Commentaire
    {
        return $this->find($id);
    }

    /**
     * @return Commentaire[]
     */
    public function findByContenu(int $contenuId): array
    {
        return $this->createQueryBuilder('c')
            ->leftJoin('c.user', 'u')
            ->addSelect('u')
            ->andWhere('c.contenu = :cid')
            ->setParameter('cid', $contenuId)
            ->orderBy('c.createdAt', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * @return Commentaire[]
     */
    public function findPublishedByContenu(int $contenuId): array
    {
        return $this->createQueryBuilder('c')
            ->leftJoin('c.user', 'u')
            ->addSelect('u')
            ->andWhere('c.contenu = :cid')
            ->andWhere('c.statut = :status')
            ->setParameter('cid', $contenuId)
            ->setParameter('status', 'publie')
            ->orderBy('c.createdAt', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * @return Commentaire[]
     */
    public function findRecentByContenu(int $contenuId, int $limit = 20): array
    {
        return $this->createQueryBuilder('c')
            ->leftJoin('c.user', 'u')
            ->addSelect('u')
            ->andWhere('c.contenu = :cid')
            ->setParameter('cid', $contenuId)
            ->orderBy('c.createdAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    public function countByContenu(int $contenuId): int
    {
        return (int) $this->createQueryBuilder('c')
            ->select('COUNT(c.id)')
            ->andWhere('c.contenu = :cid')
            ->setParameter('cid', $contenuId)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Save Commentaire entity
     */
    public function save(Commentaire $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Commentaire entity
     */
    public function delete(Commentaire $entity, bool $flush = true): void
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
