<?php

namespace App\Controller;

use App\Entity\AccompanimentPlan;
use App\Entity\CoachSportif;
use App\Entity\Patient;
use App\Entity\User;
use App\Form\AccompanimentPlanType;
use App\Repository\AccompanimentPlanRepository;
use App\Repository\CoachSportifRepository;
use App\Repository\PatientRepository;
use App\Service\Accompaniment\AccompanimentAiAdvisorService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/plans', name: 'app_plan_')]
#[IsGranted('ROLE_USER')]
class AccompanimentPlanController extends AbstractController
{
    public function __construct(
        private AccompanimentPlanRepository $planRepository,
        private PatientRepository $patientRepository,
        private CoachSportifRepository $coachRepository,
        private EntityManagerInterface $em,
        private AccompanimentAiAdvisorService $aiAdvisor,
    ) {
    }

    /**
     * List all accompaniment plans
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isPatientRole($user)) {
            if ($this->isProfessionalRole($user)) {
                return $this->redirectToRoute('app_plan_professional_plans');
            }

            throw $this->createAccessDeniedException();
        }

        // Try to get the patient profile
        $patient = $this->patientRepository->findOneBy(['user' => $user]);

        if (!$patient) {
            $this->addFlash('warning', 'Vous n\'êtes pas enregistré comme patient.');
            return $this->redirectToRoute('app_home');
        }

        $plans = $this->planRepository->findByPatient($patient);
        $stats = $this->planRepository->getPatientStatistics($patient);

        return $this->render('plan/index.html.twig', [
            'plans' => $plans,
            'stats' => $stats,
        ]);
    }

    /**
     * View plan details
     */
    #[Route('/{id}', name: 'show', methods: ['GET'])]
    public function show(AccompanimentPlan $plan): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $isPatient = $plan->getPatient()->getUser() === $user;
        $isCoach = $plan->getCoach() && $plan->getCoach()->getUser() === $user;
        $isNutritionist = $plan->getNutritionist() && $plan->getNutritionist()->getUser() === $user;
        $isMedecin = $this->isMedecinRole($user);

        // Verify access: patient, assigned professional, or any doctor (read-only)
        if (!$isPatient && !$isCoach && !$isNutritionist && !$isMedecin) {
            throw $this->createAccessDeniedException();
        }

        return $this->render('plan/show.html.twig', [
            'plan' => $plan,
        ]);
    }

    /**
     * Create new accompaniment plan (for coaches/nutritionists)
     */
    #[Route('/create/{patientId}', name: 'create', methods: ['GET', 'POST'])]
    public function create(Request $request, int $patientId): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isProfessionalRole($user) || $this->isMedecinRole($user)) {
            throw $this->createAccessDeniedException('Les médecins ne peuvent pas créer de plans. Accès en lecture seule uniquement.');
        }

        $patient = $this->patientRepository->find($patientId);
        if (!$patient) {
            throw $this->createNotFoundException('Patient non trouvé');
        }

        $plan = new AccompanimentPlan();
        $plan->setPatient($patient);
        $plan->setStartDate(new \DateTimeImmutable());

        // Auto-assign the professional before form creation
        $assignedLabel = null;
        if ($this->isCoachRole($user)) {
            $coach = $this->coachRepository->findOneBy(['user' => $user]);
            $plan->setCoach($coach);
            $plan->setNutritionist(null);
            $assignedLabel = ['role' => 'Coach sportif', 'name' => $user->getUsername()];
        } elseif ($this->isNutritionnisteRole($user)) {
            $nutritionniste = $user->getNutritionniste();
            $plan->setNutritionist($nutritionniste);
            $plan->setCoach(null);
            $assignedLabel = ['role' => 'Nutritionniste', 'name' => $user->getUsername()];
        }

        $form = $this->createForm(AccompanimentPlanType::class, $plan);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $this->em->persist($plan);
            $this->em->flush();

            $this->addFlash('success', 'Plan d\'accompagnement créé avec succès!');
            return $this->redirectToRoute('app_plan_show', ['id' => $plan->getId()]);
        }

        return $this->render('plan/create.html.twig', [
            'form'          => $form,
            'patient'       => $patient,
            'assignedLabel' => $assignedLabel,
        ]);
    }

    /**
     * Edit an accompaniment plan
     */
    #[Route('/{id}/edit', name: 'edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, AccompanimentPlan $plan): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Verify access - creator or patient can edit
        $isCreator = ($plan->getCoach() && $plan->getCoach()->getUser() === $user);
        $isNutritionCreator = ($plan->getNutritionist() && $plan->getNutritionist()->getUser() === $user);

        if (!$isCreator && !$isNutritionCreator) {
            throw $this->createAccessDeniedException();
        }

        $form = $this->createForm(AccompanimentPlanType::class, $plan);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $plan->forceUpdatedAt(new \DateTimeImmutable());
            $this->em->flush();

            $this->addFlash('success', 'Plan mis à jour avec succès!');
            return $this->redirectToRoute('app_plan_show', ['id' => $plan->getId()]);
        }

        return $this->render('plan/edit.html.twig', [
            'form' => $form,
            'plan' => $plan,
        ]);
    }

    /**
     * Delete an accompaniment plan
     */
    #[Route('/{id}/delete', name: 'delete', methods: ['POST'])]
    public function delete(AccompanimentPlan $plan): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Only creator or patient can delete
        $isCreator = ($plan->getCoach() && $plan->getCoach()->getUser() === $user);
        $isNutritionCreator = ($plan->getNutritionist() && $plan->getNutritionist()->getUser() === $user);

        if (!$isCreator && !$isNutritionCreator) {
            throw $this->createAccessDeniedException();
        }

        $this->em->remove($plan);
        $this->em->flush();

        $this->addFlash('success', 'Plan supprimé.');
        return $this->redirectToRoute('app_plan_index');
    }

    /**
     * Get my plans as coach or nutritionist
     */
    #[Route('/professional/my-plans', name: 'professional_plans', methods: ['GET'])]
    public function professionalPlans(): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isProfessionalRole($user)) {
            throw $this->createAccessDeniedException();
        }

        $plans = [];

        if ($this->isMedecinRole($user)) {
            // Médecin: read-only access to all plans
            $plans = $this->planRepository->findAll();
        } else {
            $coach = $this->coachRepository->findOneBy(['user' => $user]);
            if ($coach) {
                $plans = array_merge($plans, $this->planRepository->findByCoach($coach));
            }

            $nutritionniste = $user->getNutritionniste();
            if ($nutritionniste) {
                $plans = array_merge($plans, $this->planRepository->findByNutritionist($nutritionniste));
            }
        }

        // Sort by most recent first
        usort($plans, fn($a, $b) => $b->getCreatedAt() <=> $a->getCreatedAt());

        $patients = $this->patientRepository->findAll();

        // Calculate statistics
        $activePlans = count(array_filter($plans, fn($p) => $p->getStatus() === 'active'));
        $completedPlans = count(array_filter($plans, fn($p) => $p->getStatus() === 'completed'));
        $uniquePatientIds = array_unique(array_map(fn($p) => $p->getPatient()->getId(), $plans));
        $uniquePatients = count($uniquePatientIds);

        return $this->render('plan/professional_plans.html.twig', [
            'plans' => $plans,
            'patients' => $patients,
            'stats' => [
                'active' => $activePlans,
                'completed' => $completedPlans,
                'uniquePatients' => $uniquePatients,
            ],
        ]);
    }

    #[Route('/ai/suggestions', name: 'ai_suggestions', methods: ['POST'])]
    public function aiSuggestions(Request $request): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isProfessionalRole($user)) {
            return $this->json([
                'ok' => false,
                'error' => 'Accès refusé.',
            ], 403);
        }

        $payload = json_decode($request->getContent(), true);
        if (!is_array($payload)) {
            return $this->json([
                'ok' => false,
                'error' => 'Payload invalide.',
            ], 400);
        }

        $goal = trim((string) ($payload['goal'] ?? ''));
        if ($goal === '') {
            return $this->json([
                'ok' => false,
                'error' => 'Veuillez saisir un objectif global.',
            ], 422);
        }

        try {
            $result = $this->aiAdvisor->buildSuggestions(
                $goal,
                trim((string) ($payload['dietStyle'] ?? 'standard')),
                trim((string) ($payload['allergies'] ?? '')),
                (int) ($payload['nutritionDays'] ?? 7),
                trim((string) ($payload['level'] ?? 'intermediaire')),
                (int) ($payload['daysPerWeek'] ?? 3),
                (int) ($payload['minutes'] ?? 30),
                trim((string) ($payload['constraints'] ?? '')),
            );

            return $this->json([
                'ok' => true,
                'result' => $result,
            ]);
        } catch (\Throwable) {
            return $this->json([
                'ok' => false,
                'error' => 'Erreur lors de la génération IA. Réessayez dans quelques secondes.',
            ], 500);
        }
    }

    private function getEffectiveRole(User $user): string
    {
        return $user->getSubscriptionType() ?: $user->getRole();
    }

    private function isPatientRole(User $user): bool
    {
        return $this->getEffectiveRole($user) === 'ROLE_PATIENT';
    }

    private function isCoachRole(User $user): bool
    {
        return $this->getEffectiveRole($user) === 'ROLE_COACH';
    }

    private function isNutritionnisteRole(User $user): bool
    {
        return $this->getEffectiveRole($user) === 'ROLE_NUTRITIONNISTE';
    }

    private function isMedecinRole(User $user): bool
    {
        return $this->getEffectiveRole($user) === 'ROLE_MEDECIN';
    }

    private function isProfessionalRole(User $user): bool
    {
        return in_array($this->getEffectiveRole($user), ['ROLE_MEDECIN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE'], true);
    }
}
