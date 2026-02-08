<?php

namespace App\Controller\FrontOffice;

use App\Entity\Medicament;
use App\Entity\ReservationMedicament;
use App\Entity\StockPharmacy;
use App\Entity\User;
use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Repository\ReservationMedicamentRepository;
use App\Repository\StockPharmacyRepository;
use App\Security\PharmacyVoter;
use App\Service\AlternativeMedicamentService;
use App\Service\PharmacySearchService;
use App\Service\ReservationMedicamentService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/pharmacies', name: 'api_pharmacies_')]
class PharmacyApiController extends AbstractController
{
    public function __construct(
        private PharmacyRepository $pharmacyRepository,
        private MedicamentRepository $medicamentRepository,
        private StockPharmacyRepository $stockRepository,
        private ReservationMedicamentRepository $reservationRepository,
        private ReservationMedicamentService $reservationService,
        private AlternativeMedicamentService $alternativeService,
        private PharmacySearchService $searchService,
        private EntityManagerInterface $em
    ) {}

    #[Route('/search', name: 'search', methods: ['GET'])]
    public function search(Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);

        $q = trim((string) $request->query->get('q', ''));
        $lat = $request->query->get('lat');
        $lng = $request->query->get('lng');
        $lat = $lat !== null ? (float) $lat : null;
        $lng = $lng !== null ? (float) $lng : null;

        $qb = $this->pharmacyRepository->createQueryBuilder('p')
            ->andWhere('p.isActive = 1')
            ->orderBy('p.nom', 'ASC');
        if ($q !== '') {
            $qb->andWhere('p.nom LIKE :q OR p.adresse LIKE :q')
                ->setParameter('q', '%' . $q . '%');
        }

        $items = $qb->getQuery()->getResult();
        $data = $this->searchService->withDistance($items, $lat, $lng);

        return new JsonResponse(['items' => $data]);
    }

    #[Route('/medicaments/search', name: 'medicaments_search', methods: ['GET'])]
    public function searchMedicaments(Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);
        $q = trim((string) $request->query->get('q', ''));
        $barcode = $request->query->get('barcode');
        $items = $this->medicamentRepository->search($q, $barcode ? (string) $barcode : null);

        $data = array_map(fn(Medicament $m) => [
            'id' => $m->getId(),
            'nom' => $m->getNom(),
            'type' => $m->getType(),
            'forme' => $m->getForme(),
            'dosage' => $m->getDosage(),
            'codeBarre' => $m->getCodeBarre(),
        ], $items);

        return new JsonResponse(['items' => $data]);
    }

    #[Route('/medicaments/{id}/compare', name: 'medicament_compare', methods: ['GET'])]
    public function compare(Medicament $medicament): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);
        $stocks = $this->stockRepository->findAvailableByMedicament($medicament->getId());

        $data = array_map(fn(StockPharmacy $s) => [
            'stock_id' => $s->getId(),
            'pharmacie' => $s->getPharmecie()->getNom(),
            'adresse' => $s->getPharmecie()->getAdresse(),
            'prix' => $s->getPrixVente(),
            'quantite' => $s->getQuantite(),
        ], $stocks);

        return new JsonResponse(['items' => $data]);
    }

    #[Route('/{id}/stocks', name: 'pharmacy_stocks', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function pharmacyStocks(int $id): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);
        $pharmacy = $this->pharmacyRepository->find($id);
        if (!$pharmacy) {
            return new JsonResponse(['items' => [], 'message' => 'Pharmacie introuvable'], 404);
        }
        $stocks = $this->stockRepository->findAvailableByPharmacy($pharmacy->getId());

        $data = array_map(fn(StockPharmacy $s) => [
            'stock_id' => $s->getId(),
            'medicament' => $s->getMedicament()->getNom(),
            'type' => $s->getMedicament()->getType(),
            'forme' => $s->getMedicament()->getForme(),
            'dosage' => $s->getMedicament()->getDosage(),
            'prix' => $s->getPrixVente(),
            'quantite' => $s->getQuantite(),
        ], $stocks);

        return new JsonResponse(['items' => $data]);
    }

    #[Route('/medicaments/{id}/alternatives', name: 'medicament_alternatives', methods: ['GET'])]
    public function alternatives(Medicament $medicament): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::VIEW);
        $data = $this->alternativeService->suggestAlternatives($medicament, 5);
        return new JsonResponse(['items' => $data]);
    }

    #[Route('/reservations', name: 'reservation_create', methods: ['POST'])]
    public function reserve(Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::RESERVE);
        /** @var User $user */
        $user = $this->getUser();

        $payload = json_decode($request->getContent(), true) ?: [];
        $stockId = (int) ($payload['stock_id'] ?? 0);
        $qty = (int) ($payload['quantite'] ?? 1);
        if ($stockId <= 0) {
            return new JsonResponse(['success' => false, 'message' => 'Stock invalide'], 422);
        }
        $stock = $this->stockRepository->find($stockId);
        if (!$stock) {
            return new JsonResponse(['success' => false, 'message' => 'Stock introuvable'], 404);
        }

        try {
            $reservation = $this->reservationService->createReservation($user, $stock, $qty);
        } catch (\RuntimeException $e) {
            return new JsonResponse(['success' => false, 'message' => $e->getMessage()], 409);
        }

        return new JsonResponse(['success' => true, 'id' => $reservation->getId()]);
    }

    #[Route('/reservations/{id}/cancel', name: 'reservation_cancel', methods: ['POST'])]
    public function cancel(ReservationMedicament $reservation): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::RESERVE);
        /** @var User $user */
        $user = $this->getUser();
        if ($reservation->getPatient()->getId() !== $user->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }
        $this->reservationService->cancel($reservation);

        return new JsonResponse(['success' => true]);
    }
}
