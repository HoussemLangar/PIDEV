<?php
namespace App\Controller;

use App\Entity\User;
use App\Form\AdminUserType;
use App\Form\AdminUserResetPasswordType;
use App\Repository\AbonnementRepository;
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

#[IsGranted('ROLE_ADMIN')]
class AdminController extends AbstractController
{
    // ========== DASHBOARD ==========
    #[Route('/admin_dashboard', name: 'admin_dashboard')]
    public function dashboard(): Response
    {
        // Statistiques principales
        $stats = [
            'totalUsers' => 1234,
            'newUsers' => 45,
            'usersGrowth' => 12.5,
            'activeSubscriptions' => 567,
            'pendingSubscriptions' => 12,
            'subscriptionsGrowth' => 8.3,
            'monthlyAppointments' => 234,
            'todayAppointments' => 23,
            'weekAppointments' => 156,
            'appointmentsGrowth' => 15.7,
            'monthlyRevenue' => 12450,
            'monthRevenue' => 12450,
            'totalRevenue' => 145670,
            'revenueGrowth' => 22.4,
            'totalPharmacies' => 89,
            'totalMedications' => 3456,
        ];

        // Utilisateurs par rôle
        $usersByRole = [
            ['name' => 'Patients', 'count' => 892, 'icon' => 'fas fa-user'],
            ['name' => 'Médecins', 'count' => 156, 'icon' => 'fas fa-user-md'],
            ['name' => 'Pharmaciens', 'count' => 89, 'icon' => 'fas fa-prescription-bottle'],
            ['name' => 'Accompagnants', 'count' => 97, 'icon' => 'fas fa-hands-helping'],
        ];

        // Validations en attente
        $pendingValidations = [
            'professionals' => 8,
            'pharmacies' => 3,
            'articles' => 5,
            'reports' => 12,
        ];

        // Activité récente (24h)
        $recentActivity = [
            'newUsers' => 15,
            'newAppointments' => 34,
            'medicationSearches' => 567,
            'trackerEntries' => 892,
        ];

        // Données pour les graphiques
        $chartData = [
            'registrations' => [
                'labels' => ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'],
                'data' => [12, 19, 15, 25, 22, 30, 28],
            ],
            'revenue' => [
                'labels' => ['Abonnements', 'Rendez-vous', 'Accompagnements', 'Autres'],
                'data' => [6500, 3200, 2100, 650],
            ],
        ];

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
    public function validation(): Response
    {
        return $this->render('admin/validation/index.html.twig');
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
    public function payments(): Response
    {
        return $this->render('admin/payments/index.html.twig');
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

    #[Route('/appointments', name: 'admin_appointments')]
    public function appointments(): Response
    {
        return $this->render('admin/appointments/index.html.twig');
    }

    #[Route('/medical', name: 'admin_medical')]
    public function medical(): Response
    {
        return $this->render('admin/medical/index.html.twig');
    }

    // ========== PHARMACIE ==========
    #[Route('/pharmacies', name: 'admin_pharmacies')]
    public function pharmacies(): Response
    {
        return $this->render('admin/pharmacies/index.html.twig');
    }

    #[Route('/medications', name: 'admin_medications')]
    public function medications(): Response
    {
        return $this->render('admin/medications/index.html.twig');
    }

    // ========== TRACKER SANTÉ ==========
    #[Route('/tracker', name: 'admin_tracker')]
    public function tracker(): Response
    {
        return $this->render('admin/tracker/index.html.twig');
    }

    // ========== CONTENU ==========
    #[Route('/content', name: 'admin_content')]
    public function content(): Response
    {
        return $this->render('admin/content/index.html.twig');
    }

    #[Route('/moderation', name: 'admin_moderation')]
    public function moderation(): Response
    {
        return $this->render('admin/moderation/index.html.twig');
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
