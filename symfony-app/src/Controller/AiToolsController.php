<?php

namespace App\Controller;

use App\Entity\Abonnement;
use App\Entity\User;
use App\Repository\AbonnementRepository;
use App\Service\Ai\DocumentScannerService;
use App\Service\Ai\NutritionPlannerService;
use App\Service\Ai\ResultExplainerService;
use App\Service\Ai\WorkoutPlannerService;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/ai-tools', name: 'app_ai_tools_')]
#[IsGranted('ROLE_USER')]
class AiToolsController extends AbstractController
{
    private const AI_TOOLS_SUBSCRIPTION_TYPE = 'AI_TOOLS';
    private const AI_TOOLS_PRICE = '5.00';

    public function __construct(private readonly AbonnementRepository $abonnementRepository)
    {
    }

    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        if ($redirect = $this->redirectToAiSubscriptionIfNeeded()) {
            return $redirect;
        }

        return $this->render('ai_tools/index.html.twig');
    }

    #[Route('/document-scanner', name: 'document_scanner', methods: ['GET', 'POST'])]
    public function documentScanner(Request $request, DocumentScannerService $scannerService): Response
    {
        if ($redirect = $this->redirectToAiSubscriptionIfNeeded()) {
            return $redirect;
        }

        $result = null;

        if ($request->isMethod('POST')) {
            $content = trim((string) $request->request->get('content', ''));
            $file = $request->files->get('document');

            if ($content === '' && $file) {
                $extracted = $scannerService->extractContentFromUpload($file);
                $content = $extracted['content'] ?? '';
                if (!empty($extracted['warning'])) {
                    $this->addFlash('warning', (string) $extracted['warning']);
                }
            }

            $result = $scannerService->scan($content);
        }

        return $this->render('ai_tools/document_scanner.html.twig', [
            'result' => $result,
        ]);
    }

    #[Route('/nutrition-planner', name: 'nutrition_planner', methods: ['GET', 'POST'])]
    public function nutritionPlanner(Request $request, NutritionPlannerService $service): Response
    {
        if ($redirect = $this->redirectToAiSubscriptionIfNeeded()) {
            return $redirect;
        }

        $result = null;
        if ($request->isMethod('POST')) {
            $goal = trim((string) $request->request->get('goal', ''));
            $dietStyle = trim((string) $request->request->get('dietStyle', 'standard'));
            $allergies = trim((string) $request->request->get('allergies', ''));
            $days = $request->request->getInt('days', 7);

            $result = $service->generate($goal, $dietStyle, $allergies, $days);
        }

        return $this->render('ai_tools/nutrition_planner.html.twig', [
            'result' => $result,
        ]);
    }

    #[Route('/workout-planner', name: 'workout_planner', methods: ['GET', 'POST'])]
    public function workoutPlanner(Request $request, WorkoutPlannerService $service): Response
    {
        if ($redirect = $this->redirectToAiSubscriptionIfNeeded()) {
            return $redirect;
        }

        $result = null;
        if ($request->isMethod('POST')) {
            $goal = trim((string) $request->request->get('goal', ''));
            $level = trim((string) $request->request->get('level', 'debutant'));
            $daysPerWeek = $request->request->getInt('daysPerWeek', 3);
            $minutes = $request->request->getInt('minutes', 30);
            $constraints = trim((string) $request->request->get('constraints', ''));

            $result = $service->generate($goal, $level, $daysPerWeek, $minutes, $constraints);
        }

        return $this->render('ai_tools/workout_planner.html.twig', [
            'result' => $result,
        ]);
    }

    #[Route('/result-explainer', name: 'result_explainer', methods: ['GET', 'POST'])]
    public function resultExplainer(Request $request, ResultExplainerService $service): Response
    {
        if ($redirect = $this->redirectToAiSubscriptionIfNeeded()) {
            return $redirect;
        }

        $result = null;
        if ($request->isMethod('POST')) {
            $testName = trim((string) $request->request->get('testName', ''));
            $value = trim((string) $request->request->get('value', ''));
            $unit = trim((string) $request->request->get('unit', ''));
            $referenceRange = trim((string) $request->request->get('referenceRange', ''));

            $result = $service->explain($testName, $value, $unit, $referenceRange);
        }

        return $this->render('ai_tools/result_explainer.html.twig', [
            'result' => $result,
        ]);
    }

    #[Route('/subscription', name: 'subscription', methods: ['GET'])]
    public function subscription(AbonnementRepository $abonnementRepository): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('Accès non autorisé aux outils IA.');
        }

        $activeSubscription = $abonnementRepository->findActiveForUserAndType($user, self::AI_TOOLS_SUBSCRIPTION_TYPE);

        return $this->render('ai_tools/subscription.html.twig', [
            'activeSubscription' => $activeSubscription,
            'price' => self::AI_TOOLS_PRICE,
        ]);
    }

    #[Route('/subscription/activate', name: 'subscription_activate', methods: ['POST'])]
    public function activateSubscription(Request $request): RedirectResponse
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('Accès non autorisé aux outils IA.');
        }

        if (!$this->isCsrfTokenValid('ai_tools_subscription_activate', (string) $request->request->get('_token'))) {
            $this->addFlash('error', 'Action non autorisée. Veuillez réessayer.');
            return $this->redirectToRoute('app_ai_tools_subscription');
        }

        if ($this->getActiveAiSubscription($user) !== null) {
            $this->addFlash('info', 'Votre abonnement IA est déjà actif.');
            return $this->redirectToRoute('app_ai_tools_index');
        }

        $request->getSession()->set('subscription_type', self::AI_TOOLS_SUBSCRIPTION_TYPE);

        return $this->redirectToRoute('app_subscription_payment');
    }

    private function redirectToAiSubscriptionIfNeeded(): ?RedirectResponse
    {
        if ($this->isGranted('ROLE_ADMIN')) {
            return null;
        }

        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('Accès non autorisé aux outils IA.');
        }

        $activeAiSubscription = $this->getActiveAiSubscription($user);
        if ($activeAiSubscription !== null) {
            return null;
        }

        $this->addFlash('warning', 'L\'accès aux Outils IA SANTÉA nécessite un abonnement IA actif (5 DT / mois).');
        return $this->redirectToRoute('app_ai_tools_subscription');
    }

    private function getActiveAiSubscription(User $user): ?Abonnement
    {
        return $this->abonnementRepository->findActiveForUserAndType($user, self::AI_TOOLS_SUBSCRIPTION_TYPE);
    }
}
