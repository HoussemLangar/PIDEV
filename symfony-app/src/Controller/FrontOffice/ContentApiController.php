<?php

namespace App\Controller\FrontOffice;

use App\Entity\Commentaire;
use App\Entity\Contenu;
use App\Entity\Like;
use App\Entity\User;
use App\Repository\ArticleScoreRepository;
use App\Repository\CommentaireRepository;
use App\Repository\ContenuRepository;
use App\Repository\LikeRepository;
use App\Security\ContentVoter;
use App\Service\CommentModerationService;
use App\Service\CommentSentimentScoringService;
use App\Service\ContentRecommendationService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Validator\Validator\ValidatorInterface;

#[Route('/api/content', name: 'api_content_')]
class ContentApiController extends AbstractController
{
    public function __construct(
        private ContenuRepository $contenuRepository,
        private CommentaireRepository $commentaireRepository,
        private ArticleScoreRepository $articleScoreRepository,
        private LikeRepository $likeRepository,
        private CommentModerationService $commentModerationService,
        private CommentSentimentScoringService $commentSentimentScoringService,
        private EntityManagerInterface $em
    ) {}

    #[Route('', name: 'list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::VIEW)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $page = (int) $request->query->get('page', 1);
        $limit = max(1, min(50, (int) $request->query->get('limit', 8)));
        $type = $request->query->get('type') ?: null;
        $search = $request->query->get('q') ?: null;
        $category = $request->query->get('category') ?: null;
        $owner = $request->query->get('owner');
        /** @var User $user */
        $user = $this->getUser();
        $ownerId = null;
        $statuses = ['publie']; // Par défaut, n'afficher que les contenus publiés
        $isPatient = $user->getRole() === 'ROLE_PATIENT';

        if ($isPatient) {
            $statuses = null; // Les patients voient tous les contenus
        }
        
        if ($owner === 'me') {
            $ownerId = $user->getId();
            $statuses = null; // Si l'utilisateur regarde ses propres contenus, afficher tous les statuts
        }

        $allContents = $this->contenuRepository->findFiltered($type, $search, $category, $statuses, $ownerId);
        $contentIds = array_values(array_filter(array_map(
            static fn (Contenu $content): ?int => $content->getId(),
            $allContents
        )));
        $scoreMap = $this->articleScoreRepository->findScoreMapByContenuIds($contentIds);

        usort($allContents, function (Contenu $left, Contenu $right) use ($scoreMap): int {
            $leftId = (int) $left->getId();
            $rightId = (int) $right->getId();
            $leftScore = $this->resolveScoreData($scoreMap, $leftId)['score'];
            $rightScore = $this->resolveScoreData($scoreMap, $rightId)['score'];

            $scoreComparison = $rightScore <=> $leftScore;
            if ($scoreComparison !== 0) {
                return $scoreComparison;
            }

            $leftDate = $left->getDatePublication() ?? $left->getCreatedAt();
            $rightDate = $right->getDatePublication() ?? $right->getCreatedAt();
            $leftTimestamp = $leftDate?->getTimestamp() ?? 0;
            $rightTimestamp = $rightDate?->getTimestamp() ?? 0;
            if ($rightTimestamp !== $leftTimestamp) {
                return $rightTimestamp <=> $leftTimestamp;
            }

            return $rightId <=> $leftId;
        });

        $total = count($allContents);
        $pages = max(1, (int) ceil($total / $limit));
        $page = min(max(1, $page), $pages);
        $slice = array_slice($allContents, ($page - 1) * $limit, $limit);

        $items = array_map(function (Contenu $contenu) use ($user, $scoreMap) {
            $auteur = $contenu->getAuteur();
            $description = $contenu->getDescription();
            if (!$description) {
                $description = mb_substr(strip_tags($contenu->getContenu()), 0, 140);
            }
            $like = $this->likeRepository->findOneByUserAndContenu($user->getId(), $contenu->getId());
            $isOwner = $auteur && $user instanceof User && $auteur->getId() === $user->getId();
            $scoreData = $this->resolveScoreData($scoreMap, (int) $contenu->getId());

            return [
                'id' => $contenu->getId(),
                'titre' => $contenu->getTitre(),
                'description' => $description,
                'type' => $contenu->getType(),
                'auteur' => $auteur ? ($auteur->getFullName() ?? $auteur->getEmail()) : 'Anonyme',
                'date' => ($contenu->getDatePublication() ?? $contenu->getCreatedAt())->format('d/m/Y'),
                'likes' => $contenu->getLikes()->count(),
                'commentaires' => $contenu->getCommentaires()->count(),
                'liked' => $like !== null,
                'statut' => $isOwner ? $contenu->getStatut() : null,
                'isOwner' => $isOwner,
                'score' => round($scoreData['score'], 4),
                'score_count' => $scoreData['count'],
            ];
        }, $slice);

        return new JsonResponse([
            'items' => $items,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
        ]);
    }

    #[Route('/recommended', name: 'recommended', methods: ['GET'])]
    public function recommended(ContentRecommendationService $recommendationService): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::VIEW)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }
        /** @var User $user */
        $user = $this->getUser();
        $items = $recommendationService->getRecommendedFor($user, 6);

        $data = array_map(function (Contenu $contenu) use ($user) {
            $description = $contenu->getDescription() ?: mb_substr(strip_tags($contenu->getContenu()), 0, 140);
            $like = $this->likeRepository->findOneByUserAndContenu($user->getId(), $contenu->getId());
            return [
                'id' => $contenu->getId(),
                'titre' => $contenu->getTitre(),
                'description' => $description,
                'type' => $contenu->getType(),
                'date' => ($contenu->getDatePublication() ?? $contenu->getCreatedAt())->format('d/m/Y'),
                'likes' => $contenu->getLikes()->count(),
                'commentaires' => $contenu->getCommentaires()->count(),
                'liked' => $like !== null,
            ];
        }, $items);

        return new JsonResponse(['items' => $data]);
    }

    #[Route('/{id}', name: 'show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(Contenu $contenu): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::VIEW)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }
        
        /** @var User $user */
        $user = $this->getUser();
        $auteur = $contenu->getAuteur();
        $isOwner = $user instanceof User && $auteur && $auteur->getId() === $user->getId();
        $isPatient = $user instanceof User && $user->getRole() === 'ROLE_PATIENT';
        
        // Permettre à l'auteur de voir son propre contenu quel que soit le statut
        if (!$isOwner && !$isPatient && $contenu->getStatut() !== 'publie') {
            return new JsonResponse(['message' => 'Introuvable'], 404);
        }

        $scoreData = $this->resolveScoreData(
            $this->articleScoreRepository->findScoreMapByContenuIds([(int) $contenu->getId()]),
            (int) $contenu->getId()
        );

        return new JsonResponse([
            'id' => $contenu->getId(),
            'titre' => $contenu->getTitre(),
            'description' => $contenu->getDescription(),
            'type' => $contenu->getType(),
            'contenu' => $contenu->getContenu(),
            'auteur' => $auteur ? ($auteur->getFullName() ?? $auteur->getEmail()) : 'Anonyme',
            'date' => ($contenu->getDatePublication() ?? $contenu->getCreatedAt())->format('d/m/Y'),
            'likes' => $contenu->getLikes()->count(),
            'commentaires' => $contenu->getCommentaires()->count(),
            'score' => round($scoreData['score'], 4),
            'score_count' => $scoreData['count'],
        ]);
    }

    #[Route('/{id}/comments', name: 'comments', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function comments(Contenu $contenu): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::VIEW)) {
            return new JsonResponse(['items' => []], 403);
        }
        
        /** @var User $user */
        $user = $this->getUser();
        $auteur = $contenu->getAuteur();
        $isOwner = $user instanceof User && $auteur && $auteur->getId() === $user->getId();
        $isPatient = $user instanceof User && $user->getRole() === 'ROLE_PATIENT';
        
        // Permettre à l'auteur de voir les commentaires de son propre contenu
        if (!$isOwner && !$isPatient && $contenu->getStatut() !== 'publie') {
            return new JsonResponse(['items' => []]);
        }

        $comments = $this->commentaireRepository->findPublishedByContenu($contenu->getId());
        $items = [];
        foreach ($comments as $comment) {
            $user = $comment->getUser();
            $signedScore = $this->resolveCommentSignedScore($comment);
            $items[] = [
                'id' => $comment->getId(),
                'user' => $user->getFullName() ?? $user->getEmail(),
                'message' => $comment->getCommentaire(),
                'date' => $comment->getCreatedAt()->format('d/m/Y H:i'),
                'user_id' => $user->getId(),
                'score' => round($signedScore, 4),
                'sentiment' => $this->sentimentFromSignedScore($signedScore),
            ];
        }

        return new JsonResponse(['items' => $items]);
    }

    #[Route('/{id}/comment', name: 'comment_create', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function createComment(
        Contenu $contenu,
        Request $request,
        ValidatorInterface $validator
    ): JsonResponse {
        if (!$this->isGranted(ContentVoter::INTERACT)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        /** @var User $user */
        $user = $this->getUser();
        $auteur = $contenu->getAuteur();
        $isOwner = $user instanceof User && $auteur && $auteur->getId() === $user->getId();
        $isPatient = $user instanceof User && $user->getRole() === 'ROLE_PATIENT';
        
        // Permettre à l'auteur d'interagir avec son propre contenu
        if (!$isOwner && !$isPatient && $contenu->getStatut() !== 'publie') {
            return new JsonResponse(['success' => false, 'message' => 'Contenu indisponible'], 404);
        }

        if (!$this->isCsrfTokenValid('content_action', (string) $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide'], 403);
        }

        $payload = json_decode($request->getContent(), true) ?: [];
        $message = trim((string) ($payload['message'] ?? ''));

        $comment = new Commentaire();
        $comment->setContenu($contenu);
        /** @var User $user */
        $user = $this->getUser();
        $comment->setUser($user);
        $comment->setCommentaire($message);
        $analysis = $this->commentSentimentScoringService->analyze($message);
        $signedScore = $this->commentSentimentScoringService->toSignedScore($analysis);
        $comment->setNote((int) round($signedScore * 100));

        $errors = $validator->validate($comment);
        if (count($errors) > 0) {
            $messages = [];
            foreach ($errors as $error) {
                $messages[] = $error->getMessage();
            }
            return new JsonResponse(['success' => false, 'errors' => $messages], 422);
        }

        $moderation = $this->commentModerationService->moderate($message);
        if ($moderation['blocked']) {
            return new JsonResponse([
                'success' => false,
                'message' => 'Commentaire supprimé pour contenu inapproprié.',
                'reason' => $moderation['label'],
            ], 422);
        }

        $this->em->persist($comment);
        $this->em->flush();

        return new JsonResponse([
            'success' => true,
            'comment' => [
                'id' => $comment->getId(),
                'user' => $user->getFullName() ?? $user->getEmail(),
                'message' => $comment->getCommentaire(),
                'date' => $comment->getCreatedAt()->format('d/m/Y H:i'),
                'user_id' => $user->getId(),
                'score' => round($signedScore, 4),
                'sentiment' => $this->sentimentFromSignedScore($signedScore),
            ],
            'count' => $this->commentaireRepository->countByContenu($contenu->getId()),
        ]);
    }

    #[Route('/comments/{id}/delete', name: 'comment_delete', methods: ['POST'])]
    public function deleteComment(Commentaire $comment, Request $request): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::INTERACT)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }
        if (!$this->isCsrfTokenValid('content_action', (string) $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide'], 403);
        }

        /** @var User $user */
        $user = $this->getUser();
        if ($comment->getUser()->getId() !== $user->getId() && !in_array('ROLE_ADMIN', $user->getRoles(), true)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $this->em->remove($comment);
        $this->em->flush();

        return new JsonResponse([
            'success' => true,
            'count' => $this->commentaireRepository->countByContenu($comment->getContenu()->getId()),
        ]);
    }

    #[Route('/{id}/like', name: 'like', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function toggleLike(Contenu $contenu, Request $request): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::INTERACT)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }
        
        /** @var User $user */
        $user = $this->getUser();
        $auteur = $contenu->getAuteur();
        $isOwner = $user instanceof User && $auteur && $auteur->getId() === $user->getId();
        $isPatient = $user instanceof User && $user->getRole() === 'ROLE_PATIENT';
        
        // Permettre à l'auteur d'interagir avec son propre contenu
        if (!$isOwner && !$isPatient && $contenu->getStatut() !== 'publie') {
            return new JsonResponse(['success' => false, 'message' => 'Contenu indisponible'], 404);
        }
        if (!$this->isCsrfTokenValid('content_action', (string) $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide'], 403);
        }

        /** @var User $user */
        $user = $this->getUser();
        $existing = $this->likeRepository->findOneByUserAndContenu($user->getId(), $contenu->getId());

        if ($existing) {
            $this->em->remove($existing);
            $liked = false;
        } else {
            $like = new Like();
            $like->setUser($user);
            $like->setContenu($contenu);
            $this->em->persist($like);
            $liked = true;
        }

        $this->em->flush();

        return new JsonResponse([
            'success' => true,
            'liked' => $liked,
            'count' => $this->likeRepository->countByContenu($contenu->getId()),
        ]);
    }

    /**
     * @param array<int, Contenu> $contents
     * @return array<int, array{id:int,user:string,message:string,date:string,score:float,sentiment:string,contenu_id:int,contenu_titre:string}>
     */
    private function buildTopComments(array $contents): array
    {
        $comments = [];
        foreach ($contents as $content) {
            foreach ($content->getCommentaires() as $comment) {
                if (!$comment instanceof Commentaire) {
                    continue;
                }
                if ($comment->getStatut() !== 'publie') {
                    continue;
                }

                $signedScore = $this->resolveCommentSignedScore($comment);
                $comments[] = [
                    'id' => $comment->getId(),
                    'user' => $comment->getUser()->getFullName() ?? $comment->getUser()->getEmail(),
                    'message' => $comment->getCommentaire(),
                    'date' => $comment->getCreatedAt()->format('d/m/Y H:i'),
                    'score' => round($signedScore, 4),
                    'sentiment' => $this->sentimentFromSignedScore($signedScore),
                    'contenu_id' => $content->getId(),
                    'contenu_titre' => $content->getTitre(),
                ];
            }
        }

        usort($comments, static fn (array $a, array $b): int => ($b['score'] <=> $a['score']));
        return array_slice($comments, 0, 4);
    }

    /**
     * @param array<int, array{score: float, count: int}> $scoreMap
     * @return array{score: float, count: int}
     */
    private function resolveScoreData(array $scoreMap, int $contenuId): array
    {
        if ($contenuId <= 0 || !isset($scoreMap[$contenuId])) {
            return ['score' => 0.0, 'count' => 0];
        }

        return [
            'score' => (float) ($scoreMap[$contenuId]['score'] ?? 0.0),
            'count' => (int) ($scoreMap[$contenuId]['count'] ?? 0),
        ];
    }

    private function resolveCommentSignedScore(Commentaire $comment): float
    {
        $note = $comment->getNote();
        if (!is_numeric($note)) {
            return 0.0;
        }

        return max(-1.0, min(1.0, ((float) $note) / 100.0));
    }

    private function sentimentFromSignedScore(float $score): string
    {
        if ($score > 0.0) {
            return 'POSITIVE';
        }
        if ($score < 0.0) {
            return 'NEGATIVE';
        }

        return 'NEUTRAL';
    }
}
