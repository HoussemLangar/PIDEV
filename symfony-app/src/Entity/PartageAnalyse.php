<?php

namespace App\Entity;

use App\Repository\PartageAnalyseRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: PartageAnalyseRepository::class)]
#[ORM\Table(name: 'partage_analyses')]
class PartageAnalyse
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Accompagnement::class)]
    #[ORM\JoinColumn(name: 'accompagnement_id', nullable: true, onDelete: 'SET NULL')]
    private ?Accompagnement $accompagnement = null;

    #[ORM\ManyToOne(targetEntity: Patient::class)]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\Column(type: 'string', length: 255)]
    private string $titre;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $fichierUrl = null;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $datePartage = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getAccompagnement(): ?Accompagnement { return $this->accompagnement; }
    public function setAccompagnement(?Accompagnement $accompagnement): void { $this->accompagnement = $accompagnement; }
    public function getPatient(): Patient { return $this->patient; }
    public function setPatient(Patient $patient): void { $this->patient = $patient; }
    public function getTitre(): string { return $this->titre; }
    public function setTitre(string $titre): void { $this->titre = $titre; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getFichierUrl(): ?string { return $this->fichierUrl; }
    public function setFichierUrl(?string $fichierUrl): void { $this->fichierUrl = $fichierUrl; }
    public function getDatePartage(): ?\DateTimeInterface { return $this->datePartage; }
    public function setDatePartage(?\DateTimeInterface $datePartage): void { $this->datePartage = $datePartage; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
}