<?php

namespace App\Controller\FrontOffice;

use App\Entity\Commentaire;
use App\Entity\Contenu;
use App\Entity\Like;
use App\Entity\User;
use App\Repository\CommentaireRepository;
use App\Repository\ContenuRepository;
use App\Repository\LikeRepository;
use App\Security\ContentVoter;
use App\Service\ContentRecommendationService;
use App\Service\ForbiddenWordsFilterService;
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
        private LikeRepository $likeRepository,
        private EntityManagerInterface $em
    ) {}

    #[Route('', name: 'list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        if (!$this->isGranted(ContentVoter::VIEW)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $page = (int) $request->query->get('page', 1);
        $limit = (int) $request->query->get('limit', 8);
        $type = $request->query->get('type') ?: null;
        $search = $request->query->get('q') ?: null;
        $category = $request->query->get('category') ?: null;
        $owner = $request->query->get('owner');
        $ownerId = null;
        $statuses = ['publie']; // Par défaut, n'afficher que les contenus publiés
        
        if ($owner === 'me') {
            /** @var User $user */
            $user = $this->getUser();
            $ownerId = $user->getId();
            $statuses = null; // Si l'utilisateur regarde ses propres contenus, afficher tous les statuts
        }

        $result = $this->contenuRepository->findPage($page, $limit, $type, $search, $category, $statuses, $ownerId);

        /** @var User $user */
        $user = $this->getUser();

        $items = array_map(function (Contenu $contenu) use ($user) {
            $auteur = $contenu->getAuteur();
            $description = $contenu->getDescription();
            if (!$description) {
                $description = mb_substr(strip_tags($contenu->getContenu()), 0, 140);
            }
            $like = $this->likeRepository->findOneByUserAndContenu($user->getId(), $contenu->getId());
            
            $isOwner = $auteur && $user instanceof User && $auteur->getId() === $user->getId();

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
            ];
        }, $result['items']);

        return new JsonResponse([
            'items' => $items,
            'page' => $page,
            'pages' => $result['pages'],
            'total' => $result['total'],
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
        
        // Permettre à l'auteur de voir son propre contenu quel que soit le statut
        if (!$isOwner && $contenu->getStatut() !== 'publie') {
            return new JsonResponse(['message' => 'Introuvable'], 404);
        }

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
        
        // Permettre à l'auteur de voir les commentaires de son propre contenu
        if (!$isOwner && $contenu->getStatut() !== 'publie') {
            return new JsonResponse(['items' => []]);
        }

        $comments = $this->commentaireRepository->findByContenu($contenu->getId());
        $items = array_map(function (Commentaire $comment) {
            $user = $comment->getUser();
            return [
                'id' => $comment->getId(),
                'user' => $user->getFullName() ?? $user->getEmail(),
                'message' => $comment->getCommentaire(),
                'date' => $comment->getCreatedAt()->format('d/m/Y H:i'),
                'user_id' => $user->getId(),
            ];
        }, $comments);

        return new JsonResponse(['items' => $items]);
    }

    #[Route('/{id}/comment', name: 'comment_create', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function createComment(
        Contenu $contenu,
        Request $request,
        ForbiddenWordsFilterService $filterService,
        ValidatorInterface $validator
    ): JsonResponse {
        if (!$this->isGranted(ContentVoter::INTERACT)) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        /** @var User $user */
        $user = $this->getUser();
        $auteur = $contenu->getAuteur();
        $isOwner = $user instanceof User && $auteur && $auteur->getId() === $user->getId();
        
        // Permettre à l'auteur d'interagir avec son propre contenu
        if (!$isOwner && $contenu->getStatut() !== 'publie') {
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

        $errors = $validator->validate($comment);
        if (count($errors) > 0) {
            $messages = [];
            foreach ($errors as $error) {
                $messages[] = $error->getMessage();
            }
            return new JsonResponse(['success' => false, 'errors' => $messages], 422);
        }

        $forbidden = $filterService->findForbiddenWords($message);
        if ($forbidden !== []) {
            return new JsonResponse([
                'success' => false,
                'message' => 'Votre commentaire contient des mots interdits.',
                'errors' => $forbidden,
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
        
        // Permettre à l'auteur d'interagir avec son propre contenu
        if (!$isOwner && $contenu->getStatut() !== 'publie') {
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
}
