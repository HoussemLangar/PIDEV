<?php

namespace App\Entity;

use App\Repository\PlanExerciceRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: PlanExerciceRepository::class)]
#[ORM\Table(name: 'plans_exercices')]
class PlanExercice
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'plansExercices')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\ManyToOne(targetEntity: Accompagnement::class, inversedBy: 'plansExercices')]
    #[ORM\JoinColumn(name: 'accompagnement_id', nullable: true, onDelete: 'SET NULL')]
    private ?Accompagnement $accompagnement = null;


    #[ORM\Column(type: 'string', length: 255)]
    private string $titre;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $frequence = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $dureMinutes = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    private ?string $niveau = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $objectifs = null;

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
    public function getPatient(): Patient { return $this->patient; }
    public function setPatient(Patient $patient): void { $this->patient = $patient; }
    public function getAccompagnement(): ?Accompagnement { return $this->accompagnement; }
    public function setAccompagnement(?Accompagnement $accompagnement): void { $this->accompagnement = $accompagnement; }
    public function getTitre(): string { return $this->titre; }
    public function setTitre(string $titre): void { $this->titre = $titre; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getFrequence(): ?string { return $this->frequence; }
    public function setFrequence(?string $frequence): void { $this->frequence = $frequence; }
    public function getDureMinutes(): ?int { return $this->dureMinutes; }
    public function setDureMinutes(?int $dureMinutes): void { $this->dureMinutes = $dureMinutes; }
    public function getNiveau(): ?string { return $this->niveau; }
    public function setNiveau(?string $niveau): void { $this->niveau = $niveau; }
    public function getObjectifs(): ?string { return $this->objectifs; }
    public function setObjectifs(?string $objectifs): void { $this->objectifs = $objectifs; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}