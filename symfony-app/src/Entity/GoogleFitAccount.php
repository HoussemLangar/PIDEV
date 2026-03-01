<?php

namespace App\Entity;

use App\Repository\GoogleFitAccountRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Annotation\Ignore;

#[ORM\Entity(repositoryClass: GoogleFitAccountRepository::class)]
#[ORM\Table(name: 'google_fit_accounts')]
class GoogleFitAccount
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\OneToOne(inversedBy: 'googleFitAccount', targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?User $user = null;

    #[ORM\Column(type: 'string', length: 255)]
    private string $googleAccountId;

    #[ORM\Column(type: 'text')]
    #[Ignore]
    private string $accessToken;

    #[ORM\Column(type: 'text', nullable: true)]
    #[Ignore]
    private ?string $refreshToken = null;

    #[ORM\Column(type: 'datetimetz_immutable', nullable: true)]
    #[Ignore]
    private ?\DateTimeImmutable $tokenExpiration = null;

    #[ORM\Column(type: 'datetimetz_immutable', nullable: true)]
    private ?\DateTimeImmutable $lastSyncAt = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $scopes = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getUser(): ?User
    {
        return $this->user;
    }

    public function setUser(User $user): self
    {
        $this->user = $user;
        return $this;
    }

    public function getGoogleAccountId(): string
    {
        return $this->googleAccountId;
    }

    public function setGoogleAccountId(string $googleAccountId): self
    {
        $this->googleAccountId = $googleAccountId;
        return $this;
    }

    public function getAccessToken(): string
    {
        return $this->accessToken;
    }

    public function setAccessToken(string $accessToken): self
    {
        $this->accessToken = $accessToken;
        return $this;
    }

    public function getRefreshToken(): ?string
    {
        return $this->refreshToken;
    }

    public function setRefreshToken(?string $refreshToken): self
    {
        $this->refreshToken = $refreshToken;
        return $this;
    }

    public function getTokenExpiration(): ?\DateTimeImmutable
    {
        return $this->tokenExpiration;
    }

    public function updateTokenExpiration(?\DateTimeImmutable $tokenExpiration): self
    {
        $this->tokenExpiration = $tokenExpiration;
        return $this;
    }

    public function getLastSyncAt(): ?\DateTimeImmutable
    {
        return $this->lastSyncAt;
    }

    public function markLastSyncAt(?\DateTimeImmutable $lastSyncAt): self
    {
        $this->lastSyncAt = $lastSyncAt;
        return $this;
    }

    public function getScopes(): ?string
    {
        return $this->scopes;
    }

    public function setScopes(?string $scopes): self
    {
        $this->scopes = $scopes;
        return $this;
    }
}
