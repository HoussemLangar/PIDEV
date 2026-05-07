<?php

namespace App\Controller;

use App\Entity\Notification;
use App\Repository\NotificationRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/notification', name: 'notification_')]
class NotificationController extends AbstractController
{
    public function __construct(
        private readonly NotificationRepository $repository,
        private readonly EntityManagerInterface $entityManager,
    ) {}

    /**
     * GET /api/notification/mine
     * Retourne les notifications de l'utilisateur connecté
     */
    #[Route('/mine', name: 'mine', methods: ['GET'])]
    public function mine(): JsonResponse
    {
        $user = $this->getUser();
        if (!$user) {
            return new JsonResponse(['error' => 'Non authentifié'], Response::HTTP_UNAUTHORIZED);
        }

        $notifications = $this->repository->findBy(
            ['user' => $user],
            ['createdAt' => 'DESC'],
            20
        );

        $unreadCount = $this->repository->count(['user' => $user, 'lu' => false]);

        return new JsonResponse([
            'unread' => $unreadCount,
            'notifications' => array_map(fn (Notification $n) => [
                'id'        => $n->getId(),
                'type'      => $n->getType(),
                'titre'     => $n->getTitre(),
                'message'   => $n->getMessage(),
                'lu'        => $n->isLu(),
                'lien'      => $n->getLien(),
                'createdAt' => $n->getCreatedAt()->format('Y-m-d H:i:s'),
            ], $notifications),
        ]);
    }

    /**
     * POST /api/notification/read-all
     * Marque toutes les notifications de l'utilisateur comme lues
     */
    #[Route('/read-all', name: 'read_all', methods: ['POST'])]
    public function readAll(): JsonResponse
    {
        $user = $this->getUser();
        if (!$user) {
            return new JsonResponse(['error' => 'Non authentifié'], Response::HTTP_UNAUTHORIZED);
        }

        $notifications = $this->repository->findBy(['user' => $user, 'lu' => false]);
        foreach ($notifications as $n) {
            $n->setLu(true);
        }
        $this->entityManager->flush();

        return new JsonResponse(['success' => true]);
    }

    /**
     * GET /api/notification
     * Liste toutes les entités Notification
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): JsonResponse
    {
        $entities = $this->repository->findAll();

        $data = array_map(fn(Notification $entity) => $this->serializeEntity($entity), $entities);

        return new JsonResponse($data);
    }

    /**
     * GET /api/notification/{id}
     * Retourne une entité Notification par ID
     */
    #[Route('/{id}', name: 'show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(int $id): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Notification non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        return new JsonResponse($this->serializeEntity($entity));
    }

    /**
     * POST /api/notification
     * Crée une nouvelle entité Notification
     */
    #[Route('', name: 'create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);

        if ($data === null && json_last_error() !== JSON_ERROR_NONE) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        $entity = new Notification();
        $this->hydrateEntity($entity, $data);

        $this->repository->save($entity);

        return new JsonResponse($this->serializeEntity($entity), Response::HTTP_CREATED);
    }

    /**
     * PUT /api/notification/{id}
     * Met à jour une entité Notification existante
     */
    #[Route('/{id}', name: 'update', methods: ['PUT'], requirements: ['id' => '\\d+'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Notification non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        $data = json_decode($request->getContent(), true);

        if ($data === null && json_last_error() !== JSON_ERROR_NONE) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        $this->hydrateEntity($entity, $data);
        $this->repository->save($entity);

        return new JsonResponse($this->serializeEntity($entity));
    }

    /**
     * PATCH /api/notification/{id}
     * Mise à jour partielle d'une entité Notification
     */
    #[Route('/{id}', name: 'patch', methods: ['PATCH'], requirements: ['id' => '\\d+'])]
    public function patch(int $id, Request $request): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Notification non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        $data = json_decode($request->getContent(), true);

        if ($data === null && json_last_error() !== JSON_ERROR_NONE) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        $this->hydrateEntity($entity, $data, partiel: true);
        $this->repository->save($entity);

        return new JsonResponse($this->serializeEntity($entity));
    }

    /**
     * DELETE /api/notification/{id}
     * Supprime une entité Notification
     */
    #[Route('/{id}', name: 'delete', methods: ['DELETE'], requirements: ['id' => '\\d+'])]
    public function delete(int $id): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Notification non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        $this->repository->delete($entity);

        return new JsonResponse(['message' => 'Notification supprimée avec succès'], Response::HTTP_OK);
    }

    // ─── Serialisation ──────────────────────────────────────────────────────

    /**
     * Sérialise une entité Notification en tableau associatif
     */
    private function serializeEntity(Notification $entity): array
    {
        $data = ['id' => $entity->getId()];

        // Récupération dynamique via les getters publics
        $reflection = new \ReflectionClass($entity);
        foreach ($reflection->getMethods(\ReflectionMethod::IS_PUBLIC) as $method) {
            $name = $method->getName();
            if (str_starts_with($name, 'get') && $method->getNumberOfRequiredParameters() === 0) {
                $key = lcfirst(substr($name, 3));
                $value = $entity->{$name}();

                if ($value instanceof \DateTimeInterface) {
                    $data[$key] = $value->format('Y-m-d H:i:s');
                } elseif ($value instanceof \Doctrine\Common\Collections\Collection) {
                    $data[$key] = array_map(fn($item) => ['id' => $item->getId()], $value->toArray());
                } elseif (is_object($value) && method_exists($value, 'getId')) {
                    $data[$key] = ['id' => $value->getId()];
                } else {
                    $data[$key] = $value;
                }
            } elseif (str_starts_with($name, 'is') && $method->getNumberOfRequiredParameters() === 0) {
                $key = lcfirst(substr($name, 2));
                $data[$key] = $entity->{$name}();
            }
        }

        return $data;
    }

    // ─── Hydratation ────────────────────────────────────────────────────────

    /**
     * Hydrate une entité à partir d'un tableau de données.
     * En mode partiel, les champs absents ne sont pas modifiés.
     */
    private function hydrateEntity(Notification $entity, array $data, bool $partiel = false): void
    {
        $reflection = new \ReflectionClass($entity);

        foreach ($data as $key => $value) {
            // Convertir les clés snakeCase en camelCase pour le setter
            $camelKey = lcfirst(str_replace('_', '', ucwords($key, '_')));
            $setterName = 'set' . ucfirst($camelKey);

            if ($reflection->hasMethod($setterName)) {
                $method = $reflection->getMethod($setterName);
                $param = $method->getParameters()[0] ?? null;

                if ($param) {
                    $type = $param->getType();
                    $typeName = $type instanceof \ReflectionNamedType ? $type->getName() : null;

                    // Conversion automatique selon le type attendu
                    $converted = match ($typeName) {
                        'int' => (int) $value,
                        'float' => (float) $value,
                        'bool' => (bool) $value,
                        'DateTimeInterface', 'DateTimeImmutable' => $value ? new \DateTimeImmutable($value) : null,
                        default => $value,
                    };

                    $entity->{$setterName}($converted);
                }
            }
        }
    }
}
