<?php

namespace App\Entity;

use App\Repository\SuspiciousLoginRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: SuspiciousLoginRepository::class)]
#[ORM\Table(name: 'suspicious_logins')]
class SuspiciousLogin
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\Column(type: 'string', length: 64, nullable: true)]
    private ?string $ipAddress = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $userAgent = null;

    #[ORM\Column(type: 'string', length: 64, nullable: true)]
    private ?string $country = null;

    #[ORM\Column(type: 'string', length: 100)]
    private string $reason;

    #[ORM\Column(type: 'boolean', options: ['default' => true])]
    private bool $blocked = true;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $notified = false;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }

    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }

    public function getIpAddress(): ?string { return $this->ipAddress; }
    public function setIpAddress(?string $ipAddress): void { $this->ipAddress = $ipAddress; }

    public function getUserAgent(): ?string { return $this->userAgent; }
    public function setUserAgent(?string $userAgent): void { $this->userAgent = $userAgent; }

    public function getCountry(): ?string { return $this->country; }
    public function setCountry(?string $country): void { $this->country = $country; }

    public function getReason(): string { return $this->reason; }
    public function setReason(string $reason): void { $this->reason = $reason; }

    public function isBlocked(): bool { return $this->blocked; }
    public function setBlocked(bool $blocked): void { $this->blocked = $blocked; }

    public function isNotified(): bool { return $this->notified; }
    public function setNotified(bool $notified): void { $this->notified = $notified; }

    public function getCreatedAt(): \DateTimeImmutable { return $this->createdAt; }
    public function setCreatedAt(\DateTimeImmutable $createdAt): void { $this->createdAt = $createdAt; }
}
