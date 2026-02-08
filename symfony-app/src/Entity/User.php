<?php

namespace App\Entity;

use App\Repository\UserRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Security\Core\User\UserInterface;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Bridge\Doctrine\Validator\Constraints\UniqueEntity;

#[ORM\Entity(repositoryClass: UserRepository::class)]
#[ORM\Table(name: 'users')]
#[UniqueEntity(fields: ['username'], message: "Ce nom d'utilisateur est déjà utilisé.")]
#[UniqueEntity(fields: ['email'], message: "Cet email est déjà utilisé.")]
class User implements UserInterface, PasswordAuthenticatedUserInterface
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 180, unique: true)]
    #[Assert\NotBlank(message: "Nom d'utilisateur obligatoire")]
    #[Assert\Length(min: 3)]
    private string $username;

    #[ORM\Column(length: 255, unique: true)]
    #[Assert\NotBlank(message: "Email obligatoire")]
    #[Assert\Email(message: "Email invalide")]
    private string $email;

    #[ORM\Column(length: 255)]
    private string $password;

    // Mot de passe NON persisté
    private ?string $plainPassword = null;

    #[ORM\Column(length: 100)]
    #[Assert\NotBlank]
    private string $nom;

    #[ORM\Column(length: 100)]
    #[Assert\NotBlank]
    private string $prenom;

    #[ORM\Column(type: 'date')]
    #[Assert\NotNull]
    #[Assert\LessThan("today")]
    private \DateTimeInterface $dateNaissance;

    #[ORM\Column(length: 255, nullable: true)]
    private ?string $adresse = null;

    #[ORM\Column(length: 20, nullable: true)]
    #[Assert\Regex("/^[0-9+\s]+$/")]
    private ?string $telephone = null;

    #[ORM\Column(length: 50)]
    private string $role = 'ROLE_USER';

    #[ORM\Column(length: 20, options: ['default' => 'SKIPPED'])]
    private string $subscriptionStatus = 'SKIPPED';

    #[ORM\Column(length: 50, nullable: true)]
    private ?string $subscriptionType = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $subscriptionEndAt = null;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $emailVerified = false;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $adminApproved = false;

    #[ORM\Column(length: 255, nullable: true)]
    private ?string $emailVerificationToken = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $emailVerificationExpiresAt = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $deletedAt = null;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $isBanned = false;

    #[ORM\Column(length: 255, nullable: true)]
    private ?string $banReason = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $banUntil = null;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $mfaEnabled = false;

    #[ORM\Column(length: 255, nullable: true)]
    private ?string $googleAuthenticatorSecret = null;

    #[ORM\Column(length: 20, options: ['default' => 'light'])]
    private string $themePreference = 'light';

    #[ORM\Column(length: 5, options: ['default' => 'fr'])]
    private string $locale = 'fr';

    #[ORM\Column(length: 255, nullable: true)]
    private ?string $avatarMime = null;

    #[ORM\Column(type: 'blob', nullable: true)]
    private $avatarData = null;

    #[ORM\Column]
    private \DateTimeImmutable $createdAt;

    #[ORM\Column]
    private \DateTimeImmutable $updatedAt;

    #[ORM\OneToOne(mappedBy: 'user', targetEntity: Patient::class, cascade: ['persist', 'remove'])]
    private ?Patient $patient = null;

    #[ORM\OneToOne(mappedBy: 'user', targetEntity: Medecin::class, cascade: ['persist', 'remove'])]
    private ?Medecin $medecin = null;

    #[ORM\OneToOne(mappedBy: 'user', targetEntity: Pharmacien::class, cascade: ['persist', 'remove'])]
    private ?Pharmacien $pharmacien = null;

    #[ORM\OneToOne(mappedBy: 'user', targetEntity: CoachSportif::class, cascade: ['persist', 'remove'])]
    private ?CoachSportif $coachSportif = null;

    #[ORM\OneToOne(mappedBy: 'user', targetEntity: Nutritionniste::class, cascade: ['persist', 'remove'])]
    private ?Nutritionniste $nutritionniste = null;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }

    public function getUsername(): string { return $this->username; }
    public function setUsername(string $username): self { $this->username = $username; return $this; }

    public function getEmail(): string { return $this->email; }
    public function setEmail(string $email): self { $this->email = $email; return $this; }

    public function getPassword(): string { return $this->password; }
    public function setPassword(string $password): self { $this->password = $password; return $this; }

    public function getPlainPassword(): ?string { return $this->plainPassword; }
    public function setPlainPassword(?string $plainPassword): self { $this->plainPassword = $plainPassword; return $this; }

    public function getNom(): string { return $this->nom; }
    public function setNom(string $nom): self { $this->nom = $nom; return $this; }

    public function getPrenom(): string { return $this->prenom; }
    public function setPrenom(string $prenom): self { $this->prenom = $prenom; return $this; }

    public function getDateNaissance(): \DateTimeInterface { return $this->dateNaissance; }
    public function setDateNaissance(\DateTimeInterface $d): self { $this->dateNaissance = $d; return $this; }

    public function getAdresse(): ?string { return $this->adresse; }
    public function setAdresse(?string $a): self { $this->adresse = $a; return $this; }

    public function getTelephone(): ?string { return $this->telephone; }
    public function setTelephone(?string $t): self { $this->telephone = $t; return $this; }

    public function getRole(): string { return $this->role; }
    public function setRole(string $role): self { $this->role = $role; return $this; }

    public function getRoles(): array { return [$this->role]; }
    public function getUserIdentifier(): string { return $this->email; }
    public function eraseCredentials(): void {}

    public function getCreatedAt(): \DateTimeImmutable { return $this->createdAt; }
    public function setCreatedAt(\DateTimeImmutable $createdAt): self { $this->createdAt = $createdAt; return $this; }
    public function getUpdatedAt(): \DateTimeImmutable { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeImmutable $updatedAt): self { $this->updatedAt = $updatedAt; return $this; }

    public function getSubscriptionStatus(): string { return $this->subscriptionStatus; }
    public function setSubscriptionStatus(string $status): self { $this->subscriptionStatus = $status; return $this; }

    public function getSubscriptionType(): ?string { return $this->subscriptionType; }
    public function setSubscriptionType(?string $type): self { $this->subscriptionType = $type; return $this; }

    public function getSubscriptionEndAt(): ?\DateTimeImmutable { return $this->subscriptionEndAt; }
    public function setSubscriptionEndAt(?\DateTimeImmutable $endAt): self { $this->subscriptionEndAt = $endAt; return $this; }

    public function isSubscriptionActive(): bool
    {
        if ($this->subscriptionStatus !== 'ACTIVE') {
            return false;
        }

        if ($this->subscriptionEndAt === null) {
            return true;
        }

        return $this->subscriptionEndAt > new \DateTimeImmutable();
    }

    public function isEmailVerified(): bool { return $this->emailVerified; }
    public function setEmailVerified(bool $emailVerified): self { $this->emailVerified = $emailVerified; return $this; }

    public function isAdminApproved(): bool { return $this->adminApproved; }
    public function setAdminApproved(bool $adminApproved): self { $this->adminApproved = $adminApproved; return $this; }

    public function getEmailVerificationToken(): ?string { return $this->emailVerificationToken; }
    public function setEmailVerificationToken(?string $token): self { $this->emailVerificationToken = $token; return $this; }

    public function getEmailVerificationExpiresAt(): ?\DateTimeImmutable { return $this->emailVerificationExpiresAt; }
    public function setEmailVerificationExpiresAt(?\DateTimeImmutable $expiresAt): self { $this->emailVerificationExpiresAt = $expiresAt; return $this; }

    public function getDeletedAt(): ?\DateTimeImmutable { return $this->deletedAt; }
    public function setDeletedAt(?\DateTimeImmutable $deletedAt): self { $this->deletedAt = $deletedAt; return $this; }
    public function isDeleted(): bool { return $this->deletedAt !== null; }

    public function isBanned(): bool { return $this->isBanned; }
    public function setIsBanned(bool $isBanned): self { $this->isBanned = $isBanned; return $this; }
    public function getBanReason(): ?string { return $this->banReason; }
    public function setBanReason(?string $banReason): self { $this->banReason = $banReason; return $this; }
    public function getBanUntil(): ?\DateTimeImmutable { return $this->banUntil; }
    public function setBanUntil(?\DateTimeImmutable $banUntil): self { $this->banUntil = $banUntil; return $this; }

    public function isBannedEffective(): bool
    {
        if (!$this->isBanned) {
            return false;
        }

        if ($this->banUntil === null) {
            return true;
        }

        return $this->banUntil > new \DateTimeImmutable();
    }

    public function unbanIfExpired(): bool
    {
        if (!$this->isBanned || $this->banUntil === null) {
            return false;
        }

        if ($this->banUntil <= new \DateTimeImmutable()) {
            $this->isBanned = false;
            $this->banReason = null;
            $this->banUntil = null;
            return true;
        }

        return false;
    }

    public function isMfaEnabled(): bool { return $this->mfaEnabled; }
    public function setMfaEnabled(bool $enabled): self { $this->mfaEnabled = $enabled; return $this; }
    public function getGoogleAuthenticatorSecret(): ?string { return $this->googleAuthenticatorSecret; }
    public function setGoogleAuthenticatorSecret(?string $secret): self { $this->googleAuthenticatorSecret = $secret; return $this; }

    public function getThemePreference(): string { return $this->themePreference; }
    public function setThemePreference(string $theme): self { $this->themePreference = $theme; return $this; }

    public function getLocale(): string { return $this->locale; }
    public function setLocale(string $locale): self { $this->locale = $locale; return $this; }

    public function getAvatarMime(): ?string { return $this->avatarMime; }
    public function setAvatarMime(?string $mime): self { $this->avatarMime = $mime; return $this; }

    public function getAvatarData()
    {
        return $this->avatarData;
    }

    public function setAvatarData($data): self
    {
        $this->avatarData = $data;
        return $this;
    }

    public function getAvatarDataUri(): ?string
    {
        if (!$this->avatarData || !$this->avatarMime) {
            return null;
        }

        $data = $this->avatarData;
        if (is_resource($data)) {
            $data = stream_get_contents($data);
        }

        if (!is_string($data) || $data === '') {
            return null;
        }

        return 'data:' . $this->avatarMime . ';base64,' . base64_encode($data);
    }

    public function getPatient(): ?Patient { return $this->patient; }
    public function setPatient(?Patient $patient): self { $this->patient = $patient; return $this; }

    public function getMedecin(): ?Medecin { return $this->medecin; }
    public function setMedecin(?Medecin $medecin): self { $this->medecin = $medecin; return $this; }

    public function getPharmacien(): ?Pharmacien { return $this->pharmacien; }
    public function setPharmacien(?Pharmacien $pharmacien): self { $this->pharmacien = $pharmacien; return $this; }

    public function getCoachSportif(): ?CoachSportif { return $this->coachSportif; }
    public function setCoachSportif(?CoachSportif $coachSportif): self { $this->coachSportif = $coachSportif; return $this; }

    public function getNutritionniste(): ?Nutritionniste { return $this->nutritionniste; }
    public function setNutritionniste(?Nutritionniste $nutritionniste): self { $this->nutritionniste = $nutritionniste; return $this; }
}
