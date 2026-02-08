<?php

namespace App\Controller\FrontOffice;

use App\Entity\User;
use App\Repository\PharmacyRepository;
use App\Repository\MedicamentRepository;
use App\Repository\ReservationMedicamentRepository;
use App\Repository\StockPharmacyRepository;
use App\Security\PharmacyVoter;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/pharmacien', name: 'front_pharmacien_')]
class PharmacienDashboardController extends AbstractController
{
    #[Route('/dashboard', name: 'dashboard', methods: ['GET'])]
    public function dashboard(
        PharmacyRepository $pharmacyRepository,
        MedicamentRepository $medicamentRepository,
        StockPharmacyRepository $stockRepository,
        ReservationMedicamentRepository $reservationRepository
    ): Response {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);

        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        $pharmacies = $pharmacien ? $pharmacyRepository->findBy(['pharmacien' => $pharmacien]) : [];

        $stock = $pharmacien ? $stockRepository->createQueryBuilder('s')
            ->leftJoin('s.pharmacie', 'p')
            ->addSelect('p')
            ->leftJoin('s.medicament', 'm')
            ->addSelect('m')
            ->andWhere('p.pharmacien = :pid')
            ->setParameter('pid', $pharmacien->getId())
            ->orderBy('s.updatedAt', 'DESC')
            ->getQuery()
            ->getResult() : [];

        $reservations = $pharmacien ? $reservationRepository->findByPharmacien($pharmacien->getId()) : [];

        $medicamentsAll = $medicamentRepository->findBy([], ['nom' => 'ASC']);
        $medicaments = $medicamentsAll;

        return $this->render('front/pharmacien/dashboard.html.twig', [
            'pharmacies' => $pharmacies,
            'medicaments' => $medicaments,
            'medicaments_all' => $medicamentsAll,
            'stocks' => $stock,
            'reservations' => $reservations,
        ]);
    }
}
