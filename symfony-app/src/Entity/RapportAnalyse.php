<?php

namespace App\Entity;

use App\Repository\RapportAnalyseRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: RapportAnalyseRepository::class)]
#[ORM\Table(name: 'rapports_analyses')]
class RapportAnalyse
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'rapportsAnalyses')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\ManyToOne(targetEntity: Medecin::class, inversedBy: 'rapportsAnalyses')]
    #[ORM\JoinColumn(name: 'medecin_id', nullable: true, onDelete: 'SET NULL')]
    private ?Medecin $medecin = null;

    #[ORM\Column(type: 'string', length: 100)]
    private string $typeAnalyse;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $dateAnalyse;

    #[ORM\Column(type: 'string', length: 150, nullable: true)]
    private ?string $laboratoire = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $resultats = null;

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
    public function getMedecin(): ?Medecin { return $this->medecin; }
    public function setMedecin(?Medecin $medecin): void { $this->medecin = $medecin; }
    public function getTypeAnalyse(): string { return $this->typeAnalyse; }
    public function setTypeAnalyse(string $typeAnalyse): void { $this->typeAnalyse = $typeAnalyse; }
    public function getDateAnalyse(): \DateTimeInterface { return $this->dateAnalyse; }
    public function setDateAnalyse(\DateTimeInterface $dateAnalyse): void { $this->dateAnalyse = $dateAnalyse; }
    public function getLaboratoire(): ?string { return $this->laboratoire; }
    public function setLaboratoire(?string $laboratoire): void { $this->laboratoire = $laboratoire; }
    public function getResultats(): ?string { return $this->resultats; }
    public function setResultats(?string $resultats): void { $this->resultats = $resultats; }
    public function getFichierPath(): ?string { return $this->fichierPath; }
    public function setFichierPath(?string $fichierPath): void { $this->fichierPath = $fichierPath; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}