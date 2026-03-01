<?php

namespace App\Controller;

use App\Entity\Teleconsultation;
use App\Entity\User;
use App\Form\TeleconsultationType;
use App\Repository\TeleconsultationRepository;
use App\Service\JitsiService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/teleconsultations', name: 'app_teleconsultation_')]
#[IsGranted('ROLE_USER')]
class TeleconsultationController extends AbstractController
{
    private const PROFESSIONAL_ROLES = ['ROLE_MEDECIN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE'];

    public function __construct(
        private TeleconsultationRepository $repository,
        private JitsiService $jitsiService,
        private EntityManagerInterface $em,
    ) {
    }

    /**
     * List all teleconsultations
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        $upcoming = $this->repository->findUpcoming($user);
        $ongoing = $this->repository->findOngoing($user);
        $past = $this->repository->findPast($user);
        $stats = $this->repository->getStatistics($user);
        $canStartMap = [];
        foreach (array_merge($upcoming, $ongoing) as $consultation) {
            $canStartMap[$consultation->getId()] = $this->canStartConsultation($consultation);
        }

        return $this->render('teleconsultation/index.html.twig', [
            'upcoming' => $upcoming,
            'ongoing' => $ongoing,
            'past' => $past,
            'stats' => $stats,
            'canStartMap' => $canStartMap,
        ]);
    }

    /**
     * Schedule a new teleconsultation
     */
    #[Route('/schedule', name: 'schedule', methods: ['GET', 'POST'])]
    public function schedule(Request $request): Response
    {
        if (!$this->isPatient() && !$this->isProfessional()) {
            throw $this->createAccessDeniedException();
        }

        $user = $this->getUser();
        assert($user instanceof User);

        $consultation = new Teleconsultation();
        $consultation->setInitiator($user);
        // Set a default scheduled time to avoid null value
        $consultation->scheduleAt(new \DateTimeImmutable('+1 hour'));

        $recipientRoles = $this->isPatient() ? self::PROFESSIONAL_ROLES : ['ROLE_PATIENT'];
        $form = $this->createForm(TeleconsultationType::class, $consultation, [
            'recipient_roles' => $recipientRoles,
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $recipient = $consultation->getRecipient();
            if (!$recipient || !in_array($this->getEffectiveRole($recipient), $recipientRoles, true)) {
                $this->addFlash('error', 'Vous devez sélectionner un utilisateur valide.');
                return $this->render('teleconsultation/schedule.html.twig', [
                    'form' => $form,
                    'is_patient' => $this->isPatient(),
                ]);
            }

            if (!$this->repository->isDoctorAvailable($recipient, $consultation->getScheduledAt()) && $this->isPatient()) {
                $this->addFlash('error', 'Le professionnel n\'est pas disponible à cette date et heure.');
                return $this->render('teleconsultation/schedule.html.twig', [
                    'form' => $form,
                    'is_patient' => $this->isPatient(),
                ]);
            }

            // Generate room name
            $roomName = $this->jitsiService->generateRoomName($user, $recipient);
            $consultation->setRoomName($roomName);
            $consultation->setStatus($this->isPatient() ? 'requested' : 'pending');

            $this->em->persist($consultation);
            $this->em->flush();

            $this->addFlash('success', 'Consultation programmée avec succès!');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        return $this->render('teleconsultation/schedule.html.twig', [
            'form' => $form,
            'is_patient' => $this->isPatient(),
        ]);
    }

    /**
     * View consultation details
     */
    #[Route('/{id}', name: 'show', methods: ['GET'])]
    public function show(Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        // Check access
        if ($consultation->getInitiator() !== $user && $consultation->getRecipient() !== $user) {
            throw $this->createAccessDeniedException();
        }

        $isInitiator = $consultation->getInitiator() === $user;
        $otherUser = $isInitiator ? $consultation->getRecipient() : $consultation->getInitiator();

        // Generate Jitsi room URL if ongoing
        $roomUrl = null;
        if ($consultation->isOngoing()) {
            $roomUrl = $this->jitsiService->generateRoomUrl($consultation, $user);
        }

        return $this->render('teleconsultation/show.html.twig', [
            'consultation' => $consultation,
            'isInitiator' => $isInitiator,
            'otherUser' => $otherUser,
            'roomUrl' => $roomUrl,
            'canStart' => $this->canStartConsultation($consultation),
        ]);
    }

    /**
     * Join a video room
     */
    #[Route('/{id}/join', name: 'join', methods: ['GET'])]
    public function join(Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        // Check access
        if ($consultation->getInitiator() !== $user && $consultation->getRecipient() !== $user) {
            throw $this->createAccessDeniedException();
        }

        // Check if can join
        if (!$this->canStartConsultation($consultation)) {
            throw $this->createAccessDeniedException('Cette consultation n\'est pas disponible pour le moment.');
        }

        // Update status if not started
        if (!$consultation->getStartedAt()) {
            $consultation->setStatus('ongoing');
            $consultation->startAt(new \DateTimeImmutable());
            $this->em->flush();
        }

        $roomUrl = $this->jitsiService->generateRoomUrl($consultation, $user);

        return $this->render('teleconsultation/join.html.twig', [
            'consultation' => $consultation,
            'roomUrl' => $roomUrl,
            'jitsiServerUrl' => $this->jitsiService->getServerUrl(),
            'jitsiServerDomain' => $this->jitsiService->getServerDomain(),
        ]);
    }

    /**
     * End a teleconsultation
     */
    #[Route('/{id}/end', name: 'end', methods: ['POST'])]
    public function end(Request $request, Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isCsrfTokenValid('teleconsultation_end' . $consultation->getId(), $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('Jeton CSRF invalide.');
        }

        if ($consultation->getInitiator() !== $user && $consultation->getRecipient() !== $user) {
            throw $this->createAccessDeniedException();
        }

        if (!$consultation->isOngoing()) {
            $this->addFlash('warning', 'Cette consultation n\'est pas en cours.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        $consultation->setStatus('completed');
        $consultation->endAt(new \DateTimeImmutable());

        // Calculate duration
        if ($consultation->getStartedAt()) {
            $duration = $consultation->getEndedAt()->getTimestamp() - $consultation->getStartedAt()->getTimestamp();
            $consultation->setDurationSeconds($duration);
        }

        $this->em->flush();

        $this->addFlash('success', 'Consultation terminée.');
        return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
    }

    /**
     * Cancel a consultation
     */
    #[Route('/{id}/cancel', name: 'cancel', methods: ['POST'])]
    public function cancel(Request $request, Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isCsrfTokenValid('teleconsultation_cancel' . $consultation->getId(), $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('Jeton CSRF invalide.');
        }

        if ($consultation->getInitiator() !== $user) {
            throw $this->createAccessDeniedException('Seul l\'initiateur peut annuler une consultation.');
        }

        if ($consultation->isCompleted() || $consultation->isCancel()) {
            $this->addFlash('warning', 'Cette consultation ne peut pas être annulée.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        $consultation->setStatus('cancelled');
        $consultation->endAt(new \DateTimeImmutable());
        if ($consultation->getStartedAt()) {
            $duration = $consultation->getEndedAt()->getTimestamp() - $consultation->getStartedAt()->getTimestamp();
            $consultation->setDurationSeconds($duration);
        }
        $this->em->flush();

        $this->addFlash('success', 'Consultation annulée.');
        return $this->redirectToRoute('app_teleconsultation_index');
    }

    #[Route('/{id}/approve', name: 'approve', methods: ['POST'])]
    public function approve(Request $request, Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isCsrfTokenValid('teleconsultation_approve' . $consultation->getId(), $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('Jeton CSRF invalide.');
        }

        if ($consultation->getRecipient() !== $user || !$this->isProfessional() || !$consultation->isRequested()) {
            throw $this->createAccessDeniedException();
        }

        $consultation->setStatus('pending');
        $this->em->flush();

        $this->addFlash('success', 'Demande de téléconsultation approuvée.');
        return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
    }

    #[Route('/{id}/reject', name: 'reject', methods: ['POST'])]
    public function reject(Request $request, Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        if (!$this->isCsrfTokenValid('teleconsultation_reject' . $consultation->getId(), $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('Jeton CSRF invalide.');
        }

        if ($consultation->getRecipient() !== $user || !$this->isProfessional() || !$consultation->isRequested()) {
            throw $this->createAccessDeniedException();
        }

        $consultation->setStatus('cancelled');
        $consultation->endAt(new \DateTimeImmutable());
        $this->em->flush();

        $this->addFlash('success', 'Demande de téléconsultation refusée.');
        return $this->redirectToRoute('app_teleconsultation_index');
    }

    #[Route('/{id}/reschedule', name: 'reschedule', methods: ['POST'])]
    public function reschedule(Request $request, Teleconsultation $consultation): Response
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        if ($consultation->getInitiator() !== $user && $consultation->getRecipient() !== $user) {
            throw $this->createAccessDeniedException();
        }

        if (!$this->isCsrfTokenValid('teleconsultation_reschedule' . $consultation->getId(), $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('Jeton CSRF invalide.');
        }

        $scheduledAtRaw = (string) $request->request->get('scheduled_at', '');
        if ($scheduledAtRaw === '') {
            $this->addFlash('error', 'Veuillez indiquer une nouvelle date.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        try {
            $newScheduledAt = new \DateTimeImmutable($scheduledAtRaw);
        } catch (\Exception) {
            $this->addFlash('error', 'Date invalide.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        if ($newScheduledAt <= new \DateTimeImmutable()) {
            $this->addFlash('error', 'La date doit être dans le futur.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        $professional = $this->isProfessionalRole($consultation->getInitiator()) ? $consultation->getInitiator() : $consultation->getRecipient();
        if (!$this->repository->isDoctorAvailable($professional, $newScheduledAt)) {
            $this->addFlash('error', 'Le professionnel n\'est pas disponible sur ce créneau.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        $consultation->scheduleAt($newScheduledAt);
        if ($consultation->isRequested() && $this->isProfessionalRole($user)) {
            $consultation->setStatus('pending');
        }

        $this->em->flush();
        $this->addFlash('success', 'Téléconsultation replanifiée.');
        return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
    }

    /**
     * API: Get upcoming consultations
     */
    #[Route('/api/upcoming', name: 'api_upcoming', methods: ['GET'])]
    public function apiUpcoming(): JsonResponse
    {
        $this->denyAccessUnlessTeleconsultationRole();

        $user = $this->getUser();
        assert($user instanceof User);

        $upcoming = $this->repository->findUpcoming($user, 10);

        $data = array_map(function (Teleconsultation $c) use ($user) {
            $other = $c->getInitiator() === $user ? $c->getRecipient() : $c->getInitiator();
            return [
                'id' => $c->getId(),
                'with' => $other->getUsername(),
                'scheduled' => $c->getScheduledAt()->format('Y-m-d H:i:s'),
                'type' => $c->getType(),
                'status' => $c->getStatus(),
            ];
        }, $upcoming);

        return $this->json(['consultations' => $data]);
    }

    /**
     * Check if user can start consultation
     */
    private function canStartConsultation(Teleconsultation $consultation): bool
    {
        if ($consultation->isOngoing()) {
            return true;
        }

        if ($consultation->isPending()) {
            // Allow starting 5 minutes before scheduled time
            $scheduledAt = $consultation->getScheduledAt();
            $now = new \DateTimeImmutable('now', $scheduledAt->getTimezone());
            $allowStart = $scheduledAt->modify('-5 minutes');
            return $now >= $allowStart;
        }

        return false;
    }

    private function denyAccessUnlessTeleconsultationRole(): void
    {
        if (!$this->isPatient() && !$this->isProfessional()) {
            throw $this->createAccessDeniedException();
        }
    }

    private function isPatient(): bool
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            return false;
        }

        return $this->getEffectiveRole($user) === 'ROLE_PATIENT';
    }

    private function isProfessional(): bool
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            return false;
        }

        return $this->isProfessionalRole($user);
    }

    private function isProfessionalRole(User $user): bool
    {
        return in_array($this->getEffectiveRole($user), self::PROFESSIONAL_ROLES, true);
    }

    private function getEffectiveRole(User $user): string
    {
        return $user->getSubscriptionType() ?: $user->getRole();
    }
}
