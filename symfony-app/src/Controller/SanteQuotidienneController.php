<?php

namespace App\Controller;

use App\Entity\SanteQuotidienne;
use App\Form\SanteQuotidienneType;
use App\Repository\SanteQuotidienneRepository;
use App\Enum\NiveauActivite;
use App\Enum\Humeur;
use App\Enum\Alimentation;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Security\Csrf\CsrfTokenManagerInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/sante-quotidienne')]
#[IsGranted('IS_AUTHENTICATED_FULLY')] // obligatoire : seul un utilisateur connecté peut accéder
class SanteQuotidienneController extends AbstractController
{
    #[Route('', name: 'app_sante_quotidienne_index', methods: ['GET'])]
    public function index(SanteQuotidienneRepository $repository): Response
    {
        // On récupère uniquement les entrées de l'utilisateur connecté
        $santes = $repository->findBy(
            ['user' => $this->getUser()],
            ['date' => 'DESC']
        );

        return $this->render('sante_quotidienne/index.html.twig', [
            'santes' => $santes,
            'controller_name' => 'SanteQuotidienneController',
        ]);
    }

    #[Route('/front', name: 'app_sante_quotidienne_front', methods: ['GET'])]
    public function front(): Response
    {
        return $this->render('front/santequotidienne/base.html.twig');
    }

    #[Route('/front/new-form', name: 'app_sante_quotidienne_front_new', methods: ['GET'])]
    public function frontNewForm(SanteQuotidienneRepository $repository): Response
    {
        $sante = new SanteQuotidienne();

        $form = $this->createForm(SanteQuotidienneType::class, $sante);

        // Récupère les entrées de l'utilisateur connecté pour affichage et actions
        $santes = $repository->findBy([
            'user' => $this->getUser(),
        ], ['date' => 'DESC']);

        // Moyenne de sommeil pour l'utilisateur connecté
        $averageSommeil = $repository->getAverageSommeilForUser($this->getUser());

        // Statistiques pour les boîtes d'action
        $moodStats = $repository->getMoodStatistics($this->getUser());
        $activityStats = $repository->getActivityStatistics($this->getUser());
        $nutritionStats = $repository->getNutritionStatistics($this->getUser());

        return $this->render('front/santequotidienne/form.html.twig', [
            'form' => $form->createView(),
            'santes' => $santes,
            'averageSommeil' => $averageSommeil,
            'moodStats' => $moodStats,
            'activityStats' => $activityStats,
            'nutritionStats' => $nutritionStats,
        ]);
    }

    #[Route('/sante-quotidienne/calc-imc', name: 'app_sante_quotidienne_calc_imc', methods: ['GET'])]
    public function calcImc(Request $request): JsonResponse
    {
        $poids = $request->query->get('poids');
        $taille = $request->query->get('taille');

        if (!is_numeric($poids) || !is_numeric($taille)) {
            return new JsonResponse(['ok' => false, 'message' => 'invalid_parameters'], 400);
        }

        $poids = (float) $poids;
        $taille = (float) $taille;

        if ($poids <= 0 || $taille <= 0) {
            return new JsonResponse(['ok' => false, 'message' => 'invalid_values'], 400);
        }

        $tm = $taille / 100.0;
        $imc = round($poids / ($tm * $tm), 2);

        // Determine category
        if ($imc < 18.5) {
            $category = 'insuffisance_pondrale';
            $label = "Insuffisance pondérale";
            $emoji = '🟦';
            $color = '#60a5fa';
        } elseif ($imc < 25) {
            $category = 'normal';
            $label = 'Poids normal';
            $emoji = '🟩';
            $color = '#22c55e';
        } elseif ($imc < 30) {
            $category = 'surpoids';
            $label = 'Surpoids';
            $emoji = '🟨';
            $color = '#f59e0b';
        } else {
            $category = 'obesite';
            $label = 'Obésité';
            $emoji = '🟥';
            $color = '#ef4444';
        }

        return new JsonResponse([
            'ok' => true,
            'imc' => $imc,
            'category' => $category,
            'label' => $label,
            'emoji' => $emoji,
            'color' => $color,
        ]);
    }

    #[Route('/api/by-date', name: 'app_sante_quotidienne_by_date', methods: ['GET'])]
    public function byDate(Request $request, SanteQuotidienneRepository $repository, CsrfTokenManagerInterface $csrfTokenManager): JsonResponse
    {
        $dateStr = $request->query->get('date');
        if (!$dateStr) {
            return new JsonResponse(['ok' => false, 'message' => 'Date manquante'], 400);
        }
        $date = \DateTime::createFromFormat('Y-m-d', $dateStr);
        if (!$date) {
            return new JsonResponse(['ok' => false, 'message' => 'Format de date invalide'], 400);
        }
        $user = $this->getUser();
        if (!$user) {
            return new JsonResponse(['ok' => false, 'message' => 'Utilisateur non connecté'], 401);
        }
        
        $sante = $repository->findOneByUserAndDate($user, $date);
        if (!$sante) {
            return new JsonResponse(['ok' => false, 'message' => 'Aucune donnée pour cette date']);
        }
        
        $humeurs = $sante->getHumeur();
        $humeurValues = [];
        
        if (!empty($humeurs)) {
            foreach ($humeurs as $humeur) {
                if ($humeur instanceof Humeur) {
                    // Si c'est déjà un enum, récupérer la valeur
                    $humeurValues[] = $humeur->value;
                } else {
                    // Si c'est une string, l'ajouter directement
                    // (Doctrine SIMPLE_ARRAY retourne parfois des strings au lieu d'enums)
                    $humeurValues[] = (string) $humeur;
                }
            }
        }
        
        return new JsonResponse([
            'ok' => true,
            'id' => $sante->getId(),
            'date' => $sante->getDate()->format('d/m/Y'),
            'dateFormatted' => $sante->getDate()->format('Y-m-d'),
            'poids' => $sante->getPoids(),
            'taille' => $sante->getTaille(),
            'imc' => $sante->getImc(),
            'tensionArterielle' => $sante->getTensionArterielle(),
            'sommeil' => $sante->getSommeil(),
            'activitePhysique' => $sante->getActivitePhysique() ? $sante->getActivitePhysique()->value : null,
            'humeur' => $humeurValues,
            'alimentation' => $sante->getAlimentation() ? $sante->getAlimentation()->value : null,
            'eauBue' => $sante->getEauBue(),
            'deleteCsrf' => $csrfTokenManager->getToken('delete' . $sante->getId())->getValue(),
            'editCsrf' => $csrfTokenManager->getToken('edit' . $sante->getId())->getValue(),
        ]);
    }

    #[Route('/new', name: 'app_sante_quotidienne_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $em, SanteQuotidienneRepository $repository): Response
    {
        // Debug: Log de la méthode HTTP
        error_log('=== NEW SANTE QUOTIDIENNE ===');
        error_log('Method: ' . $request->getMethod());
        error_log('Content-Type: ' . $request->headers->get('Content-Type'));
        error_log('Request data: ' . json_encode($request->request->all()));

        $sante = new SanteQuotidienne();
        $sante->setUser($this->getUser());           // ← très important
        $sante->setDate(new \DateTime());             // date du jour par défaut

        $form = $this->createForm(SanteQuotidienneType::class, $sante);
        $form->handleRequest($request);

        error_log('Form submitted: ' . ($form->isSubmitted() ? 'YES' : 'NO'));
        error_log('Form valid: ' . ($form->isValid() ? 'YES' : 'NO'));

        if (!$form->isValid() && $form->isSubmitted()) {
            error_log('Form errors:');
            foreach ($form->getErrors(true) as $error) {
                error_log('  - ' . $error->getMessage());
            }
        }

        if ($form->isSubmitted() && $form->isValid()) {
            error_log('Saving data: poids=' . $sante->getPoids() . ', taille=' . $sante->getTaille());
            $em->persist($sante);
            $em->flush();
            error_log('Data saved successfully with ID: ' . $sante->getId());

            $this->addFlash('success', 'Données quotidiennes enregistrées avec succès !');

            return $this->redirectToRoute('app_sante_quotidienne_front_new');
        }

        // Récupère les entrées de l'utilisateur pour affichage et actions
        $santes = $repository->findBy([
            'user' => $this->getUser(),
        ], ['date' => 'DESC']);

        // Moyenne de sommeil pour l'utilisateur connecté
        $averageSommeil = $repository->getAverageSommeilForUser($this->getUser());

        // Statistiques pour les boîtes d'action
        $moodStats = $repository->getMoodStatistics($this->getUser());
        $activityStats = $repository->getActivityStatistics($this->getUser());
        $nutritionStats = $repository->getNutritionStatistics($this->getUser());

        // Affiche le formulaire dans la page front avec les erreurs de validation
        return $this->render('front/santequotidienne/form.html.twig', [
            'form' => $form->createView(),
            'santes' => $santes,
            'averageSommeil' => $averageSommeil,
            'moodStats' => $moodStats,
            'activityStats' => $activityStats,
            'nutritionStats' => $nutritionStats,
        ]);
    }

    #[Route('/{id}', name: 'app_sante_quotidienne_show', methods: ['GET'])]
    #[IsGranted('SANTE_VIEW', subject: 'sante')]  // optionnel : sécuriser par voter
    public function show(SanteQuotidienne $sante): Response
    {
        // Vérification supplémentaire que c'est bien l'utilisateur connecté
        if ($sante->getUser() !== $this->getUser()) {
            throw $this->createAccessDeniedException();
        }

        return $this->render('sante_quotidienne/show.html.twig', [
            'sante' => $sante,
        ]);
    }

    #[Route('/{id}/edit', name: 'app_sante_quotidienne_edit', methods: ['GET', 'POST'])]
    #[IsGranted('SANTE_EDIT', subject: 'sante')]
    public function edit(Request $request, SanteQuotidienne $sante, EntityManagerInterface $em, SanteQuotidienneRepository $repository, CsrfTokenManagerInterface $csrfTokenManager): Response
    {
        // Sécurité : on ne peut éditer que ses propres données
        if ($sante->getUser() !== $this->getUser()) {
            throw $this->createAccessDeniedException();
        }

        // Si c'est une requête AJAX, on gère manuellement les données
        if ($request->isXmlHttpRequest()) {
            // Vérifier le token CSRF
            if (!$csrfTokenManager->isTokenValid(new \Symfony\Component\Security\Csrf\CsrfToken('edit' . $sante->getId(), $request->get('_token')))) {
                return new JsonResponse(['ok' => false, 'message' => 'Token CSRF invalide'], 400);
            }

            // Traiter les données manuellement
            $poids = $request->get('sante_quotidienne')['poids'] ?? null;
            $taille = $request->get('sante_quotidienne')['taille'] ?? null;
            $tensionArterielle = $request->get('sante_quotidienne')['tensionArterielle'] ?? null;
            $sommeil = $request->get('sante_quotidienne')['sommeil'] ?? null;
            $activitePhysique = $request->get('sante_quotidienne')['activitePhysique'] ?? null;
            $humeursArray = $request->get('sante_quotidienne')['humeur'] ?? [];
            $alimentation = $request->get('sante_quotidienne')['alimentation'] ?? null;
            $eauBue = $request->get('sante_quotidienne')['eauBue'] ?? null;

            // Debug logging
            error_log("Received humeurs: " . json_encode($humeursArray));
            error_log("Received tension: " . $tensionArterielle);

            // Mettre à jour l'entité
            if ($poids) $sante->setPoids((float) $poids);
            if ($taille) $sante->setTaille((float) $taille);
            if ($tensionArterielle) $sante->setTensionArterielle((float) $tensionArterielle);
            if ($sommeil) $sante->setSommeil((float) $sommeil);
            if ($activitePhysique) $sante->setActivitePhysique(NiveauActivite::from($activitePhysique));
            if (!empty($humeursArray)) {
                $humeursEnums = array_map(function($humeur) {
                    return Humeur::from($humeur);
                }, $humeursArray);
                error_log("Setting humeurs enums: " . json_encode(array_map(fn($h) => $h->value, $humeursEnums)));
                $sante->setHumeur($humeursEnums);
            }
            if ($alimentation) $sante->setAlimentation(Alimentation::from($alimentation));
            if ($eauBue) $sante->setEauBue((float) $eauBue);

            $em->flush();
            
            return new JsonResponse(['ok' => true, 'message' => 'Données modifiées avec succès.']);
        }

        $form = $this->createForm(SanteQuotidienneType::class, $sante);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $em->flush();

            $this->addFlash('success', 'Données modifiées avec succès.');
            return $this->redirectToRoute('app_sante_quotidienne_front_new');
        }

        // Récupère les entrées de l'utilisateur pour affichage et actions
        $santes = $repository->findBy([
            'user' => $this->getUser(),
        ], ['date' => 'DESC']);

        // Moyenne de sommeil pour l'utilisateur connecté
        $averageSommeil = $repository->getAverageSommeilForUser($this->getUser());

        // Statistiques pour les boîtes d'action
        $moodStats = $repository->getMoodStatistics($this->getUser());
        $activityStats = $repository->getActivityStatistics($this->getUser());
        $nutritionStats = $repository->getNutritionStatistics($this->getUser());

        // Affiche le formulaire dans le template front
        return $this->render('front/santequotidienne/form.html.twig', [
            'form' => $form->createView(),
            'santes' => $santes,
            'averageSommeil' => $averageSommeil,
            'moodStats' => $moodStats,
            'activityStats' => $activityStats,
            'nutritionStats' => $nutritionStats,
        ]);
    }

    #[Route('/{id}', name: 'app_sante_quotidienne_delete', methods: ['POST'])]
    #[IsGranted('SANTE_DELETE', subject: 'sante')]
    public function delete(Request $request, SanteQuotidienne $sante, EntityManagerInterface $em): Response
    {
        if ($sante->getUser() !== $this->getUser()) {
            throw $this->createAccessDeniedException();
        }

        if ($this->isCsrfTokenValid('delete' . $sante->getId(), $request->request->get('_token'))) {
            $em->remove($sante);
            $em->flush();

            $this->addFlash('success', 'Entrée supprimée avec succès.');
        }

        return $this->redirectToRoute('app_sante_quotidienne_front_new');
    }
}