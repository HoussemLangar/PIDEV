<?php

namespace App\Entity;

use App\Enum\Alimentation;
use App\Enum\NiveauActivite;
use App\Enum\Humeur;
use App\Entity\User;
use App\Repository\SanteQuotidienneRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

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
    private ?float $poids = null;

    #[ORM\Column(type: Types::FLOAT)]
    private ?float $taille = null;

    #[ORM\Column(type: Types::FLOAT)]
    private ?float $imc = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    private ?float $tensionArterielle = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    private ?float $sommeil = null;

    #[ORM\Column(type: Types::STRING, length: 20, enumType: NiveauActivite::class, nullable: true)]
    private ?NiveauActivite $activitePhysique = null;

    #[ORM\Column(type: Types::SIMPLE_ARRAY, enumType: Humeur::class)]
    private array $humeur = [];

    #[ORM\Column(type: Types::STRING, length: 20, enumType: Alimentation::class, nullable: true)]
    private ?Alimentation $alimentation = null;

    #[ORM\Column(type: Types::FLOAT, nullable: true)]
    private ?float $eauBue = null;

    #[ORM\Column(type: Types::DATETIME_MUTABLE)]
    private ?\DateTimeInterface $date = null;

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
        return $this;
    }

    public function getTaille(): ?float
    {
        return $this->taille;
    }

    public function setTaille(float $taille): static
    {
        $this->taille = $taille;
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
}