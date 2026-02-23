<?php

namespace App\Controller\FrontOffice;

use App\Entity\Pharmacy;
use App\Entity\User;
use App\Form\PharmacyType;
use App\Repository\PharmacyRepository;
use App\Repository\MedicamentRepository;
use App\Repository\ReservationMedicamentRepository;
use App\Repository\StockPharmacyRepository;
use App\Security\PharmacyVoter;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/pharmacien', name: 'front_pharmacien_')]
class PharmacienDashboardController extends AbstractController
{
    #[Route('/dashboard', name: 'dashboard', methods: ['GET', 'POST'])]
    public function dashboard(
        PharmacyRepository $pharmacyRepository,
        MedicamentRepository $medicamentRepository,
        StockPharmacyRepository $stockRepository,
        ReservationMedicamentRepository $reservationRepository,
        EntityManagerInterface $em,
        Request $request
    ): Response {
        $this->denyAccessUnlessGranted(PharmacyVoter::MANAGE);

        /** @var User $user */
        $user = $this->getUser();
        $pharmacien = $user->getPharmacien();
        $pharmacies = $pharmacien ? $pharmacyRepository->findBy(['pharmacien' => $pharmacien]) : [];

        $pharmacy = new Pharmacy();
        if ($pharmacien) {
            $pharmacy->setPharmacien($pharmacien);
        }

        $form = $this->createForm(PharmacyType::class, $pharmacy, [
            'attr' => ['novalidate' => 'novalidate'],
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted()) {
            if (!$pharmacien) {
                $this->addFlash('error', 'Vous devez être associé à un pharmacien pour créer une pharmacie.');
            } elseif ($form->isValid()) {
                $em->persist($pharmacy);
                $em->flush();

                // Handle optional image upload
                $imageFile = $request->files->get('pharmacyImageFile');
                if ($imageFile) {
                    $mime = $imageFile->getMimeType() ?? '';
                    $allowedMimes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
                    if (in_array($mime, $allowedMimes, true) && $imageFile->getSize() <= 5 * 1024 * 1024) {
                        $slugger = new \Symfony\Component\String\Slugger\AsciiSlugger();
                        $orig = pathinfo($imageFile->getClientOriginalName(), PATHINFO_FILENAME);
                        $ext  = $imageFile->guessExtension() ?? 'jpg';
                        $name = $slugger->slug($orig) . '-' . uniqid() . '.' . $ext;
                        $dest = $this->getParameter('kernel.project_dir') . '/public/images/pharmacies';
                        $imageFile->move($dest, $name);
                        $pharmacy->setImageName($name);
                        $em->flush();
                    }
                }

                $this->addFlash('success', 'Pharmacie créée avec succès.');
                return $this->redirect($this->generateUrl('front_pharmacien_dashboard') . '#list-pharmacies');
            }
        }

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
        $medicaments = [];
        if ($pharmacien) {
            $seen = [];
            foreach ($stock as $item) {
                $med = $item->getMedicament();
                if ($med && !isset($seen[$med->getId()])) {
                    $seen[$med->getId()] = true;
                    $medicaments[] = $med;
                }
            }
        }

        return $this->render('front/pharmacien/dashboard.html.twig', [
            'pharmacies' => $pharmacies,
            'medicaments' => $medicaments,
            'medicaments_all' => $medicamentsAll,
            'stocks' => $stock,
            'reservations' => $reservations,
            'pharmacy_form' => $form->createView(),
        ]);
    }
}
