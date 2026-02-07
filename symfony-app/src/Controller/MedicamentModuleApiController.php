<?php

namespace App\Controller;

use App\Repository\MedicamentRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/medicaments', name: 'api_medicaments_')]
class MedicamentModuleApiController extends AbstractController
{
    public function __construct(private MedicamentRepository $medicamentRepository) {}

    #[Route('', name: 'list', methods: ['GET'])]
    public function list(Request $request): Response
    {
        $q = $request->query->get('q');
        $code = $request->query->get('code');
        $items = $this->medicamentRepository->search($q, $code);
        $data = array_map(fn($m) => [
            'id' => $m->getId(),
            'nom' => $m->getNom(),
            'type' => $m->getType(),
            'codeBarres' => $m->getCodeBarres(),
            'prix' => $m->getPrix(),
            'active' => $m->isActive(),
        ], $items);
        return $this->json($data);
    }
}
