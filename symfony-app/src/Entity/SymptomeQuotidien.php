<?php

namespace App\Entity;

use App\Repository\SymptomeQuotidienRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: SymptomeQuotidienRepository::class)]
#[ORM\Table(name: 'symptomes_quotidiens')]
class SymptomeQuotidien
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Patient::class, inversedBy: 'symptomesQuotidiens')]
    #[ORM\JoinColumn(name: 'patient_id', nullable: false, onDelete: 'CASCADE')]
    private ?Patient $patient = null;

    #[ORM\ManyToOne(targetEntity: SymptomeListe::class, inversedBy: 'symptomesQuotidiens')]
    #[ORM\JoinColumn(name: 'symptome_id', nullable: false, onDelete: 'CASCADE')]
    private SymptomeListe $symptome;

    #[ORM\Column(type: 'date')]
    #[Assert\LessThanOrEqual('today', message: 'La date du symptôme ne peut pas être supérieure à aujourd\'hui.')]
    private \DateTimeInterface $dateSymptome;

    #[ORM\Column(type: 'integer')]
    private int $intensite;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $duree = null;

    #[ORM\Column(type: 'text', nullable: true)]
    #[Assert\Length(max: 100, maxMessage: 'Les notes ne peuvent pas dépasser {{ limit }} caractères.')]
    private ?string $notes = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
    }

    public function getId(): ?int { return $this->id; }
    public function getPatient(): ?Patient { return $this->patient; }
    public function setPatient(?Patient $patient): void { $this->patient = $patient; }
    public function getSymptome(): SymptomeListe { return $this->symptome; }
    public function setSymptome(SymptomeListe $symptome): void { $this->symptome = $symptome; }
    public function getDateSymptome(): \DateTimeInterface { return $this->dateSymptome; }
    public function setDateSymptome(\DateTimeInterface $dateSymptome): void { $this->dateSymptome = $dateSymptome; }
    public function getIntensite(): int { return $this->intensite; }
    public function setIntensite(int $intensite): void { $this->intensite = $intensite; }
    public function getDuree(): ?string { return $this->duree; }
    public function setDuree(?string $duree): void { $this->duree = $duree; }
    public function getNotes(): ?string { return $this->notes; }
    public function setNotes(?string $notes): void { $this->notes = $notes; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
}