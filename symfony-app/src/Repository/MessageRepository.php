<?php

namespace App\Repository;

use App\Entity\Conversation;
use App\Entity\Message;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Message>
 */
class MessageRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Message::class);
    }

    /**
     * Get all messages in a conversation with pagination
     */
    public function findConversationMessages(Conversation $conversation, int $offset = 0, int $limit = 50): array
    {
        return $this->createQueryBuilder('m')
            ->where('m.conversation = :conversation')
            ->setParameter('conversation', $conversation)
            ->orderBy('m.createdAt', 'DESC')
            ->setFirstResult($offset)
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Get unread messages for a user in a conversation
     */
    public function findUnreadMessages(User $recipient, Conversation $conversation): array
    {
        return $this->createQueryBuilder('m')
            ->where('m.conversation = :conversation')
            ->andWhere('m.recipient = :recipient')
            ->andWhere('m.isRead = false')
            ->setParameter('conversation', $conversation)
            ->setParameter('recipient', $recipient)
            ->orderBy('m.createdAt', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Mark all messages as read in a conversation
     */
    public function markAllAsRead(User $user, Conversation $conversation): void
    {
        $this->createQueryBuilder('m')
            ->update()
            ->set('m.isRead', true)
            ->set('m.readAt', ':now')
            ->where('m.conversation = :conversation')
            ->andWhere('m.recipient = :recipient')
            ->andWhere('m.isRead = false')
            ->setParameter('now', new \DateTimeImmutable())
            ->setParameter('conversation', $conversation)
            ->setParameter('recipient', $user)
            ->getQuery()
            ->execute();
    }

    /**
     * Get message history between two users (paginated)
     */
    public function findHistoryBetweenUsers(User $userOne, User $userTwo, int $offset = 0, int $limit = 100): array
    {
        return $this->createQueryBuilder('m')
            ->leftJoin('m.conversation', 'c')
            ->where('(m.sender = :user1 AND m.recipient = :user2) OR (m.sender = :user2 AND m.recipient = :user1)')
            ->setParameter('user1', $userOne)
            ->setParameter('user2', $userTwo)
            ->orderBy('m.createdAt', 'DESC')
            ->setFirstResult($offset)
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }
}
