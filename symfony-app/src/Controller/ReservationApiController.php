<?php

namespace App\Controller;

use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Service\ReservationService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/reservations', name: 'api_reservations_')]
class ReservationApiController extends AbstractController
{
    public function __construct(
        private PharmacyRepository $pharmacyRepository,
        private MedicamentRepository $medicamentRepository,
        private ReservationService $reservationService
    ) {}

    #[Route('', name: 'create', methods: ['POST'])]
    public function create(Request $request): Response
    {
        $this->denyAccessUnlessGranted('ROLE_PATIENT');
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $pharmacyId = (int)$request->request->get('pharmacy_id');
        $medicamentId = (int)$request->request->get('medicament_id');
        $quantite = max(1, (int)$request->request->get('quantite', 1));

        $pharmacy = $this->pharmacyRepository->findById($pharmacyId);
        $medicament = $this->medicamentRepository->findById($medicamentId);
        if (!$pharmacy || !$medicament) {
            return $this->json(['error' => 'Invalid data'], 422);
        }

        $reservation = $this->reservationService->createReservation($user, $pharmacy, $medicament, $quantite);
        return $this->json(['id' => $reservation->getId(), 'status' => $reservation->getStatut()]);
    }
}
