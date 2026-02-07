<?php

namespace App\Repository;

use App\Entity\Pharmacy;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Pharmacy>
 */
class PharmacyRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Pharmacy::class);
    }

    /**
     * Find all Pharmacy entities ordered by ID desc
     * @return Pharmacy[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Pharmacy by ID
     */
    public function findById(int $id): ?Pharmacy
    {
        return $this->find($id);
    }

    /**
     * Save Pharmacy entity
     */
    public function save(Pharmacy $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Pharmacy entity
     */
    public function delete(Pharmacy $entity, bool $flush = true): void
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
     * Search pharmacies by name, address, or contact info
     * @param string|null $search
     * @param string $sortBy
     * @param string $sortDir
     * @return Pharmacy[]
     */
    public function searchPharmacies(?string $search = null, string $sortBy = 'id', string $sortDir = 'ASC'): array
    {
        $qb = $this->createQueryBuilder('p');

        if ($search) {
            $searchTerm = '%' . $search . '%';
            $qb->andWhere(
                $qb->expr()->orX(
                    $qb->expr()->like('LOWER(p.nom)', 'LOWER(:search)'),
                    $qb->expr()->like('LOWER(p.adresse)', 'LOWER(:search)'),
                    $qb->expr()->like('LOWER(p.telephone)', 'LOWER(:search)'),
                    $qb->expr()->like('LOWER(p.email)', 'LOWER(:search)')
                )
            )
            ->setParameter('search', $searchTerm);
        }

        // Validate sort parameters
        $validSortFields = ['id', 'nom', 'adresse', 'createdAt'];
        if (!in_array($sortBy, $validSortFields)) {
            $sortBy = 'id';
        }

        $sortDir = strtoupper($sortDir) === 'DESC' ? 'DESC' : 'ASC';

        $qb->orderBy('p.' . $sortBy, $sortDir);

        return $qb->getQuery()->getResult();
    }
}
