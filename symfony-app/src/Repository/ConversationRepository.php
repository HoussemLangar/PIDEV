<?php

namespace App\Repository;

use App\Entity\Conversation;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Conversation>
 */
class ConversationRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Conversation::class);
    }

    /**
     * Find or create a conversation between two users
     */
    public function findOrCreateConversation(User $userOne, User $userTwo): Conversation
    {
        // Ensure consistent ordering
        if ($userOne->getId() > $userTwo->getId()) {
            [$userOne, $userTwo] = [$userTwo, $userOne];
        }

        $conversation = $this->findOneBy([
            'userOne' => $userOne,
            'userTwo' => $userTwo,
        ]);

        if (!$conversation) {
            $conversation = new Conversation($userOne, $userTwo);
            $this->getEntityManager()->persist($conversation);
            $this->getEntityManager()->flush();
        }

        return $conversation;
    }

    /**
     * Get all conversations for a user, ordered by most recent
     */
    public function findUserConversations(User $user, int $limit = 50): array
    {
        return $this->createQueryBuilder('c')
            ->where('c.userOne = :user OR c.userTwo = :user')
            ->setParameter('user', $user)
            ->orderBy('c.lastMessageAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Optimized summary list for API usage (scalar hydration, no entity graph).
     *
     * @return array<int, array{id:int, otherUserId:int, otherPrenom:string, otherNom:string, lastMessageAt:\DateTimeInterface}>
     */
    public function findUserConversationSummaries(User $user, int $limit = 50): array
    {
        return $this->createQueryBuilder('c')
            ->select(
                'c.id AS id',
                'c.lastMessageAt AS lastMessageAt',
                'CASE WHEN u1.id = :userId THEN u2.id ELSE u1.id END AS otherUserId',
                'CASE WHEN u1.id = :userId THEN u2.prenom ELSE u1.prenom END AS otherPrenom',
                'CASE WHEN u1.id = :userId THEN u2.nom ELSE u1.nom END AS otherNom'
            )
            ->innerJoin('c.userOne', 'u1')
            ->innerJoin('c.userTwo', 'u2')
            ->where('u1 = :user OR u2 = :user')
            ->setParameter('user', $user)
            ->setParameter('userId', $user->getId())
            ->orderBy('c.lastMessageAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getArrayResult();
    }

    /**
     * Get unread message count for a user in a conversation
     */
    public function getUnreadCount(User $user): int
    {
        return $this->createQueryBuilder('c')
            ->select('COUNT(m.id)')
            ->leftJoin('c.messages', 'm')
            ->where('(c.userOne = :user OR c.userTwo = :user)')
            ->andWhere('m.recipient = :user')
            ->andWhere('m.isRead = false')
            ->setParameter('user', $user)
            ->getQuery()
            ->getSingleScalarResult();
    }
}
