<?php

namespace App\Repository;

use App\Entity\Medicament;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Medicament>
 */
class MedicamentRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Medicament::class);
    }

    /**
     * Find all Medicament entities ordered by ID desc
     * @return Medicament[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Medicament by ID
     */
    public function findById(int $id): ?Medicament
    {
        return $this->find($id);
    }

    /**
     * Save Medicament entity
     */
    public function save(Medicament $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Medicament entity
     */
    public function delete(Medicament $entity, bool $flush = true): void
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

    /**
     * Search Medicaments by term with sorting
     * @return Medicament[]
     */
    public function searchMedicaments(?string $search, string $sortBy = 'id', string $sortDir = 'ASC'): array
    {
        $qb = $this->createQueryBuilder('m');

        if ($search) {
            $qb->andWhere('
                LOWER(m.nom) LIKE LOWER(:search) OR
                LOWER(m.type) LIKE LOWER(:search) OR
                LOWER(m.forme) LIKE LOWER(:search) OR
                LOWER(m.dosage) LIKE LOWER(:search) OR
                LOWER(m.laboratoire) LIKE LOWER(:search)
            ')
            ->setParameter('search', '%' . $search . '%');
        }

        $qb->orderBy('m.' . $sortBy, strtoupper($sortDir));

        return $qb->getQuery()->getResult();
    }
}
