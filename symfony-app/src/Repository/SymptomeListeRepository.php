<?php

namespace App\Repository;

use App\Entity\SymptomeListe;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SymptomeListe>
 */
class SymptomeListeRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SymptomeListe::class);
    }

    /**
     * Find all SymptomeListe entities ordered by ID desc
     * @return SymptomeListe[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find SymptomeListe by ID
     */
    public function findById(int $id): ?SymptomeListe
    {
        return $this->find($id);
    }

    /**
     * Save SymptomeListe entity
     */
    public function save(SymptomeListe $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete SymptomeListe entity
     */
    public function delete(SymptomeListe $entity, bool $flush = true): void
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
     * Find distinct categories
     * @return array
     */
    public function findDistinctCategories(): array
    {
        return $this->createQueryBuilder('s')
            ->select('DISTINCT s.categorie')
            ->where('s.categorie IS NOT NULL')
            ->andWhere('s.categorie != :empty')
            ->setParameter('empty', '')
            ->orderBy('s.categorie', 'ASC')
            ->getQuery()
            ->getSingleColumnResult();
    }

    /**
     * Get total count of symptoms
     */
    public function getTotalCount(): int
    {
        return $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Get count of categories
     */
    public function getCategoriesCount(): int
    {
        return $this->createQueryBuilder('s')
            ->select('COUNT(DISTINCT s.categorie)')
            ->where('s.categorie IS NOT NULL')
            ->andWhere('s.categorie != :empty')
            ->setParameter('empty', '')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Get symptoms created this week
     */
    public function getWeeklyCount(): int
    {
        $weekAgo = new \DateTime();
        $weekAgo->modify('-7 days');
        
        return $this->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->where('s.createdAt >= :weekAgo')
            ->setParameter('weekAgo', $weekAgo)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Get recent symptoms (last 7 days)
     */
    public function getRecentCount(): int
    {
        return $this->getWeeklyCount();
    }

    /**
     * Search symptoms by name and/or category
     */
    public function searchSymptoms(string $search = '', string $category = ''): array
    {
        $qb = $this->createQueryBuilder('s');
        
        $conditions = [];
        $parameters = [];
        
        if (!empty($search)) {
            $conditions[] = 's.nom LIKE :search';
            $parameters['search'] = '%' . $search . '%';
        }
        
        if (!empty($category)) {
            $conditions[] = 's.categorie = :category';
            $parameters['category'] = $category;
        }
        
        if (!empty($conditions)) {
            $qb->where(implode(' AND ', $conditions));
            foreach ($parameters as $key => $value) {
                $qb->setParameter($key, $value);
            }
        }
        
        return $qb->orderBy('s.id', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Récupérer les symptômes par catégorie
     * @param string $categorie
     * @return SymptomeListe[]
     */
    public function findByCategorie(string $categorie): array
    {
        return $this->createQueryBuilder('s')
            ->where('s.categorie = :categorie')
            ->setParameter('categorie', $categorie)
            ->orderBy('s.nom', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
