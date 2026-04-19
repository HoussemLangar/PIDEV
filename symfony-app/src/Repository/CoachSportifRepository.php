<?php

namespace App\Repository;

use App\Entity\CoachSportif;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<CoachSportif>
 */
class CoachSportifRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, CoachSportif::class);
    }

    /**
     * Find all CoachSportif entities ordered by ID desc
     * @return CoachSportif[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find CoachSportif by ID
     */
    public function findById(int $id): ?CoachSportif
    {
        return $this->find($id);
    }

    /**
     * Save CoachSportif entity
     */
    public function save(CoachSportif $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete CoachSportif entity
     */
    public function delete(CoachSportif $entity, bool $flush = true): void
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
