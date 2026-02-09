<?php

namespace App\Entity;

use App\Repository\AccompanimentPlanRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: AccompanimentPlanRepository::class)]
#[ORM\Table(name: 'accompaniment_plans')]
class AccompanimentPlan
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class)]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\ManyToOne(targetEntity: CoachSportif::class)]
    #[ORM\JoinColumn(name: 'coach_id', nullable: true, onDelete: 'SET NULL')]
    private ?CoachSportif $coach = null;

    #[ORM\ManyToOne(targetEntity: Nutritionniste::class)]
    #[ORM\JoinColumn(name: 'nutritionist_id', nullable: true, onDelete: 'SET NULL')]
    private ?Nutritionniste $nutritionist = null;

    #[ORM\Column(type: 'string', length: 255)]
    #[Assert\NotBlank(message: "Le titre du plan est obligatoire")]
    private string $title;

    #[ORM\Column(type: Types::TEXT)]
    private string $objectives;

    #[ORM\Column(type: Types::TEXT, nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 50, options: ['default' => 'active'])]
    private string $status = 'active'; // active, completed, cancelled, paused

    #[ORM\Column(type: Types::DATE_IMMUTABLE)]
    private \DateTimeImmutable $startDate;

    #[ORM\Column(type: Types::DATE_IMMUTABLE, nullable: true)]
    private ?\DateTimeImmutable $endDate = null;

    #[ORM\Column(type: 'integer')]
    private int $durationWeeks = 12;

    #[ORM\Column(type: Types::DATETIME_IMMUTABLE)]
    private \DateTimeImmutable $createdAt;

    #[ORM\Column(type: Types::DATETIME_IMMUTABLE)]
    private \DateTimeImmutable $updatedAt;

    #[ORM\OneToMany(mappedBy: 'plan', targetEntity: PlanExercice::class, cascade: ['persist', 'remove'])]
    private Collection $exercisePlans;

    #[ORM\OneToMany(mappedBy: 'plan', targetEntity: PlanRegime::class, cascade: ['persist', 'remove'])]
    private Collection $dietPlans;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->updatedAt = new \DateTimeImmutable();
        $this->startDate = new \DateTimeImmutable();
        $this->exercisePlans = new ArrayCollection();
        $this->dietPlans = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getPatient(): Patient
    {
        return $this->patient;
    }

    public function setPatient(Patient $patient): self
    {
        $this->patient = $patient;
        return $this;
    }

    public function getCoach(): ?CoachSportif
    {
        return $this->coach;
    }

    public function setCoach(?CoachSportif $coach): self
    {
        $this->coach = $coach;
        return $this;
    }

    public function getNutritionist(): ?Nutritionniste
    {
        return $this->nutritionist;
    }

    public function setNutritionist(?Nutritionniste $nutritionist): self
    {
        $this->nutritionist = $nutritionist;
        return $this;
    }

    public function getTitle(): string
    {
        return $this->title;
    }

    public function setTitle(string $title): self
    {
        $this->title = $title;
        return $this;
    }

    public function getObjectives(): string
    {
        return $this->objectives;
    }

    public function setObjectives(string $objectives): self
    {
        $this->objectives = $objectives;
        return $this;
    }

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): self
    {
        $this->description = $description;
        return $this;
    }

    public function getStatus(): string
    {
        return $this->status;
    }

    public function setStatus(string $status): self
    {
        $this->status = $status;
        return $this;
    }

    public function getStartDate(): \DateTimeImmutable
    {
        return $this->startDate;
    }

    public function setStartDate(\DateTimeImmutable $startDate): self
    {
        $this->startDate = $startDate;
        return $this;
    }

    public function getEndDate(): ?\DateTimeImmutable
    {
        return $this->endDate;
    }

    public function setEndDate(?\DateTimeImmutable $endDate): self
    {
        $this->endDate = $endDate;
        return $this;
    }

    public function getDurationWeeks(): int
    {
        return $this->durationWeeks;
    }

    public function setDurationWeeks(int $durationWeeks): self
    {
        $this->durationWeeks = $durationWeeks;
        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    public function getUpdatedAt(): \DateTimeImmutable
    {
        return $this->updatedAt;
    }

    public function setUpdatedAt(\DateTimeImmutable $updatedAt): self
    {
        $this->updatedAt = $updatedAt;
        return $this;
    }

    public function getExercisePlans(): Collection
    {
        return $this->exercisePlans;
    }

    public function addExercisePlan(PlanExercice $plan): self
    {
        if (!$this->exercisePlans->contains($plan)) {
            $this->exercisePlans->add($plan);
            $plan->setPlan($this);
        }
        return $this;
    }

    public function removeExercisePlan(PlanExercice $plan): self
    {
        if ($this->exercisePlans->removeElement($plan)) {
            if ($plan->getPlan() === $this) {
                $plan->setPlan(null);
            }
        }
        return $this;
    }

    public function getDietPlans(): Collection
    {
        return $this->dietPlans;
    }

    public function addDietPlan(PlanRegime $plan): self
    {
        if (!$this->dietPlans->contains($plan)) {
            $this->dietPlans->add($plan);
            $plan->setPlan($this);
        }
        return $this;
    }

    public function removeDietPlan(PlanRegime $plan): self
    {
        if ($this->dietPlans->removeElement($plan)) {
            if ($plan->getPlan() === $this) {
                $plan->setPlan(null);
            }
        }
        return $this;
    }

    public function isActive(): bool
    {
        return $this->status === 'active';
    }

    public function getProgressPercentage(): int
    {
        if (!$this->startDate) {
            return 0;
        }

        $now = new \DateTimeImmutable();
        $totalDays = $this->durationWeeks * 7;
        $elapsedDays = $this->startDate->diff($now)->days;

        return min(100, (int)(($elapsedDays / $totalDays) * 100));
    }
}
