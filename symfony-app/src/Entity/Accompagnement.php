<?php

namespace App\Entity;

use App\Repository\AccompagnementRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: AccompagnementRepository::class)]
#[ORM\Table(name: 'accompagnements')]
class Accompagnement
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Abonnement::class, inversedBy: 'accompagnements')]
    #[ORM\JoinColumn(name: 'abonnement_id', nullable: false, onDelete: 'CASCADE')]
    private Abonnement $abonnement;

    #[ORM\Column(type: 'string', length: 100)]
    private string $nom;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'date')]
    private \DateTimeInterface $dateDebut;

    #[ORM\Column(type: 'date', nullable: true)]
    private ?\DateTimeInterface $dateFin = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $typeAccompagnement = null;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'en_cours'])]
    private string $statut = 'en_cours';

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'accompagnement', targetEntity: PlanExercice::class)]
    private Collection $plansExercices;

    #[ORM\OneToMany(mappedBy: 'accompagnement', targetEntity: PlanRegime::class)]
    private Collection $plansRegimes;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
        $this->plansExercices = new ArrayCollection();
        $this->plansRegimes = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getAbonnement(): Abonnement { return $this->abonnement; }
    public function setAbonnement(Abonnement $abonnement): void { $this->abonnement = $abonnement; }
    public function getNom(): string { return $this->nom; }
    public function setNom(string $nom): void { $this->nom = $nom; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getDateDebut(): \DateTimeInterface { return $this->dateDebut; }
    public function setDateDebut(\DateTimeInterface $dateDebut): void { $this->dateDebut = $dateDebut; }
    public function getDateFin(): ?\DateTimeInterface { return $this->dateFin; }
    public function setDateFin(?\DateTimeInterface $dateFin): void { $this->dateFin = $dateFin; }
    public function getTypeAccompagnement(): ?string { return $this->typeAccompagnement; }
    public function setTypeAccompagnement(?string $typeAccompagnement): void { $this->typeAccompagnement = $typeAccompagnement; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getPlansExercices(): Collection { return $this->plansExercices; }
    public function getPlansRegimes(): Collection { return $this->plansRegimes; }
}