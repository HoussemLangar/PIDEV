<?php

namespace App\Controller;

use App\Repository\PharmacyRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/pharmacies', name: 'api_pharmacies_')]
class PharmacyModuleApiController extends AbstractController
{
    public function __construct(private PharmacyRepository $pharmacyRepository) {}

    #[Route('', name: 'list', methods: ['GET'])]
    public function list(Request $request): Response
    {
        $q = $request->query->get('q');
        $items = $this->pharmacyRepository->search($q, true);
        $data = array_map(fn($p) => [
            'id' => $p->getId(),
            'nom' => $p->getNom(),
            'adresse' => $p->getAdresse(),
            'ville' => $p->getVille(),
            'latitude' => $p->getLatitude(),
            'longitude' => $p->getLongitude(),
            'active' => $p->isActive(),
        ], $items);
        return $this->json($data);
    }
}
