<?php

namespace App\Controller;

use App\Repository\PharmacyRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class PharmacyFrontController extends AbstractController
{
    #[Route('/pharmacies', name: 'app_pharmacies', methods: ['GET'])]
    public function index(PharmacyRepository $repository): Response
    {
        $pharmacies = $repository->findAll();

        return $this->render('front/pharmacies/index.html.twig', [
            'pharmacies' => $pharmacies,
        ]);
    }
}
