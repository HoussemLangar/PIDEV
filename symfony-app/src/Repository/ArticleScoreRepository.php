<?php

namespace App\Repository;

use App\Entity\ArticleScore;
use App\Entity\Commentaire;
use App\Service\CommentModerationService;
use App\Service\CommentSentimentScoringService;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<ArticleScore>
 */
class ArticleScoreRepository extends ServiceEntityRepository
{
    private ?bool $articleScoreTableExists = null;

    public function __construct(
        ManagerRegistry $registry,
        private readonly CommentaireRepository $commentaireRepository,
        private readonly CommentSentimentScoringService $sentimentScoringService,
        private readonly CommentModerationService $commentModerationService
    ) {
        parent::__construct($registry, ArticleScore::class);
    }

    public function updateScore(int $contenuId, bool $flush = true): void
    {
        if (!$this->hasArticleScoreTable()) {
            return;
        }

        $comments = $this->commentaireRepository->findPublishedByContenu($contenuId);

        $sum = 0.0;
        $count = 0;
        $em = $this->getEntityManager();

        foreach ($comments as $comment) {
            if (!$comment instanceof Commentaire) {
                continue;
            }

            $text = trim($comment->getCommentaire());
            if ($text === '') {
                continue;
            }

            if ($this->commentModerationService->isInappropriate($text)) {
                continue;
            }

            $analysis = $this->sentimentScoringService->analyze($text);
            $sum += $this->sentimentScoringService->toSignedScore($analysis);
            $count++;
        }

        $score = $count > 0 ? ($sum / $count) : 0.0;

        $contenu = $em->getReference(\App\Entity\Contenu::class, $contenuId);
        $articleScore = $this->findOneBy(['contenu' => $contenu]);
        if (!$articleScore) {
            $articleScore = (new ArticleScore())
                ->setContenu($contenu);
            $em->persist($articleScore);
        }

        $articleScore->setScoreArticle($score);
        $articleScore->setNbCommentaires($count);
        $articleScore->setUpdatedAt(new \DateTime());

        if ($flush) {
            $em->flush();
        }
    }

    /**
     * @param int[] $contenuIds
     * @return array<int, array{score: float, count: int}>
     */
    public function findScoreMapByContenuIds(array $contenuIds): array
    {
        if ($contenuIds === []) {
            return [];
        }

        $normalizedIds = array_values(array_unique(array_map(static fn (int $id): int => (int) $id, $contenuIds)));
        $result = [];
        foreach ($normalizedIds as $id) {
            if ($id > 0) {
                $result[$id] = ['score' => 0.0, 'count' => 0];
            }
        }

        if ($result === [] || !$this->hasArticleScoreTable()) {
            return $result;
        }

        $rows = $this->createQueryBuilder('s')
            ->select('IDENTITY(s.contenu) AS contenuId, s.scoreArticle AS scoreArticle, s.nbCommentaires AS nbCommentaires')
            ->andWhere('s.contenu IN (:ids)')
            ->setParameter('ids', array_keys($result))
            ->getQuery()
            ->getArrayResult();

        foreach ($rows as $row) {
            $contenuId = (int) ($row['contenuId'] ?? 0);
            if ($contenuId <= 0 || !isset($result[$contenuId])) {
                continue;
            }

            $result[$contenuId] = [
                'score' => (float) ($row['scoreArticle'] ?? 0.0),
                'count' => (int) ($row['nbCommentaires'] ?? 0),
            ];
        }

        return $result;
    }

    private function hasArticleScoreTable(): bool
    {
        if ($this->articleScoreTableExists !== null) {
            return $this->articleScoreTableExists;
        }

        try {
            $schemaManager = $this->getEntityManager()->getConnection()->createSchemaManager();
            $this->articleScoreTableExists = $schemaManager->tablesExist(['article_scores']);
        } catch (\Throwable) {
            $this->articleScoreTableExists = false;
        }

        return $this->articleScoreTableExists;
    }
}
