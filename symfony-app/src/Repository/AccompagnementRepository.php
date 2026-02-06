<?php

namespace App\Repository;

use App\Entity\Accompagnement;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Accompagnement>
 */
class AccompagnementRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Accompagnement::class);
    }

    /**
     * Find all Accompagnement entities ordered by ID desc
     * @return Accompagnement[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Accompagnement by ID
     */
    public function findById(int $id): ?Accompagnement
    {
        return $this->find($id);
    }

    /**
     * Save Accompagnement entity
     */
    public function save(Accompagnement $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Accompagnement entity
     */
    public function delete(Accompagnement $entity, bool $flush = true): void
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
