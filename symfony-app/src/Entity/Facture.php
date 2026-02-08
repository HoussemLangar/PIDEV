<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'factures')]
class Facture
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 50, unique: true)]
    private string $numero;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'user_id', referencedColumnName: 'id', nullable: false, onDelete: 'CASCADE')]
    private User $user;

    #[ORM\ManyToOne(targetEntity: Abonnement::class)]
    #[ORM\JoinColumn(name: 'abonnement_id', referencedColumnName: 'id', nullable: false, onDelete: 'CASCADE')]
    private Abonnement $abonnement;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2)]
    private string $montantHt;

    #[ORM\Column(type: 'decimal', precision: 5, scale: 2)]
    private string $tvaTaux;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2)]
    private string $tvaMontant;

    #[ORM\Column(type: 'decimal', precision: 10, scale: 2)]
    private string $montantTtc;

    #[ORM\Column(type: 'string', length: 10, options: ['default' => 'TND'])]
    private string $devise = 'TND';

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $pdfPath = null;

    #[ORM\Column(type: 'datetime', options: ['default' => 'CURRENT_TIMESTAMP'])]
    private \DateTimeInterface $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTime();
    }

    public function getId(): ?int { return $this->id; }

    public function getNumero(): string { return $this->numero; }
    public function setNumero(string $numero): void { $this->numero = $numero; }

    public function getUser(): User { return $this->user; }
    public function setUser(User $user): void { $this->user = $user; }

    public function getAbonnement(): Abonnement { return $this->abonnement; }
    public function setAbonnement(Abonnement $abonnement): void { $this->abonnement = $abonnement; }

    public function getMontantHt(): string { return $this->montantHt; }
    public function setMontantHt(string $montantHt): void { $this->montantHt = $montantHt; }

    public function getTvaTaux(): string { return $this->tvaTaux; }
    public function setTvaTaux(string $tvaTaux): void { $this->tvaTaux = $tvaTaux; }

    public function getTvaMontant(): string { return $this->tvaMontant; }
    public function setTvaMontant(string $tvaMontant): void { $this->tvaMontant = $tvaMontant; }

    public function getMontantTtc(): string { return $this->montantTtc; }
    public function setMontantTtc(string $montantTtc): void { $this->montantTtc = $montantTtc; }

    public function getDevise(): string { return $this->devise; }
    public function setDevise(string $devise): void { $this->devise = $devise; }

    public function getPdfPath(): ?string { return $this->pdfPath; }
    public function setPdfPath(?string $pdfPath): void { $this->pdfPath = $pdfPath; }

    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $createdAt): void { $this->createdAt = $createdAt; }
}
