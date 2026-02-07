<?php

namespace App\Controller;

use App\Entity\AccompanimentPlan;
use App\Entity\CoachSportif;
use App\Entity\Nutritionniste;
use App\Entity\Patient;
use App\Entity\User;
use App\Form\AccompanimentPlanType;
use App\Repository\AccompanimentPlanRepository;
use App\Repository\CoachSportifRepository;
use App\Repository\NutritionnisteRepository;
use App\Repository\PatientRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
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
        private NutritionnisteRepository $nutritionnisteRepository,
        private EntityManagerInterface $em,
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

        // Verify access (patient or assigned professional)
        if (!$isPatient && !$isCoach && !$isNutritionist) {
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
        if (
            !$this->isGranted('ROLE_COACH') &&
            !$this->isGranted('ROLE_NUTRITIONNISTE')
        ) {
            throw $this->createAccessDeniedException();
        }

        $patient = $this->patientRepository->find($patientId);
        if (!$patient) {
            throw $this->createNotFoundException('Patient non trouvé');
        }

        $plan = new AccompanimentPlan();
        $plan->setPatient($patient);
        $plan->setStartDate(new \DateTimeImmutable());

        $form = $this->createForm(AccompanimentPlanType::class, $plan);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $this->em->persist($plan);
            $this->em->flush();

            $this->addFlash('success', 'Plan d\'accompagnement créé avec succès!');
            return $this->redirectToRoute('app_plan_show', ['id' => $plan->getId()]);
        }

        return $this->render('plan/create.html.twig', [
            'form' => $form,
            'patient' => $patient,
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
        $isCreator = ($plan->getCoach() && $plan->getCoach()->getUser() === $user) ||
                    ($plan->getNutritionist() && $plan->getNutritionist()->getUser() === $user);
        $isPatient = $plan->getPatient()->getUser() === $user;

        if (!$isCreator && !$isPatient) {
            throw $this->createAccessDeniedException();
        }

        $form = $this->createForm(AccompanimentPlanType::class, $plan);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $plan->setUpdatedAt(new \DateTimeImmutable());
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
        $isCreator = ($plan->getCoach() && $plan->getCoach()->getUser() === $user) ||
                    ($plan->getNutritionist() && $plan->getNutritionist()->getUser() === $user);
        $isPatient = $plan->getPatient()->getUser() === $user;

        if (!$isCreator && !$isPatient) {
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
        if (!$this->isGranted('ROLE_COACH') && !$this->isGranted('ROLE_NUTRITIONNISTE')) {
            throw $this->createAccessDeniedException();
        }

        $user = $this->getUser();
        assert($user instanceof User);

        $plans = [];

        // Get plans for coaching
        if ($this->isGranted('ROLE_COACH')) {
            $coach = $this->coachRepository->findOneBy(['user' => $user]);
            if ($coach) {
                $plans = array_merge($plans, $this->planRepository->findByCoach($coach));
            }
        }

        // Get plans for nutrition
        if ($this->isGranted('ROLE_NUTRITIONNISTE')) {
            $nutritionist = $this->nutritionnisteRepository->findOneBy(['user' => $user]);
            if ($nutritionist) {
                $plans = array_merge($plans, $this->planRepository->findByNutritionist($nutritionist));
            }
        }

        // Sort by most recent first
        usort($plans, fn($a, $b) => $b->getCreatedAt() <=> $a->getCreatedAt());

        return $this->render('plan/professional_plans.html.twig', [
            'plans' => $plans,
        ]);
    }
}
