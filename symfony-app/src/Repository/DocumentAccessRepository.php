<?php

namespace App\Repository;

use App\Entity\DocumentAccess;
use App\Entity\SharedDocument;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<DocumentAccess>
 */
class DocumentAccessRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, DocumentAccess::class);
    }

    /**
     * Get access history for a document
     */
    public function findAccessHistory(SharedDocument $document, int $limit = 100): array
    {
        return $this->createQueryBuilder('a')
            ->where('a.document = :document')
            ->setParameter('document', $document)
            ->orderBy('a.sharedAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Find active accesses for a document
     */
    public function findActiveAccesses(SharedDocument $document): array
    {
        return $this->createQueryBuilder('a')
            ->where('a.document = :document')
            ->andWhere('a.isActive = true')
            ->andWhere('(a.expiresAt IS NULL OR a.expiresAt > :now)')
            ->setParameter('document', $document)
            ->setParameter('now', new \DateTimeImmutable())
            ->getQuery()
            ->getResult();
    }

    /**
     * Check if a user has access to a document
     */
    public function hasAccess(SharedDocument $document, User $user): bool
    {
        if ($document->getOwner() === $user || $document->isPublic()) {
            return true;
        }

        $access = $this->findOneBy([
            'document' => $document,
            'sharedWith' => $user,
            'isActive' => true,
        ]);

        if ($access && !$access->isExpired()) {
            return true;
        }

        return false;
    }

    /**
     * Get access statistics for a document
     */
    public function getAccessStats(SharedDocument $document): array
    {
        $result = $this->createQueryBuilder('a')
            ->select('COUNT(a.id) as total, SUM(a.accessCount) as totalAccesses')
            ->where('a.document = :document')
            ->setParameter('document', $document)
            ->getQuery()
            ->getSingleResult();

        return [
            'sharedWith' => (int)$result['total'],
            'totalAccesses' => (int)($result['totalAccesses'] ?? 0),
        ];
    }

    /**
     * Get users who recently accessed a document
     */
    public function getRecentAccessors(SharedDocument $document, int $days = 7): array
    {
        $date = (new \DateTimeImmutable())->modify("-{$days} days");

        return $this->createQueryBuilder('a')
            ->select('a')
            ->where('a.document = :document')
            ->andWhere('a.accessedAt IS NOT NULL')
            ->andWhere('a.accessedAt >= :date')
            ->setParameter('document', $document)
            ->setParameter('date', $date)
            ->orderBy('a.accessedAt', 'DESC')
            ->getQuery()
            ->getResult();
    }
}
