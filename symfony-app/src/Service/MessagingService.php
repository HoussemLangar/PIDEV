<?php

namespace App\Service;

use App\Entity\Conversation;
use App\Entity\Message;
use App\Entity\User;
use App\Repository\ConversationRepository;
use App\Repository\MessageRepository;
use Doctrine\ORM\EntityManagerInterface;

class MessagingService
{
    public function __construct(
        private ConversationRepository $conversationRepository,
        private MessageRepository $messageRepository,
        private EntityManagerInterface $em,
    ) {
    }

    /**
     * Send a message between two users
     * 
     * @throws \InvalidArgumentException if sender and recipient are the same
     */
    public function sendMessage(User $sender, User $recipient, string $content): Message
    {
        if ($sender === $recipient) {
            throw new \InvalidArgumentException('Un utilisateur ne peut pas s\'envoyer de messages.');
        }

        if (empty(trim($content))) {
            throw new \InvalidArgumentException('Le contenu du message ne peut pas être vide.');
        }

        $conversation = $this->conversationRepository->findOrCreateConversation($sender, $recipient);
        $message = new Message($conversation, $sender, $recipient, $content);

        $this->em->persist($message);
        $conversation->setLastMessageAt(new \DateTimeImmutable());
        $this->em->flush();

        return $message;
    }

    /**
     * Check if a user has access to a conversation
     */
    public function hasConversationAccess(User $user, Conversation $conversation): bool
    {
        return $conversation->hasUser($user);
    }

    /**
     * Get the other user in a conversation
     */
    public function getOtherUser(User $user, Conversation $conversation): ?User
    {
        if (!$this->hasConversationAccess($user, $conversation)) {
            return null;
        }

        return $conversation->getOtherUser($user);
    }

    /**
     * Mark all messages as read in a conversation
     */
    public function markConversationAsRead(User $user, Conversation $conversation): void
    {
        if (!$this->hasConversationAccess($user, $conversation)) {
            throw new \InvalidArgumentException('L\'utilisateur n\'a pas accès à cette conversation.');
        }

        $this->messageRepository->markAllAsRead($user, $conversation);
        $this->em->flush();
    }

    /**
     * Get unread message count for a user
     */
    public function getUnreadCount(User $user): int
    {
        return $this->conversationRepository->getUnreadCount($user);
    }

    /**
     * Check if a message can be deleted by a user
     */
    public function canDeleteMessage(User $user, Message $message): bool
    {
        // Only the sender can delete their messages
        return $message->getSender() === $user;
    }

    /**
     * Delete a message
     */
    public function deleteMessage(User $user, Message $message): void
    {
        if (!$this->canDeleteMessage($user, $message)) {
            throw new \InvalidArgumentException('Vous ne pouvez pas supprimer ce message.');
        }

        $this->em->remove($message);
        $this->em->flush();
    }
}
