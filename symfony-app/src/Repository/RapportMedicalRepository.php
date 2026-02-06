<?php

namespace App\Repository;

use App\Entity\RapportMedical;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<RapportMedical>
 */
class RapportMedicalRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, RapportMedical::class);
    }

    /**
     * Find all RapportMedical entities ordered by ID desc
     * @return RapportMedical[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find RapportMedical by ID
     */
    public function findById(int $id): ?RapportMedical
    {
        return $this->find($id);
    }

    /**
     * Save RapportMedical entity
     */
    public function save(RapportMedical $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete RapportMedical entity
     */
    public function delete(RapportMedical $entity, bool $flush = true): void
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
