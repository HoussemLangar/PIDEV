<?php

namespace App\Entity;

use App\Repository\UserRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Security\Core\User\UserInterface;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: UserRepository::class)]
#[ORM\Table(name: 'users')]
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

    // 🔹 Mot de passe NON persisté
    #[Assert\NotBlank(message: "Mot de passe obligatoire")]
    #[Assert\Length(min: 8, minMessage: "Au moins 8 caractères")]
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
    public string $role = 'ROLE_USER';

    #[ORM\Column]
    private \DateTimeImmutable $createdAt;

    #[ORM\Column]
    private \DateTimeImmutable $updatedAt;

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

    public function getRoles(): array { return [$this->role]; }
    public function getUserIdentifier(): string { return $this->email; }
    public function eraseCredentials(): void {}
}
