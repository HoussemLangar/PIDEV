<?php

namespace App\Entity;

use App\Repository\RapportMedicalRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: RapportMedicalRepository::class)]
#[ORM\Table(name: 'rapports_medicaux')]
class RapportMedical
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'rapportsMedicaux')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\ManyToOne(targetEntity: Medecin::class, inversedBy: 'rapportsMedicaux')]
    #[ORM\JoinColumn(name: 'medecin_id', nullable: false, onDelete: 'CASCADE')]
    private Medecin $medecin;

    #[ORM\Column(type: 'string', length: 255)]
    private string $titre;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $diagnostic = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $traitement = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $observations = null;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $dateRapport;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $fichierPath = null;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getPatient(): Patient { return $this->patient; }
    public function setPatient(Patient $patient): void { $this->patient = $patient; }
    public function getMedecin(): Medecin { return $this->medecin; }
    public function setMedecin(Medecin $medecin): void { $this->medecin = $medecin; }
    public function getTitre(): string { return $this->titre; }
    public function setTitre(string $titre): void { $this->titre = $titre; }
    public function getDiagnostic(): ?string { return $this->diagnostic; }
    public function setDiagnostic(?string $diagnostic): void { $this->diagnostic = $diagnostic; }
    public function getTraitement(): ?string { return $this->traitement; }
    public function setTraitement(?string $traitement): void { $this->traitement = $traitement; }
    public function getObservations(): ?string { return $this->observations; }
    public function setObservations(?string $observations): void { $this->observations = $observations; }
    public function getDateRapport(): \DateTimeInterface { return $this->dateRapport; }
    public function setDateRapport(\DateTimeInterface $dateRapport): void { $this->dateRapport = $dateRapport; }
    public function getFichierPath(): ?string { return $this->fichierPath; }
    public function setFichierPath(?string $fichierPath): void { $this->fichierPath = $fichierPath; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}