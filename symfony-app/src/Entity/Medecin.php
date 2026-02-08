<?php

namespace App\Entity;

use App\Repository\MedecinRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: MedecinRepository::class)]
#[ORM\Table(name: 'medecins')]
class Medecin
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\OneToOne(inversedBy: 'medecin', targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'user_id', nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\Column(type: 'string', length: 100)]
    private string $specialite;

    #[ORM\Column(type: 'string', length: 50, nullable: true, unique: true)]
    private ?string $numeroOrdre = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $cabinetAdresse = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $cabinetVille = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 6, nullable: true)]
    private ?string $cabinetLat = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 6, nullable: true)]
    private ?string $cabinetLng = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    private ?string $telephoneCabinet = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2, nullable: true)]
    private ?string $tarifConsultation = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'medecin', targetEntity: Disponibilite::class, cascade: ['persist', 'remove'])]
    private Collection $disponibilites;

    #[ORM\OneToMany(mappedBy: 'medecin', targetEntity: RendezVous::class)]
    private Collection $rendezVous;

    #[ORM\OneToMany(mappedBy: 'medecin', targetEntity: RapportAnalyse::class)]
    private Collection $rapportsAnalyses;

    #[ORM\OneToMany(mappedBy: 'medecin', targetEntity: RapportMedical::class)]
    private Collection $rapportsMedicaux;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
        $this->disponibilites = new ArrayCollection();
        $this->rendezVous = new ArrayCollection();
        $this->rapportsAnalyses = new ArrayCollection();
        $this->rapportsMedicaux = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }
    public function getSpecialite(): string { return $this->specialite; }
    public function setSpecialite(string $specialite): void { $this->specialite = $specialite; }
    public function getNumeroOrdre(): ?string { return $this->numeroOrdre; }
    public function setNumeroOrdre(?string $numeroOrdre): void { $this->numeroOrdre = $numeroOrdre; }
    public function getCabinetAdresse(): ?string { return $this->cabinetAdresse; }
    public function setCabinetAdresse(?string $cabinetAdresse): void { $this->cabinetAdresse = $cabinetAdresse; }
    public function getCabinetVille(): ?string { return $this->cabinetVille; }
    public function setCabinetVille(?string $cabinetVille): void { $this->cabinetVille = $cabinetVille; }
    public function getCabinetLat(): ?string { return $this->cabinetLat; }
    public function setCabinetLat(?string $cabinetLat): void { $this->cabinetLat = $cabinetLat; }
    public function getCabinetLng(): ?string { return $this->cabinetLng; }
    public function setCabinetLng(?string $cabinetLng): void { $this->cabinetLng = $cabinetLng; }
    public function getTelephoneCabinet(): ?string { return $this->telephoneCabinet; }
    public function setTelephoneCabinet(?string $telephoneCabinet): void { $this->telephoneCabinet = $telephoneCabinet; }
    public function getTarifConsultation(): ?string { return $this->tarifConsultation; }
    public function setTarifConsultation(?string $tarifConsultation): void { $this->tarifConsultation = $tarifConsultation; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getDisponibilites(): Collection { return $this->disponibilites; }
    public function getRendezVous(): Collection { return $this->rendezVous; }
    public function getRapportsAnalyses(): Collection { return $this->rapportsAnalyses; }
    public function getRapportsMedicaux(): Collection { return $this->rapportsMedicaux; }
}
