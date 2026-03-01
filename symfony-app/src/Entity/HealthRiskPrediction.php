<?php

namespace App\Entity;

use App\Repository\HealthRiskPredictionRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: HealthRiskPredictionRepository::class)]
#[ORM\Table(name: 'health_risk_predictions')]
#[ORM\UniqueConstraint(name: 'uniq_health_risk_user_day', columns: ['user_id', 'prediction_date'])]
class HealthRiskPrediction
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: Types::INTEGER)]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?User $user = null;

    #[ORM\Column(type: Types::DATE_IMMUTABLE)]
    private \DateTimeImmutable $predictionDate;

    #[ORM\Column(type: Types::FLOAT)]
    private float $riskHtn = 0.0;

    #[ORM\Column(type: Types::FLOAT)]
    private float $riskDiabetes = 0.0;

    #[ORM\Column(type: Types::FLOAT)]
    private float $riskDepression = 0.0;

    #[ORM\Column(type: Types::FLOAT)]
    private float $riskRespiratory = 0.0;

    #[ORM\Column(type: Types::STRING, length: 20, options: ['default' => 'LOW'])]
    private string $levelHtn = 'LOW';

    #[ORM\Column(type: Types::STRING, length: 20, options: ['default' => 'LOW'])]
    private string $levelDiabetes = 'LOW';

    #[ORM\Column(type: Types::STRING, length: 20, options: ['default' => 'LOW'])]
    private string $levelDepression = 'LOW';

    #[ORM\Column(type: Types::STRING, length: 20, options: ['default' => 'LOW'])]
    private string $levelRespiratory = 'LOW';

    #[ORM\Column(type: Types::JSON)]
    private array $explanationsJson = [];

    #[ORM\Column(type: Types::JSON, nullable: true)]
    private ?array $featureSnapshot = null;

    #[ORM\Column(type: Types::DATETIME_IMMUTABLE)]
    private \DateTimeImmutable $updatedAt;

    public function __construct()
    {
        $this->predictionDate = new \DateTimeImmutable('today');
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getUser(): ?User { return $this->user; }
    public function setUser(User $user): self { $this->user = $user; return $this; }
    public function getPredictionDate(): \DateTimeImmutable { return $this->predictionDate; }
    public function setPredictionDate(\DateTimeImmutable $predictionDate): self { $this->predictionDate = $predictionDate; return $this; }
    public function getRiskHtn(): float { return $this->riskHtn; }
    public function setRiskHtn(float $riskHtn): self { $this->riskHtn = $riskHtn; return $this; }
    public function getRiskDiabetes(): float { return $this->riskDiabetes; }
    public function setRiskDiabetes(float $riskDiabetes): self { $this->riskDiabetes = $riskDiabetes; return $this; }
    public function getRiskDepression(): float { return $this->riskDepression; }
    public function setRiskDepression(float $riskDepression): self { $this->riskDepression = $riskDepression; return $this; }
    public function getRiskRespiratory(): float { return $this->riskRespiratory; }
    public function setRiskRespiratory(float $riskRespiratory): self { $this->riskRespiratory = $riskRespiratory; return $this; }
    public function getLevelHtn(): string { return $this->levelHtn; }
    public function setLevelHtn(string $levelHtn): self { $this->levelHtn = $levelHtn; return $this; }
    public function getLevelDiabetes(): string { return $this->levelDiabetes; }
    public function setLevelDiabetes(string $levelDiabetes): self { $this->levelDiabetes = $levelDiabetes; return $this; }
    public function getLevelDepression(): string { return $this->levelDepression; }
    public function setLevelDepression(string $levelDepression): self { $this->levelDepression = $levelDepression; return $this; }
    public function getLevelRespiratory(): string { return $this->levelRespiratory; }
    public function setLevelRespiratory(string $levelRespiratory): self { $this->levelRespiratory = $levelRespiratory; return $this; }
    public function getExplanationsJson(): array { return $this->explanationsJson; }
    public function setExplanationsJson(array $explanationsJson): self { $this->explanationsJson = $explanationsJson; return $this; }
    public function getFeatureSnapshot(): ?array { return $this->featureSnapshot; }
    public function setFeatureSnapshot(?array $featureSnapshot): self { $this->featureSnapshot = $featureSnapshot; return $this; }
    public function getUpdatedAt(): \DateTimeImmutable { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeImmutable $updatedAt): self { $this->updatedAt = $updatedAt; return $this; }
}

