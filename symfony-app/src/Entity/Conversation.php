<?php

namespace App\Entity;

use App\Repository\ConversationRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: ConversationRepository::class)]
#[ORM\Table(name: 'conversations')]
#[ORM\Index(name: 'idx_conversation_user_one_last', columns: ['user_one_id', 'last_message_at'])]
#[ORM\Index(name: 'idx_conversation_user_two_last', columns: ['user_two_id', 'last_message_at'])]
class Conversation
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'user_one_id', nullable: false, onDelete: 'CASCADE')]
    private User $userOne;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'user_two_id', nullable: false, onDelete: 'CASCADE')]
    private User $userTwo;

    #[ORM\OneToMany(mappedBy: 'conversation', targetEntity: Message::class, cascade: ['persist'], orphanRemoval: true)]
    private Collection $messages;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    private \DateTimeImmutable $createdAt;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    private \DateTimeImmutable $lastMessageAt;

    public function __construct(User $userOne, User $userTwo)
    {
        $this->userOne = $userOne;
        $this->userTwo = $userTwo;
        $this->createdAt = new \DateTimeImmutable();
        $this->lastMessageAt = new \DateTimeImmutable();
        $this->messages = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getUserOne(): User
    {
        return $this->userOne;
    }

    public function setUserOne(User $userOne): self
    {
        $this->userOne = $userOne;
        return $this;
    }

    public function getUserTwo(): User
    {
        return $this->userTwo;
    }

    public function setUserTwo(User $userTwo): self
    {
        $this->userTwo = $userTwo;
        return $this;
    }

    public function getMessages(): Collection
    {
        return $this->messages;
    }

    public function addMessage(Message $message): self
    {
        if (!$this->messages->contains($message)) {
            $this->messages->add($message);
            $message->setConversation($this);
        }
        return $this;
    }

    public function removeMessage(Message $message): self
    {
        if ($this->messages->removeElement($message)) {
        }
        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    public function getLastMessageAt(): \DateTimeImmutable
    {
        return $this->lastMessageAt;
    }

    public function touchLastMessageAt(\DateTimeImmutable $lastMessageAt): self
    {
        $this->lastMessageAt = $lastMessageAt;
        return $this;
    }

    /**
     * Get the other user in the conversation
     */
    public function getOtherUser(User $user): User
    {
        return $user === $this->userOne ? $this->userTwo : $this->userOne;
    }

    /**
     * Check if a user is part of this conversation
     */
    public function hasUser(User $user): bool
    {
        return $user === $this->userOne || $user === $this->userTwo;
    }
}
