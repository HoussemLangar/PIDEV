<?php

namespace App\Controller\AdminDashboard;

use App\Entity\Medicament;
use App\Entity\Pharmacy;
use App\Entity\StockPharmacy;
use App\Repository\MedicamentRepository;
use App\Repository\PharmacyRepository;
use App\Repository\StockPharmacyRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[IsGranted('ROLE_ADMIN')]
#[Route('/admin/pharmacy', name: 'admin_pharmacy_')]
class PharmacyAdminApiController extends AbstractController
{
    public function __construct(
        private EntityManagerInterface $em,
        private PharmacyRepository $pharmacyRepository,
        private MedicamentRepository $medicamentRepository,
        private StockPharmacyRepository $stockRepository
    ) {}

    #[Route('/pharmacies', name: 'pharmacies_create', methods: ['POST'])]
    public function createPharmacy(Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
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

        $this->em->persist($pharmacy);
        $this->em->flush();

        return new JsonResponse(['success' => true, 'id' => $pharmacy->getId()]);
    }

    #[Route('/pharmacies/{id}', name: 'pharmacies_update', methods: ['POST'])]
    public function updatePharmacy(Pharmacy $pharmacy, Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
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

    #[Route('/pharmacies/{id}/delete', name: 'pharmacies_delete', methods: ['POST'])]
    public function deletePharmacy(Pharmacy $pharmacy, Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
        $this->em->remove($pharmacy);
        $this->em->flush();
        return new JsonResponse(['success' => true]);
    }

    #[Route('/medicaments', name: 'medicaments_create', methods: ['POST'])]
    public function createMedicament(Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
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

    #[Route('/medicaments/{id}', name: 'medicaments_update', methods: ['POST'])]
    public function updateMedicament(Medicament $med, Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
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

    #[Route('/medicaments/{id}/delete', name: 'medicaments_delete', methods: ['POST'])]
    public function deleteMedicament(Medicament $med, Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
        $this->em->remove($med);
        $this->em->flush();
        return new JsonResponse(['success' => true]);
    }

    #[Route('/stocks', name: 'stocks_create', methods: ['POST'])]
    public function createStock(Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
        $payload = json_decode($request->getContent(), true) ?: [];
        $pharmacy = $this->pharmacyRepository->find((int) ($payload['pharmacy_id'] ?? 0));
        $med = $this->medicamentRepository->find((int) ($payload['medicament_id'] ?? 0));
        if (!$pharmacy || !$med) {
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

    #[Route('/stocks/{id}', name: 'stocks_update', methods: ['POST'])]
    public function updateStock(StockPharmacy $stock, Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
        $payload = json_decode($request->getContent(), true) ?: [];
        if (isset($payload['quantite'])) $stock->setQuantite((int) $payload['quantite']);
        if (isset($payload['prix'])) $stock->setPrixVente($payload['prix']);
        $stock->setUpdatedAt(new \DateTime());
        $this->em->flush();
        return new JsonResponse(['success' => true]);
    }

    #[Route('/stocks/{id}/delete', name: 'stocks_delete', methods: ['POST'])]
    public function deleteStock(StockPharmacy $stock, Request $request): JsonResponse
    {
        if (!$this->isCsrfTokenValid('admin_pharmacy', $request->headers->get('X-CSRF-TOKEN'))) {
            return new JsonResponse(['success' => false, 'message' => 'CSRF invalide'], 403);
        }
        $this->em->remove($stock);
        $this->em->flush();
        return new JsonResponse(['success' => true]);
    }
}
