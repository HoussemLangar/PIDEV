<?php

namespace App\Repository;

use App\Entity\SharedDocument;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SharedDocument>
 */
class SharedDocumentRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SharedDocument::class);
    }

    /**
     * Find all documents owned by a user
     */
    public function findByOwner(User $owner, int $limit = 50): array
    {
        return $this->createQueryBuilder('d')
            ->where('d.owner = :owner')
            ->setParameter('owner', $owner)
            ->orderBy('d.uploadedAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Find all documents shared with a user
     */
    public function findSharedWithUser(User $user, int $limit = 50): array
    {
        return $this->createQueryBuilder('d')
            ->where('EXISTS (
                SELECT 1 FROM App\\Entity\\DocumentAccess a
                WHERE a.document = d
                  AND a.sharedWith = :user
                  AND a.isActive = true
                  AND (a.expiresAt IS NULL OR a.expiresAt > :now)
            )')
            ->setParameter('user', $user)
            ->setParameter('now', new \DateTimeImmutable())
            ->orderBy('d.uploadedAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Find documents by type
     */
    public function findByType(User $user, string $type, int $limit = 50): array
    {
        return $this->createQueryBuilder('d')
            ->where('d.owner = :owner')
            ->andWhere('d.documentType = :type')
            ->setParameter('owner', $user)
            ->setParameter('type', $type)
            ->orderBy('d.uploadedAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Find public documents
     */
    public function findPublicDocuments(int $limit = 50): array
    {
        return $this->createQueryBuilder('d')
            ->where('d.public = true')
            ->orderBy('d.uploadedAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Get total storage used by user
     */
    public function getTotalStorageByUser(User $user): int
    {
        $result = $this->createQueryBuilder('d')
            ->select('SUM(d.fileSize) as total')
            ->where('d.owner = :owner')
            ->setParameter('owner', $user)
            ->getQuery()
            ->getSingleResult();

        return (int)($result['total'] ?? 0);
    }
}
