<?php
namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

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
    public function users(): Response
    {
        return $this->render('admin/users/index.html.twig');
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
    public function subscriptions(): Response
    {
        return $this->render('admin/subscriptions/index.html.twig');
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