<?php

namespace App\Repository;

use App\Entity\SymptomeListe;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SymptomeListe>
 */
class SymptomeListeRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SymptomeListe::class);
    }

    /**
     * Find all SymptomeListe entities ordered by ID desc
     * @return SymptomeListe[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find SymptomeListe by ID
     */
    public function findById(int $id): ?SymptomeListe
    {
        return $this->find($id);
    }

    /**
     * Save SymptomeListe entity
     */
    public function save(SymptomeListe $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete SymptomeListe entity
     */
    public function delete(SymptomeListe $entity, bool $flush = true): void
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
