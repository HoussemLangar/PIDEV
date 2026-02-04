<?php

namespace App\Entity;

use App\Repository\ReponseMedicamentRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: ReponseMedicamentRepository::class)]
#[ORM\Table(name: 'reponses_medicaments')]
class ReponseMedicament
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Medicament::class, inversedBy: 'reponsesMedicaments')]
    #[ORM\JoinColumn(name: 'medicament_id', nullable: false, onDelete: 'CASCADE')]
    private Medicament $medicament;

    #[ORM\ManyToOne(targetEntity: User::class, inversedBy: 'reponsesMedicaments')]
    #[ORM\JoinColumn(name: 'user_id', nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\Column(type: 'text')]
    private string $question;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $reponse = null;

    #[ORM\Column(type: 'datetime')]
    private \DateTimeInterface $dateQuestion;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $dateReponse = null;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'en_attente'])]
    private string $statut = 'en_attente';

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->dateQuestion = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getMedicament(): Medicament { return $this->medicament; }
    public function setMedicament(Medicament $medicament): void { $this->medicament = $medicament; }
    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }
    public function getQuestion(): string { return $this->question; }
    public function setQuestion(string $question): void { $this->question = $question; }
    public function getReponse(): ?string { return $this->reponse; }
    public function setReponse(?string $reponse): void { $this->reponse = $reponse; }
    public function getDateQuestion(): \DateTimeInterface { return $this->dateQuestion; }
    public function setDateQuestion(\DateTimeInterface $dateQuestion): void { $this->dateQuestion = $dateQuestion; }
    public function getDateReponse(): ?\DateTimeInterface { return $this->dateReponse; }
    public function setDateReponse(?\DateTimeInterface $dateReponse): void { $this->dateReponse = $dateReponse; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
}