<?php

namespace App\Controller\FrontOffice;

use App\Entity\Pharmacy;
use App\Entity\User;
use App\Repository\ReservationMedicamentRepository;
use App\Security\PharmacyVoter;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/pharmacies', name: 'front_pharmacy_')]
class PharmacyFrontController extends AbstractController
{
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);
        return $this->render('front/pharmacies/index.html.twig');
    }

    #[Route('/{id}', name: 'show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(Pharmacy $pharmacy): Response
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);
        return $this->render('front/pharmacies/show.html.twig', [
            'pharmacy' => $pharmacy,
        ]);
    }

    #[Route('/mes-reservations', name: 'my_reservations', methods: ['GET'])]
    public function myReservations(ReservationMedicamentRepository $repository): Response
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::RESERVE);
        /** @var User $user */
        $user = $this->getUser();
        $items = $repository->findByPatient($user->getId());

        return $this->render('front/pharmacies/my_reservations.html.twig', [
            'reservations' => $items,
        ]);
    }
}
