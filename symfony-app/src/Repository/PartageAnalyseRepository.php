<?php

namespace App\Repository;

use App\Entity\PartageAnalyse;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<PartageAnalyse>
 */
class PartageAnalyseRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, PartageAnalyse::class);
    }

    /**
     * Find all PartageAnalyse entities ordered by ID desc
     * @return PartageAnalyse[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find PartageAnalyse by ID
     */
    public function findById(int $id): ?PartageAnalyse
    {
        return $this->find($id);
    }

    /**
     * Save PartageAnalyse entity
     */
    public function save(PartageAnalyse $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete PartageAnalyse entity
     */
    public function delete(PartageAnalyse $entity, bool $flush = true): void
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
