<?php

namespace App\Entity;

use App\Repository\DocumentAccessRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: DocumentAccessRepository::class)]
#[ORM\Table(name: 'document_accesses')]
class DocumentAccess
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: SharedDocument::class, inversedBy: 'accesses')]
    #[ORM\JoinColumn(name: 'document_id', nullable: false, onDelete: 'CASCADE')]
    private SharedDocument $document;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'shared_with_id', nullable: false, onDelete: 'CASCADE')]
    private User $sharedWith;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE)]
    private \DateTimeImmutable $sharedAt;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE, nullable: true)]
    private ?\DateTimeImmutable $expiresAt = null;

    #[ORM\Column(type: Types::DATETIMETZ_IMMUTABLE, nullable: true)]
    private ?\DateTimeImmutable $accessedAt = null;

    #[ORM\Column(type: 'integer', options: ['default' => 0])]
    private int $accessCount = 0;

    #[ORM\Column(type: 'boolean', options: ['default' => 1])]
    private bool $isActive = true;

    #[ORM\Column(type: 'string', length: 50, options: ['default' => 'view'])]
    private string $permission = 'view'; // view, download, etc.

    public function __construct(SharedDocument $document, User $sharedWith)
    {
        $this->document = $document;
        $this->sharedWith = $sharedWith;
        $this->sharedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getDocument(): SharedDocument
    {
        return $this->document;
    }

    public function setDocument(SharedDocument $document): self
    {
        $this->document = $document;
        return $this;
    }

    public function getSharedWith(): User
    {
        return $this->sharedWith;
    }

    public function setSharedWith(User $sharedWith): self
    {
        $this->sharedWith = $sharedWith;
        return $this;
    }

    public function getSharedAt(): \DateTimeImmutable
    {
        return $this->sharedAt;
    }

    public function getExpiresAt(): ?\DateTimeImmutable
    {
        return $this->expiresAt;
    }

    public function expireAt(?\DateTimeImmutable $expiresAt): self
    {
        $this->expiresAt = $expiresAt;
        return $this;
    }

    public function getAccessedAt(): ?\DateTimeImmutable
    {
        return $this->accessedAt;
    }

    public function markAccessedAt(?\DateTimeImmutable $accessedAt): self
    {
        $this->accessedAt = $accessedAt;
        return $this;
    }

    public function getAccessCount(): int
    {
        return $this->accessCount;
    }

    public function setAccessCount(int $accessCount): self
    {
        $this->accessCount = $accessCount;
        return $this;
    }

    public function incrementAccessCount(): self
    {
        $this->accessCount++;
        $this->accessedAt = new \DateTimeImmutable();
        return $this;
    }

    public function isActive(): bool
    {
        return $this->isActive;
    }

    public function setActive(bool $isActive): self
    {
        $this->isActive = $isActive;
        return $this;
    }

    public function getPermission(): string
    {
        return $this->permission;
    }

    public function setPermission(string $permission): self
    {
        $this->permission = $permission;
        return $this;
    }

    public function isExpired(): bool
    {
        if ($this->expiresAt === null) {
            return false;
        }

        return $this->expiresAt < new \DateTimeImmutable();
    }

    public function canAccess(): bool
    {
        return $this->isActive && !$this->isExpired();
    }
}
