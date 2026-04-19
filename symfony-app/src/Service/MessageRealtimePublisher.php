<?php

namespace App\Service;

use App\Entity\Message;

class MessageRealtimePublisher
{
    public function __construct(
        private readonly ?object $hub = null,
    ) {
    }

    public function publishNewMessage(Message $message): void
    {
        $conversation = $message->getConversation();
        $topic = self::topicForConversation((int) $conversation->getId());

        $payload = [
            'event' => 'message.created',
            'conversationId' => $conversation->getId(),
            'messageId' => $message->getId(),
            'senderId' => $message->getSender()->getId(),
            'recipientId' => $message->getRecipient()->getId(),
            'createdAt' => $message->getCreatedAt()->format(\DateTimeInterface::ATOM),
        ];

        try {
            if (!$this->hub || !method_exists($this->hub, 'publish')) {
                return;
            }

            $updateClass = \Symfony\Component\Mercure\Update::class;
            if (!class_exists($updateClass)) {
                return;
            }

            $this->hub->publish(new $updateClass($topic, json_encode($payload, JSON_THROW_ON_ERROR)));
        } catch (\Throwable) {
            // Do not block user messaging if the realtime hub is unavailable.
        }
    }

    public static function topicForConversation(int $conversationId): string
    {
        return sprintf('https://santea.local/messages/conversation/%d', $conversationId);
    }
}
