<?php

namespace App\Controller;

use App\Entity\User;
use App\Entity\SanteQuotidienne;
use App\Form\SanteQuotidienneType;
use App\Repository\SanteQuotidienneRepository;
use App\Service\MentalHealthChatbotService;
use App\Service\RiskPredictionService;
use App\Enum\NiveauActivite;
use App\Enum\Humeur;
use App\Enum\Alimentation;
use Dompdf\Dompdf;
use Dompdf\Options;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Form\FormInterface;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Mime\Address;
use Symfony\Component\Mime\Email;
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
    public function __construct(
        private readonly RiskPredictionService $riskPredictionService
    ) {}

    #[Route('', name: 'app_sante_quotidienne_index', methods: ['GET'])]
    public function index(SanteQuotidienneRepository $repository): Response
    {
        $sante = new SanteQuotidienne();
        $form = $this->createForm(SanteQuotidienneType::class, $sante);

        return $this->render('front/santequotidienne/form.html.twig', $this->buildFormPageData($form, $repository));
    }

    #[Route('/front', name: 'app_sante_quotidienne_front', methods: ['GET'])]
    public function front(): Response
    {
        return $this->redirectToRoute('app_symptomes');
    }

    #[Route('/front/new-form', name: 'app_sante_quotidienne_front_new', methods: ['GET'])]
    public function frontNewForm(SanteQuotidienneRepository $repository): Response
    {
        $sante = new SanteQuotidienne();
        $form = $this->createForm(SanteQuotidienneType::class, $sante);

        return $this->render('front/santequotidienne/form.html.twig', $this->buildFormPageData($form, $repository));
    }

    #[Route('/rapport-total/download', name: 'app_sante_quotidienne_total_report_download', methods: ['GET'])]
    public function downloadTotalHealthReport(
        Request $request,
        SanteQuotidienneRepository $repository,
        MailerInterface $mailer
    ): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException();
        }

        $requestedPeriod = (int) $request->query->get('period', 30);
        $allowedPeriods = [7, 30, 90];
        $periodDays = in_array($requestedPeriod, $allowedPeriods, true) ? $requestedPeriod : 30;

        $entries = $repository->findBy(['user' => $user], ['date' => 'DESC']);
        $cutoff = (new \DateTimeImmutable('today'))->modify(sprintf('-%d days', $periodDays - 1));
        $entries = array_values(array_filter(
            $entries,
            static fn(SanteQuotidienne $entry) => $entry->getDate() instanceof \DateTimeInterface
                && \DateTimeImmutable::createFromInterface($entry->getDate()) >= $cutoff
        ));

        if (count($entries) === 0) {
            $this->addFlash('error', sprintf('Aucune donnée santé disponible pour la période %d jours.', $periodDays));
            return $this->redirectToRoute('app_sante_quotidienne_index');
        }

        $summaryStats = $repository->getDashboardSummaryStats($user);
        $averageSommeil = $repository->getAverageSommeilForUser($user);
        $activityStats = $repository->getActivityStatistics($user);
        $nutritionStats = $repository->getNutritionStatistics($user);
        $moodStats = $repository->getMoodStatistics($user);

        $prediction = $this->riskPredictionService->recalculateForUser($user);

        $entriesForReport = array_map(static function (SanteQuotidienne $entry): array {
            $humeurs = array_map(static fn($h) => $h instanceof \BackedEnum ? $h->value : (string) $h, $entry->getHumeur());

            return [
                'date' => $entry->getDate()?->format('d/m/Y'),
                'poids' => $entry->getPoids(),
                'imc' => $entry->getImc(),
                'tension' => $entry->getTensionArterielle(),
                'sommeil' => $entry->getSommeil(),
                'eauBue' => $entry->getEauBue(),
                'activite' => $entry->getActivitePhysique()?->value,
                'alimentation' => $entry->getAlimentation()?->value,
                'humeurs' => $humeurs,
            ];
        }, array_slice($entries, 0, 60));

        $html = $this->renderView('front/santequotidienne/report_total.pdf.twig', [
            'user' => $user,
            'generatedAt' => new \DateTimeImmutable(),
            'periodDays' => $periodDays,
            'totalEntries' => count($entries),
            'summaryStats' => $summaryStats,
            'averageSommeil' => $averageSommeil,
            'activityStats' => $activityStats,
            'nutritionStats' => $nutritionStats,
            'moodStats' => $moodStats,
            'riskPrediction' => [
                'htn' => ['score' => $prediction->getRiskHtn(), 'level' => $prediction->getLevelHtn()],
                'diabetes' => ['score' => $prediction->getRiskDiabetes(), 'level' => $prediction->getLevelDiabetes()],
                'depression' => ['score' => $prediction->getRiskDepression(), 'level' => $prediction->getLevelDepression()],
                'nutrition' => ['score' => $prediction->getRiskRespiratory(), 'level' => $prediction->getLevelRespiratory()],
            ],
            'entries' => $entriesForReport,
        ]);

        $options = new Options();
        $options->set('isRemoteEnabled', true);
        $options->set('defaultFont', 'DejaVu Sans');

        $dompdf = new Dompdf($options);
        $dompdf->loadHtml($html);
        $dompdf->setPaper('A4', 'portrait');
        $dompdf->render();

        $filename = sprintf('rapport-sante-total-%dj-%s.pdf', $periodDays, (new \DateTimeImmutable())->format('Ymd-His'));
        $pdfContent = $dompdf->output();

        try {
            $email = (new Email())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Votre rapport global de santé')
                ->text(sprintf(
                    "Bonjour %s,\n\nVotre rapport global de santé (%d jours) est en pièce jointe.\n\nCordialement,\nSANTÉA",
                    trim($user->getPrenom() ?: $user->getNom() ?: 'utilisateur'),
                    $periodDays
                ))
                ->attach($pdfContent, $filename, 'application/pdf');

            $mailer->send($email);
        } catch (\Throwable) {
        }

        return new Response($pdfContent, 200, [
            'Content-Type' => 'application/pdf',
            'Content-Disposition' => 'attachment; filename="' . $filename . '"',
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

    #[Route('/api/chatbot/empathy', name: 'app_sante_quotidienne_chatbot_empathy', methods: ['POST'])]
    public function chatbotEmpathy(Request $request, MentalHealthChatbotService $chatbotService): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['ok' => false, 'message' => 'Acces refuse'], 403);
        }

        $payload = json_decode($request->getContent(), true);
        if (!is_array($payload)) {
            return new JsonResponse(['ok' => false, 'message' => 'Payload invalide'], 400);
        }

        $message = trim((string) ($payload['message'] ?? ''));
        if ($message === '') {
            return new JsonResponse(['ok' => false, 'message' => 'Message vide'], 422);
        }

        $result = $chatbotService->reply($message);

        return new JsonResponse([
            'ok' => true,
            'emotion' => $result['emotion'],
            'intent' => $result['intent'],
            'response' => $result['response'],
            'safetyAlert' => $result['safetyAlert'],
        ]);
    }

    #[Route('/new', name: 'app_sante_quotidienne_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $em, SanteQuotidienneRepository $repository): Response
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException();
        }

        // Debug: Log de la méthode HTTP
        error_log('=== NEW SANTE QUOTIDIENNE ===');
        error_log('Method: ' . $request->getMethod());
        error_log('Content-Type: ' . $request->headers->get('Content-Type'));
        error_log('Request data: ' . json_encode($request->request->all()));

        $sante = new SanteQuotidienne();
        $sante->setUser($user);           // ← très important
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
            $this->riskPredictionService->recalculateForUser($user);
            error_log('Data saved successfully with ID: ' . $sante->getId());

            $this->addFlash('success', 'Données quotidiennes enregistrées avec succès !');

            return $this->redirectToRoute('app_sante_quotidienne_front_new');
        }

        // Affiche le formulaire dans la page front avec les erreurs de validation
        return $this->render('front/santequotidienne/form.html.twig', $this->buildFormPageData($form, $repository));
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
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException();
        }

        // Sécurité : on ne peut éditer que ses propres données
        if ($sante->getUser() !== $user) {
            throw $this->createAccessDeniedException();
        }

        // Si c'est une requête AJAX, on renvoie un JSON avec les erreurs éventuelles
        if ($request->isXmlHttpRequest()) {
            if (!$csrfTokenManager->isTokenValid(new \Symfony\Component\Security\Csrf\CsrfToken('edit' . $sante->getId(), $request->get('_token')))) {
                return new JsonResponse(['ok' => false, 'message' => 'Token CSRF invalide'], 400);
            }

            $form = $this->createForm(SanteQuotidienneType::class, $sante, [
                'csrf_protection' => false,
            ]);
            $form->handleRequest($request);

            if ($form->isSubmitted() && $form->isValid()) {
                $em->flush();
                /** @var User $currentUser */
                $currentUser = $this->getUser();
                if ($currentUser instanceof User) {
                    $this->riskPredictionService->recalculateForUser($currentUser);
                }
                return new JsonResponse(['ok' => true, 'message' => 'Données modifiées avec succès.']);
            }

            $errors = [];
            $firstErrorMessage = null;
            foreach ($form->getErrors(true) as $error) {
                $origin = $error->getOrigin();
                $field = $origin instanceof FormInterface ? $origin->getName() : '_form';
                $errors[$field][] = $error->getMessage();
                if ($firstErrorMessage === null) {
                    $humanField = $field === '_form' ? '' : ucfirst(str_replace('_', ' ', $field)) . ' : ';
                    $firstErrorMessage = $humanField . $error->getMessage();
                }
            }

            return new JsonResponse([
                'ok' => false,
                'message' => $firstErrorMessage ?? 'Formulaire invalide',
                'errors' => $errors,
            ], 422);
        }

        $form = $this->createForm(SanteQuotidienneType::class, $sante);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $em->flush();
            $this->riskPredictionService->recalculateForUser($user);

            $this->addFlash('success', 'Données modifiées avec succès.');
            return $this->redirectToRoute('app_sante_quotidienne_front_new');
        }

        // Affiche le formulaire dans le template front
        return $this->render('front/santequotidienne/form.html.twig', $this->buildFormPageData($form, $repository));
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

    /**
     * @return array{
     *   form: mixed,
     *   santes: array<int, SanteQuotidienne>,
     *   averageSommeil: ?float,
     *   moodStats: array<string, mixed>,
     *   activityStats: array<string, mixed>,
     *   nutritionStats: array<string, mixed>,
     *   googleFitLatest: array<string, mixed>,
     *   googleFitAvgSteps: ?float,
     *   summaryStats: array{latestWeight:?float,weeklyActivityMinutes:int,averageWater:?float},
     *   chartStats: array{labels: array<int, string>, weights: array<int, ?float>, sleep: array<int, ?float>, tension: array<int, ?float>, water: array<int, ?float>, activity: array<int, int>},
     *   riskPrediction: array<string, mixed>
     * }
     */
    private function buildFormPageData(FormInterface $form, SanteQuotidienneRepository $repository): array
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException();
        }
        $santes = $repository->findBy(['user' => $user], ['date' => 'DESC']);
        $prediction = $this->riskPredictionService->recalculateForUser($user);
        $explanations = $prediction->getExplanationsJson();

        return [
            'form' => $form->createView(),
            'santes' => $santes,
            'averageSommeil' => $repository->getAverageSommeilForUser($user),
            'moodStats' => $repository->getMoodStatistics($user),
            'activityStats' => $repository->getActivityStatistics($user),
            'nutritionStats' => $repository->getNutritionStatistics($user),
            'googleFitLatest' => $repository->getLatestGoogleFitMetrics($user),
            'googleFitAvgSteps' => $repository->getAverageStepsLastDays($user, 7),
            'summaryStats' => $repository->getDashboardSummaryStats($user),
            'chartStats' => $repository->getDashboardChartSeries($user, 7),
            'riskPrediction' => [
                'htn' => ['score' => $prediction->getRiskHtn(), 'level' => $prediction->getLevelHtn(), 'explanations' => $explanations['htn'] ?? [], 'details' => $explanations['htn_details'] ?? []],
                'diabetes' => ['score' => $prediction->getRiskDiabetes(), 'level' => $prediction->getLevelDiabetes(), 'explanations' => $explanations['diabetes'] ?? [], 'details' => $explanations['diabetes_details'] ?? []],
                'depression' => ['score' => $prediction->getRiskDepression(), 'level' => $prediction->getLevelDepression(), 'explanations' => $explanations['depression'] ?? [], 'details' => $explanations['depression_details'] ?? []],
                'nutrition' => ['score' => $prediction->getRiskRespiratory(), 'level' => $prediction->getLevelRespiratory(), 'explanations' => $explanations['nutrition'] ?? ($explanations['respiratory'] ?? []), 'details' => $explanations['nutrition_details'] ?? ($explanations['respiratory_details'] ?? [])],
                'updatedAt' => $prediction->getUpdatedAt()->format('d/m/Y H:i'),
            ],
        ];
    }
}
