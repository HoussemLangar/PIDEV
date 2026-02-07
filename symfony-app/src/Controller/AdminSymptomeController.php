<?php

namespace App\Controller;

use App\Entity\SymptomeListe;
use App\Form\SymptomeListeType;
use App\Repository\SymptomeListeRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Csrf\CsrfTokenManagerInterface;

#[Route('/admin/symptomes')]
final class AdminSymptomeController extends AbstractController
{
    public function __construct(
        private readonly SymptomeListeRepository $symptomeRepository,
        private readonly EntityManagerInterface $entityManager,
        private readonly CsrfTokenManagerInterface $csrfTokenManager,
    ) {}

    #[Route('', name: 'app_symptome_liste_index', methods: ['GET', 'POST'])]
    public function index(Request $request): Response
    {
        $symptome = new SymptomeListe();
        $categories = $this->symptomeRepository->findDistinctCategories();
        
        // Prepare categories for form choices (categories is already an array of strings)
        $categoryChoices = [];
        foreach ($categories as $category) {
            $categoryChoices[$category] = $category;
        }
        
        $form = $this->createForm(SymptomeListeType::class, $symptome, [
            'categories' => $categoryChoices
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $this->entityManager->persist($symptome);
            $this->entityManager->flush();

            $this->addFlash('success', 'Le symptôme a été ajouté avec succès.');
            return $this->redirectToRoute('app_symptome_liste_index');
        }

        // Handle search
        $search = $request->query->get('search', '');
        if ($search) {
            $symptomes = $this->symptomeRepository->searchSymptoms($search);
        } else {
            $symptomes = $this->symptomeRepository->findAll();
        }
        
        // Get statistics from repository
        $stats = [
            'total' => $this->symptomeRepository->getTotalCount(),
            'categories' => $this->symptomeRepository->getCategoriesCount(),
            'weekly' => $this->symptomeRepository->getWeeklyCount(),
            'recent' => $this->symptomeRepository->getRecentCount(),
        ];
        
        // Convert categories array to format expected by template
        $categoriesForTemplate = [];
        foreach ($categories as $category) {
            $categoriesForTemplate[] = ['categorie' => $category];
        }

        return $this->render('back/symptomes.html.twig', [
            'symptomes' => $symptomes,
            'categories' => $categoriesForTemplate,
            'stats' => $stats,
            'form' => $form->createView(),
        ]);
    }

    #[Route('/{id}/edit', name: 'app_symptome_liste_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, SymptomeListe $symptome): Response
    {
        $categories = $this->symptomeRepository->findDistinctCategories();
        
        // Prepare categories for form choices (categories is already an array of strings)
        $categoryChoices = [];
        foreach ($categories as $category) {
            $categoryChoices[$category] = $category;
        }
        
        $form = $this->createForm(SymptomeListeType::class, $symptome, [
            'categories' => $categoryChoices
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $this->entityManager->flush();

            $this->addFlash('success', 'Le symptôme a été modifié avec succès.');
            return $this->redirectToRoute('app_symptome_liste_index');
        }

        // Handle search
        $search = $request->query->get('search', '');
        if ($search) {
            $symptomes = $this->symptomeRepository->searchSymptoms($search);
        } else {
            $symptomes = $this->symptomeRepository->findAll();
        }
        
        // Get statistics from repository
        $stats = [
            'total' => $this->symptomeRepository->getTotalCount(),
            'categories' => $this->symptomeRepository->getCategoriesCount(),
            'weekly' => $this->symptomeRepository->getWeeklyCount(),
            'recent' => $this->symptomeRepository->getRecentCount(),
        ];
        
        // Convert categories array to format expected by template
        $categoriesForTemplate = [];
        foreach ($categories as $category) {
            $categoriesForTemplate[] = ['categorie' => $category];
        }

        return $this->render('back/symptomes.html.twig', [
            'symptomes' => $symptomes,
            'categories' => $categoriesForTemplate,
            'stats' => $stats,
            'form' => $form->createView(),
            'edit_mode' => true,
            'current_symptome' => $symptome,
        ]);
    }

    #[Route('/{id}', name: 'app_symptome_liste_delete', methods: ['POST'])]
    public function delete(Request $request, SymptomeListe $symptome): Response
    {
        if ($this->isCsrfTokenValid('delete'.$symptome->getId(), $request->request->get('_token'))) {
            $this->entityManager->remove($symptome);
            $this->entityManager->flush();

            $this->addFlash('success', 'Le symptôme a été supprimé avec succès.');
        }

        return $this->redirectToRoute('app_symptome_liste_index');
    }

    #[Route('/search', name: 'app_symptome_liste_search', methods: ['GET'])]
    public function search(Request $request): Response
    {
        $search = $request->query->get('search', '');
        $category = $request->query->get('category', '');
        
        if ($search || $category) {
            $symptomes = $this->symptomeRepository->searchSymptoms($search, $category);
        } else {
            $symptomes = $this->symptomeRepository->findAll();
        }

        // Return JSON response for AJAX
        $symptomesData = [];
        foreach ($symptomes as $symptome) {
            $symptomesData[] = [
                'id' => $symptome->getId(),
                'nom' => $symptome->getNom(),
                'categorie' => $symptome->getCategorie(),
                'createdAt' => $symptome->getCreatedAt()->format('d/m/Y'),
                'editUrl' => $this->generateUrl('app_symptome_liste_edit', ['id' => $symptome->getId()]),
                'deleteUrl' => $this->generateUrl('app_symptome_liste_delete', ['id' => $symptome->getId()]),
                'deleteToken' => $this->csrfTokenManager->getToken('delete'.$symptome->getId())->getValue(),
            ];
        }

        return $this->json([
            'symptomes' => $symptomesData,
            'count' => count($symptomesData)
        ]);
    }

    #[Route('/export', name: 'app_symptome_liste_export', methods: ['GET'])]
    public function export(): Response
    {
        $symptomes = $this->symptomeRepository->findAll();

        $csvContent = "Nom,Catégorie,Créé le\n";
        foreach ($symptomes as $symptome) {
            $csvContent .= sprintf(
                '"%s","%s","%s"' . "\n",
                str_replace('"', '""', $symptome->getNom()),
                str_replace('"', '""', $symptome->getCategorie() ?? ''),
                $symptome->getCreatedAt()->format('d/m/Y')
            );
        }

        $response = new Response($csvContent);
        $response->headers->set('Content-Type', 'text/csv');
        $response->headers->set('Content-Disposition', 'attachment; filename="symptomes-' . date('Y-m-d') . '.csv"');

        return $response;
    }
}
