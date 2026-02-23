<?php

namespace App\Entity;

use App\Repository\PharmacyRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Bridge\Doctrine\Validator\Constraints\UniqueEntity;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: PharmacyRepository::class)]
#[ORM\Table(name: 'pharmacies')]
#[UniqueEntity(fields: ['email'], message: 'Cette adresse email est déjà utilisée.')]
#[UniqueEntity(fields: ['telephone'], message: 'Ce numéro de téléphone est déjà utilisé.')]
class Pharmacy
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Pharmacien::class, inversedBy: 'pharmacies')]
    #[ORM\JoinColumn(name: 'pharmacien_id', nullable: true, onDelete: 'SET NULL')]
    private ?Pharmacien $pharmacien = null;

    #[ORM\Column(type: 'string', length: 150)]
    #[Assert\NotBlank(message: 'Le nom de la pharmacie est obligatoire.')]
    #[Assert\Length(
        min: 3,
        max: 150,
        minMessage: 'Le nom doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'Le nom ne peut pas dépasser {{ limit }} caractères.'
    )]
    #[Assert\Regex(
        pattern: '/^[a-zA-ZÀ-ÿ0-9\s\-\.\']+$/u',
        message: 'Le nom contient des caractères non autorisés.'
    )]
    private string $nom;

    #[ORM\Column(type: 'string', length: 255)]
    #[Assert\NotBlank(message: 'L\'adresse est obligatoire.')]
    #[Assert\Length(
        min: 4,
        max: 255,
        minMessage: 'L\'adresse doit contenir au moins {{ limit }} caractères.',
        maxMessage: 'L\'adresse ne peut pas dépasser {{ limit }} caractères.'
    )]
    private string $adresse;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    #[Assert\Regex(
        pattern: '/^[\+]?[(]?[0-9]{1,4}[)]?[-\s\.]?[(]?[0-9]{1,4}[)]?[-\s\.]?[0-9]{1,9}$/i',
        message: 'Le format du numéro de téléphone est invalide.'
    )]
    #[Assert\Length(
        min: 8,
        max: 20,
        minMessage: 'Le téléphone doit contenir au moins {{ limit }} chiffres.',
        maxMessage: 'Le téléphone ne peut pas dépasser {{ limit }} caractères.'
    )]
    private ?string $telephone = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    #[Assert\Email(message: 'L\'adresse email "{{ value }}" n\'est pas valide.')]
    #[Assert\Length(
        max: 100,
        maxMessage: 'L\'email ne peut pas dépasser {{ limit }} caractères.'
    )]
    private ?string $email = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $horaires = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 6, nullable: true)]
    #[Assert\Range(
        min: -90,
        max: 90,
        notInRangeMessage: 'La latitude doit être entre {{ min }} et {{ max }}.'
    )]
    private ?string $latitude = null;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 6, nullable: true)]
    #[Assert\Range(
        min: -180,
        max: 180,
        notInRangeMessage: 'La longitude doit être entre {{ min }} et {{ max }}.'
    )]
    private ?string $longitude = null;

    #[ORM\Column(type: 'boolean', options: ['default' => true])]
    private bool $isActive = true;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $imageName = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $updatedAt;

    #[ORM\OneToMany(mappedBy: 'pharmacie', targetEntity: StockPharmacy::class, cascade: ['persist', 'remove'])]
    private Collection $stocks;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
        $this->updatedAt = new \DateTime();
        $this->stocks = new ArrayCollection();
    }

    public function getId(): ?int { return $this->id; }
    public function getPharmacien(): ?Pharmacien { return $this->pharmacien; }
    public function setPharmacien(?Pharmacien $pharmacien): void { $this->pharmacien = $pharmacien; }
    public function getNom(): string { return $this->nom; }
    public function setNom(string $nom): void { $this->nom = $nom; }
    public function getAdresse(): string { return $this->adresse; }
    public function setAdresse(string $adresse): void { $this->adresse = $adresse; }
    public function getTelephone(): ?string { return $this->telephone; }
    public function setTelephone(?string $telephone): void { $this->telephone = $telephone; }
    public function getEmail(): ?string { return $this->email; }
    public function setEmail(?string $email): void { $this->email = $email; }
    public function getHoraires(): ?string { return $this->horaires; }
    public function setHoraires(?string $horaires): void { $this->horaires = $horaires; }
    public function getLatitude(): ?string { return $this->latitude; }
    public function setLatitude(?string $latitude): void { $this->latitude = $latitude; }
    public function getLongitude(): ?string { return $this->longitude; }
    public function setLongitude(?string $longitude): void { $this->longitude = $longitude; }
    public function isActive(): bool { return $this->isActive; }
    public function setIsActive(bool $isActive): void { $this->isActive = $isActive; }
    public function getImageName(): ?string { return $this->imageName; }
    public function setImageName(?string $imageName): void { $this->imageName = $imageName; }
    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function setUpdatedAt(\DateTimeInterface $updatedAt): void { $this->updatedAt = $updatedAt; }
    public function getStocks(): Collection { return $this->stocks; }
}
