<?php

namespace App\Controller\FrontOffice;

use App\Entity\ReservationMedicament;
use App\Entity\StockPharmacy;
use App\Entity\User;
use App\Entity\Pharmacy;
use App\Entity\Medicament;
use App\Entity\Pharmacien;
use App\Repository\PharmacyRepository;
use App\Repository\MedicamentRepository;
use App\Repository\StockPharmacyRepository;
use App\Security\PharmacyVoter;
use App\Service\ReservationMedicamentService;
use App\Service\NotificationService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/pharmacien', name: 'api_pharmacien_')]
class PharmacienApiController extends AbstractController
{
    public function __construct(
        private ReservationMedicamentService $reservationService,
        private StockPharmacyRepository $stockRepository,
        private PharmacyRepository $pharmacyRepository,
        private MedicamentRepository $medicamentRepository,
        private NotificationService $notificationService,
        private EntityManagerInterface $em
    ) {}

    #[Route('/reservations/{id}/confirm', name: 'reservation_confirm', methods: ['POST'])]
    public function confirm(ReservationMedicament $reservation): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::ORDERS);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien || $reservation->getPharmacie()->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        try {
            $this->reservationService->confirm($reservation);
        } catch (\RuntimeException $e) {
            return new JsonResponse(['success' => false, 'message' => $e->getMessage()], 409);
        }

        return new JsonResponse(['success' => true, 'status' => 'confirmee']);
    }

    #[Route('/reservations/{id}/reject', name: 'reservation_reject', methods: ['POST'])]
    public function reject(ReservationMedicament $reservation): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::ORDERS);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien || $reservation->getPharmacie()->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $this->reservationService->reject($reservation);
        return new JsonResponse(['success' => true, 'status' => 'refusee']);
    }

    #[Route('/stocks/{id}', name: 'stock_update', methods: ['POST'])]
    public function updateStock(StockPharmacy $stock, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::STOCK);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien || $stock->getPharmecie()->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $payload = json_decode($request->getContent(), true) ?: [];
        if (isset($payload['quantite'])) {
            $stock->setQuantite((int) $payload['quantite']);
        }
        if (isset($payload['prix'])) {
            $stock->setPrixVente((string) $payload['prix']);
        }
        $stock->setUpdatedAt(new \DateTime());
        $this->em->flush();

        if ($stock->getQuantite() <= 5) {
            $this->notificationService->notify(
                $user,
                'Alerte stock faible',
                'Le stock d\'un médicament est faible.',
                'pharmacy',
                null,
                'normal'
            );
        }

        return new JsonResponse(['success' => true]);
    }

    #[Route('/stocks/{id}/delete', name: 'stock_delete', methods: ['POST'])]
    public function deleteStock(StockPharmacy $stock): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::STOCK);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien || $stock->getPharmecie()->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $this->em->remove($stock);
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }

    #[Route('/pharmacies', name: 'pharmacy_create', methods: ['POST'])]
    public function createPharmacy(Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien) {
            return new JsonResponse(['success' => false, 'message' => 'Pharmacien requis'], 403);
        }

        $payload = json_decode($request->getContent(), true) ?: [];
        $pharmacy = new Pharmacy();
        $pharmacy->setNom((string) ($payload['nom'] ?? ''));
        $pharmacy->setAdresse((string) ($payload['adresse'] ?? ''));
        $pharmacy->setTelephone($payload['telephone'] ?? null);
        $pharmacy->setEmail($payload['email'] ?? null);
        $pharmacy->setHoraires($payload['horaires'] ?? null);
        $pharmacy->setLatitude($payload['latitude'] ?? null);
        $pharmacy->setLongitude($payload['longitude'] ?? null);
        $pharmacy->setIsActive((bool) ($payload['is_active'] ?? true));
        $pharmacy->setPharmacien($pharmacien);

        $this->em->persist($pharmacy);
        $this->em->flush();

        return new JsonResponse(['success' => true, 'id' => $pharmacy->getId()]);
    }

    #[Route('/pharmacies/{id}', name: 'pharmacy_update', methods: ['POST'])]
    public function updatePharmacy(Pharmacy $pharmacy, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien || $pharmacy->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $payload = json_decode($request->getContent(), true) ?: [];
        if (isset($payload['nom'])) $pharmacy->setNom((string) $payload['nom']);
        if (isset($payload['adresse'])) $pharmacy->setAdresse((string) $payload['adresse']);
        if (array_key_exists('telephone', $payload)) $pharmacy->setTelephone($payload['telephone']);
        if (array_key_exists('email', $payload)) $pharmacy->setEmail($payload['email']);
        if (array_key_exists('horaires', $payload)) $pharmacy->setHoraires($payload['horaires']);
        if (array_key_exists('latitude', $payload)) $pharmacy->setLatitude($payload['latitude']);
        if (array_key_exists('longitude', $payload)) $pharmacy->setLongitude($payload['longitude']);
        if (array_key_exists('is_active', $payload)) $pharmacy->setIsActive((bool) $payload['is_active']);
        $pharmacy->setUpdatedAt(new \DateTime());

        $this->em->flush();
        return new JsonResponse(['success' => true]);
    }

    #[Route('/pharmacies/{id}/delete', name: 'pharmacy_delete', methods: ['POST'])]
    public function deletePharmacy(Pharmacy $pharmacy): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien || $pharmacy->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Accès refusé'], 403);
        }

        $this->em->remove($pharmacy);
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }

    #[Route('/medicaments', name: 'medicament_create', methods: ['POST'])]
    public function createMedicament(Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);
        $payload = json_decode($request->getContent(), true) ?: [];
        $med = new Medicament();
        $med->setNom((string) ($payload['nom'] ?? ''));
        $med->setType($payload['type'] ?? null);
        $med->setForme($payload['forme'] ?? null);
        $med->setDosage($payload['dosage'] ?? null);
        $med->setLaboratoire($payload['laboratoire'] ?? null);
        $med->setCodeBarre($payload['code_barre'] ?? null);
        $med->setPrix($payload['prix'] ?? null);
        $med->setStock((int) ($payload['stock'] ?? 0));

        $this->em->persist($med);
        $this->em->flush();

        return new JsonResponse(['success' => true, 'id' => $med->getId()]);
    }

    #[Route('/medicaments/{id}', name: 'medicament_update', methods: ['POST'])]
    public function updateMedicament(Medicament $med, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);

        $payload = json_decode($request->getContent(), true) ?: [];
        if (isset($payload['nom'])) $med->setNom((string) $payload['nom']);
        if (array_key_exists('type', $payload)) $med->setType($payload['type']);
        if (array_key_exists('forme', $payload)) $med->setForme($payload['forme']);
        if (array_key_exists('dosage', $payload)) $med->setDosage($payload['dosage']);
        if (array_key_exists('laboratoire', $payload)) $med->setLaboratoire($payload['laboratoire']);
        if (array_key_exists('code_barre', $payload)) $med->setCodeBarre($payload['code_barre']);
        if (array_key_exists('prix', $payload)) $med->setPrix($payload['prix']);
        $med->setUpdatedAt(new \DateTime());
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }

    #[Route('/medicaments/{id}/delete', name: 'medicament_delete', methods: ['POST'])]
    public function deleteMedicament(Medicament $med): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);

        $this->em->remove($med);
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }

    #[Route('/stocks', name: 'stock_create', methods: ['POST'])]
    public function createStock(Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted(PharmacyVoter::STOCK);
        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        if (!$pharmacien) {
            return new JsonResponse(['success' => false, 'message' => 'Pharmacien requis'], 403);
        }
        $payload = json_decode($request->getContent(), true) ?: [];
        $pharmacy = $this->pharmacyRepository->find((int) ($payload['pharmacy_id'] ?? 0));
        $med = $this->medicamentRepository->find((int) ($payload['medicament_id'] ?? 0));
        if (!$pharmacy || !$med || $pharmacy->getPharmacien()?->getId() !== $pharmacien->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Pharmacie ou médicament invalide'], 422);
        }

        $stock = new StockPharmacy();
        $stock->setPharmecie($pharmacy);
        $stock->setMedicament($med);
        $stock->setQuantite((int) ($payload['quantite'] ?? 0));
        $stock->setPrixVente($payload['prix'] ?? null);
        $this->em->persist($stock);
        $this->em->flush();

        return new JsonResponse(['success' => true, 'id' => $stock->getId()]);
    }

    private function pharmacienOwnsMedicament(?Pharmacien $pharmacien, Medicament $med): bool
    {
        if (!$pharmacien) {
            return false;
        }

        $count = $this->stockRepository->createQueryBuilder('s')
            ->select('COUNT(s.id)')
            ->join('s.pharmacie', 'p')
            ->andWhere('p.pharmacien = :pid')
            ->andWhere('s.medicament = :med')
            ->setParameter('pid', $pharmacien->getId())
            ->setParameter('med', $med)
            ->getQuery()
            ->getSingleScalarResult();

        return (int) $count > 0;
    }
}
