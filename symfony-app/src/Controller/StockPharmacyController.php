<?php

namespace App\Controller;

use App\Entity\Pharmacy;
use App\Entity\StockPharmacy;
use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Repository\StockPharmacyRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/pharmacy/{pharmacyId}/medicament', name: 'pharmacy_medicament_')]
class StockPharmacyController extends AbstractController
{
    public function __construct(
        private readonly PharmacyRepository $pharmacyRepository,
        private readonly MedicamentRepository $medicamentRepository,
        private readonly StockPharmacyRepository $stockPharmacyRepository,
        private readonly EntityManagerInterface $entityManager,
    ) {}

    /**
     * GET /api/pharmacy/{pharmacyId}/medicament
     * Liste les médicaments d'une pharmacie
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(int $pharmacyId, Request $request): JsonResponse
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            return new JsonResponse(['error' => 'Pharmacie introuvable'], Response::HTTP_NOT_FOUND);
        }

        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'id');
        $sortDir = $request->query->get('dir', 'asc');

        $qb = $this->buildStockQuery($pharmacy, $search, $sortBy, $sortDir);
        $stocks = $qb->getQuery()->getResult();

        $data = array_map(fn(StockPharmacy $stock) => $this->serializeStock($stock), $stocks);

        return new JsonResponse($data);
    }

    /**
     * GET /api/pharmacy/{pharmacyId}/medicament/{stockId}
     * Retourne un stock de médicament par ID
     */
    #[Route('/{stockId}', name: 'show', methods: ['GET'], requirements: ['stockId' => '\\d+'])]
    public function show(int $pharmacyId, int $stockId): JsonResponse
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            return new JsonResponse(['error' => 'Pharmacie introuvable'], Response::HTTP_NOT_FOUND);
        }

        $stock = $this->stockPharmacyRepository->find($stockId);
        if (!$stock || $stock->getPharmecie()->getId() !== $pharmacyId) {
            return new JsonResponse(['error' => 'Stock non trouvé'], Response::HTTP_NOT_FOUND);
        }

        return new JsonResponse($this->serializeStock($stock));
    }

    /**
     * POST /api/pharmacy/{pharmacyId}/medicament
     * Crée un nouveau stock de médicament
     */
    #[Route('', name: 'create', methods: ['POST'])]
    public function create(int $pharmacyId, Request $request): JsonResponse
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            return new JsonResponse(['error' => 'Pharmacie introuvable'], Response::HTTP_NOT_FOUND);
        }

        $data = json_decode($request->getContent(), true);
        if (!is_array($data)) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        [$errors, $medicament, $quantite, $prixVente, $dateExpiration] = $this->validatePayload($data, $pharmacy, null, false);
        if ($errors) {
            return new JsonResponse(['errors' => $errors], Response::HTTP_BAD_REQUEST);
        }

        $stock = new StockPharmacy();
        $stock->setPharmecie($pharmacy);
        $stock->setMedicament($medicament);
        $stock->setQuantite($quantite);
        $stock->setPrixVente($prixVente);
        $stock->setDateExpiration($dateExpiration);
        $stock->setUpdatedAt(new \DateTime());

        $this->entityManager->persist($stock);
        $this->entityManager->flush();

        return new JsonResponse($this->serializeStock($stock), Response::HTTP_CREATED);
    }

    /**
     * PUT /api/pharmacy/{pharmacyId}/medicament/{stockId}
     * Met à jour un stock existant
     */
    #[Route('/{stockId}', name: 'update', methods: ['PUT'], requirements: ['stockId' => '\\d+'])]
    public function update(int $pharmacyId, int $stockId, Request $request): JsonResponse
    {
        return $this->updateStock($pharmacyId, $stockId, $request, false);
    }

    /**
     * PATCH /api/pharmacy/{pharmacyId}/medicament/{stockId}
     * Mise à jour partielle d'un stock
     */
    #[Route('/{stockId}', name: 'patch', methods: ['PATCH'], requirements: ['stockId' => '\\d+'])]
    public function patch(int $pharmacyId, int $stockId, Request $request): JsonResponse
    {
        return $this->updateStock($pharmacyId, $stockId, $request, true);
    }

    /**
     * DELETE /api/pharmacy/{pharmacyId}/medicament/{stockId}
     * Supprime un stock
     */
    #[Route('/{stockId}', name: 'delete', methods: ['DELETE'], requirements: ['stockId' => '\\d+'])]
    public function delete(int $pharmacyId, int $stockId): JsonResponse
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            return new JsonResponse(['error' => 'Pharmacie introuvable'], Response::HTTP_NOT_FOUND);
        }

        $stock = $this->stockPharmacyRepository->find($stockId);
        if (!$stock || $stock->getPharmecie()->getId() !== $pharmacyId) {
            return new JsonResponse(['error' => 'Stock non trouvé'], Response::HTTP_NOT_FOUND);
        }

        $this->entityManager->remove($stock);
        $this->entityManager->flush();

        return new JsonResponse(['message' => 'Stock supprimé avec succès'], Response::HTTP_OK);
    }

    private function updateStock(int $pharmacyId, int $stockId, Request $request, bool $partial): JsonResponse
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            return new JsonResponse(['error' => 'Pharmacie introuvable'], Response::HTTP_NOT_FOUND);
        }

        $stock = $this->stockPharmacyRepository->find($stockId);
        if (!$stock || $stock->getPharmecie()->getId() !== $pharmacyId) {
            return new JsonResponse(['error' => 'Stock non trouvé'], Response::HTTP_NOT_FOUND);
        }

        $data = json_decode($request->getContent(), true);
        if (!is_array($data)) {
            return new JsonResponse(['error' => 'Données JSON invalides'], Response::HTTP_BAD_REQUEST);
        }

        [$errors, $medicament, $quantite, $prixVente, $dateExpiration] = $this->validatePayload($data, $pharmacy, $stock, $partial);
        if ($errors) {
            return new JsonResponse(['errors' => $errors], Response::HTTP_BAD_REQUEST);
        }

        if ($medicament) {
            $stock->setMedicament($medicament);
        }
        if ($quantite !== null) {
            $stock->setQuantite($quantite);
        }
        if (array_key_exists('prixVente', $data)) {
            $stock->setPrixVente($prixVente);
        }
        if (array_key_exists('dateExpiration', $data)) {
            $stock->setDateExpiration($dateExpiration);
        }
        $stock->setUpdatedAt(new \DateTime());

        $this->entityManager->flush();

        return new JsonResponse($this->serializeStock($stock));
    }

    private function buildStockQuery(Pharmacy $pharmacy, ?string $search, string &$sortBy, string &$sortDir)
    {
        $validSort = ['id', 'nom', 'dosage', 'forme', 'quantite', 'createdAt', 'prixVente', 'dateExpiration'];
        if (!in_array($sortBy, $validSort, true)) {
            $sortBy = 'id';
        }
        if (!in_array($sortDir, ['asc', 'desc'], true)) {
            $sortDir = 'asc';
        }

        $qb = $this->stockPharmacyRepository->createQueryBuilder('sp')
            ->join('sp.medicament', 'm')
            ->where('sp.pharmacie = :pharmacy')
            ->setParameter('pharmacy', $pharmacy);

        if ($search) {
            $qb->andWhere('
                LOWER(m.nom) LIKE LOWER(:search) OR
                LOWER(m.dosage) LIKE LOWER(:search) OR
                LOWER(m.forme) LIKE LOWER(:search) OR
                LOWER(m.laboratoire) LIKE LOWER(:search)
            ')
            ->setParameter('search', '%' . $search . '%');
        }

        $sortField = match ($sortBy) {
            'id' => 'sp.id',
            'quantite' => 'sp.quantite',
            'createdAt' => 'sp.createdAt',
            'prixVente' => 'sp.prixVente',
            'dateExpiration' => 'sp.dateExpiration',
            default => 'm.' . $sortBy,
        };
        $qb->orderBy($sortField, strtoupper($sortDir));

        return $qb;
    }

    private function validatePayload(array $data, Pharmacy $pharmacy, ?StockPharmacy $stock, bool $partial): array
    {
        $errors = [];
        $medicament = null;
        $quantite = null;
        $prixVente = null;
        $dateExpiration = null;

        if (array_key_exists('medicament_id', $data) || !$partial) {
            $medicamentId = $data['medicament_id'] ?? null;
            if ($medicamentId === null || $medicamentId === '') {
                $errors['medicament_id'] = 'Veuillez sélectionner un médicament';
            } elseif (!is_numeric($medicamentId)) {
                $errors['medicament_id'] = 'Médicament invalide';
            } else {
                $medicament = $this->medicamentRepository->find((int)$medicamentId);
                if (!$medicament) {
                    $errors['medicament_id'] = 'Médicament introuvable';
                } else {
                    $existing = $this->stockPharmacyRepository->findOneBy([
                        'pharmacie' => $pharmacy,
                        'medicament' => $medicament
                    ]);
                    if ($existing && (!$stock || $existing->getId() !== $stock->getId())) {
                        $errors['medicament_id'] = 'Ce médicament existe déjà pour cette pharmacie';
                    }
                }
            }
        }

        if (array_key_exists('quantite', $data) || !$partial) {
            $quantiteValue = $data['quantite'] ?? null;
            if ($quantiteValue === null || $quantiteValue === '') {
                $errors['quantite'] = 'La quantité est obligatoire';
            } elseif (!is_numeric($quantiteValue) || $quantiteValue < 0) {
                $errors['quantite'] = 'La quantité doit être numérique et positive';
            } else {
                $quantite = (int)$quantiteValue;
            }
        }

        if (array_key_exists('prixVente', $data)) {
            $prixVenteValue = $data['prixVente'];
            if ($prixVenteValue === null || $prixVenteValue === '') {
                $prixVente = null;
            } elseif (!is_numeric($prixVenteValue) || $prixVenteValue < 0) {
                $errors['prixVente'] = 'Le prix doit être numérique et positif';
            } else {
                $prixVente = (string)$prixVenteValue;
            }
        }

        if (array_key_exists('dateExpiration', $data)) {
            $dateValue = $data['dateExpiration'];
            if ($dateValue === null || $dateValue === '') {
                $dateExpiration = null;
            } else {
                $parsed = \DateTimeImmutable::createFromFormat('Y-m-d', $dateValue);
                $dateErrors = \DateTimeImmutable::getLastErrors();
                $hasDateErrors = is_array($dateErrors)
                    ? ($dateErrors['warning_count'] > 0 || $dateErrors['error_count'] > 0)
                    : false;
                if ($parsed === false || $hasDateErrors) {
                    $errors['dateExpiration'] = 'Date d\'expiration invalide';
                } else {
                    $dateExpiration = $parsed;
                }
            }
        }

        return [$errors, $medicament, $quantite, $prixVente, $dateExpiration];
    }

    private function serializeStock(StockPharmacy $stock): array
    {
        $medicament = $stock->getMedicament();

        return [
            'id' => $stock->getId(),
            'pharmacy_id' => $stock->getPharmecie()->getId(),
            'medicament' => [
                'id' => $medicament->getId(),
                'nom' => $medicament->getNom(),
                'dosage' => $medicament->getDosage(),
                'forme' => $medicament->getForme(),
                'laboratoire' => $medicament->getLaboratoire(),
                'prix' => $medicament->getPrix(),
            ],
            'quantite' => $stock->getQuantite(),
            'prixVente' => $stock->getPrixVente(),
            'dateExpiration' => $stock->getDateExpiration()?->format('Y-m-d'),
            'createdAt' => $stock->getCreatedAt()->format('Y-m-d H:i:s'),
            'updatedAt' => $stock->getUpdatedAt()->format('Y-m-d H:i:s'),
        ];
    }
}
