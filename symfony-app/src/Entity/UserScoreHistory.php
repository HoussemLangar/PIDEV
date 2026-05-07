<?php

namespace App\Entity;

use App\Doctrine\Id\ManualIntId;
use App\Enum\UserScoreSnapshotType;
use App\Repository\UserScoreHistoryRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: UserScoreHistoryRepository::class)]
#[ORM\Table(name: 'user_score_history')]
#[ORM\Index(name: 'idx_user_score_history_user_created', columns: ['user_id', 'created_at'])]
class UserScoreHistory
{
    #[ORM\Id]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\Column(type: 'smallint')]
    private int $score = 0;

    #[ORM\Column(type: 'smallint')]
    private int $activityScore = 0;

    #[ORM\Column(type: 'smallint')]
    private int $seniorityScore = 0;

    #[ORM\Column(type: 'smallint')]
    private int $ruleComplianceScore = 0;

    #[ORM\Column(type: 'smallint')]
    private int $sanctionsHistoryScore = 0;

    #[ORM\Column(type: 'user_score_snapshot_type', length: 50, options: ['default' => 'daily'])]
    private UserScoreSnapshotType $snapshotType = UserScoreSnapshotType::DAILY;

    #[ORM\Column(type: 'datetimetz_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->id = ManualIntId::generate();
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }

    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }

    public function getScore(): int { return $this->score; }
    public function setScore(int $score): void { $this->score = $score; }

    public function getActivityScore(): int { return $this->activityScore; }
    public function setActivityScore(int $activityScore): void { $this->activityScore = $activityScore; }

    public function getSeniorityScore(): int { return $this->seniorityScore; }
    public function setSeniorityScore(int $seniorityScore): void { $this->seniorityScore = $seniorityScore; }

    public function getRuleComplianceScore(): int { return $this->ruleComplianceScore; }
    public function setRuleComplianceScore(int $ruleComplianceScore): void { $this->ruleComplianceScore = $ruleComplianceScore; }

    public function getSanctionsHistoryScore(): int { return $this->sanctionsHistoryScore; }
    public function setSanctionsHistoryScore(int $sanctionsHistoryScore): void { $this->sanctionsHistoryScore = $sanctionsHistoryScore; }

    public function getSnapshotType(): UserScoreSnapshotType { return $this->snapshotType; }
    public function setSnapshotType(UserScoreSnapshotType $snapshotType): void { $this->snapshotType = $snapshotType; }

    public function getCreatedAt(): \DateTimeImmutable { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeImmutable $createdAt): void { $this->createdAt = $createdAt; }
}
