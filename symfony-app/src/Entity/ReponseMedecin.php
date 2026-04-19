<?php

namespace App\Entity;

use App\Repository\ReponseMedecinRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: ReponseMedecinRepository::class)]
#[ORM\Table(name: 'reponses_medecin')]
class ReponseMedecin
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: PartageAnalyse::class)]
    #[ORM\JoinColumn(name: 'partage_id', nullable: false, onDelete: 'CASCADE')]
    private PartageAnalyse $partage;

    #[ORM\ManyToOne(targetEntity: Medecin::class)]
    #[ORM\JoinColumn(name: 'medecin_id', nullable: false, onDelete: 'CASCADE')]
    private Medecin $medecin;

    #[ORM\Column(type: 'text')]
    private string $reponse;

    #[ORM\Column(type: 'datetimetz', nullable: true)]
    private ?\DateTimeInterface $dateReponse = null;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getPartage(): PartageAnalyse { return $this->partage; }
    public function setPartage(PartageAnalyse $partage): void { $this->partage = $partage; }
    public function getMedecin(): Medecin { return $this->medecin; }
    public function setMedecin(Medecin $medecin): void { $this->medecin = $medecin; }
    public function getReponse(): string { return $this->reponse; }
    public function setReponse(string $reponse): void { $this->reponse = $reponse; }
    public function getDateReponse(): ?\DateTimeInterface { return $this->dateReponse; }
    public function markResponseDate(?\DateTimeInterface $dateReponse): void { $this->dateReponse = $dateReponse; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
}