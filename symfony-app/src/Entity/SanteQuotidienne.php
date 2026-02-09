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
    #[Assert\Range(
        min: 0.1,
        max: 300,
        notInRangeMessage: "Le poids doit être compris entre 0.1 et 300 kg"
    )]
    private ?float $poids = null;

    #[ORM\Column(type: Types::FLOAT)]
    #[Assert\NotBlank(message: "La taille est obligatoire")]
    #[Assert\Range(
        min: 0.1,
        max: 250,
        notInRangeMessage: "La taille doit être comprise entre 0.1 et 250 cm"
    )]
    private ?float $taille = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\Range(
        min: 0.1,
        max: 100,
        notInRangeMessage: "L'IMC doit être compris entre 0.1 et 100"
    )]
    private ?float $imc = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\Range(
        min: 0,
        max: 300,
        notInRangeMessage: "La tension artérielle doit être comprise entre 0 et 300"
    )]
    private ?float $tensionArterielle = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\Range(
        min: 0,
        max: 24,
        notInRangeMessage: "Le sommeil doit être compris entre 0 et 24 heures"
    )]
    private ?float $sommeil = null;

    #[ORM\Column(type: Types::STRING, length: 20, enumType: NiveauActivite::class, nullable: true)]
    private ?NiveauActivite $activitePhysique = null;

    #[ORM\Column(type: Types::SIMPLE_ARRAY, enumType: Humeur::class)]
    #[Assert\Count(
        min: 1,
        minMessage: "Veuillez choisir au moins une humeur"
    )]
    private array $humeur = [];

    #[ORM\Column(type: Types::STRING, length: 20, enumType: Alimentation::class, nullable: true)]
    private ?Alimentation $alimentation = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    #[Assert\Range(
        min: 0,
        max: 5,
        notInRangeMessage: "La quantité d'eau bue doit être comprise entre 0 et 5 litres"
    )]
    private ?float $eauBue = null;

    #[ORM\Column(type: Types::INTEGER, nullable: true)]
    private ?int $pas = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    private ?float $calories = null;

    #[ORM\Column(type: Types::INTEGER, nullable: true)]
    private ?int $dureeActiviteMinutes = null;

    #[ORM\Column(type: Types::STRING, length: 20)]
    private string $sourceDonnees = 'manuel';

    #[ORM\Column(type: Types::DATETIME_MUTABLE)]
    #[Assert\NotBlank(message: "La date est obligatoire")]
    #[Assert\LessThanOrEqual('today', message: 'Veuillez choisir une date valide (aujourd\'hui ou avant).')]
    private ?\DateTimeInterface $date = null;

    public function __construct()
    {
        $this->date = new \DateTime(); // Date du jour par défaut
        $this->humeur = []; // Tableau vide par défaut
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

    public function setPoids(?float $poids): static
    {
        $this->poids = $poids;
        $this->calculateImc();
        return $this;
    }

    public function getTaille(): ?float
    {
        return $this->taille;
    }

    public function setTaille(?float $taille): static
    {
        $this->taille = $taille;
        $this->calculateImc();
        return $this;
    }

    public function getImc(): ?float
    {
        return $this->imc;
    }

    public function setImc(?float $imc): static
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

    public function getPas(): ?int { return $this->pas; }
    public function setPas(?int $pas): static { $this->pas = $pas; return $this; }

    public function getCalories(): ?float { return $this->calories; }
    public function setCalories(?float $calories): static { $this->calories = $calories; return $this; }

    public function getDureeActiviteMinutes(): ?int { return $this->dureeActiviteMinutes; }
    public function setDureeActiviteMinutes(?int $dureeActiviteMinutes): static { $this->dureeActiviteMinutes = $dureeActiviteMinutes; return $this; }

    public function getSourceDonnees(): string { return $this->sourceDonnees; }
    public function setSourceDonnees(string $sourceDonnees): static { $this->sourceDonnees = $sourceDonnees; return $this; }

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
            $this->imc = null;
        }
    }
}