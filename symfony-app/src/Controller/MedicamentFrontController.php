<?php

namespace App\Controller;

use App\Repository\MedicamentRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/medicaments', name: 'front_medicaments')]
class MedicamentFrontController extends AbstractController
{
    public function __construct(private MedicamentRepository $medicamentRepository) {}

    #[Route('', name: '', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $q = $request->query->get('q');
        $items = $this->medicamentRepository->search($q, null);
        return $this->render('front/medicaments/index.html.twig', [
            'medicaments' => $items,
        ]);
    }
}
