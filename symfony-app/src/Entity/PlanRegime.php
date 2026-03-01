<?php

namespace App\Entity;

use App\Repository\PlanRegimeRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: PlanRegimeRepository::class)]
#[ORM\Table(name: 'plans_regimes')]
class PlanRegime
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'plansRegimes')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private Patient $patient;

    #[ORM\ManyToOne(targetEntity: Accompagnement::class, inversedBy: 'plansRegimes')]
    #[ORM\JoinColumn(name: 'accompagnement_id', nullable: true, onDelete: 'SET NULL')]
    private ?Accompagnement $accompagnement = null;

    #[ORM\ManyToOne(targetEntity: AccompanimentPlan::class, inversedBy: 'dietPlans')]
    #[ORM\JoinColumn(name: 'accompaniment_plan_id', nullable: true, onDelete: 'SET NULL')]
    private ?AccompanimentPlan $plan = null;

    #[ORM\Column(type: 'string', length: 255)]
    private string $titre;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $typeRegime = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $objectif = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $restrictions = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $caloriesJour = null;

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
    public function getAccompagnement(): ?Accompagnement { return $this->accompagnement; }
    public function setAccompagnement(?Accompagnement $accompagnement): void { $this->accompagnement = $accompagnement; }
    public function getPlan(): ?AccompanimentPlan { return $this->plan; }
    public function setPlan(?AccompanimentPlan $plan): self { $this->plan = $plan; return $this; }
    public function getTitre(): string { return $this->titre; }
    public function setTitre(string $titre): void { $this->titre = $titre; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getTypeRegime(): ?string { return $this->typeRegime; }
    public function setTypeRegime(?string $typeRegime): void { $this->typeRegime = $typeRegime; }
    public function getObjectif(): ?string { return $this->objectif; }
    public function setObjectif(?string $objectif): void { $this->objectif = $objectif; }
    public function getRestrictions(): ?string { return $this->restrictions; }
    public function setRestrictions(?string $restrictions): void { $this->restrictions = $restrictions; }
    public function getCaloriesJour(): ?int { return $this->caloriesJour; }
    public function setCaloriesJour(?int $caloriesJour): void { $this->caloriesJour = $caloriesJour; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
}