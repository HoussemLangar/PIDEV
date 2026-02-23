<?php

namespace App\Entity;

use Scheb\TwoFactorBundle\Model\Google\TwoFactorInterface;
use App\Repository\UserRepository;
use App\Entity\GoogleFitAccount;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\UserInterface;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Bridge\Doctrine\Validator\Constraints\UniqueEntity;

#[ORM\Entity(repositoryClass: UserRepository::class)]
#[ORM\Table(name: 'users')]
#[UniqueEntity(
    fields: ['username'],
    message: 'Ce nom d\'utilisateur est déjà utilisé. Veuillez en choisir un autre.'
)]
#[UniqueEntity(
    fields: ['email'],
    message: 'Cette adresse email est déjà associée à un compte existant.'
)]
class User implements UserInterface, PasswordAuthenticatedUserInterface, TwoFactorInterface
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 180, unique: true)]
    #[Assert\NotBlank(message: 'Le nom d\'utilisateur est obligatoire.')]
    #[Assert\Length(
        min: 3,
        max: 180,
        minMessage: 'Le nom d\'utilisateur doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le nom d\'utilisateur ne peut pas dépasser {{ limit }} caractères.'
    )]
    #[Assert\Regex(
        pattern: '/^[a-zA-Z0-9_.-]+$/',
        message: 'Le nom d\'utilisateur ne peut contenir que des lettres, chiffres, tirets, underscores et points.'
    )]
    private string $username;

    #[ORM\Column(type: 'string', length: 255, unique: true)]
    #[Assert\NotBlank(message: 'L\'adresse email est obligatoire.')]
    #[Assert\Email(
        message: 'L\'adresse email "{{ value }}" n\'est pas valide.'
    )]
    #[Assert\Length(
        max: 255,
        maxMessage: 'L\'adresse email ne peut pas dépasser {{ limit }} caractères.'
    )]
    private string $email;

    #[ORM\Column(type: 'string', length: 255)]
    private string $password;

    #[ORM\Column(type: 'string', length: 100)]
    #[Assert\NotBlank(message: 'Le nom est obligatoire.')]
    #[Assert\Length(
        min: 2,
        max: 100,
        minMessage: 'Le nom doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le nom ne peut pas dépasser {{ limit }} caractères.'
    )]
    #[Assert\Regex(
        pattern: '/^[a-zA-ZÀ-ÿ\s\'-]+$/u',
        message: 'Le nom ne peut contenir que des lettres, espaces, apostrophes et tirets.'
    )]
    private string $nom;

    #[ORM\Column(type: 'string', length: 100)]
    #[Assert\NotBlank(message: 'Le prénom est obligatoire.')]
    #[Assert\Length(
        min: 2,
        max: 100,
        minMessage: 'Le prénom doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le prénom ne peut pas dépasser {{ limit }} caractères.'
    )]
    #[Assert\Regex(
        pattern: '/^[a-zA-ZÀ-ÿ\s\'-]+$/u',
        message: 'Le prénom ne peut contenir que des lettres, espaces, apostrophes et tirets.'
    )]
    private string $prenom;

    #[ORM\Column(type: 'date', nullable: true)]
    #[Assert\NotBlank(message: 'La date de naissance est obligatoire.')]
    #[Assert\Type(
        type: \DateTimeInterface::class,
        message: 'La date de naissance doit être une date valide.'
    )]
    #[Assert\LessThan(
        value: 'today',
        message: 'La date de naissance doit être antérieure à aujourd\'hui.'
    )]
    #[Assert\GreaterThan(
        value: '-120 years',
        message: 'La date de naissance ne peut pas être antérieure à 120 ans.'
    )]
    #[Assert\Expression(
        expression: 'this.getDateNaissance() === null or this.getAge() >= 13',
        message: 'Vous devez avoir au moins 13 ans pour vous inscrire.'
    )]
    private ?\DateTimeInterface $dateNaissance = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    #[Assert\Length(
        max: 255,
        maxMessage: 'L\'adresse ne peut pas dépasser {{ limit }} caractères.'
    )]
    private ?string $adresse = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    #[Assert\Length(
        min: 8,
        max: 20,
        minMessage: 'Le numéro de téléphone doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le numéro de téléphone ne peut pas dépasser {{ limit }} caractères.'
    )]
    #[Assert\Regex(
        pattern: '/^[\d\s\+\-\(\)]+$/',
        message: 'Le numéro de téléphone n\'est pas valide. Utilisez uniquement des chiffres, espaces et symboles (+, -, parenthèses).'
    )]
    private ?string $telephone = null;

    #[ORM\Column(type: 'string', length: 50)]
    #[Assert\NotBlank(message: 'Le rôle est obligatoire.')]
    #[Assert\Choice(
        choices: ['ROLE_USER', 'ROLE_PATIENT', 'ROLE_MEDECIN', 'ROLE_PHARMACIEN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE', 'ROLE_ADMIN'],
        message: 'Le rôle "{{ value }}" n\'est pas valide. Rôles autorisés: {{ choices }}.'
    )]
    private string $role = 'ROLE_USER';

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $emailVerified = false;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $adminApproved = false;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $mfaEnabled = false;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $googleAuthenticatorSecret = null;

    #[ORM\Column(type: 'string', length: 10, options: ['default' => 'light'])]
    private string $themePreference = 'light';

    #[ORM\Column(type: 'string', length: 5, options: ['default' => 'fr'])]
    private string $locale = 'fr';

    #[ORM\Column(type: 'blob', nullable: true)]
    private $avatarData = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $avatarMime = null;

    #[ORM\Column(type: 'boolean', options: ['default' => true])]
    private bool $reminderEnabled = true;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $emailVerificationToken = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $emailVerificationExpiresAt = null;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'PENDING'])]
    private string $subscriptionStatus = 'PENDING';

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $subscriptionType = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $subscriptionEndAt = null;

    #[ORM\Column(type: 'boolean', options: ['default' => false])]
    private bool $isBanned = false;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $banReason = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $banUntil = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $deletedAt = null;

    #[ORM\Column(type: 'datetime_immutable', options: ['default' => 'CURRENT_TIMESTAMP'])]
    #[Assert\NotBlank(message: 'La date de création est obligatoire.')]
    #[Assert\Type(
        type: \DateTimeImmutable::class,
        message: 'La date de création doit être une date valide.'
    )]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime_immutable', options: ['default' => 'CURRENT_TIMESTAMP'])]
    #[Assert\NotBlank(message: 'La date de mise à jour est obligatoire.')]
    #[Assert\Type(
        type: \DateTimeImmutable::class,
        message: 'La date de mise à jour doit être une date valide.'
    )]
    #[Assert\GreaterThanOrEqual(
        propertyPath: 'createdAt',
        message: 'La date de mise à jour ne peut pas être antérieure à la date de création.'
    )]
    private \DateTimeInterface $updatedAt;

    // Relations
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

    #[ORM\OneToOne(
        mappedBy: 'user',
        targetEntity: GoogleFitAccount::class,
        cascade: ['persist', 'remove'],
        fetch: 'LAZY'
    )]
    private ?GoogleFitAccount $googleFitAccount = null;

    #[ORM\OneToMany(mappedBy: 'auteur', targetEntity: Contenu::class)]
    #[Assert\Valid]
    private Collection $contenus;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: Like::class, cascade: ['persist', 'remove'])]
    #[Assert\Valid]
    private Collection $likes;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: Commentaire::class, cascade: ['persist', 'remove'])]
    #[Assert\Valid]
    private Collection $commentaires;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: ReponseMedicament::class)]
    #[Assert\Valid]
    private Collection $reponsesMedicaments;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: Notification::class, cascade: ['persist', 'remove'])]
    #[Assert\Valid]
    private Collection $notifications;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
        $this->contenus = new ArrayCollection();
        $this->likes = new ArrayCollection();
        $this->commentaires = new ArrayCollection();
        $this->reponsesMedicaments = new ArrayCollection();
        $this->notifications = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }

    public function getUsername(): string { return $this->username; }
    public function setUsername(string $username): void { $this->username = $username; }

    public function getEmail(): string { return $this->email; }
    public function setEmail(string $email): void { $this->email = $email; }

    public function getPassword(): string { return $this->password; }
    public function setPassword(string $password): void { $this->password = $password; }

    public function getNom(): string { return $this->nom; }
    public function setNom(string $nom): void { $this->nom = $nom; }

    public function getPrenom(): string { return $this->prenom; }
    public function setPrenom(string $prenom): void { $this->prenom = $prenom; }

    public function getDateNaissance(): ?\DateTimeInterface { return $this->dateNaissance; }
    public function setDateNaissance(?\DateTimeInterface $dateNaissance): void { $this->dateNaissance = $dateNaissance; }

    public function getAdresse(): ?string { return $this->adresse; }
    public function setAdresse(?string $adresse): void { $this->adresse = $adresse; }

    public function getTelephone(): ?string { return $this->telephone; }
    public function setTelephone(?string $telephone): void { $this->telephone = $telephone; }

    public function getRole(): string { return $this->role; }
    public function setRole(string $role): void { $this->role = $role; }

    public function isEmailVerified(): bool { return $this->emailVerified; }
    public function setEmailVerified(bool $emailVerified): void { $this->emailVerified = $emailVerified; }

    public function isAdminApproved(): bool { return $this->adminApproved; }
    public function setAdminApproved(bool $adminApproved): void { $this->adminApproved = $adminApproved; }

    public function isMfaEnabled(): bool { return $this->mfaEnabled; }
    public function setMfaEnabled(bool $mfaEnabled): void { $this->mfaEnabled = $mfaEnabled; }

    public function getGoogleAuthenticatorSecret(): ?string { return $this->googleAuthenticatorSecret; }
    public function setGoogleAuthenticatorSecret(?string $secret): void { $this->googleAuthenticatorSecret = $secret; }

    public function isGoogleAuthenticatorEnabled(): bool
    {
        return $this->mfaEnabled && $this->googleAuthenticatorSecret !== null;
    }

    public function getGoogleAuthenticatorUsername(): string
    {
        return $this->getEmail();
    }

    public function getThemePreference(): string { return $this->themePreference; }
    public function setThemePreference(string $themePreference): void { $this->themePreference = $themePreference; }

    public function getLocale(): string { return $this->locale; }
    public function setLocale(string $locale): void { $this->locale = $locale; }

    public function getAvatarData(): ?string
    {
        if ($this->avatarData === null) {
            return null;
        }
        if (is_resource($this->avatarData)) {
            return stream_get_contents($this->avatarData) ?: null;
        }
        return $this->avatarData;
    }

    public function setAvatarData(?string $avatarData): void { $this->avatarData = $avatarData; }

    public function getAvatarMime(): ?string { return $this->avatarMime; }
    public function setAvatarMime(?string $avatarMime): void { $this->avatarMime = $avatarMime; }

    public function getAvatarDataUri(): ?string
    {
        $data = $this->getAvatarData();
        if ($data === null || $this->avatarMime === null) {
            return null;
        }
        return 'data:' . $this->avatarMime . ';base64,' . base64_encode($data);
    }

    public function isReminderEnabled(): bool { return $this->reminderEnabled; }
    public function setReminderEnabled(bool $reminderEnabled): void { $this->reminderEnabled = $reminderEnabled; }

    public function getEmailVerificationToken(): ?string { return $this->emailVerificationToken; }
    public function setEmailVerificationToken(?string $token): void { $this->emailVerificationToken = $token; }

    public function getEmailVerificationExpiresAt(): ?\DateTimeImmutable { return $this->emailVerificationExpiresAt; }
    public function setEmailVerificationExpiresAt(?\DateTimeImmutable $expiresAt): void { $this->emailVerificationExpiresAt = $expiresAt; }

    public function getSubscriptionStatus(): string { return $this->subscriptionStatus; }
    public function setSubscriptionStatus(string $subscriptionStatus): void { $this->subscriptionStatus = $subscriptionStatus; }

    public function getSubscriptionType(): ?string { return $this->subscriptionType; }
    public function setSubscriptionType(?string $subscriptionType): void { $this->subscriptionType = $subscriptionType; }

    public function getSubscriptionEndAt(): ?\DateTimeImmutable { return $this->subscriptionEndAt; }
    public function setSubscriptionEndAt(?\DateTimeImmutable $subscriptionEndAt): void { $this->subscriptionEndAt = $subscriptionEndAt; }

    public function isSubscriptionActive(): bool
    {
        if ($this->subscriptionStatus !== 'ACTIVE') {
            return false;
        }
        if ($this->subscriptionEndAt === null) {
            return false;
        }
        return $this->subscriptionEndAt > new \DateTimeImmutable();
    }

    public function isSubscriptionExpired(): bool
    {
        if ($this->subscriptionStatus === 'ACTIVE' && $this->subscriptionEndAt === null) {
            return true;
        }
        if ($this->subscriptionEndAt === null) {
            return false;
        }
        return $this->subscriptionEndAt <= new \DateTimeImmutable();
    }

    public function isBanned(): bool { return $this->isBanned; }
    public function setIsBanned(bool $isBanned): void { $this->isBanned = $isBanned; }

    public function getBanReason(): ?string { return $this->banReason; }
    public function setBanReason(?string $banReason): void { $this->banReason = $banReason; }

    public function getBanUntil(): ?\DateTimeImmutable { return $this->banUntil; }
    public function setBanUntil(?\DateTimeImmutable $banUntil): void { $this->banUntil = $banUntil; }

    public function getDeletedAt(): ?\DateTimeImmutable { return $this->deletedAt; }
    public function setDeletedAt(?\DateTimeImmutable $deletedAt): void { $this->deletedAt = $deletedAt; }

    public function isDeleted(): bool { return $this->deletedAt !== null; }

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
        if ($this->isBanned && $this->banUntil !== null && $this->banUntil <= new \DateTimeImmutable()) {
            $this->isBanned = false;
            $this->banReason = null;
            $this->banUntil = null;
            return true;
        }
        return false;
    }

    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }

    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }

    // Relation getters/setters
    public function getPatient(): ?Patient { return $this->patient; }
    public function setPatient(?Patient $patient): void { $this->patient = $patient; }

    public function getMedecin(): ?Medecin { return $this->medecin; }
    public function setMedecin(?Medecin $medecin): void { $this->medecin = $medecin; }

    public function getPharmacien(): ?Pharmacien { return $this->pharmacien; }
    public function setPharmacien(?Pharmacien $pharmacien): void { $this->pharmacien = $pharmacien; }

    public function getCoachSportif(): ?CoachSportif { return $this->coachSportif; }
    public function setCoachSportif(?CoachSportif $coachSportif): void { $this->coachSportif = $coachSportif; }

    public function getNutritionniste(): ?Nutritionniste { return $this->nutritionniste; }
    public function setNutritionniste(?Nutritionniste $nutritionniste): void { $this->nutritionniste = $nutritionniste; }

    public function getContenus(): Collection { return $this->contenus; }
    public function getLikes(): Collection { return $this->likes; }
    public function getCommentaires(): Collection { return $this->commentaires; }
    public function getReponsesMedicaments(): Collection { return $this->reponsesMedicaments; }
    public function getNotifications(): Collection { return $this->notifications; }

    public function getGoogleFitAccount(): ?GoogleFitAccount { return $this->googleFitAccount; }
    public function setGoogleFitAccount(?GoogleFitAccount $googleFitAccount): void { $this->googleFitAccount = $googleFitAccount; }

    // UserInterface
    public function getRoles(): array { return [$this->role]; }
    public function eraseCredentials(): void {}
    public function getUserIdentifier(): string { return $this->email; }

    /**
     * Calcule l'âge de l'utilisateur à partir de sa date de naissance
     */
    public function getAge(): ?int
    {
        if ($this->dateNaissance === null) {
            return null;
        }
        
        $now = new \DateTime();
        $interval = $this->dateNaissance->diff($now);
        return $interval->y;
    }

    /**
     * Retourne le nom complet de l'utilisateur
     */
    public function getFullName(): string
    {
        return $this->prenom . ' ' . $this->nom;
    }
}
