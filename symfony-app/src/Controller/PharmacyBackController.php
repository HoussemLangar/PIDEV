<?php

namespace App\Controller;

use App\Entity\Pharmacy;
use App\Repository\PharmacyRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/admin/pharmacy', name: 'admin_pharmacy_')]
class PharmacyBackController extends AbstractController
{
    public function __construct(
        private readonly PharmacyRepository $repository,
        private readonly EntityManagerInterface $entityManager,
    ) {}

    /**
     * Liste toutes les pharmacies avec recherche et tri
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'id');
        $sortDir = $request->query->get('dir', 'asc');

        // Validation du tri
        $validSort = ['id', 'nom', 'adresse', 'telephone', 'createdAt'];
        if (!in_array($sortBy, $validSort)) {
            $sortBy = 'id';
        }
        if (!in_array($sortDir, ['asc', 'desc'])) {
            $sortDir = 'asc';
        }

        // Recherche et tri
        if ($search) {
            $pharmacies = $this->repository->searchPharmacies($search, $sortBy, $sortDir);
        } else {
            $pharmacies = $this->repository->findBy(
                [],
                [$sortBy => $sortDir]
            );
        }

        return $this->render('back/pharmacy/index.html.twig', [
            'pharmacies' => $pharmacies,
            'search' => $search,
            'sort' => $sortBy,
            'dir' => $sortDir,
        ]);
    }

    /**
     * Affiche le formulaire de création
     */
    #[Route('/new', name: 'new', methods: ['GET'])]
    public function new(): Response
    {
        return $this->render('back/pharmacy/form.html.twig', [
            'pharmacy' => null,
        ]);
    }

    /**
     * Crée une nouvelle pharmacie
     */
    #[Route('', name: 'create', methods: ['POST'])]
    public function create(Request $request): Response
    {
        $errors = [];
        
        $nom = trim($request->request->get('nom', ''));
        $adresse = trim($request->request->get('adresse', ''));
        $telephone = trim($request->request->get('telephone', ''));
        $email = trim($request->request->get('email', ''));
        $horaires = trim($request->request->get('horaires', ''));

        // Validation serveur
        if (empty($nom)) {
            $errors['nom'] = 'Le nom est obligatoire';
        } elseif (strlen($nom) > 150) {
            $errors['nom'] = 'Le nom ne doit pas dépasser 150 caractères';
        }

        if (empty($adresse)) {
            $errors['adresse'] = 'L\'adresse est obligatoire';
        } elseif (strlen($adresse) > 255) {
            $errors['adresse'] = 'L\'adresse ne doit pas dépasser 255 caractères';
        }

        if ($telephone && strlen($telephone) > 20) {
            $errors['telephone'] = 'Le téléphone ne doit pas dépasser 20 caractères';
        }

        if ($email) {
            if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
                $errors['email'] = 'L\'email n\'est pas valide';
            } elseif (strlen($email) > 100) {
                $errors['email'] = 'L\'email ne doit pas dépasser 100 caractères';
            }
        }

        if ($horaires && strlen($horaires) > 255) {
            $errors['horaires'] = 'Les horaires ne doivent pas dépasser 255 caractères';
        }

        if (!empty($errors)) {
            return $this->render('back/pharmacy/form.html.twig', [
                'pharmacy' => null,
                'errors' => $errors,
                'data' => [
                    'nom' => $nom,
                    'adresse' => $adresse,
                    'telephone' => $telephone,
                    'email' => $email,
                    'horaires' => $horaires,
                ],
            ]);
        }

        $pharmacy = new Pharmacy();
        $pharmacy->setNom($nom);
        $pharmacy->setAdresse($adresse);
        $pharmacy->setTelephone($telephone ?: null);
        $pharmacy->setEmail($email ?: null);
        $pharmacy->setHoraires($horaires ?: null);
        $pharmacy->setCreatedAt(new \DateTime());
        $pharmacy->setUpdatedAt(new \DateTime());

        $this->entityManager->persist($pharmacy);
        $this->entityManager->flush();

        $this->addFlash('success', 'Pharmacie créée avec succès');

        return $this->redirectToRoute('admin_pharmacy_index');
    }

    /**
     * Affiche le formulaire d'édition
     */
    #[Route('/{id}/edit', name: 'edit', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function edit(int $id): Response
    {
        $pharmacy = $this->repository->find($id);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        return $this->render('back/pharmacy/form.html.twig', [
            'pharmacy' => $pharmacy,
        ]);
    }

    /**
     * Met à jour une pharmacie
     */
    #[Route('/{id}', name: 'update', methods: ['POST'], requirements: ['id' => '\d+'])]
    public function update(int $id, Request $request): Response
    {
        $pharmacy = $this->repository->find($id);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $errors = [];

        $nom = trim($request->request->get('nom', ''));
        $adresse = trim($request->request->get('adresse', ''));
        $telephone = trim($request->request->get('telephone', ''));
        $email = trim($request->request->get('email', ''));
        $horaires = trim($request->request->get('horaires', ''));

        // Validation serveur
        if (empty($nom)) {
            $errors['nom'] = 'Le nom est obligatoire';
        } elseif (strlen($nom) > 150) {
            $errors['nom'] = 'Le nom ne doit pas dépasser 150 caractères';
        }

        if (empty($adresse)) {
            $errors['adresse'] = 'L\'adresse est obligatoire';
        } elseif (strlen($adresse) > 255) {
            $errors['adresse'] = 'L\'adresse ne doit pas dépasser 255 caractères';
        }

        if ($telephone && strlen($telephone) > 20) {
            $errors['telephone'] = 'Le téléphone ne doit pas dépasser 20 caractères';
        }

        if ($email) {
            if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
                $errors['email'] = 'L\'email n\'est pas valide';
            } elseif (strlen($email) > 100) {
                $errors['email'] = 'L\'email ne doit pas dépasser 100 caractères';
            }
        }

        if ($horaires && strlen($horaires) > 255) {
            $errors['horaires'] = 'Les horaires ne doivent pas dépasser 255 caractères';
        }

        if (!empty($errors)) {
            return $this->render('back/pharmacy/form.html.twig', [
                'pharmacy' => $pharmacy,
                'errors' => $errors,
                'data' => [
                    'nom' => $nom,
                    'adresse' => $adresse,
                    'telephone' => $telephone,
                    'email' => $email,
                    'horaires' => $horaires,
                ],
            ]);
        }

        $pharmacy->setNom($nom);
        $pharmacy->setAdresse($adresse);
        $pharmacy->setTelephone($telephone ?: null);
        $pharmacy->setEmail($email ?: null);
        $pharmacy->setHoraires($horaires ?: null);
        $pharmacy->setUpdatedAt(new \DateTime());

        $this->entityManager->flush();

        $this->addFlash('success', 'Pharmacie mise à jour avec succès');

        return $this->redirectToRoute('admin_pharmacy_index');
    }

    /**
     * Supprime une pharmacie
     */
    #[Route('/{id}', name: 'delete', methods: ['DELETE'], requirements: ['id' => '\d+'])]
    public function delete(int $id, Request $request): Response
    {
        $pharmacy = $this->repository->find($id);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $this->entityManager->remove($pharmacy);
        $this->entityManager->flush();

        $this->addFlash('success', 'Pharmacie supprimée avec succès');

        return $this->redirectToRoute('admin_pharmacy_index');
    }

    /**
     * Exporte en PDF
     */
    #[Route('/{id}/pdf', name: 'pdf', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function exportPdf(int $id): Response
    {
        $pharmacy = $this->repository->find($id);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        return $this->render('back/pharmacy/pdf.html.twig', [
            'pharmacy' => $pharmacy,
        ]);
    }
}
