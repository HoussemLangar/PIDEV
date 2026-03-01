<?php

namespace App\Controller;

use App\Entity\SymptomeQuotidien;
use App\Form\SymptomeQuotidienType;
use App\Repository\SymptomeQuotidienRepository;
use App\Repository\SymptomeListeRepository;
use App\Service\RiskPredictionService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Component\Validator\Validator\ValidatorInterface;

class SymptomeQuotidienController extends AbstractController
{
    public function __construct(
        private readonly SymptomeQuotidienRepository $repository,
        private readonly EntityManagerInterface $entityManager,
        private readonly RiskPredictionService $riskPredictionService,
    ) {}

    /**
     * Interface web pour ajouter un symptôme
     */
    #[Route('/symptomes', name: 'app_symptomes', methods: ['GET', 'POST'])]
    #[IsGranted('ROLE_PATIENT')]
    public function webInterface(Request $request, EntityManagerInterface $em, SymptomeQuotidienRepository $symptomeRepository): Response
    {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User || !$user->getPatient()) {
            throw $this->createAccessDeniedException('Accès réservé aux patients.');
        }
        $patient = $user->getPatient();
        
        // Création d'un symptome quotidien
        $symptomeQuotidien = new SymptomeQuotidien();
        // Initialiser les propriétés requises avec des valeurs par défaut
        $symptomeQuotidien->setIntensite(5); // valeur par défaut pour l'intensité
        
        // S'assurer que la date par défaut utilise le bon timezone
        $dateActuelle = new \DateTime('now', new \DateTimeZone('Europe/Paris'));
        $symptomeQuotidien->setDateSymptome($dateActuelle);
        // Ne pas assigner le patient ici pour éviter les erreurs Doctrine
        
        // Récupérer la catégorie sélectionnée depuis la requête
        $categorieSelectionnee = $request->query->get('categorie', '');
        
        $form = $this->createForm(SymptomeQuotidienType::class, $symptomeQuotidien, [
            'categorie_selectionnee' => $categorieSelectionnee
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            // Assigner le patient seulement au moment de la persistance
            $symptomeQuotidien->setPatient($patient);
            
            // Correction timezone - récréer la date avec le bon timezone
            $dateSymptome = $symptomeQuotidien->getDateSymptome();
            if ($dateSymptome) {
                // Récupérer la date au format string et la recréer avec le bon timezone
                $dateString = $dateSymptome->format('Y-m-d');
                $nouvelleDateSymptome = \DateTime::createFromFormat('Y-m-d H:i:s', $dateString . ' 12:00:00', new \DateTimeZone('Europe/Paris'));
                $symptomeQuotidien->setDateSymptome($nouvelleDateSymptome);
            }
            
            $em->persist($symptomeQuotidien);
            $em->flush();
            $this->riskPredictionService->recalculateForUser($user);

            $this->addFlash('success', '✅ Symptôme enregistré avec succès!');
            return $this->redirectToRoute('app_symptomes');
        }

        // Pour l'instant, liste vide des derniers symptômes
        // En attendant la configuration complète du système d'authentification
        $derniersSymptomes = [];
        
        // Récupérer les dates des symptômes enregistrés pour le calendrier
        $symptomesAvecDates = $symptomeRepository->createQueryBuilder('s')
            ->select('s.dateSymptome')
            ->where('s.patient = :patient')
            ->setParameter('patient', $patient)
            ->getQuery()
            ->getResult();
        
        // Transformer en format de date simple pour le template
        $datesAvecSymptomes = [];
        foreach ($symptomesAvecDates as $symptome) {
            $dateStr = $symptome['dateSymptome']->format('Y-m-d');
            if (!in_array($dateStr, $datesAvecSymptomes)) {
                $datesAvecSymptomes[] = $dateStr;
            }
        }

        // Récupérer les statistiques des symptômes pour l'affichage
        $topSymptoms = [];
        try {
            $topSymptoms = $symptomeRepository->getTopSymptomStatistics($patient, 6);
        } catch (\Exception $e) {
            // En cas d'erreur, laisser vide
        }

        return $this->render('front/santequotidienne/symptomes.html.twig', [
            'form' => $form,
            'derniers_symptomes' => $derniersSymptomes,
            'categorie_selectionnee' => $categorieSelectionnee,
            'dates_symptomes' => $datesAvecSymptomes,
            'top_symptoms' => $topSymptoms
        ]);
    }

    #[Route('/sante-quotidienne', name: 'app_sante_quotidienne')]
    public function santeQuotidienne(): Response
    {
        return $this->render('front/santequotidienne/index.html.twig');
    }

    #[Route('/symptomes/filter-by-category', name: 'app_symptomes_filter', methods: ['POST'])]
    #[IsGranted('ROLE_PATIENT')]
    public function filterSymptomesByCategory(Request $request, SymptomeListeRepository $symptomeListeRepository): JsonResponse
    {
        $categorie = $request->request->get('categorie');

        if (empty($categorie)) {
            // Si aucune catégorie, retourner tous les symptômes
            $symptomes = $symptomeListeRepository->findBy([], ['categorie' => 'ASC', 'nom' => 'ASC']);
        } else {
            // Filtrer par catégorie
            $symptomes = $symptomeListeRepository->findByCategorie($categorie);
        }

        $data = [];
        foreach ($symptomes as $symptome) {
            $data[] = [
                'id' => $symptome->getId(),
                'nom' => $symptome->getNom(),
                'categorie' => $symptome->getCategorie()
            ];
        }

        return new JsonResponse($data);
    }

    #[Route('/symptomes/by-date/{date}', name: 'app_symptomes_by_date', methods: ['GET'])]
    #[IsGranted('ROLE_PATIENT')]
    public function getSymptomesByDate(string $date, SymptomeQuotidienRepository $symptomeRepository): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User || !$user->getPatient()) {
            return new JsonResponse(['error' => 'Accès refusé'], 403);
        }
        $patient = $user->getPatient();

        $symptomes = $symptomeRepository->findByDateForPatient($date, $patient);

        $data = [];
        foreach ($symptomes as $symptome) {
            $data[] = [
                'id' => $symptome->getId(),
                'symptome' => $symptome->getSymptome()->getNom(),
                'categorie' => $symptome->getSymptome()->getCategorie(),
                'intensite' => $symptome->getIntensite(),
                'duree' => $symptome->getDuree(),
                'notes' => $symptome->getNotes(),
                'heureCreation' => $symptome->getCreatedAt()->format('H:i')
            ];
        }

        return new JsonResponse($data);
    }

    #[Route('/symptomes/delete/{id}', name: 'app_symptome_delete', methods: ['DELETE'])]
    #[IsGranted('ROLE_PATIENT')]
    public function deleteSymptome(int $id, SymptomeQuotidienRepository $symptomeRepository, EntityManagerInterface $em): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User || !$user->getPatient()) {
            return new JsonResponse(['error' => 'Accès refusé'], 403);
        }
        $patient = $user->getPatient();

        $symptome = $symptomeRepository->find($id);

        if (!$symptome || $symptome->getPatient() !== $patient) {
            return new JsonResponse(['error' => 'Symptôme non trouvé'], 404);
        }

        $em->remove($symptome);
        $em->flush();
        $this->riskPredictionService->recalculateForUser($user);

        return new JsonResponse(['success' => true, 'message' => 'Symptôme supprimé avec succès']);
    }

    #[Route('/symptomes/edit/{id}', name: 'app_symptome_edit', methods: ['POST'])]
    #[IsGranted('ROLE_PATIENT')]
    public function editSymptome(int $id, Request $request, SymptomeQuotidienRepository $symptomeRepository, EntityManagerInterface $em, ValidatorInterface $validator): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User || !$user->getPatient()) {
            return new JsonResponse(['success' => false, 'error' => 'Accès refusé'], 403);
        }
        $patient = $user->getPatient();

        $symptome = $symptomeRepository->find($id);

        if (!$symptome || $symptome->getPatient() !== $patient) {
            return new JsonResponse(['success' => false, 'error' => 'Symptôme non trouvé'], 404);
        }

        $data = json_decode($request->getContent(), true);

        // Mettre à jour les données
        if (isset($data['intensite'])) {
            $symptome->setIntensite((int)$data['intensite']);
        }
        if (isset($data['duree'])) {
            $symptome->setDuree(trim($data['duree']));
        }
        if (isset($data['notes'])) {
            $symptome->setNotes(trim($data['notes']));
        }

        // Validation avec le validator Symfony (données d'édition)
        $dataConstraints = new Assert\Collection([
            'intensite' => [
                new Assert\NotBlank(message: 'Veuillez renseigner ce champ.'),
                new Assert\Range(
                    min: 1,
                    max: 10,
                    minMessage: 'L\'intensité doit être au moins {{ limit }}.',
                    maxMessage: 'L\'intensité ne peut pas dépasser {{ limit }}.'
                ),
            ],
            'duree' => [
                new Assert\NotBlank(message: 'Veuillez renseigner ce champ.'),
            ],
            'notes' => [
                new Assert\Length(max: 100, maxMessage: 'Les notes ne peuvent pas dépasser {{ limit }} caractères.'),
            ],
        ], allowExtraFields: true, allowMissingFields: false);

        $dataViolations = $validator->validate($data, $dataConstraints);
        if (count($dataViolations) > 0) {
            $errors = [];
            foreach ($dataViolations as $violation) {
                $path = $violation->getPropertyPath();
                $path = trim((string) $path, '[]');
                if (!$path) {
                    $path = 'form';
                }
                $errors[$path][] = $violation->getMessage();
            }

            return new JsonResponse([
                'success' => false,
                'errors' => $errors,
                'error' => implode(', ', array_merge(...array_values($errors)))
            ], 400);
        }

        // Validation entité Symfony
        $errors = $validator->validate($symptome);

        if (count($errors) > 0) {
            // Collecter tous les messages d'erreur
            $errorMessages = [];
            foreach ($errors as $error) {
                $errorMessages[] = $error->getMessage();
            }

            return new JsonResponse([
                'success' => false,
                'error' => implode(', ', $errorMessages)
            ], 400);
        }

        try {
            $em->flush();
            $this->riskPredictionService->recalculateForUser($user);
            return new JsonResponse(['success' => true, 'message' => 'Symptôme modifié avec succès']);
        } catch (\Exception $e) {
            return new JsonResponse(['success' => false, 'error' => 'Erreur lors de la modification: ' . $e->getMessage()], 500);
        }
    }

    /**
     * Route pour récupérer les statistiques des symptômes
     */
    #[Route('/symptomes/statistics', name: 'app_symptomes_statistics', methods: ['GET'])]
    #[IsGranted('ROLE_PATIENT')]
    public function getSymptomStatistics(
        SymptomeQuotidienRepository $symptomeRepository
    ): JsonResponse {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User || !$user->getPatient()) {
            return new JsonResponse([
                'success' => false,
                'statistics' => [],
                'totalSymptoms' => 0,
                'overallAverageIntensity' => 0.0,
                'message' => 'Accès refusé'
            ], 403);
        }
        $patient = $user->getPatient();

        try {
            // Récupérer les statistiques depuis le repository
            $topSymptoms = $symptomeRepository->getTopSymptomStatistics($patient, 6);

            // Formater les données pour le frontend
            $formattedStats = [];
            foreach ($topSymptoms as $stat) {
                $lastDate = null;
                if (isset($stat['lastDate'])) {
                    if ($stat['lastDate'] instanceof \DateTimeInterface) {
                        $lastDate = $stat['lastDate']->format('Y-m-d');
                    } elseif (is_string($stat['lastDate'])) {
                        $lastDate = $stat['lastDate'];
                    }
                }

                $formattedStats[] = [
                    'nom' => $stat['symptome_nom'] ?? $stat['nom'] ?? '',
                    'categorie' => $stat['categorie_nom'] ?? $stat['categorie'] ?? 'Général',
                    'count' => (int)$stat['count'],
                    'averageIntensity' => number_format((float)($stat['avg_intensite'] ?? $stat['averageIntensity'] ?? 0), 1),
                    'lastDate' => $lastDate
                ];
            }

            return new JsonResponse([
                'success' => true,
                'statistics' => $formattedStats,
                'totalSymptoms' => $symptomeRepository->getTotalSymptomsCount($patient),
                'overallAverageIntensity' => $symptomeRepository->getAverageIntensity($patient)
            ]);

        } catch (\Exception $e) {
            // Retourner des statistiques vides en cas d'erreur
            return new JsonResponse([
                'success' => true,
                'statistics' => [],
                'totalSymptoms' => 0,
                'overallAverageIntensity' => 0.0,
                'message' => 'Aucun symptôme enregistré'
            ]);
        }
    }

    /**
     * GET /api/symptome-quotidien
     * Liste toutes les entités SymptomeQuotidien
     */
    #[Route('/api/symptome-quotidien', name: 'api_symptome_quotidien_index', methods: ['GET'])]
    public function index(): JsonResponse
    {
        $entities = $this->repository->findAll();

        $data = array_map(fn(SymptomeQuotidien $entity) => $this->serializeEntity($entity), $entities);

        return new JsonResponse($data);
    }

    /**
     * GET /api/symptome-quotidien/{id}
     * Retourne une entité SymptomeQuotidien par ID
     */
    #[Route('/api/symptome-quotidien/{id}', name: 'api_symptome_quotidien_show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(int $id): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'SymptomeQuotidien non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        return new JsonResponse($this->serializeEntity($entity));
    }

    /**
     * POST /api/symptome-quotidien
     * Crée une nouvelle entité SymptomeQuotidien
     */
    #[Route('/api/symptome-quotidien', name: 'api_symptome_quotidien_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);

        if ($data === null && json_last_error() !== JSON_ERROR_NONE) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        $entity = new SymptomeQuotidien();
        $this->hydrateEntity($entity, $data);

        $this->repository->save($entity);

        return new JsonResponse($this->serializeEntity($entity), Response::HTTP_CREATED);
    }

    /**
     * PUT /api/symptome-quotidien/{id}
     * Met à jour une entité SymptomeQuotidien existante
     */
    #[Route('/api/symptome-quotidien/{id}', name: 'api_symptome_quotidien_update', methods: ['PUT'], requirements: ['id' => '\\d+'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'SymptomeQuotidien non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
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
     * PATCH /api/symptome-quotidien/{id}
     * Mise à jour partielle d'une entité SymptomeQuotidien
     */
    #[Route('/api/symptome-quotidien/{id}', name: 'api_symptome_quotidien_patch', methods: ['PATCH'], requirements: ['id' => '\\d+'])]
    public function patch(int $id, Request $request): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'SymptomeQuotidien non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
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
     * DELETE /api/symptome-quotidien/{id}
     * Supprime une entité SymptomeQuotidien
     */
    #[Route('/api/symptome-quotidien/{id}', name: 'api_symptome_quotidien_delete', methods: ['DELETE'], requirements: ['id' => '\\d+'])]
    public function delete(int $id): JsonResponse
    {
        $entity = $this->repository->findById($id);

        if (!$entity) {
            return new JsonResponse(['error' => 'SymptomeQuotidien non trouvée avec l\'ID ' . $id], Response::HTTP_NOT_FOUND);
        }

        $this->repository->delete($entity);

        return new JsonResponse(['message' => 'SymptomeQuotidien supprimée avec succès'], Response::HTTP_OK);
    }


    // ─── Serialisation ──────────────────────────────────────────────────────

    /**
     * Sérialise une entité SymptomeQuotidien en tableau associatif
     */
    private function serializeEntity(SymptomeQuotidien $entity): array
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
    private function hydrateEntity(SymptomeQuotidien $entity, array $data, bool $partiel = false): void
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
