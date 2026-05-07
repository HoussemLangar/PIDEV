<?php

namespace App\Twig\Components;

use App\Entity\Conversation;
use App\Entity\Message;
use App\Entity\User;
use App\Repository\ConversationRepository;
use App\Repository\MessageRepository;
use App\Service\MessageRealtimePublisher;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\UX\LiveComponent\Attribute\AsLiveComponent;
use Symfony\UX\LiveComponent\Attribute\LiveAction;
use Symfony\UX\LiveComponent\Attribute\LiveProp;
use Symfony\UX\LiveComponent\ComponentToolsTrait;
use Symfony\UX\LiveComponent\DefaultActionTrait;

#[AsLiveComponent('MessageThread')]
class MessageThread
{
    use DefaultActionTrait;
    use ComponentToolsTrait;

    #[LiveProp]
    public int $conversationId;

    #[LiveProp(writable: true)]
    public string $content = '';

    public function __construct(
        private readonly ConversationRepository $conversationRepository,
        private readonly MessageRepository $messageRepository,
        private readonly MessageRealtimePublisher $publisher,
        private readonly EntityManagerInterface $em,
        private readonly Security $security,
    ) {
    }

    public function getConversation(): ?Conversation
    {
        return $this->conversationRepository->find($this->conversationId);
    }

    public function getMessages(): array
    {
        $conversation = $this->getConversation();
        if (!$conversation) {
            return [];
        }

        $messages = $this->messageRepository->findConversationMessages($conversation, 0, 100);

        return array_reverse($messages);
    }

    public function getMercureTopic(): string
    {
        return MessageRealtimePublisher::topicForConversation($this->conversationId);
    }

    #[LiveAction]
    public function send(): void
    {
        $content = trim($this->content);
        if ($content === '') {
            return;
        }

        $user = $this->security->getUser();
        if (!$user instanceof User) {
            return;
        }

        $conversation = $this->getConversation();
        if (!$conversation || !$conversation->hasUser($user)) {
            return;
        }

        $recipient = $conversation->getOtherUser($user);
        $message = new Message($conversation, $user, $recipient, $content);

        $this->em->persist($message);
        $conversation->touchLastMessageAt(new \DateTimeImmutable());
        $this->em->flush();

        $this->publisher->publishNewMessage($message);
        $this->content = '';

        $this->dispatchBrowserEvent('chat:message-sent');
    }

    #[LiveAction]
    public function refresh(): void
    {
        // No-op: used by JS listener to trigger component re-render.
    }
}

