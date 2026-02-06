<?php

namespace App\Repository;

use App\Entity\ReponseMedecin;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<ReponseMedecin>
 */
class ReponseMedecinRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, ReponseMedecin::class);
    }

    /**
     * Find all ReponseMedecin entities ordered by ID desc
     * @return ReponseMedecin[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find ReponseMedecin by ID
     */
    public function findById(int $id): ?ReponseMedecin
    {
        return $this->find($id);
    }

    /**
     * Save ReponseMedecin entity
     */
    public function save(ReponseMedecin $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete ReponseMedecin entity
     */
    public function delete(ReponseMedecin $entity, bool $flush = true): void
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
