<?php

namespace App\Controller;

use App\Service\Ai\DocumentScannerService;
use App\Service\Ai\NutritionPlannerService;
use App\Service\Ai\ResultExplainerService;
use App\Service\Ai\WorkoutPlannerService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/ai-tools', name: 'app_ai_tools_')]
#[IsGranted('ROLE_USER')]
class AiToolsController extends AbstractController
{
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        return $this->render('ai_tools/index.html.twig');
    }

    #[Route('/document-scanner', name: 'document_scanner', methods: ['GET', 'POST'])]
    public function documentScanner(Request $request, DocumentScannerService $scannerService): Response
    {
        $result = null;

        if ($request->isMethod('POST')) {
            $content = trim((string) $request->request->get('content', ''));
            $file = $request->files->get('document');

            if ($content === '' && $file) {
                $mime = (string) $file->getMimeType();
                $ext = strtolower((string) $file->getClientOriginalExtension());
                $isTextLike = str_starts_with($mime, 'text/') || in_array($ext, ['txt', 'csv', 'log', 'md'], true);

                if ($isTextLike) {
                    $raw = file_get_contents($file->getPathname());
                    $content = is_string($raw) ? $raw : '';
                } else {
                    $this->addFlash('warning', 'Format non textuel detecte. Collez le contenu texte pour une extraction fiable.');
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
}
