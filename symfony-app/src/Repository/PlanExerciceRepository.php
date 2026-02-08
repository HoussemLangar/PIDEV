<?php

namespace App\Repository;

use App\Entity\PlanExercice;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<PlanExercice>
 */
class PlanExerciceRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, PlanExercice::class);
    }

    /**
     * Find all PlanExercice entities ordered by ID desc
     * @return PlanExercice[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find PlanExercice by ID
     */
    public function findById(int $id): ?PlanExercice
    {
        return $this->find($id);
    }

    /**
     * Save PlanExercice entity
     */
    public function save(PlanExercice $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete PlanExercice entity
     */
    public function delete(PlanExercice $entity, bool $flush = true): void
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
