<?php

namespace App\Repository;

use App\Entity\ReponseMedicament;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<ReponseMedicament>
 */
class ReponseMedicamentRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, ReponseMedicament::class);
    }

    /**
     * Find all ReponseMedicament entities ordered by ID desc
     * @return ReponseMedicament[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find ReponseMedicament by ID
     */
    public function findById(int $id): ?ReponseMedicament
    {
        return $this->find($id);
    }

    /**
     * Save ReponseMedicament entity
     */
    public function save(ReponseMedicament $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete ReponseMedicament entity
     */
    public function delete(ReponseMedicament $entity, bool $flush = true): void
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
