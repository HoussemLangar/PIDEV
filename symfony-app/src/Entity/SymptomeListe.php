<?php

namespace App\Entity;

use App\Repository\SymptomeListeRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: SymptomeListeRepository::class)]
#[ORM\Table(name: 'symptomes_liste')]
class SymptomeListe
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 100, unique: true)]
    private string $nom;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $categorie = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\OneToMany(mappedBy: 'symptome', targetEntity: SymptomeQuotidien::class, cascade: ['persist', 'remove'])]
    private Collection $symptomesQuotidiens;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->symptomesQuotidiens = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getNom(): string { return $this->nom; }
    public function setNom(string $nom): void { $this->nom = $nom; }
    public function getCategorie(): ?string { return $this->categorie; }
    public function setCategorie(?string $categorie): void { $this->categorie = $categorie; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getSymptomesQuotidiens(): Collection { return $this->symptomesQuotidiens; }
}