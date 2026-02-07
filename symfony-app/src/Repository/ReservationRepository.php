<?php

namespace App\Repository;

use App\Entity\Reservation;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Reservation>
 */
class ReservationRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Reservation::class);
    }

    /** @return Reservation[] */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    public function findById(int $id): ?Reservation
    {
        return $this->find($id);
    }

    public function save(Reservation $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    public function delete(Reservation $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->remove($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /** @return Reservation[] */
    public function findForPharmacien(\App\Entity\Pharmacien $pharmacien): array
    {
        return $this->createQueryBuilder('r')
            ->innerJoin('r.pharmacy', 'p')
            ->andWhere('p.pharmacien = :pharmacien')
            ->setParameter('pharmacien', $pharmacien)
            ->orderBy('r.createdAt', 'DESC')
            ->getQuery()
            ->getResult();
    }
}
