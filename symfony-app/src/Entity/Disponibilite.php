<?php

namespace App\Entity;

use App\Repository\DisponibiliteRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: DisponibiliteRepository::class)]
#[ORM\Table(name: 'disponibilites')]
class Disponibilite
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Medecin::class, inversedBy: 'disponibilites')]
    #[ORM\JoinColumn(name: 'medecin_id', nullable: false, onDelete: 'CASCADE')]
    private Medecin $medecin;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $date;

    #[ORM\Column(type: 'time')]
    private \DateTimeInterface $heureDebut;

    #[ORM\Column(type: 'time')]
    private \DateTimeInterface $heureFin;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'disponible'])]
    private string $statut = 'disponible';

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToOne(mappedBy: 'disponibilite', targetEntity: RendezVous::class)]
    private ?RendezVous $rendezvous = null;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
    }

    public function getId(): ?int { return $this->id; }
    public function getMedecin(): Medecin { return $this->medecin; }
    public function setMedecin(Medecin $medecin): void { $this->medecin = $medecin; }
    public function getDate(): \DateTimeInterface { return $this->date; }
    public function setDate(\DateTimeInterface $date): void { $this->date = $date; }
    public function getHeureDebut(): \DateTimeInterface { return $this->heureDebut; }
    public function setHeureDebut(\DateTimeInterface $heureDebut): void { $this->heureDebut = $heureDebut; }
    public function getHeureFin(): \DateTimeInterface { return $this->heureFin; }
    public function setHeureFin(\DateTimeInterface $heureFin): void { $this->heureFin = $heureFin; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getRendezvous(): ?RendezVous { return $this->rendezvous; }
    public function setRendezvous(?RendezVous $rendezvous): void { $this->rendezvous = $rendezvous; }
}
