<?php

namespace App\Entity;

use App\Repository\RendezVousRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: RendezVousRepository::class)]
#[ORM\Table(name: 'rendez_vous')]
class RendezVous
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'rendezVous')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\ManyToOne(targetEntity: Medecin::class, inversedBy: 'rendezVous')]
    #[ORM\JoinColumn(name: 'medecin_id', nullable: false, onDelete: 'CASCADE')]
    private Medecin $medecin;

    #[ORM\OneToOne(inversedBy: 'rendezvous', targetEntity: Disponibilite::class)]
    #[ORM\JoinColumn(name: 'disponibilite_id', nullable: true, onDelete: 'SET NULL')]
    private ?Disponibilite $disponibilite = null;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $dateRdv;

    #[ORM\Column(type: 'time')]
    private \DateTimeInterface $heureRdv;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $motif = null;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'confirme'])]
    private string $statut = 'confirme';

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $typeConsultation = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $notes = null;

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
    public function getMedecin(): Medecin { return $this->medecin; }
    public function setMedecin(Medecin $medecin): void { $this->medecin = $medecin; }
    public function getDisponibilite(): ?Disponibilite { return $this->disponibilite; }
    public function setDisponibilite(?Disponibilite $disponibilite): void { $this->disponibilite = $disponibilite; }
    public function getDateRdv(): \DateTimeInterface { return $this->dateRdv; }
    public function setDateRdv(\DateTimeInterface $dateRdv): void { $this->dateRdv = $dateRdv; }
    public function getHeureRdv(): \DateTimeInterface { return $this->heureRdv; }
    public function setHeureRdv(\DateTimeInterface $heureRdv): void { $this->heureRdv = $heureRdv; }
    public function getMotif(): ?string { return $this->motif; }
    public function setMotif(?string $motif): void { $this->motif = $motif; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getTypeConsultation(): ?string { return $this->typeConsultation; }
    public function setTypeConsultation(?string $typeConsultation): void { $this->typeConsultation = $typeConsultation; }
    public function getNotes(): ?string { return $this->notes; }
    public function setNotes(?string $notes): void { $this->notes = $notes; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}