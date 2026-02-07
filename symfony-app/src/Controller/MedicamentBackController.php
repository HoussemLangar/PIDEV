<?php

namespace App\Controller;

use App\Entity\Pharmacy;
use App\Entity\StockPharmacy;
use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Repository\StockPharmacyRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/admin/pharmacy/{pharmacyId}/medicament', name: 'admin_medicament_')]
class MedicamentBackController extends AbstractController
{
    public function __construct(
        private readonly MedicamentRepository $medicamentRepository,
        private readonly PharmacyRepository $pharmacyRepository,
        private readonly StockPharmacyRepository $stockPharmacyRepository,
        private readonly EntityManagerInterface $entityManager,
    ) {}

    /**
     * Liste les médicaments d'une pharmacie via StockPharmacy
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(int $pharmacyId, Request $request): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'id');
        $sortDir = $request->query->get('dir', 'asc');

        $qb = $this->buildStockQuery($pharmacy, $search, $sortBy, $sortDir);
        $stocks = $qb->getQuery()->getResult();

        return $this->render('back/medicament/index.html.twig', [
            'pharmacy' => $pharmacy,
            'stocks' => $stocks,
            'search' => $search,
            'sortBy' => $sortBy,
            'sortDir' => $sortDir,
        ]);
    }

    /**
     * Formulaire pour ajouter un médicament à une pharmacie
     */
    #[Route('/new', name: 'new', methods: ['GET'])]
    public function new(int $pharmacyId): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        // Récupérer tous les médicaments
        $medicaments = $this->medicamentRepository->findAll();

        return $this->render('back/medicament/form.html.twig', [
            'pharmacy' => $pharmacy,
            'medicaments' => $medicaments,
            'stock' => null,
            'action' => 'create',
            'selectedMedicamentId' => null,
            'medicamentForm' => [],
        ]);
    }

    /**
     * Créer un nouveau StockPharmacy (lier un médicament à une pharmacie)
     */
    #[Route('/create', name: 'create', methods: ['POST'])]
    public function create(int $pharmacyId, Request $request): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $medicamentId = $request->request->get('medicament_id');
        $quantite = '0';
        $prixVente = null;
        $dateExpiration = null;

        $errors = [];
        $medicament = null;
        $parsedDate = null;

        $isNewMedicament = $medicamentId === null || $medicamentId === '';
        if ($isNewMedicament) {
            $medicamentErrors = $this->validateMedicamentInput($request);
            if ($medicamentErrors) {
                $errors = array_merge($errors, $medicamentErrors);
            }
        } else {
            [$errors, $medicament, $parsedDate] = $this->validateStockInput(
                $pharmacy,
                $medicamentId,
                $quantite,
                $prixVente,
                $dateExpiration,
                isCreate: true
            );

            $medicamentErrors = $this->validateMedicamentInput($request);
            if ($medicamentErrors) {
                $errors = array_merge($errors, $medicamentErrors);
            }
        }

        if ($errors) {
            $medicaments = $this->medicamentRepository->findAll();
            return $this->render('back/medicament/form.html.twig', [
                'pharmacy' => $pharmacy,
                'medicaments' => $medicaments,
                'stock' => null,
                'action' => 'create',
                'errors' => $errors,
                'selectedMedicamentId' => $medicamentId,
                'medicamentForm' => $this->buildMedicamentFormData($request, $medicament),
            ]);
        }

        if ($isNewMedicament) {
            $medicament = new \App\Entity\Medicament();
        }

        $this->updateMedicamentFromRequest($medicament, $request);

        $stock = new StockPharmacy();
        $stock->setPharmecie($pharmacy);
        $stock->setMedicament($medicament);
        $stock->setQuantite((int)$quantite);
        $stock->setPrixVente(null);
        $stock->setDateExpiration(null);
        $stock->setUpdatedAt(new \DateTime());

        if ($isNewMedicament) {
            $this->entityManager->persist($medicament);
        }
        $this->entityManager->persist($stock);
        $this->entityManager->flush();

        return $this->redirectToRoute('admin_medicament_index', ['pharmacyId' => $pharmacyId]);
    }

    /**
     * Formulaire pour éditer un stock de médicament
     */
    #[Route('/{stockId}/edit', name: 'edit', methods: ['GET'])]
    public function edit(int $pharmacyId, int $stockId, Request $request): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $stock = $this->stockPharmacyRepository->find($stockId);
        if (!$stock || $stock->getPharmecie()->getId() !== $pharmacyId) {
            throw $this->createNotFoundException('Stock non trouvé');
        }

        return $this->render('back/medicament/form.html.twig', [
            'pharmacy' => $pharmacy,
            'stock' => $stock,
            'medicaments' => [],
            'action' => 'edit',
            'selectedMedicamentId' => $stock->getMedicament()->getId(),
            'medicamentForm' => $this->buildMedicamentFormData($request, $stock->getMedicament()),
        ]);
    }

    /**
     * Mettre à jour un stock de médicament
     */
    #[Route('/{stockId}/update', name: 'update', methods: ['POST'])]
    public function update(int $pharmacyId, int $stockId, Request $request): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $stock = $this->stockPharmacyRepository->find($stockId);
        if (!$stock || $stock->getPharmecie()->getId() !== $pharmacyId) {
            throw $this->createNotFoundException('Stock non trouvé');
        }

        $quantite = '0';
        $prixVente = null;
        $dateExpiration = null;

        [$errors, $_medicament, $parsedDate] = $this->validateStockInput(
            $pharmacy,
            null,
            $quantite,
            $prixVente,
            $dateExpiration,
            isCreate: false
        );

        $medicamentErrors = $this->validateMedicamentInput($request);
        if ($medicamentErrors) {
            $errors = array_merge($errors, $medicamentErrors);
        }

        if ($errors) {
            return $this->render('back/medicament/form.html.twig', [
                'pharmacy' => $pharmacy,
                'stock' => $stock,
                'medicaments' => [],
                'action' => 'edit',
                'errors' => $errors,
                'selectedMedicamentId' => $stock->getMedicament()->getId(),
                'medicamentForm' => $this->buildMedicamentFormData($request, $stock->getMedicament()),
            ]);
        }

        $stock->setQuantite((int)$quantite);
        $stock->setPrixVente(null);
        $stock->setDateExpiration(null);
        $stock->setUpdatedAt(new \DateTime());

        $this->updateMedicamentFromRequest($stock->getMedicament(), $request);

        $this->entityManager->flush();

        return $this->redirectToRoute('admin_medicament_index', ['pharmacyId' => $pharmacyId]);
    }

    /**
     * Exporte la liste des médicaments d'une pharmacie en PDF
     */
    #[Route('/pdf', name: 'pdf', methods: ['GET'])]
    public function exportPdf(int $pharmacyId, Request $request): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'id');
        $sortDir = $request->query->get('dir', 'asc');

        $qb = $this->buildStockQuery($pharmacy, $search, $sortBy, $sortDir);
        $stocks = $qb->getQuery()->getResult();

        return $this->render('back/medicament/pdf.html.twig', [
            'pharmacy' => $pharmacy,
            'stocks' => $stocks,
            'search' => $search,
            'sortBy' => $sortBy,
            'sortDir' => $sortDir,
        ]);
    }

    /**
     * Supprimer un stock de médicament
     */
    #[Route('/{stockId}', name: 'delete', methods: ['DELETE'])]
    public function delete(int $pharmacyId, int $stockId): Response
    {
        $pharmacy = $this->pharmacyRepository->find($pharmacyId);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $stock = $this->stockPharmacyRepository->find($stockId);
        if (!$stock || $stock->getPharmecie()->getId() !== $pharmacyId) {
            throw $this->createNotFoundException('Stock non trouvé');
        }

        $this->entityManager->remove($stock);
        $this->entityManager->flush();

        return $this->redirectToRoute('admin_medicament_index', ['pharmacyId' => $pharmacyId]);
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

    private function validateStockInput(
        Pharmacy $pharmacy,
        ?string $medicamentId,
        ?string $quantite,
        ?string $prixVente,
        ?string $dateExpiration,
        bool $isCreate
    ): array {
        $errors = [];
        $medicament = null;
        $parsedDate = null;

        if ($isCreate) {
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
                    if ($existing) {
                        $errors['medicament_id'] = 'Ce médicament existe déjà pour cette pharmacie';
                    }
                }
            }
        }

        if ($quantite === null || $quantite === '') {
            $errors['quantite'] = 'La quantité est obligatoire';
        } elseif (!is_numeric($quantite) || $quantite < 0) {
            $errors['quantite'] = 'La quantité doit être numérique et positive';
        }

        if ($prixVente !== null && $prixVente !== '') {
            if (!is_numeric($prixVente) || $prixVente < 0) {
                $errors['prixVente'] = 'Le prix doit être numérique et positif';
            }
        }

        if ($dateExpiration) {
            $parsedDate = \DateTimeImmutable::createFromFormat('Y-m-d', $dateExpiration);
            $dateErrors = \DateTimeImmutable::getLastErrors();
            $hasDateErrors = is_array($dateErrors)
                ? ($dateErrors['warning_count'] > 0 || $dateErrors['error_count'] > 0)
                : false;
            if ($parsedDate === false || $hasDateErrors) {
                $errors['dateExpiration'] = 'Date d\'expiration invalide';
                $parsedDate = null;
            }
        }

        return [$errors, $medicament, $parsedDate];
    }

    private function validateMedicamentInput(Request $request): array
    {
        $errors = [];

        $nom = trim((string)$request->request->get('medicament_nom', ''));
        $prix = $request->request->get('medicament_prix');
        $stock = $request->request->get('medicament_stock');

        if ($nom === '') {
            $errors['medicament_nom'] = 'Le nom du médicament est obligatoire';
        }

        if ($prix !== null && $prix !== '') {
            if (!is_numeric($prix) || $prix < 0) {
                $errors['medicament_prix'] = 'Le prix doit être numérique et positif';
            }
        }

        if ($stock !== null && $stock !== '') {
            if (!is_numeric($stock) || $stock < 0) {
                $errors['medicament_stock'] = 'Le stock doit être numérique et positif';
            }
        }

        return $errors;
    }

    private function updateMedicamentFromRequest(\App\Entity\Medicament $medicament, Request $request): void
    {
        $medicament->setNom(trim((string)$request->request->get('medicament_nom', '')));
        $medicament->setType($this->nullableString($request->request->get('medicament_type')));
        $medicament->setDescription($this->nullableString($request->request->get('medicament_description')));
        $medicament->setForme($this->nullableString($request->request->get('medicament_forme')));
        $medicament->setDosage($this->nullableString($request->request->get('medicament_dosage')));
        $medicament->setPrix($this->nullableNumericString($request->request->get('medicament_prix')));
        $medicament->setLaboratoire($this->nullableString($request->request->get('medicament_laboratoire')));

        $stockValue = $request->request->get('medicament_stock');
        if ($stockValue !== null && $stockValue !== '') {
            $medicament->setStock((int)$stockValue);
        }

        $medicament->setUpdatedAt(new \DateTime());
    }

    private function buildMedicamentFormData(Request $request, ?\App\Entity\Medicament $medicament): array
    {
        $get = fn(string $key, ?string $fallback = null) => $request->request->get($key) ?? $fallback;
        $safe = fn(string $method) => $this->safeMedicamentValue($medicament, $method);

        return [
            'nom' => $get('medicament_nom', $safe('getNom')),
            'type' => $get('medicament_type', $safe('getType')),
            'description' => $get('medicament_description', $safe('getDescription')),
            'forme' => $get('medicament_forme', $safe('getForme')),
            'dosage' => $get('medicament_dosage', $safe('getDosage')),
            'prix' => $get('medicament_prix', $safe('getPrix')),
            'stock' => $get('medicament_stock', $safe('getStock')),
            'laboratoire' => $get('medicament_laboratoire', $safe('getLaboratoire')),
            'createdAt' => $safe('getCreatedAt'),
            'updatedAt' => $safe('getUpdatedAt'),
        ];
    }

    private function safeMedicamentValue(?\App\Entity\Medicament $medicament, string $method)
    {
        if (!$medicament) {
            return null;
        }
        try {
            return $medicament->$method();
        } catch (\Error) {
            return null;
        }
    }

    private function nullableString(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }
        $trimmed = trim($value);
        return $trimmed === '' ? null : $trimmed;
    }

    private function nullableNumericString(?string $value): ?string
    {
        if ($value === null || $value === '') {
            return null;
        }
        return is_numeric($value) ? (string)$value : null;
    }
}
