<?php

namespace App\Entity;

use App\Repository\ReservationMedicamentRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: ReservationMedicamentRepository::class)]
#[ORM\Table(name: 'reservations_medicaments')]
class ReservationMedicament
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private User $patient;

    #[ORM\ManyToOne(targetEntity: Pharmacy::class)]
    #[ORM\JoinColumn(name: 'pharmacie_id', nullable: false, onDelete: 'CASCADE')]
    private Pharmacy $pharmacie;

    #[ORM\ManyToOne(targetEntity: Medicament::class)]
    #[ORM\JoinColumn(name: 'medicament_id', nullable: false, onDelete: 'CASCADE')]
    private Medicament $medicament;

    #[ORM\ManyToOne(targetEntity: StockPharmacy::class)]
    #[ORM\JoinColumn(name: 'stock_id', nullable: false, onDelete: 'CASCADE')]
    private StockPharmacy $stock;

    #[ORM\Column(type: 'integer', options: ['default' => 1])]
    private int $quantite = 1;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2, nullable: true)]
    private ?string $prixUnitaire = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2, nullable: true)]
    private ?string $prixTotal = null;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'en_attente'])]
    private string $statut = 'en_attente';

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $expiresAt = null;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $confirmedAt = null;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $cancelledAt = null;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $rejectedAt = null;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
    }

    public function getId(): ?int { return $this->id; }
    public function getPatient(): User { return $this->patient; }
    public function setPatient(User $patient): void { $this->patient = $patient; }
    public function getPharmacie(): Pharmacy { return $this->pharmacie; }
    public function setPharmacie(Pharmacy $pharmacie): void { $this->pharmacie = $pharmacie; }
    public function getMedicament(): Medicament { return $this->medicament; }
    public function setMedicament(Medicament $medicament): void { $this->medicament = $medicament; }
    public function getStock(): StockPharmacy { return $this->stock; }
    public function setStock(StockPharmacy $stock): void { $this->stock = $stock; }
    public function getQuantite(): int { return $this->quantite; }
    public function setQuantite(int $quantite): void { $this->quantite = $quantite; }
    public function getPrixUnitaire(): ?string { return $this->prixUnitaire; }
    public function setPrixUnitaire(?string $prixUnitaire): void { $this->prixUnitaire = $prixUnitaire; }
    public function getPrixTotal(): ?string { return $this->prixTotal; }
    public function setPrixTotal(?string $prixTotal): void { $this->prixTotal = $prixTotal; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getExpiresAt(): ?\DateTimeInterface { return $this->expiresAt; }
    public function setExpiresAt(?\DateTimeInterface $expiresAt): void { $this->expiresAt = $expiresAt; }
    public function getConfirmedAt(): ?\DateTimeInterface { return $this->confirmedAt; }
    public function setConfirmedAt(?\DateTimeInterface $confirmedAt): void { $this->confirmedAt = $confirmedAt; }
    public function getCancelledAt(): ?\DateTimeInterface { return $this->cancelledAt; }
    public function setCancelledAt(?\DateTimeInterface $cancelledAt): void { $this->cancelledAt = $cancelledAt; }
    public function getRejectedAt(): ?\DateTimeInterface { return $this->rejectedAt; }
    public function setRejectedAt(?\DateTimeInterface $rejectedAt): void { $this->rejectedAt = $rejectedAt; }
}
