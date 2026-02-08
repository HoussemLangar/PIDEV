<?php

namespace App\Entity;

use App\Repository\MedicamentRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: MedicamentRepository::class)]
#[ORM\Table(name: 'medicaments')]
class Medicament
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $nom;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $type = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $forme = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $dosage = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2, nullable: true)]
    private ?string $prix = null;

    #[ORM\Column(type: 'integer', options: ['default' => 0])]
    private int $stock = 0;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $laboratoire = null;

    #[ORM\Column(type: 'string', length: 120, nullable: true)]
    private ?string $codeBarre = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'medicament', targetEntity: StockPharmacy::class, cascade: ['persist', 'remove'])]
    private Collection $stockPharmacies;

    #[ORM\OneToMany(mappedBy: 'medicament', targetEntity: ReponseMedicament::class)]
    private Collection $reponsesMedicaments;


    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
        $this->stockPharmacies = new ArrayCollection();
        $this->reponsesMedicaments = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getNom(): string { return $this->nom; }
    public function setNom(string $nom): void { $this->nom = $nom; }
    public function getType(): ?string { return $this->type; }
    public function setType(?string $type): void { $this->type = $type; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getForme(): ?string { return $this->forme; }
    public function setForme(?string $forme): void { $this->forme = $forme; }
    public function getDosage(): ?string { return $this->dosage; }
    public function setDosage(?string $dosage): void { $this->dosage = $dosage; }
    public function getPrix(): ?string { return $this->prix; }
    public function setPrix(?string $prix): void { $this->prix = $prix; }
    public function getStock(): int { return $this->stock; }
    public function setStock(int $stock): void { $this->stock = $stock; }
    public function getLaboratoire(): ?string { return $this->laboratoire; }
    public function setLaboratoire(?string $laboratoire): void { $this->laboratoire = $laboratoire; }
    public function getCodeBarre(): ?string { return $this->codeBarre; }
    public function setCodeBarre(?string $codeBarre): void { $this->codeBarre = $codeBarre; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getStockPharmacies(): Collection { return $this->stockPharmacies; }
    public function getReponsesMedicaments(): Collection { return $this->reponsesMedicaments; }
}
