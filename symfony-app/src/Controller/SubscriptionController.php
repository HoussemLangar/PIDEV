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
use App\Service\Payment\StripeCheckoutService;
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
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

#[AsController]
class SubscriptionController extends AbstractController
{
    private const AI_TOOLS_TYPE = 'AI_TOOLS';

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
            self::AI_TOOLS_TYPE,
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
    public function payment(
        Request $request,
        AbonnementRepository $abonnementRepository,
        StripeCheckoutService $stripeCheckoutService
    ): Response
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

        $planLabel = $this->labelFromRole($type);
        $price = $this->priceFromType($type);

        if ($type === self::AI_TOOLS_TYPE && $abonnementRepository->findActiveForUserAndType($user, self::AI_TOOLS_TYPE)) {
            $this->addFlash('info', 'Votre abonnement IA est déjà actif.');
            $request->getSession()->remove('subscription_type');
            return $this->redirectToRoute('app_ai_tools_index');
        }

        if ($request->isMethod('POST')) {
            $successUrl = $this->generateUrl('app_subscription_payment_success', [], UrlGeneratorInterface::ABSOLUTE_URL)
                . '?session_id={CHECKOUT_SESSION_ID}';
            $cancelUrl = $this->generateUrl('app_subscription_payment_cancel', [], UrlGeneratorInterface::ABSOLUTE_URL);

            $checkoutUrl = $stripeCheckoutService->createCheckoutSession(
                planLabel: $planLabel,
                amount: $price,
                successUrl: $successUrl,
                cancelUrl: $cancelUrl,
                customerEmail: $user->getEmail(),
                metadata: [
                    'subscription_type' => $type,
                    'user_id' => (string) $user->getId(),
                ]
            );

            if ($checkoutUrl === null) {
                $this->addFlash('error', 'API de paiement indisponible. Vérifiez la configuration Stripe.');
                return $this->render('front/subscription_payment.html.twig', [
                    'type' => $type,
                    'planLabel' => $planLabel,
                    'price' => $price,
                    'providerCurrency' => strtoupper($stripeCheckoutService->getCurrency()),
                    'providerAmount' => $stripeCheckoutService->convertFromTnd($price),
                ]);
            }

            return $this->redirect($checkoutUrl);
        }

        return $this->render('front/subscription_payment.html.twig', [
            'type' => $type,
            'planLabel' => $planLabel,
            'price' => $price,
            'providerCurrency' => strtoupper($stripeCheckoutService->getCurrency()),
            'providerAmount' => $stripeCheckoutService->convertFromTnd($price),
        ]);
    }

    #[Route('/subscription/payment/success', name: 'app_subscription_payment_success', methods: ['GET'])]
    public function paymentSuccess(
        Request $request,
        EntityManagerInterface $em,
        InvoiceService $invoiceService,
        MailerInterface $mailer,
        AbonnementRepository $abonnementRepository,
        StripeCheckoutService $stripeCheckoutService
    ): RedirectResponse {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('login');
        }

        $type = (string) $request->getSession()->get('subscription_type', '');
        if ($type === '') {
            return $this->redirectToRoute('app_subscription');
        }

        $sessionId = trim((string) $request->query->get('session_id', ''));
        if ($sessionId === '') {
            $this->addFlash('error', 'Session de paiement invalide.');
            return $this->redirectToRoute('app_subscription_payment');
        }

        $checkoutSession = $stripeCheckoutService->fetchSession($sessionId);
        if (!$checkoutSession || (($checkoutSession['payment_status'] ?? null) !== 'paid')) {
            $this->addFlash('error', 'Paiement non confirmé.');
            return $this->redirectToRoute('app_subscription_payment');
        }

        $alreadyProcessed = (array) $request->getSession()->get('processed_stripe_sessions', []);
        if (in_array($sessionId, $alreadyProcessed, true)) {
            if ($type === self::AI_TOOLS_TYPE) {
                return $this->redirectToRoute('app_ai_tools_index');
            }

            return $this->redirectToRoute('app_subscription_overview');
        }

        if ($type === self::AI_TOOLS_TYPE && $abonnementRepository->findActiveForUserAndType($user, self::AI_TOOLS_TYPE)) {
            $request->getSession()->remove('subscription_type');
            $this->addFlash('info', 'Votre abonnement IA est déjà actif.');
            return $this->redirectToRoute('app_ai_tools_index');
        }

        $planLabel = $this->labelFromRole($type);
        $price = $this->priceFromType($type);

        $abonnement = new Abonnement();
        $abonnement->setNom($planLabel);
        $abonnement->setTypeAbonnement($type);
        $abonnement->setPrix(number_format($price, 2, '.', ''));
        $abonnement->setDureeMois(1);
        $abonnement->setDateDebut(new \DateTime());
        $abonnement->setDateFin((new \DateTime())->modify('+1 month'));
        $abonnement->setStatut('actif');
        $abonnement->setUser($user);

        $em->persist($abonnement);

        $tvaTaux = 19.0;
        $montantHt = $price;
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

        if ($type !== self::AI_TOOLS_TYPE) {
            $user->setRole($type);
            $user->setSubscriptionStatus('ACTIVE');
            $user->setSubscriptionType($type);
            $user->setSubscriptionEndAt((new \DateTimeImmutable())->modify('+1 month'));
            $user->setUpdatedAt(new \DateTimeImmutable());

            $this->ensureRoleEntity($user, $type, $em);
        }

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

        $alreadyProcessed[] = $sessionId;
        $request->getSession()->set('processed_stripe_sessions', array_values(array_unique($alreadyProcessed)));
        $request->getSession()->remove('subscription_type');
        $request->getSession()->remove('subscription_expired_notice_shown');

        $this->addFlash('success', 'Paiement réussi. Abonnement activé.');

        if ($type === self::AI_TOOLS_TYPE) {
            return $this->redirectToRoute('app_ai_tools_index');
        }

        return $this->redirectToRoute('app_subscription_overview');
    }

    #[Route('/subscription/payment/cancel', name: 'app_subscription_payment_cancel', methods: ['GET'])]
    public function paymentCancel(Request $request): RedirectResponse
    {
        $type = (string) $request->getSession()->get('subscription_type', '');

        $this->addFlash('warning', 'Paiement annulé.');

        if ($type === self::AI_TOOLS_TYPE) {
            return $this->redirectToRoute('app_ai_tools_subscription');
        }

        return $this->redirectToRoute('app_subscription_payment');
    }

    private function labelFromRole(string $role): string
    {
        return match ($role) {
            'ROLE_MEDECIN' => 'Médecin',
            'ROLE_PHARMACIEN' => 'Pharmacien',
            'ROLE_COACH' => 'Coach sportif',
            'ROLE_NUTRITIONNISTE' => 'Nutritionniste',
            'ROLE_PATIENT' => 'Patient',
            self::AI_TOOLS_TYPE => 'Outils IA SANTÉA',
            default => 'Abonnement',
        };
    }

    private function priceFromType(string $type): float
    {
        if ($type === self::AI_TOOLS_TYPE) {
            return 5.00;
        }

        return 10.00;
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
