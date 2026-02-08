<?php

namespace App\Entity;

use App\Repository\PatientRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: PatientRepository::class)]
#[ORM\Table(name: 'patients')]
class Patient
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\OneToOne(inversedBy: 'patient', targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'user_id', nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\Column(type: 'string', length: 50, nullable: true, unique: true)]
    private ?string $numeroSecu = null;

    #[ORM\Column(type: 'string', length: 5, nullable: true)]
    private ?string $groupeSanguin = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $allergies = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $antecedentsMedicaux = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: RendezVous::class, cascade: ['persist', 'remove'])]
    private Collection $rendezVous;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: RapportAnalyse::class, cascade: ['persist', 'remove'])]
    private Collection $rapportsAnalyses;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: RapportMedical::class, cascade: ['persist', 'remove'])]
    private Collection $rapportsMedicaux;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: JournalItem::class, cascade: ['persist', 'remove'])]
    private Collection $journalItems;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: SymptomeQuotidien::class, cascade: ['persist', 'remove'])]
    private Collection $symptomesQuotidiens;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: PlanExercice::class, cascade: ['persist', 'remove'])]
    private Collection $plansExercices;

    #[ORM\OneToMany(mappedBy: 'patient', targetEntity: PlanRegime::class, cascade: ['persist', 'remove'])]
    private Collection $plansRegimes;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
        $this->rendezVous = new ArrayCollection();
        $this->rapportsAnalyses = new ArrayCollection();
        $this->rapportsMedicaux = new ArrayCollection();
        $this->journalItems = new ArrayCollection();
        $this->symptomesQuotidiens = new ArrayCollection();
        $this->plansExercices = new ArrayCollection();
        $this->plansRegimes = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }
    public function getNumeroSecu(): ?string { return $this->numeroSecu; }
    public function setNumeroSecu(?string $numeroSecu): void { $this->numeroSecu = $numeroSecu; }
    public function getGroupeSanguin(): ?string { return $this->groupeSanguin; }
    public function setGroupeSanguin(?string $groupeSanguin): void { $this->groupeSanguin = $groupeSanguin; }
    public function getAllergies(): ?string { return $this->allergies; }
    public function setAllergies(?string $allergies): void { $this->allergies = $allergies; }
    public function getAntecedentsMedicaux(): ?string { return $this->antecedentsMedicaux; }
    public function setAntecedentsMedicaux(?string $antecedentsMedicaux): void { $this->antecedentsMedicaux = $antecedentsMedicaux; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getRendezVous(): Collection { return $this->rendezVous; }
    public function getRapportsAnalyses(): Collection { return $this->rapportsAnalyses; }
    public function getRapportsMedicaux(): Collection { return $this->rapportsMedicaux; }
    public function getJournalItems(): Collection { return $this->journalItems; }
    public function getSymptomesQuotidiens(): Collection { return $this->symptomesQuotidiens; }
    public function getPlansExercices(): Collection { return $this->plansExercices; }
    public function getPlansRegimes(): Collection { return $this->plansRegimes; }
}
