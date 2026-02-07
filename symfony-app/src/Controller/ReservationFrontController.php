<?php

namespace App\Controller;

use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Repository\ReservationRepository;
use App\Service\ReservationService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/reservations', name: 'front_reservations')]
class ReservationFrontController extends AbstractController
{
    public function __construct(
        private PharmacyRepository $pharmacyRepository,
        private MedicamentRepository $medicamentRepository,
        private ReservationRepository $reservationRepository,
        private ReservationService $reservationService
    ) {}

    #[Route('', name: '', methods: ['GET'])]
    public function index(): Response
    {
        $this->denyAccessUnlessGranted('ROLE_PATIENT');
        $user = $this->getUser();
        $items = [];
        if ($user instanceof \App\Entity\User) {
            $items = $this->reservationRepository->findBy(['patient' => $user], ['createdAt' => 'DESC']);
        }
        return $this->render('front/reservations/index.html.twig', [
            'reservations' => $items,
        ]);
    }

    #[Route('/new', name: '_new', methods: ['GET', 'POST'])]
    public function new(Request $request): Response
    {
        $this->denyAccessUnlessGranted('ROLE_PATIENT');
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User) {
            throw $this->createAccessDeniedException();
        }

        if ($request->isMethod('POST')) {
            $pharmacyId = (int)$request->request->get('pharmacy_id');
            $medicamentId = (int)$request->request->get('medicament_id');
            $quantite = max(1, (int)$request->request->get('quantite', 1));

            $pharmacy = $this->pharmacyRepository->findById($pharmacyId);
            $medicament = $this->medicamentRepository->findById($medicamentId);
            if (!$pharmacy || !$medicament) {
                $this->addFlash('error', 'Données invalides.');
                return $this->redirectToRoute('front_reservations_new');
            }

            $this->reservationService->createReservation($user, $pharmacy, $medicament, $quantite);
            $this->addFlash('success', 'Réservation effectuée.');
            return $this->redirectToRoute('front_reservations');
        }

        return $this->render('front/reservations/new.html.twig', [
            'pharmacies' => $this->pharmacyRepository->findAll(),
            'medicaments' => $this->medicamentRepository->findAll(),
        ]);
    }
}
