<?php

namespace App\Repository;

use App\Entity\Nutritionniste;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Nutritionniste>
 */
class NutritionnisteRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Nutritionniste::class);
    }

    /**
     * Find all Nutritionniste entities ordered by ID desc
     * @return Nutritionniste[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Nutritionniste by ID
     */
    public function findById(int $id): ?Nutritionniste
    {
        return $this->find($id);
    }

    /**
     * Save Nutritionniste entity
     */
    public function save(Nutritionniste $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Nutritionniste entity
     */
    public function delete(Nutritionniste $entity, bool $flush = true): void
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
