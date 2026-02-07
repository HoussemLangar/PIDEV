<?php

namespace App\Controller;

use App\Repository\PharmacyRepository;
use App\Repository\StockPharmacyRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/pharmacy', name: 'pharmacy_')]
class PharmacyFrontController extends AbstractController
{
    public function __construct(
        private readonly PharmacyRepository $repository,
        private readonly StockPharmacyRepository $stockPharmacyRepository,
    ) {}

    /**
     * Liste publique des pharmacies avec recherche et tri
     */
    #[Route('', name: 'list', methods: ['GET'])]
    public function list(Request $request): Response
    {
        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'nom');
        $sortDir = $request->query->get('dir', 'asc');

        // Validation du tri
        $validSort = ['nom', 'adresse', 'telephone', 'createdAt'];
        if (!in_array($sortBy, $validSort)) {
            $sortBy = 'nom';
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

        return $this->render('front/pharmacy/list.html.twig', [
            'pharmacies' => $pharmacies,
            'search' => $search,
            'sort' => $sortBy,
            'dir' => $sortDir,
        ]);
    }

    /**
     * Affiche les détails d'une pharmacie
     */
    #[Route('/{id}', name: 'show', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function show(int $id): Response
    {
        $pharmacy = $this->repository->find($id);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        return $this->render('front/pharmacy/show.html.twig', [
            'pharmacy' => $pharmacy,
        ]);
    }

    /**
     * Liste publique des medicaments d'une pharmacie
     */
    #[Route('/{id}/medicaments', name: 'medicaments', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function medicaments(int $id): Response
    {
        $pharmacy = $this->repository->find($id);
        if (!$pharmacy) {
            throw $this->createNotFoundException('Pharmacie introuvable');
        }

        $stocks = $this->stockPharmacyRepository->createQueryBuilder('sp')
            ->join('sp.medicament', 'm')
            ->addSelect('m')
            ->where('sp.pharmacie = :pharmacy')
            ->setParameter('pharmacy', $pharmacy)
            ->orderBy('m.nom', 'ASC')
            ->getQuery()
            ->getResult();

        return $this->render('front/pharmacy/medicaments.html.twig', [
            'pharmacy' => $pharmacy,
            'stocks' => $stocks,
        ]);
    }
}
