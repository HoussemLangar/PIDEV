<?php

namespace App\Repository;

use App\Entity\PlanRegime;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<PlanRegime>
 */
class PlanRegimeRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, PlanRegime::class);
    }

    /**
     * Find all PlanRegime entities ordered by ID desc
     * @return PlanRegime[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find PlanRegime by ID
     */
    public function findById(int $id): ?PlanRegime
    {
        return $this->find($id);
    }

    /**
     * Save PlanRegime entity
     */
    public function save(PlanRegime $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete PlanRegime entity
     */
    public function delete(PlanRegime $entity, bool $flush = true): void
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
