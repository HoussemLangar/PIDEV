<?php

namespace App\Repository;

use App\Entity\SymptomeQuotidien;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SymptomeQuotidien>
 */
class SymptomeQuotidienRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SymptomeQuotidien::class);
    }

    /**
     * Find all SymptomeQuotidien entities ordered by ID desc
     * @return SymptomeQuotidien[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find SymptomeQuotidien by ID
     */
    public function findById(int $id): ?SymptomeQuotidien
    {
        return $this->find($id);
    }

    /**
     * Save SymptomeQuotidien entity
     */
    public function save(SymptomeQuotidien $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete SymptomeQuotidien entity
     */
    public function delete(SymptomeQuotidien $entity, bool $flush = true): void
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
