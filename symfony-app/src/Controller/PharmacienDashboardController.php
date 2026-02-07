<?php

namespace App\Controller;

use App\Entity\Medicament;
use App\Entity\Pharmacy;
use App\Entity\StockPharmacy;
use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Repository\ReservationRepository;
use App\Repository\StockPharmacyRepository;
use App\Service\FormErrorSerializer;
use App\Service\ReservationService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/pharmacien', name: 'pharmacien_')]
class PharmacienDashboardController extends AbstractController
{
    public function __construct(
        private PharmacyRepository $pharmacyRepository,
        private StockPharmacyRepository $stockRepository,
        private ReservationRepository $reservationRepository,
        private ReservationService $reservationService,
        private MedicamentRepository $medicamentRepository,
        private FormErrorSerializer $formErrorSerializer
    ) {}

    #[Route('/dashboard', name: 'dashboard', methods: ['GET'])]
    public function dashboard(): Response
    {
        $user = $this->assertPharmacienAccess();
        $pharmacien = $user->getPharmacien();

        $pharmacies = $pharmacien ? $this->pharmacyRepository->findByPharmacien($pharmacien) : [];
        $reservations = $pharmacien ? $this->reservationRepository->findForPharmacien($pharmacien) : [];
        $lowStocks = $pharmacien ? $this->stockRepository->findLowStockForPharmacien($pharmacien) : [];
        $medicaments = $this->medicamentRepository->findAll();

        $pharmacyForm = $this->createForm(\App\Form\PharmacyType::class, new Pharmacy(), [
            'action' => $this->generateUrl('pharmacien_pharmacies_new'),
            'method' => 'POST',
        ]);
        $medicamentForm = $this->createForm(\App\Form\MedicamentType::class, new Medicament(), [
            'action' => $this->generateUrl('pharmacien_medicaments_new'),
            'method' => 'POST',
        ]);
        $stockCreateForm = $this->createForm(\App\Form\StockPharmacyType::class, new StockPharmacy(), [
            'pharmacy_choices' => $pharmacies,
            'medicament_choices' => $medicaments,
            'action' => $this->generateUrl('pharmacien_stocks_new'),
            'method' => 'POST',
        ]);

        $stockEditForms = [];
        foreach ($pharmacies as $pharmacy) {
            foreach ($pharmacy->getStocks() as $stock) {
                $stockEditForms[$stock->getId()] = $this->createForm(\App\Form\StockUpdateType::class, $stock, [
                    'action' => $this->generateUrl('pharmacien_stocks_update', ['id' => $stock->getId()]),
                    'method' => 'POST',
                ])->createView();
            }
        }

        $pharmacyEditForms = [];
        foreach ($pharmacies as $pharmacy) {
            $pharmacyEditForms[$pharmacy->getId()] = $this->createForm(\App\Form\PharmacyType::class, $pharmacy, [
                'action' => $this->generateUrl('pharmacien_pharmacies_update', ['id' => $pharmacy->getId()]),
                'method' => 'POST',
            ])->createView();
        }

        $medicamentEditForms = [];
        foreach ($medicaments as $medicament) {
            $medicamentEditForms[$medicament->getId()] = $this->createForm(\App\Form\MedicamentType::class, $medicament, [
                'action' => $this->generateUrl('pharmacien_medicaments_update', ['id' => $medicament->getId()]),
                'method' => 'POST',
            ])->createView();
        }

        return $this->render('front/pharmacien/dashboard.html.twig', [
            'pharmacies' => $pharmacies,
            'reservations' => $reservations,
            'lowStocks' => $lowStocks,
            'medicaments' => $medicaments,
            'pharmacyForm' => $pharmacyForm->createView(),
            'medicamentForm' => $medicamentForm->createView(),
            'stockCreateForm' => $stockCreateForm->createView(),
            'pharmacyEditForms' => $pharmacyEditForms,
            'stockEditForms' => $stockEditForms,
            'medicamentEditForms' => $medicamentEditForms,
        ]);
    }

    #[Route('/pharmacies/new', name: 'pharmacies_new', methods: ['POST'])]
    public function createPharmacy(Request $request): Response
    {
        $user = $this->assertPharmacienAccess();
        $pharmacien = $user->getPharmacien();

        $pharmacy = new Pharmacy();
        $pharmacy->setPharmacien($pharmacien);
        $form = $this->createForm(\App\Form\PharmacyType::class, $pharmacy);
        $form->handleRequest($request);
        if (!$form->isSubmitted() || !$form->isValid()) {
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => false, 'errors' => $this->formErrorSerializer->toArray($form)], 422);
            }
            $this->addFlash('error', 'Veuillez corriger les erreurs.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $pharmacy->setUpdatedAt(new \DateTimeImmutable());
        $this->pharmacyRepository->save($pharmacy);
        if ($request->isXmlHttpRequest()) {
            return $this->json(['success' => true, 'redirect' => $this->generateUrl('pharmacien_dashboard')]);
        }
        $this->addFlash('success', 'Pharmacie créée.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/pharmacies/{id}/update', name: 'pharmacies_update', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function updatePharmacy(int $id, Request $request): Response
    {
        $this->assertPharmacienAccess();
        $pharmacy = $this->pharmacyRepository->findById($id);
        if (!$pharmacy) {
            $this->addFlash('error', 'Pharmacie introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }

        $form = $this->createForm(\App\Form\PharmacyType::class, $pharmacy);
        $form->handleRequest($request);
        if (!$form->isSubmitted() || !$form->isValid()) {
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => false, 'errors' => $this->formErrorSerializer->toArray($form)], 422);
            }
            $this->addFlash('error', 'Veuillez corriger les erreurs.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $pharmacy->setUpdatedAt(new \DateTimeImmutable());
        $this->pharmacyRepository->save($pharmacy);
        if ($request->isXmlHttpRequest()) {
            return $this->json(['success' => true, 'redirect' => $this->generateUrl('pharmacien_dashboard')]);
        }
        $this->addFlash('success', 'Pharmacie modifiée.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/pharmacies/{id}/delete', name: 'pharmacies_delete', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function deletePharmacy(int $id): RedirectResponse
    {
        $this->assertPharmacienAccess();
        $pharmacy = $this->pharmacyRepository->findById($id);
        if (!$pharmacy) {
            $this->addFlash('error', 'Pharmacie introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $this->pharmacyRepository->delete($pharmacy);
        $this->addFlash('success', 'Pharmacie supprimée.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/stocks/new', name: 'stocks_new', methods: ['POST'])]
    public function createStock(Request $request): Response
    {
        $this->assertPharmacienAccess();

        $stock = new StockPharmacy();
        $form = $this->createForm(\App\Form\StockPharmacyType::class, $stock, [
            'pharmacy_choices' => $this->pharmacyRepository->findAll(),
            'medicament_choices' => $this->medicamentRepository->findAll(),
        ]);
        $form->handleRequest($request);
        if (!$form->isSubmitted() || !$form->isValid()) {
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => false, 'errors' => $this->formErrorSerializer->toArray($form)], 422);
            }
            $this->addFlash('error', 'Veuillez corriger les erreurs.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $stock->setUpdatedAt(new \DateTimeImmutable());
        $this->stockRepository->save($stock);
        if ($request->isXmlHttpRequest()) {
            return $this->json(['success' => true, 'redirect' => $this->generateUrl('pharmacien_dashboard')]);
        }
        $this->addFlash('success', 'Stock ajouté.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/stocks/{id}/update', name: 'stocks_update', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function updateStock(int $id, Request $request): Response
    {
        $this->assertPharmacienAccess();
        $stock = $this->stockRepository->findById($id);
        if (!$stock) {
            $this->addFlash('error', 'Stock introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }

        $form = $this->createForm(\App\Form\StockUpdateType::class, $stock);
        $form->handleRequest($request);
        if (!$form->isSubmitted() || !$form->isValid()) {
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => false, 'errors' => $this->formErrorSerializer->toArray($form)], 422);
            }
            $this->addFlash('error', 'Veuillez corriger les erreurs.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $stock->setUpdatedAt(new \DateTimeImmutable());
        $this->stockRepository->save($stock);
        if ($request->isXmlHttpRequest()) {
            return $this->json(['success' => true, 'redirect' => $this->generateUrl('pharmacien_dashboard')]);
        }
        $this->addFlash('success', 'Stock modifié.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/stocks/{id}/delete', name: 'stocks_delete', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function deleteStock(int $id): RedirectResponse
    {
        $this->assertPharmacienAccess();
        $stock = $this->stockRepository->findById($id);
        if (!$stock) {
            $this->addFlash('error', 'Stock introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $this->stockRepository->delete($stock);
        $this->addFlash('success', 'Stock supprimé.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/medicaments/new', name: 'medicaments_new', methods: ['POST'])]
    public function createMedicament(Request $request): Response
    {
        $this->assertPharmacienAccess();

        $medicament = new Medicament();
        $form = $this->createForm(\App\Form\MedicamentType::class, $medicament);
        $form->handleRequest($request);
        if (!$form->isSubmitted() || !$form->isValid()) {
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => false, 'errors' => $this->formErrorSerializer->toArray($form)], 422);
            }
            $this->addFlash('error', 'Veuillez corriger les erreurs.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $medicament->setUpdatedAt(new \DateTimeImmutable());
        $this->medicamentRepository->save($medicament);
        if ($request->isXmlHttpRequest()) {
            return $this->json(['success' => true, 'redirect' => $this->generateUrl('pharmacien_dashboard')]);
        }
        $this->addFlash('success', 'Médicament créé.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/medicaments/{id}/update', name: 'medicaments_update', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function updateMedicament(int $id, Request $request): Response
    {
        $this->assertPharmacienAccess();
        $medicament = $this->medicamentRepository->findById($id);
        if (!$medicament) {
            $this->addFlash('error', 'Médicament introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }

        $form = $this->createForm(\App\Form\MedicamentType::class, $medicament);
        $form->handleRequest($request);
        if (!$form->isSubmitted() || !$form->isValid()) {
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => false, 'errors' => $this->formErrorSerializer->toArray($form)], 422);
            }
            $this->addFlash('error', 'Veuillez corriger les erreurs.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $medicament->setUpdatedAt(new \DateTimeImmutable());
        $this->medicamentRepository->save($medicament);
        if ($request->isXmlHttpRequest()) {
            return $this->json(['success' => true, 'redirect' => $this->generateUrl('pharmacien_dashboard')]);
        }
        $this->addFlash('success', 'Médicament modifié.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/medicaments/{id}/delete', name: 'medicaments_delete', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function deleteMedicament(int $id): RedirectResponse
    {
        $this->assertPharmacienAccess();
        $medicament = $this->medicamentRepository->findById($id);
        if (!$medicament) {
            $this->addFlash('error', 'Médicament introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }
        $this->medicamentRepository->delete($medicament);
        $this->addFlash('success', 'Médicament supprimé.');
        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/reservations/{id}/confirm', name: 'reservations_confirm', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function confirmReservation(int $id): RedirectResponse
    {
        $this->assertPharmacienAccess();
        $reservation = $this->reservationRepository->findById($id);
        if (!$reservation) {
            $this->addFlash('error', 'Réservation introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }

        try {
            $this->reservationService->confirmReservation($reservation, $this->getUser());
            $this->addFlash('success', 'Réservation confirmée.');
        } catch (\RuntimeException $e) {
            $this->addFlash('error', $e->getMessage());
        }

        return $this->redirectToRoute('pharmacien_dashboard');
    }

    #[Route('/reservations/{id}/refuse', name: 'reservations_refuse', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function refuseReservation(int $id, Request $request): RedirectResponse
    {
        $this->assertPharmacienAccess();
        $reservation = $this->reservationRepository->findById($id);
        if (!$reservation) {
            $this->addFlash('error', 'Réservation introuvable.');
            return $this->redirectToRoute('pharmacien_dashboard');
        }

        try {
            $this->reservationService->refuseReservation($reservation, $request->request->get('commentaire'));
            $this->addFlash('success', 'Réservation refusée.');
        } catch (\RuntimeException $e) {
            $this->addFlash('error', $e->getMessage());
        }

        return $this->redirectToRoute('pharmacien_dashboard');
    }

    private function assertPharmacienAccess(): \App\Entity\User
    {
        $this->denyAccessUnlessGranted('ROLE_PHARMACIEN');
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User) {
            throw $this->createAccessDeniedException('Accès refusé.');
        }
        return $user;
    }
}
