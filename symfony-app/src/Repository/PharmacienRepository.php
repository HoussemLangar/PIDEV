<?php

namespace App\Repository;

use App\Entity\Pharmacien;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Pharmacien>
 */
class PharmacienRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Pharmacien::class);
    }

    /**
     * Find all Pharmacien entities ordered by ID desc
     * @return Pharmacien[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Pharmacien by ID
     */
    public function findById(int $id): ?Pharmacien
    {
        return $this->find($id);
    }

    /**
     * Save Pharmacien entity
     */
    public function save(Pharmacien $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Pharmacien entity
     */
    public function delete(Pharmacien $entity, bool $flush = true): void
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
