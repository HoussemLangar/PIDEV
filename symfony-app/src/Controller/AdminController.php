<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/admin')]
final class AdminController extends AbstractController
{
    #[Route('', name: 'admin_dashboard')]
    public function dashboard(): Response
    {
        return $this->render('back/dashboard.html.twig', [
            'controller_name' => 'AdminController',
        ]);
    }

    #[Route('/users', name: 'admin_users')]
    public function users(): Response
    {
        return $this->render('admin/users.html.twig', [
            'title' => 'Gestion des Utilisateurs'
        ]);
    }

    #[Route('/validation', name: 'admin_validation')]
    public function validation(): Response
    {
        return $this->render('admin/validation.html.twig', [
            'title' => 'Validation des Comptes'
        ]);
    }

    #[Route('/roles', name: 'admin_roles')]
    public function roles(): Response
    {
        return $this->render('admin/roles.html.twig', [
            'title' => 'Rôles & Permissions'
        ]);
    }

    #[Route('/subscriptions', name: 'admin_subscriptions')]
    public function subscriptions(): Response
    {
        return $this->render('admin/subscriptions.html.twig', [
            'title' => 'Gestion des Abonnements'
        ]);
    }

    #[Route('/payments', name: 'admin_payments')]
    public function payments(): Response
    {
        return $this->render('admin/payments.html.twig', [
            'title' => 'Gestion des Paiements'
        ]);
    }

    #[Route('/analytics', name: 'admin_analytics')]
    public function analytics(): Response
    {
        return $this->render('admin/analytics.html.twig', [
            'title' => 'Analyses & Statistiques'
        ]);
    }

    #[Route('/settings', name: 'admin_settings')]
    public function settings(): Response
    {
        return $this->render('admin/settings.html.twig', [
            'title' => 'Paramètres Système'
        ]);
    }
}
