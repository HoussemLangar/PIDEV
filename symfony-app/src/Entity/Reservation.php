<?php

namespace App\Entity;

use App\Repository\ReservationRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: ReservationRepository::class)]
#[ORM\Table(name: 'reservations')]
class Reservation
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Pharmacy::class, inversedBy: 'reservations')]
    #[ORM\JoinColumn(name: 'pharmacy_id', nullable: false, onDelete: 'CASCADE')]
    #[Assert\NotNull(message: 'La pharmacie est obligatoire.')]
    private Pharmacy $pharmacy;

    #[ORM\ManyToOne(targetEntity: Medicament::class)]
    #[ORM\JoinColumn(name: 'medicament_id', nullable: false, onDelete: 'CASCADE')]
    #[Assert\NotNull(message: 'Le médicament est obligatoire.')]
    private Medicament $medicament;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    #[Assert\NotNull(message: 'Le patient est obligatoire.')]
    private User $patient;

    #[ORM\Column(type: 'integer', options: ['default' => 1])]
    #[Assert\NotBlank(message: 'La quantité est obligatoire.')]
    #[Assert\Range(min: 1)]
    private int $quantite = 1;

    #[ORM\Column(type: 'string', length: 30, options: ['default' => 'pending'])]
    #[Assert\NotBlank]
    private string $statut = 'pending';

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $commentaire = null;

    #[ORM\Column(type: 'datetime_immutable', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime_immutable', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getPharmacy(): Pharmacy { return $this->pharmacy; }
    public function setPharmacy(Pharmacy $pharmacy): void { $this->pharmacy = $pharmacy; }
    public function getMedicament(): Medicament { return $this->medicament; }
    public function setMedicament(Medicament $medicament): void { $this->medicament = $medicament; }
    public function getPatient(): User { return $this->patient; }
    public function setPatient(User $patient): void { $this->patient = $patient; }
    public function getQuantite(): int { return $this->quantite; }
    public function setQuantite(int $quantite): void { $this->quantite = $quantite; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getCommentaire(): ?string { return $this->commentaire; }
    public function setCommentaire(?string $commentaire): void { $this->commentaire = $commentaire; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}
