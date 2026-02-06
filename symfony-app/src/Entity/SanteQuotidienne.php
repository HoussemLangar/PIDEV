<?php

namespace App\Entity;

use App\Enum\Alimentation;
use App\Enum\NiveauActivite;
use App\Enum\Humeur;
use App\Entity\User;
use App\Repository\SanteQuotidienneRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: SanteQuotidienneRepository::class)]
#[ORM\Table(name: 'sante_quotidienne')]
class SanteQuotidienne
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\ManyToOne(inversedBy: 'santeQuotidiennes')]
    #[ORM\JoinColumn(nullable: false)]
    private ?User $user = null;

    #[ORM\Column(type: Types::FLOAT)]
    #[Assert\NotBlank(message: "Le poids est obligatoire")]
    #[Assert\GreaterThan(value: 0, message: "Le poids doit être supérieur à 0 kg")]
    private ?float $poids = null;

    #[ORM\Column(type: Types::FLOAT)]
    #[Assert\NotBlank(message: "La taille est obligatoire")]
    #[Assert\GreaterThan(value: 0, message: "La taille doit être supérieure à 0 cm")]
    private ?float $taille = null;

    #[ORM\Column(type: Types::FLOAT)]
    #[Assert\GreaterThan(value: 0, message: "L'IMC doit être positif")]
    private ?float $imc = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\GreaterThanOrEqual(value: 0, message: "La tension artérielle ne peut pas être négative")]
    private ?float $tensionArterielle = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\GreaterThanOrEqual(value: 0, message: "Le sommeil ne peut pas être négatif")]
    private ?float $sommeil = null;

    #[ORM\Column(type: Types::STRING, length: 20, enumType: NiveauActivite::class, nullable: true)]
    private ?NiveauActivite $activitePhysique = null;

    #[ORM\Column(type: Types::SIMPLE_ARRAY, enumType: Humeur::class)]
    #[Assert\Count(min: 1, minMessage: "Veuillez choisir au moins une humeur")]
    private array $humeur = [];

    #[ORM\Column(type: Types::STRING, length: 20, enumType: Alimentation::class, nullable: true)]
    private ?Alimentation $alimentation = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\GreaterThanOrEqual(value: 0, message: "La quantité d'eau bue ne peut pas être négative")]
    private ?float $eauBue = null;

    #[ORM\Column(type: Types::DATETIME_MUTABLE)]
    #[Assert\NotBlank(message: "La date est obligatoire")]
    private ?\DateTimeInterface $date = null;

    public function __construct()
    {
        $this->date = new \DateTime();  // Date du jour par défaut
        $this->humeur = [];             // Tableau vide par défaut
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getUser(): ?User
    {
        return $this->user;
    }

    public function setUser(?User $user): static
    {
        $this->user = $user;
        return $this;
    }

    public function getPoids(): ?float
    {
        return $this->poids;
    }

    public function setPoids(float $poids): static
    {
        $this->poids = $poids;
        $this->calculateImc();  // recalcule automatiquement l'IMC
        return $this;
    }

    public function getTaille(): ?float
    {
        return $this->taille;
    }

    public function setTaille(float $taille): static
    {
        $this->taille = $taille;
        $this->calculateImc();  // recalcule automatiquement l'IMC
        return $this;
    }

    public function getImc(): ?float
    {
        return $this->imc;
    }

    public function setImc(float $imc): static
    {
        $this->imc = $imc;
        return $this;
    }

    public function getTensionArterielle(): ?float
    {
        return $this->tensionArterielle;
    }

    public function setTensionArterielle(?float $tensionArterielle): static
    {
        $this->tensionArterielle = $tensionArterielle;
        return $this;
    }

    public function getSommeil(): ?float
    {
        return $this->sommeil;
    }

    public function setSommeil(?float $sommeil): static
    {
        $this->sommeil = $sommeil;
        return $this;
    }

    public function getActivitePhysique(): ?NiveauActivite
    {
        return $this->activitePhysique;
    }

    public function setActivitePhysique(?NiveauActivite $activitePhysique): static
    {
        $this->activitePhysique = $activitePhysique;
        return $this;
    }

    /**
     * @return Humeur[]
     */
    public function getHumeur(): array
    {
        return $this->humeur;
    }

    public function setHumeur(array $humeur): static
    {
        $this->humeur = $humeur;
        return $this;
    }

    public function getAlimentation(): ?Alimentation
    {
        return $this->alimentation;
    }

    public function setAlimentation(?Alimentation $alimentation): static
    {
        $this->alimentation = $alimentation;
        return $this;
    }

    public function getEauBue(): ?float
    {
        return $this->eauBue;
    }

    public function setEauBue(?float $eauBue): static
    {
        $this->eauBue = $eauBue;
        return $this;
    }

    public function getDate(): ?\DateTimeInterface
    {
        return $this->date;
    }

    public function setDate(\DateTimeInterface $date): static
    {
        $this->date = $date;
        return $this;
    }

    /**
     * Calcule automatiquement l'IMC (poids / taille²) avec arrondi à 2 décimales
     */
    private function calculateImc(): void
    {
        if ($this->poids > 0 && $this->taille > 0) {
            $tailleEnMetres = $this->taille / 100;
            $this->imc = round($this->poids / ($tailleEnMetres * $tailleEnMetres), 2);
        } else {
            $this->imc = null;  // ou 0 si tu préfères
        }
    }
}