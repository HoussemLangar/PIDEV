<?php

namespace App\Entity;

use App\Repository\ArticleScoreRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: ArticleScoreRepository::class)]
#[ORM\Table(name: 'article_scores')]
#[ORM\HasLifecycleCallbacks]
class ArticleScore
{
    #[ORM\Id]
    #[ORM\OneToOne(targetEntity: Contenu::class)]
    #[ORM\JoinColumn(name: 'contenu_id', referencedColumnName: 'id', nullable: false, onDelete: 'CASCADE')]
    private Contenu $contenu;

    #[ORM\Column(name: 'score_article', type: 'float')]
    private float $scoreArticle = 0.0;

    #[ORM\Column(name: 'nb_commentaires', type: 'integer', options: ['default' => 0])]
    private int $nbCommentaires = 0;

    #[ORM\Column(name: 'updated_at', type: 'datetimetz')]
    private \DateTimeInterface $updatedAt;

    public function getContenu(): Contenu { return $this->contenu; }
    public function setContenu(Contenu $contenu): self { $this->contenu = $contenu; return $this; }

    public function getScoreArticle(): float { return $this->scoreArticle; }
    public function setScoreArticle(float $scoreArticle): self { $this->scoreArticle = $scoreArticle; return $this; }

    public function getNbCommentaires(): int { return $this->nbCommentaires; }
    public function setNbCommentaires(int $nbCommentaires): self { $this->nbCommentaires = $nbCommentaires; return $this; }

    public function getUpdatedAt(): \DateTimeInterface { return $this->updatedAt; }
    public function forceUpdatedAt(\DateTimeInterface $updatedAt): self { $this->updatedAt = $updatedAt; return $this; }

    #[ORM\PrePersist]
    #[ORM\PreUpdate]
    public function touch(): void
    {
        $this->updatedAt = new \DateTime();
    }
}
