<?php

namespace App\Controller;

use App\Repository\PharmacyRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/admin', name: 'admin_')]
class AdminController extends AbstractController
{
    /**
     * Admin Dashboard
     */
    #[Route('', name: 'dashboard', methods: ['GET'])]
    public function dashboard(PharmacyRepository $pharmacyRepository): Response
    {
        $pharmacies = $pharmacyRepository->findAll();
        $totalPharmacies = count($pharmacies);

        // Provide safe defaults for dashboard widgets to avoid missing variable errors
        $stats = [
            'activeSubscriptions' => 0,
            'subscriptionsGrowth' => 0,
            'monthlyAppointments' => 0,
            'appointmentsGrowth' => 0,
            'monthlyRevenue' => 0,
            'revenueGrowth' => 0,
        ];

        $usersByRole = [
            ['icon' => 'fas fa-user', 'name' => 'Patients', 'count' => 0],
            ['icon' => 'fas fa-user-md', 'name' => 'Professionnels', 'count' => 0],
            ['icon' => 'fas fa-user-shield', 'name' => 'Administrateurs', 'count' => 0],
        ];

        $pendingValidations = [
            'professionals' => 0,
            'pharmacies' => 0,
            'articles' => 0,
            'reports' => 0,
        ];

        $recentActivity = [
            'newUsers' => 0,
            'newAppointments' => 0,
            'medicationSearches' => 0,
            'trackerEntries' => 0,
        ];

        $recentActivities = [];

        $chartData = [
            'registrations' => ['labels' => [], 'data' => []],
            'revenue' => ['labels' => [], 'data' => []],
        ];

        return $this->render('back/dashboard.html.twig', [
            'totalPharmacies' => $totalPharmacies,
            'pharmacies' => $pharmacies,
            'stats' => $stats,
            'usersByRole' => $usersByRole,
            'pendingValidations' => $pendingValidations,
            'recentActivity' => $recentActivity,
            'recentActivities' => $recentActivities,
            'chartData' => $chartData,
        ]);
    }

    /**
     * Pending validations overview (placeholder)
     */
    #[Route('/validation', name: 'validation', methods: ['GET'])]
    public function validation(): Response
    {
        $pendingValidations = [
            'professionals' => 0,
            'pharmacies' => 0,
            'articles' => 0,
            'reports' => 0,
        ];

        return $this->render('back/validation.html.twig', [
            'pendingValidations' => $pendingValidations,
        ]);
    }
}
