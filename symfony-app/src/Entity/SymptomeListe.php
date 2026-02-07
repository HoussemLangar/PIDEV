<?php

namespace App\Entity;

use App\Repository\SymptomeListeRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Bridge\Doctrine\Validator\Constraints\UniqueEntity;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: SymptomeListeRepository::class)]
#[ORM\Table(name: 'symptomes_liste')]
#[UniqueEntity(
    fields: ['nom'],
    message: 'Ce symptôme existe déjà dans la liste.'
)]
class SymptomeListe
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 100, unique: true)]
    #[Assert\NotBlank(message: 'Le nom du symptôme est obligatoire')]
    #[Assert\Length(
        min: 4,
        minMessage: 'Le nom du symptôme doit contenir au moins {{ limit }} caractères',
        max: 100,
        maxMessage: 'Le nom ne doit pas dépasser {{ limit }} caractères'
    )]
    #[Assert\Regex(
        pattern: '/^[^\d]+$/u',
        message: 'Le nom ne doit pas contenir de chiffres'
    )]
    private string $nom;

    #[ORM\Column(type: 'string', length: 100, nullable: false)]
    #[Assert\NotBlank(message: 'La catégorie est obligatoire')]
    #[Assert\Length(
        max: 100,
        maxMessage: 'La catégorie ne doit pas dépasser {{ limit }} caractères'
    )]
    #[Assert\Regex(
        pattern: '/^[^\d]+$/u',
        message: 'La catégorie ne doit pas contenir de chiffres',
        match: true,
    )]
    private string $categorie;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    #[Assert\NotNull]
    private \DateTimeInterface $createdAt;

    #[ORM\OneToMany(mappedBy: 'symptome', targetEntity: SymptomeQuotidien::class, cascade: ['persist', 'remove'])]
    private Collection $symptomesQuotidiens;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->symptomesQuotidiens = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getNom(): string
    {
        return $this->nom;
    }

    public function setNom(string $nom): self
    {
        $this->nom = trim($nom);
        return $this;
    }

    public function getCategorie(): ?string
    {
        return $this->categorie;
    }

    public function setCategorie(?string $categorie): self
    {
        $this->categorie = $categorie ? trim($categorie) : null;
        return $this;
    }

    public function getCreatedAt(): \DateTimeInterface
    {
        return $this->createdAt;
    }

    /**
     * On empêche la modification manuelle de createdAt
     */
    public function setCreatedAt(\DateTimeInterface $createdAt): never
    {
        throw new \LogicException('La date de création ne peut pas être modifiée manuellement.');
    }

    /**
     * @return Collection<int, SymptomeQuotidien>
     */
    public function getSymptomesQuotidiens(): Collection
    {
        return $this->symptomesQuotidiens;
    }
}