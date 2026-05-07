<?php

namespace App\Entity;

use App\Repository\PharmacienRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: PharmacienRepository::class)]
#[ORM\Table(name: 'pharmaciens')]
class Pharmacien
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\OneToOne(inversedBy: 'pharmacien', targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'user_id', nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\Column(type: 'string', length: 50, nullable: true, unique: true)]
    private ?string $numeroOrdre = null;

    #[ORM\Column(type: 'string', length: 150, nullable: true)]
    private ?string $pharmacieNom = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $pharmacieAdresse = null;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'pharmacien', targetEntity: Pharmacy::class)]
    private Collection $pharmacies;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
        $this->pharmacies = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }
    public function getNumeroOrdre(): ?string { return $this->numeroOrdre; }
    public function setNumeroOrdre(?string $numeroOrdre): void { $this->numeroOrdre = $numeroOrdre; }
    public function getPharmacieNom(): ?string { return $this->pharmacieNom; }
    public function setPharmacieNom(?string $pharmacieNom): void { $this->pharmacieNom = $pharmacieNom; }
    public function getPharmacieAdresse(): ?string { return $this->pharmacieAdresse; }
    public function setPharmacieAdresse(?string $pharmacieAdresse): void { $this->pharmacieAdresse = $pharmacieAdresse; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getPharmacies(): Collection { return $this->pharmacies; }
}
