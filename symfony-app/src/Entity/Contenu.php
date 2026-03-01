<?php

namespace App\Entity;

use App\Repository\ContenuRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: ContenuRepository::class)]
#[ORM\Table(name: 'contenu')]
class Contenu
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class, inversedBy: 'contenus')]
    #[ORM\JoinColumn(name: 'auteur_id', nullable: true, onDelete: 'SET NULL')]
    private ?User $auteur = null;

    #[ORM\Column(type: 'string', length: 255)]
    #[Assert\NotBlank(message: 'Le titre est obligatoire.')]
    #[Assert\Length(
        min: 5,
        max: 255,
        minMessage: 'Le titre doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le titre ne peut pas dépasser {{ limit }} caractères.'
    )]
    private string $titre;

    #[ORM\Column(type: 'string', length: 50)]
    #[Assert\NotBlank(message: 'Le type de contenu est obligatoire.')]
    #[Assert\Choice(
        choices: ['article', 'video', 'pdf', 'lien'],
        message: 'Type de contenu invalide.'
    )]
    private string $type;

    #[ORM\Column(type: 'text', nullable: true)]
    #[Assert\Length(
        max: 500,
        maxMessage: 'La description ne peut pas dépasser {{ limit }} caractères.'
    )]
    private ?string $description = null;

    #[ORM\Column(type: 'text')]
    #[Assert\NotBlank(message: 'Le contenu est obligatoire.')]
    #[Assert\Length(
        min: 5,
        max: 20000,
        minMessage: 'Le contenu doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le contenu ne peut pas dépasser {{ limit }} caractères.'
    )]
    private string $contenu = '';

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $categorie = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $tags = null;

    #[ORM\Column(type: 'string', length: 20, options: ['default' => 'en_attente'])]
    private string $statut = 'en_attente';

    #[ORM\Column(type: 'datetimetz', nullable: true)]
    private ?\DateTimeInterface $datePublication = null;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetimetz', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'contenu', targetEntity: Like::class, cascade: ['persist'], orphanRemoval: true)]
    private Collection $likes;

    #[ORM\OneToMany(mappedBy: 'contenu', targetEntity: Commentaire::class, cascade: ['persist'], orphanRemoval: true)]
    private Collection $commentaires;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
        $this->likes = new ArrayCollection();
        $this->commentaires = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getAuteur(): ?User { return $this->auteur; }
    public function setAuteur(?User $auteur): void { $this->auteur = $auteur; }
    public function getTitre(): string { return $this->titre; }
    public function setTitre(string $titre): void { $this->titre = $titre; }
    public function getType(): string { return $this->type; }
    public function setType(string $type): void { $this->type = $type; }
    public function getDescription(): ?string { return $this->description; }
    public function setDescription(?string $description): void { $this->description = $description; }
    public function getContenu(): string { return $this->contenu; }
    public function setContenu(string $contenu): void { $this->contenu = $contenu; }
    public function getCategorie(): ?string { return $this->categorie; }
    public function setCategorie(?string $categorie): void { $this->categorie = $categorie; }
    public function getTags(): ?string { return $this->tags; }
    public function setTags(?string $tags): void { $this->tags = $tags; }
    public function getStatut(): string { return $this->statut; }
    public function setStatut(string $statut): void { $this->statut = $statut; }
    public function getDatePublication(): ?\DateTimeInterface { return $this->datePublication; }
    public function schedulePublicationAt(?\DateTimeInterface $datePublication): void { $this->datePublication = $datePublication; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function forceCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getLikes(): Collection { return $this->likes; }
    public function getCommentaires(): Collection { return $this->commentaires; }
}
