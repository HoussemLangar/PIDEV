<?php
namespace App\Controller;

use App\Entity\User;
use App\Entity\UserSession;
use App\Form\AdminUserType;
use App\Form\AdminUserResetPasswordType;
use App\Entity\Facture;
use App\Repository\AbonnementRepository;
use App\Repository\ContenuRepository;
use App\Repository\SuspiciousLoginRepository;
use App\Repository\UserSessionRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\Pagination\Paginator;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Form\FormError;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\HttpFoundation\StreamedResponse;

#[IsGranted('ROLE_ADMIN')]
class AdminController extends AbstractController
{
    // ========== DASHBOARD ==========
    #[Route('/admin_dashboard', name: 'admin_dashboard')]
    public function dashboard(
        UserRepository $userRepository,
        AbonnementRepository $abonnementRepository,
        ContenuRepository $contenuRepository,
        SuspiciousLoginRepository $suspiciousLoginRepository,
        EntityManagerInterface $em
    ): Response
    {
        $userStats = $userRepository->getUserStats();
        $validationStats = $userRepository->getValidationStats();
        $subscriptionStats = $abonnementRepository->getSubscriptionStats();

        $monthStart = new \DateTimeImmutable('first day of this month 00:00:00');
        $monthEnd = $monthStart->modify('first day of next month 00:00:00');

        $monthlyRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->andWhere('f.createdAt >= :from')
            ->andWhere('f.createdAt < :to')
            ->setParameter('from', $monthStart)
            ->setParameter('to', $monthEnd)
            ->getQuery()
            ->getSingleScalarResult();

        $totalRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $totalInvoices = (int) $em->createQueryBuilder()
            ->select('COUNT(f.id)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $pendingPayments = (int) $em->createQueryBuilder()
            ->select('COUNT(u.id)')
            ->from(User::class, 'u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.subscriptionStatus = :status')
            ->setParameter('status', 'PENDING')
            ->getQuery()
            ->getSingleScalarResult();

        // Statistiques principales (demandées)
        $stats = [
            'totalUsers' => $userStats['total'],
            'activeSubscriptions' => $subscriptionStats['active'],
            'monthlyRevenue' => $monthlyRevenue,
            'pendingPayments' => $pendingPayments,
            // autres stats disponibles
            'activeUsers' => $userStats['active'],
            'bannedUsers' => $userStats['banned'],
            'emailVerified' => $validationStats['emailVerified'],
            'adminPending' => $validationStats['adminPending'],
            'totalInvoices' => $totalInvoices,
            'totalRevenue' => $totalRevenue,
            'expiredSubscriptions' => $subscriptionStats['expired'],
            'cancelledSubscriptions' => $subscriptionStats['cancelled'],
        ];

        // Utilisateurs par rôle
        $usersByRole = [
            ['name' => 'Patients', 'count' => $userRepository->countByRole('ROLE_PATIENT'), 'icon' => 'fas fa-user'],
            ['name' => 'Médecins', 'count' => $userRepository->countByRole('ROLE_MEDECIN'), 'icon' => 'fas fa-user-md'],
            ['name' => 'Pharmaciens', 'count' => $userRepository->countByRole('ROLE_PHARMACIEN'), 'icon' => 'fas fa-prescription-bottle'],
            ['name' => 'Coach', 'count' => $userRepository->countByRole('ROLE_COACH'), 'icon' => 'fas fa-dumbbell'],
            ['name' => 'Nutritionnistes', 'count' => $userRepository->countByRole('ROLE_NUTRITIONNISTE'), 'icon' => 'fas fa-apple-alt'],
            ['name' => 'Admins', 'count' => $userRepository->countByRole('ROLE_ADMIN'), 'icon' => 'fas fa-shield-alt'],
        ];

        // Validations en attente (existant)
        $pendingValidations = [
            'professionals' => $validationStats['adminPending'],
            'pharmacies' => 0,
            'articles' => $contenuRepository->countPending(),
            'reports' => 0,
        ];

        // Activité récente (placeholder)
        $recentActivity = [
            'newUsers' => 0,
            'newAppointments' => 0,
            'medicationSearches' => 0,
            'trackerEntries' => 0,
        ];

        $registrationsChart = $userRepository->getRegistrationsChart(30);

        $revenueByTypeRows = $em->createQueryBuilder()
            ->select('a.typeAbonnement as type, COALESCE(SUM(f.montantTtc), 0) as total')
            ->from(Facture::class, 'f')
            ->leftJoin('f.abonnement', 'a')
            ->groupBy('a.typeAbonnement')
            ->getQuery()
            ->getResult();

        $revenueByType = [];
        foreach ($revenueByTypeRows as $row) {
            $revenueByType[$row['type'] ?? ''] = (float) $row['total'];
        }

        // Données pour les graphiques
        $chartData = [
            'registrations' => $registrationsChart,
            'revenue' => [
                'labels' => ['Médecin', 'Pharmacien', 'Coach', 'Nutritionniste', 'Patient'],
                'data' => [
                    $revenueByType['ROLE_MEDECIN'] ?? 0,
                    $revenueByType['ROLE_PHARMACIEN'] ?? 0,
                    $revenueByType['ROLE_COACH'] ?? 0,
                    $revenueByType['ROLE_NUTRITIONNISTE'] ?? 0,
                    $revenueByType['ROLE_PATIENT'] ?? 0,
                ],
            ],
        ];

        $suspiciousRecent = $suspiciousLoginRepository->findRecent(6);
        $suspiciousCount = $suspiciousLoginRepository->countBlocked();

        // Activités récentes pour le tableau
        $recentActivities = [
            [
                'createdAt' => new \DateTime('-5 minutes'),
                'type' => 'registration',
                'typeName' => 'Inscription',
                'icon' => 'fas fa-user-plus',
                'user' => ['name' => 'Marie Dubois', 'avatar' => null],
                'description' => 'Nouvel utilisateur enregistré',
                'status' => 'success',
                'statusText' => 'Complété',
            ],
            [
                'createdAt' => new \DateTime('-12 minutes'),
                'type' => 'appointment',
                'typeName' => 'Rendez-vous',
                'icon' => 'fas fa-calendar-check',
                'user' => ['name' => 'Dr. Jean Martin', 'avatar' => null],
                'description' => 'Rendez-vous confirmé',
                'status' => 'success',
                'statusText' => 'Confirmé',
            ],
            [
                'createdAt' => new \DateTime('-25 minutes'),
                'type' => 'subscription',
                'typeName' => 'Abonnement',
                'icon' => 'fas fa-credit-card',
                'user' => ['name' => 'Sophie Laurent', 'avatar' => null],
                'description' => 'Souscription Premium',
                'status' => 'success',
                'statusText' => 'Actif',
            ],
            [
                'createdAt' => new \DateTime('-35 minutes'),
                'type' => 'validation',
                'typeName' => 'Validation',
                'icon' => 'fas fa-user-check',
                'user' => ['name' => 'Dr. Paul Bernard', 'avatar' => null],
                'description' => 'Compte professionnel en attente',
                'status' => 'pending',
                'statusText' => 'En attente',
            ],
            [
                'createdAt' => new \DateTime('-45 minutes'),
                'type' => 'report',
                'typeName' => 'Signalement',
                'icon' => 'fas fa-flag',
                'user' => ['name' => 'Lucas Petit', 'avatar' => null],
                'description' => 'Signalement d\'un contenu',
                'status' => 'warning',
                'statusText' => 'À traiter',
            ],
        ];

        return $this->render('back/dashboard.html.twig', [
            'stats' => $stats,
            'usersByRole' => $usersByRole,
            'pendingValidations' => $pendingValidations,
            'recentActivity' => $recentActivity,
            'chartData' => $chartData,
            'recentActivities' => $recentActivities,
            'suspiciousRecent' => $suspiciousRecent,
            'suspiciousCount' => $suspiciousCount,
        ]);
    }

    #[Route('/admin_dashboard/export', name: 'admin_dashboard_export')]
    public function exportDashboardStats(
        UserRepository $userRepository,
        AbonnementRepository $abonnementRepository,
        SuspiciousLoginRepository $suspiciousLoginRepository,
        EntityManagerInterface $em
    ): StreamedResponse {
        $userStats = $userRepository->getUserStats();
        $validationStats = $userRepository->getValidationStats();
        $subscriptionStats = $abonnementRepository->getSubscriptionStats();

        $monthStart = new \DateTimeImmutable('first day of this month 00:00:00');
        $monthEnd = $monthStart->modify('first day of next month 00:00:00');

        $monthlyRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->andWhere('f.createdAt >= :from')
            ->andWhere('f.createdAt < :to')
            ->setParameter('from', $monthStart)
            ->setParameter('to', $monthEnd)
            ->getQuery()
            ->getSingleScalarResult();

        $totalRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $totalInvoices = (int) $em->createQueryBuilder()
            ->select('COUNT(f.id)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $pendingPayments = (int) $em->createQueryBuilder()
            ->select('COUNT(u.id)')
            ->from(User::class, 'u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.subscriptionStatus = :status')
            ->setParameter('status', 'PENDING')
            ->getQuery()
            ->getSingleScalarResult();
        $suspiciousCount = $suspiciousLoginRepository->countBlocked();

        $filename = 'dashboard_stats_' . (new \DateTimeImmutable())->format('Ymd_His') . '.csv';
        $response = new StreamedResponse(function () use (
            $userStats,
            $validationStats,
            $subscriptionStats,
            $monthlyRevenue,
            $totalRevenue,
            $totalInvoices,
            $pendingPayments
        ) {
            $handle = fopen('php://output', 'w');
            fputcsv($handle, ['KPI', 'Valeur']);
            fputcsv($handle, ['Utilisateurs total', $userStats['total']]);
            fputcsv($handle, ['Utilisateurs actifs', $userStats['active']]);
            fputcsv($handle, ['Utilisateurs bannis', $userStats['banned']]);
            fputcsv($handle, ['Abonnements actifs', $subscriptionStats['active']]);
            fputcsv($handle, ['Abonnements expirés', $subscriptionStats['expired']]);
            fputcsv($handle, ['Abonnements annulés', $subscriptionStats['cancelled']]);
            fputcsv($handle, ['Paiements en attente', $pendingPayments]);
            fputcsv($handle, ['Revenus du mois (TND)', number_format($monthlyRevenue, 2, '.', '')]);
            fputcsv($handle, ['Revenus total (TND)', number_format($totalRevenue, 2, '.', '')]);
            fputcsv($handle, ['Factures total', $totalInvoices]);
            fputcsv($handle, ['Email confirmés', $validationStats['emailVerified']]);
            fputcsv($handle, ['Admin en attente', $validationStats['adminPending']]);
            fputcsv($handle, ['Connexions suspectes', $suspiciousCount]);
            fclose($handle);
        });

        $response->headers->set('Content-Type', 'text/csv; charset=UTF-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="' . $filename . '"');

        return $response;
    }

    // ========== SÉCURITÉ (SESSIONS / ALERTES) ==========
    #[Route('/admin/security/sessions', name: 'admin_security_sessions')]
    public function sessions(Request $request, UserSessionRepository $userSessionRepository): Response
    {
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'status' => (string) $request->query->get('status', ''),
            'ip' => trim((string) $request->query->get('ip', '')),
            'country' => (string) $request->query->get('country', ''),
        ];

        $sessions = $userSessionRepository->createAdminQueryBuilder($filters)
            ->getQuery()
            ->getResult();

        return $this->render('admin/security/sessions.html.twig', [
            'sessions' => $sessions,
            'filters' => $filters,
            'idleMinutes' => 30,
        ]);
    }

    #[Route('/admin/security/sessions/{id}/revoke', name: 'admin_security_sessions_revoke', methods: ['POST'])]
    public function revokeSession(UserSession $session, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('revoke_session_' . $session->getId(), $request->request->get('_token'))) {
            return $this->redirectToRoute('admin_security_sessions');
        }

        if (!$session->isRevoked()) {
            $session->setRevokedAt(new \DateTimeImmutable());
            $em->flush();
        }

        $this->addFlash('success', 'Session révoquée.');
        return $this->redirectToRoute('admin_security_sessions');
    }

    #[Route('/admin/security/suspicious', name: 'admin_security_suspicious')]
    public function suspiciousLogins(Request $request, SuspiciousLoginRepository $suspiciousLoginRepository): Response
    {
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'status' => (string) $request->query->get('status', ''),
            'country' => (string) $request->query->get('country', ''),
        ];

        $logins = $suspiciousLoginRepository->createAdminQueryBuilder($filters)
            ->getQuery()
            ->getResult();

        return $this->render('admin/security/suspicious.html.twig', [
            'logins' => $logins,
            'filters' => $filters,
        ]);
    }
    // ========== GESTION UTILISATEURS ==========
    #[Route('/users', name: 'admin_users')]
    public function users(Request $request, UserRepository $userRepository): Response
    {
        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 12;

        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'role' => (string) $request->query->get('role', ''),
            'status' => (string) $request->query->get('status', ''),
            'from' => (string) $request->query->get('from', ''),
            'to' => (string) $request->query->get('to', ''),
        ];

        $qb = $userRepository->createFilteredQueryBuilder($filters);
        $qb->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        return $this->render('admin/users/index.html.twig', [
            'users' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'filters' => $filters,
        ]);
    }

    #[Route('/users/new', name: 'admin_users_new')]
    public function createUser(
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher
    ): Response {
        $user = new User();
        $form = $this->createForm(AdminUserType::class, $user, [
            'require_password' => true,
        ]);

        $form->handleRequest($request);
        if ($form->isSubmitted() && $form->isValid()) {
            $plainPassword = (string) $form->get('plainPassword')->getData();
            if ($plainPassword === '') {
                $form->addError(new FormError('Le mot de passe est obligatoire.'));
            } else {
                $user->setPassword($passwordHasher->hashPassword($user, $plainPassword));
                $em->persist($user);
                $em->flush();
                $this->addFlash('success', 'Utilisateur créé avec succès.');
                return $this->redirectToRoute('admin_users');
            }
        }

        return $this->render('admin/users/form.html.twig', [
            'form' => $form->createView(),
            'mode' => 'create',
        ]);
    }

    #[Route('/users/{id}/edit', name: 'admin_users_edit')]
    public function editUser(
        User $user,
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher
    ): Response {
        $form = $this->createForm(AdminUserType::class, $user, [
            'require_password' => false,
        ]);

        $form->handleRequest($request);
        if ($form->isSubmitted() && $form->isValid()) {
            $plainPassword = (string) $form->get('plainPassword')->getData();
            if ($plainPassword !== '') {
                $user->setPassword($passwordHasher->hashPassword($user, $plainPassword));
            }
            $user->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();
            $this->addFlash('success', 'Utilisateur mis à jour.');
            return $this->redirectToRoute('admin_users');
        }

        return $this->render('admin/users/form.html.twig', [
            'form' => $form->createView(),
            'mode' => 'edit',
            'user' => $user,
        ]);
    }

    #[Route('/users/{id}/reset-password', name: 'admin_users_reset_password')]
    public function resetPassword(
        User $user,
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher
    ): Response {
        $form = $this->createForm(AdminUserResetPasswordType::class);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $plainPassword = (string) $form->get('plainPassword')->getData();
            $user->setPassword($passwordHasher->hashPassword($user, $plainPassword));
            $user->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();
            $this->addFlash('success', 'Mot de passe réinitialisé.');
            return $this->redirectToRoute('admin_users_edit', ['id' => $user->getId()]);
        }

        return $this->render('admin/users/reset_password.html.twig', [
            'form' => $form->createView(),
            'user' => $user,
        ]);
    }

    #[Route('/users/{id}/ban', name: 'admin_users_ban', methods: ['POST'])]
    public function banUser(User $user, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('ban_user_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $isBanned = (bool) $request->request->get('is_banned', false);
        $banReason = trim((string) $request->request->get('ban_reason', ''));
        $banUntilRaw = trim((string) $request->request->get('ban_until', ''));

        $banUntil = null;
        if ($banUntilRaw !== '') {
            try {
                $banUntil = new \DateTimeImmutable($banUntilRaw);
            } catch (\Throwable) {
                $banUntil = null;
            }
        }

        $user->setIsBanned($isBanned);
        $user->setBanReason($isBanned ? ($banReason !== '' ? $banReason : null) : null);
        $user->setBanUntil($isBanned ? $banUntil : null);
        $user->setUpdatedAt(new \DateTimeImmutable());

        $em->flush();
        $this->addFlash('success', $isBanned ? 'Utilisateur banni.' : 'Utilisateur débanni.');

        return $this->redirectToRoute('admin_users');
    }

    #[Route('/users/{id}/delete', name: 'admin_users_delete', methods: ['POST'])]
    public function deleteUser(User $user, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('delete_user_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        if ($user->isDeleted()) {
            $this->addFlash('info', 'Utilisateur déjà supprimé.');
            return $this->redirectToRoute('admin_users');
        }

        $user->setDeletedAt(new \DateTimeImmutable());
        $user->setUpdatedAt(new \DateTimeImmutable());
        $em->flush();

        $this->addFlash('success', 'Utilisateur supprimé (soft delete).');
        return $this->redirectToRoute('admin_users');
    }

    #[Route('/users/stats', name: 'admin_users_stats')]
    public function usersStats(UserRepository $userRepository): Response
    {
        $stats = $userRepository->getUserStats();
        $chart = $userRepository->getRegistrationsChart(30);

        return $this->render('admin/users/stats.html.twig', [
            'stats' => $stats,
            'chart' => $chart,
        ]);
    }

    #[Route('/validation', name: 'admin_validation')]
    public function validation(Request $request, UserRepository $userRepository): Response
    {
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'email_verified' => (string) $request->query->get('email_verified', ''),
            'admin_approved' => (string) $request->query->get('admin_approved', 'no'),
        ];

        $qb = $userRepository->createValidationQueryBuilder($filters);
        $users = $qb->getQuery()->getResult();
        $stats = $userRepository->getValidationStats();
        return $this->render('admin/validation/index.html.twig', [
            'users' => $users,
            'filters' => $filters,
            'stats' => $stats,
        ]);
    }

    #[Route('/validation/{id}/approve', name: 'admin_validation_approve', methods: ['POST'])]
    public function approveValidation(int $id, Request $request, UserRepository $userRepository, EntityManagerInterface $em): RedirectResponse
    {
        $user = $userRepository->find($id);
        if (!$user) {
            throw $this->createNotFoundException('Utilisateur introuvable.');
        }

        if (!$this->isCsrfTokenValid('approve_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $user->setAdminApproved(true);
        $user->setUpdatedAt(new \DateTimeImmutable());
        $em->flush();

        $this->addFlash('success', 'Compte approuvé.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/validation/approve-all', name: 'admin_validation_approve_all', methods: ['POST'])]
    public function approveAllValidation(Request $request, UserRepository $userRepository, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('approve_all', (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $users = $userRepository->findPendingApprovals();
        foreach ($users as $user) {
            $user->setAdminApproved(true);
            $user->setUpdatedAt(new \DateTimeImmutable());
        }

        $em->flush();
        $this->addFlash('success', 'Tous les comptes en attente ont été approuvés.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/validation/{id}/resend', name: 'admin_validation_resend', methods: ['POST'])]
    public function resendValidationEmail(
        int $id,
        Request $request,
        UserRepository $userRepository,
        EntityManagerInterface $em,
        MailerInterface $mailer
    ): RedirectResponse {
        $user = $userRepository->find($id);
        if (!$user) {
            throw $this->createNotFoundException('Utilisateur introuvable.');
        }

        if (!$this->isCsrfTokenValid('resend_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $token = bin2hex(random_bytes(32));
        $user->setEmailVerificationToken($token);
        $user->setEmailVerificationExpiresAt((new \DateTimeImmutable())->modify('+2 days'));
        $user->setUpdatedAt(new \DateTimeImmutable());

        $verifyUrl = $this->generateUrl('app_verify_email', [
            'token' => $token,
        ], UrlGeneratorInterface::ABSOLUTE_URL);

        $email = (new TemplatedEmail())
            ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($user->getEmail())
            ->subject('Confirmation de votre email')
            ->htmlTemplate('emails/verify_email.html.twig')
            ->context([
                'user' => $user,
                'verifyUrl' => $verifyUrl,
                'expiresAt' => $user->getEmailVerificationExpiresAt(),
            ]);

        $mailer->send($email);
        $em->flush();

        $this->addFlash('success', 'Email de vérification renvoyé.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/validation/resend-all', name: 'admin_validation_resend_all', methods: ['POST'])]
    public function resendAllValidationEmail(
        Request $request,
        UserRepository $userRepository,
        EntityManagerInterface $em,
        MailerInterface $mailer
    ): RedirectResponse {
        if (!$this->isCsrfTokenValid('resend_all', (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $users = $userRepository->createQueryBuilder('u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.emailVerified = false')
            ->getQuery()
            ->getResult();

        foreach ($users as $user) {
            $token = bin2hex(random_bytes(32));
            $user->setEmailVerificationToken($token);
            $user->setEmailVerificationExpiresAt((new \DateTimeImmutable())->modify('+2 days'));
            $user->setUpdatedAt(new \DateTimeImmutable());

            $verifyUrl = $this->generateUrl('app_verify_email', [
                'token' => $token,
            ], UrlGeneratorInterface::ABSOLUTE_URL);

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Confirmation de votre email')
                ->htmlTemplate('emails/verify_email.html.twig')
                ->context([
                    'user' => $user,
                    'verifyUrl' => $verifyUrl,
                    'expiresAt' => $user->getEmailVerificationExpiresAt(),
                ]);

            $mailer->send($email);
        }

        $em->flush();
        $this->addFlash('success', 'Emails renvoyés à tous les comptes non vérifiés.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/roles', name: 'admin_roles')]
    public function roles(): Response
    {
        return $this->render('admin/roles/index.html.twig');
    }

    // ========== ABONNEMENTS ==========
    #[Route('/subscriptions', name: 'admin_subscriptions')]
    public function subscriptions(Request $request, AbonnementRepository $abonnementRepository): Response
    {
        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 12;

        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'status' => (string) $request->query->get('status', ''),
            'type' => (string) $request->query->get('type', ''),
            'from' => (string) $request->query->get('from', ''),
            'to' => (string) $request->query->get('to', ''),
        ];

        $qb = $abonnementRepository->createFilteredQueryBuilder($filters);
        $qb->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        $stats = $abonnementRepository->getSubscriptionStats();

        return $this->render('admin/subscriptions/index.html.twig', [
            'abonnements' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'filters' => $filters,
            'stats' => $stats,
        ]);
    }

    #[Route('/subscriptions/{id}/update', name: 'admin_subscriptions_update', methods: ['POST'])]
    public function updateSubscription(
        int $id,
        Request $request,
        AbonnementRepository $abonnementRepository,
        EntityManagerInterface $em
    ): RedirectResponse {
        $abonnement = $abonnementRepository->find($id);
        if (!$abonnement) {
            throw $this->createNotFoundException('Abonnement introuvable.');
        }

        if (!$this->isCsrfTokenValid('sub_update_' . $abonnement->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $type = (string) $request->request->get('type', $abonnement->getTypeAbonnement());
        $status = (string) $request->request->get('status', $abonnement->getStatut());
        $dateFinRaw = (string) $request->request->get('date_fin', '');

        if ($type !== '') {
            $abonnement->setTypeAbonnement($type);
            $abonnement->setNom($this->labelFromRole($type));
        }
        if ($status !== '') {
            $abonnement->setStatut($status);
        }
        if ($dateFinRaw !== '') {
            try {
                $abonnement->setDateFin(new \DateTime($dateFinRaw));
            } catch (\Throwable) {
            }
        }

        $user = $abonnement->getUser();
        if ($user) {
            $user->setSubscriptionType($abonnement->getTypeAbonnement());
            $user->setSubscriptionEndAt(\DateTimeImmutable::createFromMutable($abonnement->getDateFin()));
            $user->setSubscriptionStatus($abonnement->getStatut() === 'actif' ? 'ACTIVE' : 'EXPIRED');
            $user->setRole($abonnement->getTypeAbonnement());
            $user->setUpdatedAt(new \DateTimeImmutable());
            $this->ensureRoleEntity($user, $abonnement->getTypeAbonnement(), $em);
        }

        $em->flush();
        $this->addFlash('success', 'Abonnement mis à jour.');
        return $this->redirectToRoute('admin_subscriptions');
    }

    #[Route('/subscriptions/{id}/cancel', name: 'admin_subscriptions_cancel', methods: ['POST'])]
    public function cancelSubscription(
        int $id,
        Request $request,
        AbonnementRepository $abonnementRepository,
        EntityManagerInterface $em
    ): RedirectResponse {
        $abonnement = $abonnementRepository->find($id);
        if (!$abonnement) {
            throw $this->createNotFoundException('Abonnement introuvable.');
        }

        if (!$this->isCsrfTokenValid('sub_cancel_' . $abonnement->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $abonnement->setStatut('annule');
        $abonnement->setDateFin(new \DateTime());

        $user = $abonnement->getUser();
        if ($user) {
            $this->removeRoleEntity($user, $em);
            $user->setRole('ROLE_USER');
            $user->setSubscriptionStatus('EXPIRED');
            $user->setSubscriptionType(null);
            $user->setSubscriptionEndAt(null);
            $user->setUpdatedAt(new \DateTimeImmutable());
        }

        $em->flush();
        $this->addFlash('success', 'Abonnement annulé.');
        return $this->redirectToRoute('admin_subscriptions');
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
        $this->removeRoleEntity($user, $em);

        switch ($role) {
            case 'ROLE_PATIENT':
                if (!$user->getPatient()) {
                    $patient = new \App\Entity\Patient();
                    $patient->setUser($user);
                    $em->persist($patient);
                }
                break;
            case 'ROLE_MEDECIN':
                if (!$user->getMedecin()) {
                    $medecin = new \App\Entity\Medecin();
                    $medecin->setUser($user);
                    $medecin->setSpecialite('Non défini');
                    $em->persist($medecin);
                }
                break;
            case 'ROLE_PHARMACIEN':
                if (!$user->getPharmacien()) {
                    $pharmacien = new \App\Entity\Pharmacien();
                    $pharmacien->setUser($user);
                    $em->persist($pharmacien);
                }
                break;
            case 'ROLE_COACH':
                if (!$user->getCoachSportif()) {
                    $coach = new \App\Entity\CoachSportif();
                    $coach->setUser($user);
                    $coach->setSpecialite('Non défini');
                    $em->persist($coach);
                }
                break;
            case 'ROLE_NUTRITIONNISTE':
                if (!$user->getNutritionniste()) {
                    $nutritionniste = new \App\Entity\Nutritionniste();
                    $nutritionniste->setUser($user);
                    $nutritionniste->setSpecialite('Non défini');
                    $em->persist($nutritionniste);
                }
                break;
        }
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

    #[Route('/payments', name: 'admin_payments')]
    public function payments(Request $request, \Doctrine\ORM\EntityManagerInterface $em): Response
    {
        $repo = $em->getRepository(\App\Entity\Facture::class);

        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 12;
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'from' => (string) $request->query->get('from', ''),
            'to' => (string) $request->query->get('to', ''),
            'min' => (string) $request->query->get('min', ''),
            'max' => (string) $request->query->get('max', ''),
        ];

        $qb = $repo->createQueryBuilder('f')
            ->leftJoin('f.user', 'u')
            ->leftJoin('f.abonnement', 'a')
            ->addSelect('u', 'a');

        if ($filters['q'] !== '') {
            $qb->andWhere('u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q OR f.numero LIKE :q')
                ->setParameter('q', '%' . $filters['q'] . '%');
        }
        if ($filters['from'] !== '') {
            try {
                $qb->andWhere('f.createdAt >= :from')->setParameter('from', new \DateTime($filters['from'] . ' 00:00:00'));
            } catch (\Throwable) {}
        }
        if ($filters['to'] !== '') {
            try {
                $qb->andWhere('f.createdAt <= :to')->setParameter('to', new \DateTime($filters['to'] . ' 23:59:59'));
            } catch (\Throwable) {}
        }
        if ($filters['min'] !== '') {
            $qb->andWhere('f.montantTtc >= :min')->setParameter('min', $filters['min']);
        }
        if ($filters['max'] !== '') {
            $qb->andWhere('f.montantTtc <= :max')->setParameter('max', $filters['max']);
        }

        $qb->orderBy('f.createdAt', 'DESC')
            ->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new \Doctrine\ORM\Tools\Pagination\Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        $stats = [
            'total' => (int) $repo->createQueryBuilder('f')->select('COUNT(f.id)')->getQuery()->getSingleScalarResult(),
            'revenue' => (string) $repo->createQueryBuilder('f')->select('COALESCE(SUM(f.montantTtc),0)')->getQuery()->getSingleScalarResult(),
            'month' => (string) $repo->createQueryBuilder('f')->select('COALESCE(SUM(f.montantTtc),0)')->andWhere('f.createdAt >= :m')->setParameter('m', new \DateTime('first day of this month 00:00:00'))->getQuery()->getSingleScalarResult(),
        ];

        $chartRows = $repo->createQueryBuilder('f')
            ->select('f.createdAt')
            ->orderBy('f.createdAt', 'ASC')
            ->getQuery()
            ->getResult();

        $labels = [];
        $data = [];
        $start = new \DateTimeImmutable('-30 days');
        $cursor = $start;
        for ($i = 0; $i <= 30; $i++) {
            $labels[] = $cursor->format('Y-m-d');
            $data[$cursor->format('Y-m-d')] = 0.0;
            $cursor = $cursor->modify('+1 day');
        }
        foreach ($chartRows as $row) {
            $key = $row['createdAt']->format('Y-m-d');
            if (isset($data[$key])) {
                $data[$key] += 1;
            }
        }

        $chart = [
            'labels' => $labels,
            'data' => array_values($data),
        ];

        return $this->render('admin/payments/index.html.twig', [
            'factures' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'filters' => $filters,
            'stats' => $stats,
            'chart' => $chart,
        ]);
    }

    #[Route('/payments/{id}/download', name: 'admin_payments_download')]
    public function downloadInvoice(int $id, \Doctrine\ORM\EntityManagerInterface $em): Response
    {
        $facture = $em->getRepository(\App\Entity\Facture::class)->find($id);
        if (!$facture || !$facture->getPdfPath()) {
            throw $this->createNotFoundException('Facture introuvable.');
        }

        return $this->file($facture->getPdfPath(), 'facture-' . $facture->getNumero() . '.pdf');
    }

    #[Route('/revenues', name: 'admin_revenues')]
    public function revenues(): Response
    {
        return $this->render('admin/revenues/index.html.twig');
    }

    // ========== SERVICES MÉDICAUX ==========
    #[Route('/accompaniments', name: 'admin_accompaniments')]
    public function accompaniments(): Response
    {
        return $this->render('admin/accompaniments/index.html.twig');
    }

    #[Route('/admin/appointments', name: 'admin_appointments')]
    public function appointments(
        \Symfony\Component\HttpFoundation\Request $request,
        \Doctrine\ORM\EntityManagerInterface $em
    ): Response
    {
        $status = (string) $request->query->get('status', '');
        $medecinId = (int) $request->query->get('medecin', 0);
        $patientId = (int) $request->query->get('patient', 0);
        $date = (string) $request->query->get('date', '');
        $search = trim((string) $request->query->get('q', ''));

        $repo = $em->getRepository(\App\Entity\RendezVous::class);
        $qb = $repo->createQueryBuilder('r')
            ->leftJoin('r.patient', 'p')->addSelect('p')
            ->leftJoin('p.user', 'pu')->addSelect('pu')
            ->leftJoin('r.medecin', 'm')->addSelect('m')
            ->leftJoin('m.user', 'mu')->addSelect('mu')
            ->orderBy('r.dateRdv', 'DESC');

        if ($status) {
            $qb->andWhere('r.statut = :statut')->setParameter('statut', $status);
        }
        if ($medecinId) {
            $qb->andWhere('m.id = :mid')->setParameter('mid', $medecinId);
        }
        if ($patientId) {
            $qb->andWhere('p.id = :pid')->setParameter('pid', $patientId);
        }
        if ($date) {
            $qb->andWhere('r.dateRdv = :d')->setParameter('d', new \DateTime($date));
        }
        if ($search) {
            $qb->andWhere('pu.email LIKE :q OR pu.nom LIKE :q OR pu.prenom LIKE :q OR mu.email LIKE :q OR mu.nom LIKE :q OR mu.prenom LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }

        $rdvs = $qb->setMaxResults(200)->getQuery()->getResult();

        $stats = [
            'total' => count($rdvs),
            'confirmed' => 0,
            'cancelled' => 0,
        ];
        foreach ($rdvs as $rdv) {
            if ($rdv->getStatut() === 'confirme') {
                $stats['confirmed']++;
            }
            if ($rdv->getStatut() === 'annule') {
                $stats['cancelled']++;
            }
        }

        $medecins = $em->getRepository(\App\Entity\Medecin::class)->findAll();
        $patients = $em->getRepository(\App\Entity\Patient::class)->findAll();

        return $this->render('admin/appointments/index.html.twig', [
            'rdvs' => $rdvs,
            'stats' => $stats,
            'filters' => [
                'status' => $status,
                'medecin' => $medecinId,
                'patient' => $patientId,
                'date' => $date,
                'q' => $search,
            ],
            'medecins' => $medecins,
            'patients' => $patients,
        ]);
    }

    #[Route('/admin/appointments/{id}/cancel', name: 'admin_appointments_cancel', methods: ['POST'])]
    public function cancelAppointment(
        int $id,
        \Doctrine\ORM\EntityManagerInterface $em,
        \App\Service\AppointmentService $appointmentService
    ): \Symfony\Component\HttpFoundation\Response {
        $rdv = $em->getRepository(\App\Entity\RendezVous::class)->find($id);
        if (!$rdv) {
            throw $this->createNotFoundException('Rendez-vous introuvable.');
        }
        $appointmentService->updateStatusByDoctor($rdv, 'annule');
        $this->addFlash('success', 'Rendez-vous annulé.');
        return $this->redirectToRoute('admin_appointments');
    }

    #[Route('/admin/appointments/{id}/reschedule', name: 'admin_appointments_reschedule', methods: ['POST'])]
    public function rescheduleAppointment(
        int $id,
        \Symfony\Component\HttpFoundation\Request $request,
        \Doctrine\ORM\EntityManagerInterface $em,
        \App\Service\AppointmentService $appointmentService
    ): \Symfony\Component\HttpFoundation\Response {
        $rdv = $em->getRepository(\App\Entity\RendezVous::class)->find($id);
        if (!$rdv) {
            throw $this->createNotFoundException('Rendez-vous introuvable.');
        }

        $date = (string) $request->request->get('date', '');
        $time = (string) $request->request->get('time', '');
        if (!$date || !$time) {
            $this->addFlash('error', 'Date et heure obligatoires.');
            return $this->redirectToRoute('admin_appointments');
        }

        $medecin = $rdv->getMedecin();
        $dispoRepo = $em->getRepository(\App\Entity\Disponibilite::class);
        $dispo = $dispoRepo->createQueryBuilder('d')
            ->andWhere('d.medecin = :m')->setParameter('m', $medecin)
            ->andWhere('d.date = :d')->setParameter('d', new \DateTime($date))
            ->andWhere('d.heureDebut = :h')->setParameter('h', new \DateTime($date . ' ' . $time))
            ->getQuery()->getOneOrNullResult();

        if (!$dispo) {
            $dispo = new \App\Entity\Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date));
            $dispo->setHeureDebut(new \DateTime($date . ' ' . $time));
            $dispo->setHeureFin((new \DateTime($date . ' ' . $time))->modify('+30 minutes'));
            $dispo->setStatut('disponible');
            $em->persist($dispo);
            $em->flush();
        }

        if ($dispo->getRendezvous()) {
            $this->addFlash('error', 'Créneau déjà réservé.');
            return $this->redirectToRoute('admin_appointments');
        }

        $appointmentService->reschedule($rdv, $dispo);
        $this->addFlash('success', 'Rendez-vous replanifié.');
        return $this->redirectToRoute('admin_appointments');
    }

    #[Route('/admin/appointments/create', name: 'admin_appointments_create', methods: ['POST'])]
    public function createAppointment(
        \Symfony\Component\HttpFoundation\Request $request,
        \Doctrine\ORM\EntityManagerInterface $em,
        \App\Service\AppointmentService $appointmentService,
        \Symfony\Component\Mailer\MailerInterface $mailer
    ): \Symfony\Component\HttpFoundation\Response {
        $patientId = (int) $request->request->get('patient_id', 0);
        $medecinId = (int) $request->request->get('medecin_id', 0);
        $date = (string) $request->request->get('date', '');
        $time = (string) $request->request->get('time', '');
        $motif = (string) $request->request->get('motif', '');

        if (!$patientId || !$medecinId || !$date || !$time) {
            $this->addFlash('error', 'Patient, médecin, date et heure sont obligatoires.');
            return $this->redirectToRoute('admin_appointments');
        }

        $patient = $em->getRepository(\App\Entity\Patient::class)->find($patientId);
        $medecin = $em->getRepository(\App\Entity\Medecin::class)->find($medecinId);
        if (!$patient || !$medecin) {
            $this->addFlash('error', 'Patient ou médecin introuvable.');
            return $this->redirectToRoute('admin_appointments');
        }

        $dispoRepo = $em->getRepository(\App\Entity\Disponibilite::class);
        $dispo = $dispoRepo->createQueryBuilder('d')
            ->andWhere('d.medecin = :m')->setParameter('m', $medecin)
            ->andWhere('d.date = :d')->setParameter('d', new \DateTime($date))
            ->andWhere('d.heureDebut = :h')->setParameter('h', new \DateTime($date . ' ' . $time))
            ->getQuery()->getOneOrNullResult();

        if (!$dispo) {
            $dispo = new \App\Entity\Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date));
            $dispo->setHeureDebut(new \DateTime($date . ' ' . $time));
            $dispo->setHeureFin((new \DateTime($date . ' ' . $time))->modify('+30 minutes'));
            $dispo->setStatut('disponible');
            $em->persist($dispo);
            $em->flush();
        }

        if ($dispo->getRendezvous()) {
            $this->addFlash('error', 'Créneau déjà réservé.');
            return $this->redirectToRoute('admin_appointments');
        }

        $rdv = $appointmentService->book($patient, $medecin, $dispo, $motif ?: null);
        $rdv->setStatut('confirme');
        $em->flush();

        $email = (new \Symfony\Bridge\Twig\Mime\TemplatedEmail())
            ->from(new \Symfony\Component\Mime\Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($patient->getUser()->getEmail())
            ->subject('Rendez-vous confirmé')
            ->htmlTemplate('emails/appointment_accepted.html.twig')
            ->context(['rdv' => $rdv, 'user' => $patient->getUser(), 'medecin' => $medecin]);
        $mailer->send($email);

        $this->addFlash('success', 'Rendez-vous créé.');
        return $this->redirectToRoute('admin_appointments');
    }

    #[Route('/medical', name: 'admin_medical')]
    public function medical(): Response
    {
        return $this->render('admin/medical/index.html.twig');
    }

    // ========== PHARMACIE ==========
    #[Route('/admin/pharmacies', name: 'admin_pharmacies')]
    public function pharmacies(
        \App\Repository\PharmacyRepository $pharmacyRepository,
        \App\Repository\ReservationMedicamentRepository $reservationRepository
    ): Response {
        $pharmacies = $pharmacyRepository->findBy([], ['createdAt' => 'DESC']);
        $stats = [
            'pharmacies_total' => count($pharmacies),
            'pharmacies_active' => (int) $pharmacyRepository->createQueryBuilder('p')
                ->select('COUNT(p.id)')
                ->andWhere('p.isActive = 1')
                ->getQuery()
                ->getSingleScalarResult(),
            'reservations_pending' => (int) $reservationRepository->createQueryBuilder('r')
                ->select('COUNT(r.id)')
                ->andWhere('r.statut = :s')
                ->setParameter('s', 'en_attente')
                ->getQuery()
                ->getSingleScalarResult(),
        ];

        return $this->render('admin/pharmacies/index.html.twig', [
            'pharmacies' => $pharmacies,
            'stats' => $stats,
        ]);
    }

    #[Route('/admin/medications', name: 'admin_medications')]
    public function medications(\App\Repository\MedicamentRepository $medicamentRepository): Response
    {
        $medicaments = $medicamentRepository->findBy([], ['nom' => 'ASC']);
        return $this->render('admin/medications/index.html.twig', [
            'medicaments' => $medicaments,
        ]);
    }

    #[Route('/admin/stocks', name: 'admin_stocks')]
    public function stocks(
        \App\Repository\StockPharmacyRepository $stockRepository,
        \App\Repository\PharmacyRepository $pharmacyRepository,
        \App\Repository\MedicamentRepository $medicamentRepository
    ): Response {
        $stocks = $stockRepository->createQueryBuilder('s')
            ->leftJoin('s.pharmacie', 'p')
            ->addSelect('p')
            ->leftJoin('s.medicament', 'm')
            ->addSelect('m')
            ->orderBy('s.updatedAt', 'DESC')
            ->getQuery()
            ->getResult();
        $pharmacies = $pharmacyRepository->findBy([], ['nom' => 'ASC']);
        $medicaments = $medicamentRepository->findBy([], ['nom' => 'ASC']);
        return $this->render('admin/stocks/index.html.twig', [
            'stocks' => $stocks,
            'pharmacies' => $pharmacies,
            'medicaments' => $medicaments,
        ]);
    }

    // ========== TRACKER SANTÉ ==========
    #[Route('/tracker', name: 'admin_tracker')]
    public function tracker(
        \App\Repository\SymptomeListeRepository $symptomeListeRepository,
        \App\Repository\SymptomeQuotidienRepository $symptomeQuotidienRepository
    ): Response
    {
        $userStats = $symptomeQuotidienRepository->getUserSymptomStatistics();

        $stats = [
            'list_total' => $symptomeListeRepository->getTotalCount(),
            'tracked_total' => $symptomeQuotidienRepository->getTotalTrackedCount(),
            'tracked_weekly' => $symptomeQuotidienRepository->getWeeklyTrackedCount(),
            'avg_intensity' => $symptomeQuotidienRepository->getAverageIntensityAll(),
            'active_patients' => count($userStats),
        ];

        $mostCommonSymptoms = $symptomeQuotidienRepository->getMostCommonSymptoms(10);

        return $this->render('admin/tracker/index.html.twig', [
            'stats' => $stats,
            'user_stats' => $userStats,
            'most_common_symptoms' => $mostCommonSymptoms,
        ]);
    }

    // ========== SYSTÈME ==========
    #[Route('/reports', name: 'admin_reports')]
    public function reports(): Response
    {
        return $this->render('admin/reports/index.html.twig');
    }

    #[Route('/settings', name: 'admin_settings')]
    public function settings(): Response
    {
        return $this->render('admin/settings/index.html.twig');
    }
}
