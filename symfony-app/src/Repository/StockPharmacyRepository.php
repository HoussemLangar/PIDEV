<?php

namespace App\Repository;

use App\Entity\StockPharmacy;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<StockPharmacy>
 */
class StockPharmacyRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, StockPharmacy::class);
    }

    /**
     * Find all StockPharmacy entities ordered by ID desc
     * @return StockPharmacy[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find StockPharmacy by ID
     */
    public function findById(int $id): ?StockPharmacy
    {
        return $this->find($id);
    }

    /**
     * Save StockPharmacy entity
     */
    public function save(StockPharmacy $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete StockPharmacy entity
     */
    public function delete(StockPharmacy $entity, bool $flush = true): void
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
