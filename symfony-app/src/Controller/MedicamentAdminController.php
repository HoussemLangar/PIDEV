<?php

namespace App\Controller;

use App\Entity\Medicament;
use App\Repository\MedicamentRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/admin/medicament', name: 'admin_medicament_catalog_')]
class MedicamentAdminController extends AbstractController
{
    public function __construct(
        private readonly MedicamentRepository $repository,
        private readonly EntityManagerInterface $entityManager,
    ) {}

    /**
     * Liste des médicaments avec recherche et tri
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'id');
        $sortDir = $request->query->get('dir', 'asc');

        $validSort = ['id', 'nom', 'type', 'forme', 'dosage', 'prix', 'stock', 'laboratoire', 'createdAt'];
        if (!in_array($sortBy, $validSort, true)) {
            $sortBy = 'id';
        }
        if (!in_array($sortDir, ['asc', 'desc'], true)) {
            $sortDir = 'asc';
        }

        if ($search) {
            $medicaments = $this->repository->searchMedicaments($search, $sortBy, $sortDir);
        } else {
            $medicaments = $this->repository->findBy([], [$sortBy => $sortDir]);
        }

        return $this->render('back/medicament_catalog/index.html.twig', [
            'medicaments' => $medicaments,
            'search' => $search,
            'sort' => $sortBy,
            'dir' => $sortDir,
        ]);
    }

    /**
     * Formulaire de création
     */
    #[Route('/new', name: 'new', methods: ['GET'])]
    public function new(): Response
    {
        return $this->render('back/medicament_catalog/form.html.twig', [
            'medicament' => null,
        ]);
    }

    /**
     * Crée un médicament
     */
    #[Route('', name: 'create', methods: ['POST'])]
    public function create(Request $request): Response
    {
        [$errors, $data] = $this->validateRequest($request);

        if ($errors) {
            return $this->render('back/medicament_catalog/form.html.twig', [
                'medicament' => null,
                'errors' => $errors,
                'data' => $data,
            ]);
        }

        $medicament = new Medicament();
        $this->hydrateMedicament($medicament, $data);
        $medicament->setCreatedAt(new \DateTime());
        $medicament->setUpdatedAt(new \DateTime());

        $this->entityManager->persist($medicament);
        $this->entityManager->flush();

        $this->addFlash('success', 'Médicament créé avec succès');

        return $this->redirectToRoute('admin_medicament_catalog_index');
    }

    /**
     * Formulaire d'édition
     */
    #[Route('/{id}/edit', name: 'edit', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function edit(int $id): Response
    {
        $medicament = $this->repository->find($id);
        if (!$medicament) {
            throw $this->createNotFoundException('Médicament introuvable');
        }

        return $this->render('back/medicament_catalog/form.html.twig', [
            'medicament' => $medicament,
        ]);
    }

    /**
     * Met à jour un médicament
     */
    #[Route('/{id}', name: 'update', methods: ['POST'], requirements: ['id' => '\d+'])]
    public function update(int $id, Request $request): Response
    {
        $medicament = $this->repository->find($id);
        if (!$medicament) {
            throw $this->createNotFoundException('Médicament introuvable');
        }

        [$errors, $data] = $this->validateRequest($request);

        if ($errors) {
            return $this->render('back/medicament_catalog/form.html.twig', [
                'medicament' => $medicament,
                'errors' => $errors,
                'data' => $data,
            ]);
        }

        $this->hydrateMedicament($medicament, $data);
        $medicament->setUpdatedAt(new \DateTime());

        $this->entityManager->flush();

        $this->addFlash('success', 'Médicament mis à jour avec succès');

        return $this->redirectToRoute('admin_medicament_catalog_index');
    }

    /**
     * Supprime un médicament
     */
    #[Route('/{id}', name: 'delete', methods: ['DELETE'], requirements: ['id' => '\d+'])]
    public function delete(int $id): Response
    {
        $medicament = $this->repository->find($id);
        if (!$medicament) {
            throw $this->createNotFoundException('Médicament introuvable');
        }

        $this->entityManager->remove($medicament);
        $this->entityManager->flush();

        $this->addFlash('success', 'Médicament supprimé avec succès');

        return $this->redirectToRoute('admin_medicament_catalog_index');
    }

    private function validateRequest(Request $request): array
    {
        $errors = [];

        $nom = trim((string)$request->request->get('nom', ''));
        $type = trim((string)$request->request->get('type', ''));
        $description = trim((string)$request->request->get('description', ''));
        $forme = trim((string)$request->request->get('forme', ''));
        $dosage = trim((string)$request->request->get('dosage', ''));
        $prix = trim((string)$request->request->get('prix', ''));
        $stock = trim((string)$request->request->get('stock', ''));
        $laboratoire = trim((string)$request->request->get('laboratoire', ''));

        if ($nom === '') {
            $errors['nom'] = 'Le nom est obligatoire';
        } elseif (strlen($nom) > 150) {
            $errors['nom'] = 'Le nom ne doit pas dépasser 150 caractères';
        }

        if ($type && strlen($type) > 50) {
            $errors['type'] = 'Le type ne doit pas dépasser 50 caractères';
        }

        if ($forme && strlen($forme) > 50) {
            $errors['forme'] = 'La forme ne doit pas dépasser 50 caractères';
        }

        if ($dosage && strlen($dosage) > 50) {
            $errors['dosage'] = 'Le dosage ne doit pas dépasser 50 caractères';
        }

        if ($laboratoire && strlen($laboratoire) > 100) {
            $errors['laboratoire'] = 'Le laboratoire ne doit pas dépasser 100 caractères';
        }

        if ($prix !== '') {
            if (!is_numeric($prix) || (float)$prix < 0) {
                $errors['prix'] = 'Le prix doit être numérique et positif';
            }
        }

        if ($stock === '') {
            $errors['stock'] = 'Le stock est obligatoire';
        } elseif (!is_numeric($stock) || (int)$stock < 0) {
            $errors['stock'] = 'Le stock doit être numérique et positif';
        }

        return [$errors, [
            'nom' => $nom,
            'type' => $type,
            'description' => $description,
            'forme' => $forme,
            'dosage' => $dosage,
            'prix' => $prix,
            'stock' => $stock,
            'laboratoire' => $laboratoire,
        ]];
    }

    private function hydrateMedicament(Medicament $medicament, array $data): void
    {
        $medicament->setNom($data['nom']);
        $medicament->setType($data['type'] ?: null);
        $medicament->setDescription($data['description'] ?: null);
        $medicament->setForme($data['forme'] ?: null);
        $medicament->setDosage($data['dosage'] ?: null);
        $medicament->setPrix($data['prix'] !== '' ? (string)$data['prix'] : null);
        $medicament->setStock((int)$data['stock']);
        $medicament->setLaboratoire($data['laboratoire'] ?: null);
    }
}
