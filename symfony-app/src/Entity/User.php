<?php

namespace App\Entity;

use App\Repository\UserRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\UserInterface;

#[ORM\Entity(repositoryClass: UserRepository::class)]
#[ORM\Table(name: 'users')]
class User implements UserInterface, PasswordAuthenticatedUserInterface
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 180, unique: true)]
    private string $username;

    #[ORM\Column(type: 'string', length: 255, unique: true)]
    private string $email;

    #[ORM\Column(type: 'string', length: 255)]
    private string $password;

    #[ORM\Column(type: 'string', length: 100)]
    private string $nom;

    #[ORM\Column(type: 'string', length: 100)]
    private string $prenom;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $dateNaissance;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $adresse = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    private ?string $telephone = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $role;

    #[ORM\Column(type: 'datetime_immutable', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime_immutable', options: ['default' => 'CURRENT_TIMESTAMP'])]
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

    #[ORM\OneToMany(mappedBy: 'auteur', targetEntity: Contenu::class)]
    private Collection $contenus;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: Like::class, cascade: ['persist', 'remove'])]
    private Collection $likes;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: Commentaire::class, cascade: ['persist', 'remove'])]
    private Collection $commentaires;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: ReponseMedicament::class)]
    private Collection $reponsesMedicaments;

    #[ORM\OneToMany(mappedBy: 'user', targetEntity: Notification::class, cascade: ['persist', 'remove'])]
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

    public function getDateNaissance(): \DateTimeInterface { return $this->dateNaissance; }
    public function setDateNaissance(\DateTimeInterface $dateNaissance): void { $this->dateNaissance = $dateNaissance; }

    public function getAdresse(): ?string { return $this->adresse; }
    public function setAdresse(?string $adresse): void { $this->adresse = $adresse; }

    public function getTelephone(): ?string { return $this->telephone; }
    public function setTelephone(?string $telephone): void { $this->telephone = $telephone; }

    public function getRole(): string { return $this->role; }
    public function setRole(string $role): void { $this->role = $role; }

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

    // UserInterface
    public function getRoles(): array { return [$this->role]; }
    public function eraseCredentials(): void {}
    public function getUserIdentifier(): string { return $this->email; }
}