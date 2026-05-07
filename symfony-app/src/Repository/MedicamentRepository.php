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
     * @return Medicament[]
     */
    public function search(string $q, ?string $codeBarre = null): array
    {
        $qb = $this->createQueryBuilder('m')
            ->orderBy('m.nom', 'ASC');

        if ($q !== '') {
            $qb->andWhere('m.nom LIKE :q OR m.description LIKE :q')
                ->setParameter('q', '%' . $q . '%');
        }
        if ($codeBarre) {
            $qb->andWhere('m.codeBarre = :cb')->setParameter('cb', $codeBarre);
        }

        return $qb->getQuery()->getResult();
    }
}
