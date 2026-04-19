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

    /**
     * @return StockPharmacy[]
     */
    public function findAvailableByMedicament(int $medicamentId): array
    {
        return $this->createQueryBuilder('s')
            ->leftJoin('s.pharmacie', 'p')
            ->addSelect('p')
            ->andWhere('s.medicament = :mid')
            ->andWhere('s.quantite > 0')
            ->setParameter('mid', $medicamentId)
            ->orderBy('s.prixVente', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * @return StockPharmacy[]
     */
    public function findAvailableByPharmacy(int $pharmacyId): array
    {
        return $this->createQueryBuilder('s')
            ->leftJoin('s.pharmacie', 'p')
            ->addSelect('p')
            ->leftJoin('s.medicament', 'm')
            ->addSelect('m')
            ->andWhere('p.id = :pid')
            ->andWhere('s.quantite > 0')
            ->setParameter('pid', $pharmacyId)
            ->orderBy('m.nom', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
