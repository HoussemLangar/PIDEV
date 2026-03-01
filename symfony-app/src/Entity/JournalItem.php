<?php

namespace App\Entity;

use App\Repository\JournalItemRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: JournalItemRepository::class)]
#[ORM\Table(name: 'journaux_items')]
class JournalItem
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'journalItems')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\Column(type: 'string', length: 255)]
    private string $titre;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $type;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $dateJournal;

    #[ORM\Column(type: 'time', nullable: true)]
    private ?\DateTimeInterface $heure = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $valeur = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    private ?string $unite = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $humeur = null;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
    }

    public function getId(): ?int { return $this->id; }
    public function getPatient(): Patient { return $this->patient; }
    public function setPatient(Patient $patient): void { $this->patient = $patient; }
    public function getTitre(): string { return $this->titre; }
    public function setTitre(string $titre): void { $this->titre = $titre; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getType(): string { return $this->type; }
    public function setType(string $type): void { $this->type = $type; }
    public function getDateJournal(): \DateTimeInterface { return $this->dateJournal; }
    public function setDateJournal(\DateTimeInterface $dateJournal): void { $this->dateJournal = $dateJournal; }
    public function getHeure(): ?\DateTimeInterface { return $this->heure; }
    public function setHeure(?\DateTimeInterface $heure): void { $this->heure = $heure; }
    public function getValeur(): ?string { return $this->valeur; }
    public function setValeur(?string $valeur): void { $this->valeur = $valeur; }
    public function getUnite(): ?string { return $this->unite; }
    public function setUnite(?string $unite): void { $this->unite = $unite; }
    public function getHumeur(): ?string { return $this->humeur; }
    public function setHumeur(?string $humeur): void { $this->humeur = $humeur; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}