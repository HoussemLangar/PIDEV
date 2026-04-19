<?php

namespace App\Repository;

use App\Entity\RapportAnalyse;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<RapportAnalyse>
 */
class RapportAnalyseRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, RapportAnalyse::class);
    }

    /**
     * Find all RapportAnalyse entities ordered by ID desc
     * @return RapportAnalyse[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find RapportAnalyse by ID
     */
    public function findById(int $id): ?RapportAnalyse
    {
        return $this->find($id);
    }

    /**
     * Save RapportAnalyse entity
     */
    public function save(RapportAnalyse $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete RapportAnalyse entity
     */
    public function delete(RapportAnalyse $entity, bool $flush = true): void
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
