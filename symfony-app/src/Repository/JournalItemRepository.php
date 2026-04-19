<?php

namespace App\Repository;

use App\Entity\JournalItem;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<JournalItem>
 */
class JournalItemRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, JournalItem::class);
    }

    /**
     * Find all JournalItem entities ordered by ID desc
     * @return JournalItem[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find JournalItem by ID
     */
    public function findById(int $id): ?JournalItem
    {
        return $this->find($id);
    }

    /**
     * Save JournalItem entity
     */
    public function save(JournalItem $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete JournalItem entity
     */
    public function delete(JournalItem $entity, bool $flush = true): void
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
