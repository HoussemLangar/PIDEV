<?php

namespace App\Entity;

use App\Repository\StockPharmacyRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: StockPharmacyRepository::class)]
#[ORM\Table(name: 'stock_pharmacies', uniqueConstraints: [
    new ORM\UniqueConstraint(name: 'unique_stock', columns: ['pharmacie_id', 'medicament_id'])
])]
class StockPharmacy
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Pharmacy::class, inversedBy: 'stocks')]
    #[ORM\JoinColumn(name: 'pharmacie_id', nullable: false, onDelete: 'CASCADE')]
    private Pharmacy $pharmacie;

    #[ORM\ManyToOne(targetEntity: Medicament::class, inversedBy: 'stockPharmacies')]
    #[ORM\JoinColumn(name: 'medicament_id', nullable: false, onDelete: 'CASCADE')]
    private Medicament $medicament;

    #[ORM\Column(type: 'integer', options: ['default' => 0])]
    private int $quantite = 0;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2, nullable: true)]
    private ?string $prixVente = null;

    #[ORM\Column(type: 'date', nullable: true)]
    private ?\DateTimeInterface $dateExpiration = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getPharmecie(): Pharmacy { return $this->pharmacie; }
    public function setPharmecie(Pharmacy $pharmacie): void { $this->pharmacie = $pharmacie; }
    public function getMedicament(): Medicament { return $this->medicament; }
    public function setMedicament(Medicament $medicament): void { $this->medicament = $medicament; }
    public function getQuantite(): int { return $this->quantite; }
    public function setQuantite(int $quantite): void { $this->quantite = $quantite; }
    public function getPrixVente(): ?string { return $this->prixVente; }
    public function setPrixVente(?string $prixVente): void { $this->prixVente = $prixVente; }
    public function getDateExpiration(): ?\DateTimeInterface { return $this->dateExpiration; }
    public function setDateExpiration(?\DateTimeInterface $dateExpiration): void { $this->dateExpiration = $dateExpiration; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}