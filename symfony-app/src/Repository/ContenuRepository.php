<?php

namespace App\Repository;

use App\Entity\Contenu;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\ORM\QueryBuilder;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Contenu>
 */
class ContenuRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Contenu::class);
    }

    /**
     * Find all Contenu entities ordered by ID desc
     * @return Contenu[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find Contenu by ID
     */
    public function findById(int $id): ?Contenu
    {
        return $this->find($id);
    }

    /**
     * Query builder for validated/published content list.
     */
    public function createValidatedQueryBuilder(
        ?string $type = null,
        ?string $search = null,
        ?string $category = null,
        array $statuses = ['valide', 'publie']
    ): QueryBuilder {
        $qb = $this->createQueryBuilder('c')
            ->leftJoin('c.auteur', 'a')
            ->addSelect('a')
            ->andWhere('c.statut IN (:statuses)')
            ->setParameter('statuses', $statuses);

        if ($type) {
            $qb->andWhere('c.type = :type')->setParameter('type', $type);
        }
        if ($category) {
            $qb->andWhere('c.categorie = :categorie')->setParameter('categorie', $category);
        }
        if ($search) {
            $qb->andWhere('c.titre LIKE :q OR c.description LIKE :q OR c.tags LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }

        return $qb;
    }

    /**
     * Paginated validated content list.
     * @return array{items: Contenu[], total: int, pages: int}
     */
    public function findValidatedPage(
        int $page,
        int $limit,
        ?string $type = null,
        ?string $search = null,
        ?string $category = null,
        array $statuses = ['valide', 'publie']
    ): array {
        $page = max(1, $page);
        $limit = max(1, min(50, $limit));

        $qb = $this->createValidatedQueryBuilder($type, $search, $category, $statuses)
            ->orderBy('c.datePublication', 'DESC')
            ->addOrderBy('c.createdAt', 'DESC')
            ->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $items = $qb->getQuery()->getResult();
        $total = $this->countValidated($type, $search, $category, $statuses);

        return [
            'items' => $items,
            'total' => $total,
            'pages' => (int) ceil($total / $limit),
        ];
    }

    /**
     * Paginated content list (all statuses unless provided).
     * @return array{items: Contenu[], total: int, pages: int}
     */
    public function findPage(
        int $page,
        int $limit,
        ?string $type = null,
        ?string $search = null,
        ?string $category = null,
        ?array $statuses = null,
        ?int $ownerId = null
    ): array {
        $page = max(1, $page);
        $limit = max(1, min(50, $limit));

        $qb = $this->createQueryBuilder('c')
            ->leftJoin('c.auteur', 'a')
            ->addSelect('a')
            ->orderBy('c.datePublication', 'DESC')
            ->addOrderBy('c.createdAt', 'DESC');

        if ($statuses !== null) {
            $qb->andWhere('c.statut IN (:statuses)')->setParameter('statuses', $statuses);
        }
        if ($ownerId !== null) {
            $qb->andWhere('c.auteur = :owner')->setParameter('owner', $ownerId);
        }
        if ($type) {
            $qb->andWhere('c.type = :type')->setParameter('type', $type);
        }
        if ($category) {
            $qb->andWhere('c.categorie = :categorie')->setParameter('categorie', $category);
        }
        if ($search) {
            $qb->andWhere('c.titre LIKE :q OR c.description LIKE :q OR c.tags LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }

        $qb->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $items = $qb->getQuery()->getResult();
        $countQb = $this->createQueryBuilder('c')
            ->select('COUNT(DISTINCT c.id)');
        if ($ownerId !== null) {
            $countQb->andWhere('c.auteur = :owner')->setParameter('owner', $ownerId);
        }
        if ($statuses !== null) {
            $countQb->andWhere('c.statut IN (:statuses)')->setParameter('statuses', $statuses);
        }
        if ($type) {
            $countQb->andWhere('c.type = :type')->setParameter('type', $type);
        }
        if ($category) {
            $countQb->andWhere('c.categorie = :categorie')->setParameter('categorie', $category);
        }
        if ($search) {
            $countQb->andWhere('c.titre LIKE :q OR c.description LIKE :q OR c.tags LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }
        $total = (int) $countQb->getQuery()->getSingleScalarResult();

        return [
            'items' => $items,
            'total' => $total,
            'pages' => (int) ceil($total / $limit),
        ];
    }

    public function countValidated(
        ?string $type = null,
        ?string $search = null,
        ?string $category = null,
        array $statuses = ['valide', 'publie']
    ): int {
        $qb = $this->createValidatedQueryBuilder($type, $search, $category, $statuses)
            ->select('COUNT(DISTINCT c.id)');

        return (int) $qb->getQuery()->getSingleScalarResult();
    }

    /**
     * @return Contenu[]
     */
    public function findPending(int $limit = 50): array
    {
        return $this->createQueryBuilder('c')
            ->leftJoin('c.auteur', 'a')
            ->addSelect('a')
            ->andWhere('c.statut = :s')
            ->setParameter('s', 'en_attente')
            ->orderBy('c.createdAt', 'ASC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    public function countPending(): int
    {
        return (int) $this->createQueryBuilder('c')
            ->select('COUNT(c.id)')
            ->andWhere('c.statut = :s')
            ->setParameter('s', 'en_attente')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Save Contenu entity
     */
    public function save(Contenu $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete Contenu entity
     */
    public function delete(Contenu $entity, bool $flush = true): void
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
