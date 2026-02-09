<?php

namespace App\Controller;

use App\Entity\Disponibilite;
use App\Repository\DisponibiliteRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/disponibilite', name: 'disponibilite_')]
class DisponibiliteController extends AbstractController
{
    public function __construct(
        private readonly DisponibiliteRepository $repository,
        private readonly EntityManagerInterface $entityManager,
    ) {}

    /**
     * GET /api/disponibilite
     * Liste toutes les entités Disponibilite
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): JsonResponse
    {
        $entities = $this->repository->findAll();

        $data = array_map(fn(Disponibilite $entity) => $this->serializeEntity($entity), $entities);

        return new JsonResponse($data);
    }

    /**
     * GET /api/disponibilite/{id}
     * Retourne une entité Disponibilite par ID
     */
    #[Route('/{id}', name: 'show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(int $id): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Disponibilite non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        return new JsonResponse($this->serializeEntity($entity));
    }

    /**
     * POST /api/disponibilite
     * Crée une nouvelle entité Disponibilite
     */
    #[Route('', name: 'create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);

        if ($data === null && json_last_error() !== JSON_ERROR_NONE) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        $entity = new Disponibilite();
        $this->hydrateEntity($entity, $data);

        $this->repository->save($entity);

        return new JsonResponse($this->serializeEntity($entity), Response::HTTP_CREATED);
    }

    /**
     * PUT /api/disponibilite/{id}
     * Met à jour une entité Disponibilite existante
     */
    #[Route('/{id}', name: 'update', methods: ['PUT'], requirements: ['id' => '\\d+'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Disponibilite non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
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
     * PATCH /api/disponibilite/{id}
     * Mise à jour partielle d'une entité Disponibilite
     */
    #[Route('/{id}', name: 'patch', methods: ['PATCH'], requirements: ['id' => '\\d+'])]
    public function patch(int $id, Request $request): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Disponibilite non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
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
     * DELETE /api/disponibilite/{id}
     * Supprime une entité Disponibilite
     */
    #[Route('/{id}', name: 'delete', methods: ['DELETE'], requirements: ['id' => '\\d+'])]
    public function delete(int $id): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'Disponibilite non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        $this->repository->delete($entity);

        return new JsonResponse(['message' => 'Disponibilite supprimée avec succès'], Response::HTTP_OK);
    }

    // ─── Serialisation ──────────────────────────────────────────────────────

    /**
     * Sérialise une entité Disponibilite en tableau associatif
     */
    private function serializeEntity(Disponibilite $entity): array
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
    private function hydrateEntity(Disponibilite $entity, array $data, bool $partiel = false): void
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
                    $typeName = $type ? $type->getName() : null;

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
