<?php

namespace App\Controller;

use App\Entity\User;
use App\Entity\Abonnement;
use App\Entity\Patient;
use App\Entity\Medecin;
use App\Entity\Pharmacien;
use App\Entity\CoachSportif;
use App\Entity\Nutritionniste;
use App\Repository\AbonnementRepository;
use App\Repository\SuspiciousLoginRepository;
use App\Entity\Facture;
use App\Service\InvoiceService;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Attribute\AsController;
use Symfony\Component\Routing\Annotation\Route;

#[AsController]
class SubscriptionController extends AbstractController
{
    #[Route('/subscription', name: 'app_subscription')]
    public function choice(): Response
    {
        $this->denyAccessUnlessGranted('ROLE_USER');

        $plans = [
            ['label' => 'Médecin', 'role' => 'ROLE_MEDECIN', 'icon' => '👨‍⚕️'],
            ['label' => 'Pharmacien', 'role' => 'ROLE_PHARMACIEN', 'icon' => '💊'],
            ['label' => 'Coach sportif', 'role' => 'ROLE_COACH', 'icon' => '🏋️'],
            ['label' => 'Nutritionniste', 'role' => 'ROLE_NUTRITIONNISTE', 'icon' => '🥗'],
            ['label' => 'Patient', 'role' => 'ROLE_PATIENT', 'icon' => '🧑'],
        ];

        return $this->render('front/subscription_choice.html.twig', [
            'plans' => $plans,
        ]);
    }

    #[Route('/subscription/overview', name: 'app_subscription_overview')]
    public function overview(AbonnementRepository $abonnementRepository, EntityManagerInterface $em): Response
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('login');
        }

        $abonnement = $abonnementRepository->findLatestForUser($user);

        if ($abonnement) {
            $today = new \DateTimeImmutable('today');
            $endDate = $abonnement->getDateFin();
            $endDay = ($endDate instanceof \DateTimeImmutable)
                ? $endDate->setTime(0, 0)
                : \DateTimeImmutable::createFromMutable((clone $endDate)->setTime(0, 0));

            if ($endDay < $today) {
                if (strtolower($abonnement->getStatut()) !== 'expire') {
                    $abonnement->setStatut('expire');
                }

                $this->removeRoleEntity($user, $em);
                $user->setRole('ROLE_USER');
                $user->setSubscriptionStatus('EXPIRED');
                $user->setSubscriptionType(null);
                $user->setSubscriptionEndAt(null);
                $user->setUpdatedAt(new \DateTimeImmutable());

                $em->flush();

                $this->addFlash('warning', 'Votre abonnement est terminé. Veuillez renouveler pour récupérer l\'accès aux services premium.');
                return $this->redirectToRoute('app_subscription');
            }
        }

        if (!$user->isSubscriptionActive() || !$abonnement) {
            return $this->redirectToRoute('app_subscription');
        }

        return $this->render('front/subscription_overview.html.twig', [
            'abonnement' => $abonnement,
        ]);
    }

    #[Route('/subscription/checkout', name: 'app_subscription_checkout', methods: ['POST'])]
    public function checkout(Request $request): RedirectResponse
    {
        $type = (string) $request->request->get('subscription_type', '');
        $allowed = [
            'ROLE_MEDECIN',
            'ROLE_PHARMACIEN',
            'ROLE_COACH',
            'ROLE_NUTRITIONNISTE',
            'ROLE_PATIENT',
        ];

        if (!in_array($type, $allowed, true)) {
            $this->addFlash('error', 'Type d\'abonnement invalide.');
            return $this->redirectToRoute('app_subscription');
        }

        $request->getSession()->set('subscription_type', $type);
        return $this->redirectToRoute('app_subscription_payment');
    }

    #[Route('/subscription/skip', name: 'app_subscription_skip', methods: ['POST'])]
    public function skip(EntityManagerInterface $em): RedirectResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('login');
        }

        $user->setSubscriptionStatus('SKIPPED');
        $user->setSubscriptionType(null);
        $user->setSubscriptionEndAt(null);
        $user->setUpdatedAt(new \DateTimeImmutable());

        $em->flush();

        $this->addFlash('info', 'Mode découverte activé.');
        return $this->redirectToRoute('app_home');
    }

    #[Route('/subscription/payment', name: 'app_subscription_payment', methods: ['GET', 'POST'])]
    public function payment(Request $request, EntityManagerInterface $em, InvoiceService $invoiceService, MailerInterface $mailer): Response
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('login');
        }

        $type = (string) $request->getSession()->get('subscription_type', '');
        if ($type === '') {
            return $this->redirectToRoute('app_subscription');
        }

        if ($request->isMethod('POST')) {
            $cardNumber = preg_replace('/\\D+/', '', (string) $request->request->get('card_number', ''));
            $cardName = trim((string) $request->request->get('card_name', ''));
            $expMonth = (int) $request->request->get('exp_month', 0);
            $expYear = (int) $request->request->get('exp_year', 0);
            $cvv = preg_replace('/\\D+/', '', (string) $request->request->get('cvv', ''));

            $now = new \DateTimeImmutable();
            $currentYear = (int) $now->format('Y');
            $currentMonth = (int) $now->format('n');

            $errors = [];
            if (strlen($cardNumber) < 16) {
                $errors[] = 'Numéro de carte invalide.';
            }
            if ($cardName === '') {
                $errors[] = 'Nom du titulaire requis.';
            }
            if ($expMonth < 1 || $expMonth > 12) {
                $errors[] = 'Mois d\'expiration invalide.';
            }
            if ($expYear < $currentYear || ($expYear === $currentYear && $expMonth < $currentMonth)) {
                $errors[] = 'Carte expirée.';
            }
            if (strlen($cvv) < 3) {
                $errors[] = 'CVV invalide.';
            }

            if ($errors) {
                foreach ($errors as $error) {
                    $this->addFlash('error', $error);
                }
                return $this->render('front/subscription_payment.html.twig', [
                    'type' => $type,
                ]);
            }

            $abonnement = new Abonnement();
            $abonnement->setNom($this->labelFromRole($type));
            $abonnement->setTypeAbonnement($type);
            $abonnement->setPrix('10.00');
            $abonnement->setDureeMois(1);
            $abonnement->setDateDebut(new \DateTime());
            $abonnement->setDateFin((new \DateTime())->modify('+1 month'));
            $abonnement->setStatut('actif');
            $abonnement->setUser($user);

            $em->persist($abonnement);

            $tvaTaux = 19.0;
            $montantHt = 10.00;
            $tvaMontant = round($montantHt * $tvaTaux / 100, 2);
            $montantTtc = $montantHt + $tvaMontant;

            $facture = new Facture();
            $facture->setNumero('INV-' . date('Ymd') . '-' . strtoupper(bin2hex(random_bytes(3))));
            $facture->setUser($user);
            $facture->setAbonnement($abonnement);
            $facture->setMontantHt(number_format($montantHt, 2, '.', ''));
            $facture->setTvaTaux(number_format($tvaTaux, 2, '.', ''));
            $facture->setTvaMontant(number_format($tvaMontant, 2, '.', ''));
            $facture->setMontantTtc(number_format($montantTtc, 2, '.', ''));
            $em->persist($facture);

            $user->setRole($type);
            $user->setSubscriptionStatus('ACTIVE');
            $user->setSubscriptionType($type);
            $user->setSubscriptionEndAt((new \DateTimeImmutable())->modify('+1 month'));
            $user->setUpdatedAt(new \DateTimeImmutable());

            $this->ensureRoleEntity($user, $type, $em);

            $em->flush();

            $pdfPath = $invoiceService->generatePdf($facture);
            $facture->setPdfPath($pdfPath);
            $em->flush();

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Votre facture - ' . $facture->getNumero())
                ->htmlTemplate('emails/invoice.html.twig')
                ->context([
                    'user' => $user,
                    'facture' => $facture,
                ])
                ->attachFromPath($pdfPath, 'facture-' . $facture->getNumero() . '.pdf');

            $mailer->send($email);
            $request->getSession()->remove('subscription_type');
            $request->getSession()->remove('subscription_expired_notice_shown');

            $this->addFlash('success', 'Paiement réussi. Abonnement activé.');
            return $this->redirectToRoute('app_subscription_overview');
        }

        return $this->render('front/subscription_payment.html.twig', [
            'type' => $type,
        ]);
    }

    private function labelFromRole(string $role): string
    {
        return match ($role) {
            'ROLE_MEDECIN' => 'Médecin',
            'ROLE_PHARMACIEN' => 'Pharmacien',
            'ROLE_COACH' => 'Coach sportif',
            'ROLE_NUTRITIONNISTE' => 'Nutritionniste',
            'ROLE_PATIENT' => 'Patient',
            default => 'Abonnement',
        };
    }

    private function ensureRoleEntity(User $user, string $role, EntityManagerInterface $em): void
    {
        switch ($role) {
            case 'ROLE_PATIENT':
                if (!$user->getPatient()) {
                    $patient = new Patient();
                    $patient->setUser($user);
                    $em->persist($patient);
                }
                break;
            case 'ROLE_MEDECIN':
                if (!$user->getMedecin()) {
                    $medecin = new Medecin();
                    $medecin->setUser($user);
                    $medecin->setSpecialite('Non défini');
                    $em->persist($medecin);
                }
                break;
            case 'ROLE_PHARMACIEN':
                if (!$user->getPharmacien()) {
                    $pharmacien = new Pharmacien();
                    $pharmacien->setUser($user);
                    $em->persist($pharmacien);
                }
                break;
            case 'ROLE_COACH':
                if (!$user->getCoachSportif()) {
                    $coach = new CoachSportif();
                    $coach->setUser($user);
                    $coach->setSpecialite('Non défini');
                    $em->persist($coach);
                }
                break;
            case 'ROLE_NUTRITIONNISTE':
                if (!$user->getNutritionniste()) {
                    $nutritionniste = new Nutritionniste();
                    $nutritionniste->setUser($user);
                    $nutritionniste->setSpecialite('Non défini');
                    $em->persist($nutritionniste);
                }
                break;
        }
    }

    #[Route('/subscription/cancel', name: 'app_subscription_cancel', methods: ['POST'])]
    public function cancel(EntityManagerInterface $em, AbonnementRepository $abonnementRepository): RedirectResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('login');
        }

        $abonnements = $abonnementRepository->findBy(['user' => $user]);
        foreach ($abonnements as $abonnement) {
            $em->remove($abonnement);
        }

        $this->removeRoleEntity($user, $em);

        $user->setRole('ROLE_USER');
        $user->setSubscriptionStatus('EXPIRED');
        $user->setSubscriptionType(null);
        $user->setSubscriptionEndAt(null);
        $user->setUpdatedAt(new \DateTimeImmutable());

        $em->flush();

        $this->addFlash('success', 'Abonnement annulé.');
        return $this->redirectToRoute('app_subscription');
    }

    private function removeRoleEntity(User $user, EntityManagerInterface $em): void
    {
        if ($user->getPatient()) {
            $em->remove($user->getPatient());
            $user->setPatient(null);
        }
        if ($user->getMedecin()) {
            $em->remove($user->getMedecin());
            $user->setMedecin(null);
        }
        if ($user->getPharmacien()) {
            $em->remove($user->getPharmacien());
            $user->setPharmacien(null);
        }
        if ($user->getCoachSportif()) {
            $em->remove($user->getCoachSportif());
            $user->setCoachSportif(null);
        }
        if ($user->getNutritionniste()) {
            $em->remove($user->getNutritionniste());
            $user->setNutritionniste(null);
        }
    }
}
