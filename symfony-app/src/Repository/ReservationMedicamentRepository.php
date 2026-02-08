<?php

namespace App\Repository;

use App\Entity\ReservationMedicament;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<ReservationMedicament>
 */
class ReservationMedicamentRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, ReservationMedicament::class);
    }

    /**
     * @return ReservationMedicament[]
     */
    public function findByPatient(int $userId): array
    {
        return $this->createQueryBuilder('r')
            ->leftJoin('r.pharmacie', 'p')
            ->addSelect('p')
            ->leftJoin('r.medicament', 'm')
            ->addSelect('m')
            ->andWhere('r.patient = :uid')
            ->setParameter('uid', $userId)
            ->orderBy('r.createdAt', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * @return ReservationMedicament[]
     */
    public function findByPharmacien(int $pharmacienId): array
    {
        return $this->createQueryBuilder('r')
            ->leftJoin('r.pharmacie', 'p')
            ->addSelect('p')
            ->leftJoin('r.medicament', 'm')
            ->addSelect('m')
            ->andWhere('p.pharmacien = :pid')
            ->setParameter('pid', $pharmacienId)
            ->orderBy('r.createdAt', 'DESC')
            ->getQuery()
            ->getResult();
    }
}
