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
    public function analytics(
        \App\Repository\SymptomeListeRepository $symptomeListeRepository,
        \App\Repository\SymptomeQuotidienRepository $symptomeQuotidienRepository
    ): Response
    {
        // Créer un formulaire vide pour l'ajout de symptômes
        $symptome = new \App\Entity\SymptomeListe();
        
        // Récupérer les catégories pour le formulaire
        $categories = $symptomeListeRepository->findDistinctCategories();
        $categoryChoices = [];
        foreach ($categories as $category) {
            $categoryChoices[$category] = $category;
        }
        
        $form = $this->createForm(\App\Form\SymptomeListeType::class, $symptome, [
            'categories' => $categoryChoices
        ]);
        
        // Récupérer les statistiques pour la page
        $stats = [
            'total' => $symptomeListeRepository->getTotalCount(),
            'categories' => $symptomeListeRepository->getCategoriesCount(),
            'weekly' => $symptomeListeRepository->getWeeklyCount(),
            'recent' => $symptomeListeRepository->getRecentCount(),
        ];
        
        // Formater les catégories pour le template
        $categoriesFormatted = [];
        foreach ($categories as $category) {
            $categoriesFormatted[] = ['categorie' => $category];
        }
        
        // Récupérer tous les symptômes
        $symptomes = $symptomeListeRepository->findAll();
        
        // Récupérer les statistiques des utilisateurs
        $userStats = $symptomeQuotidienRepository->getUserSymptomStatistics();
        
        return $this->render('back/symptomes.html.twig', [
            'stats' => $stats,
            'categories' => $categoriesFormatted,
            'symptomes' => $symptomes,
            'form' => $form->createView(),
            'user_stats' => $userStats,
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
