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
        $user = $this->getUser();
        assert($user instanceof User);

        $upcoming = $this->repository->findUpcoming($user);
        $ongoing = $this->repository->findOngoing($user);
        $past = $this->repository->findPast($user);
        $stats = $this->repository->getStatistics($user);

        return $this->render('teleconsultation/index.html.twig', [
            'upcoming' => $upcoming,
            'ongoing' => $ongoing,
            'past' => $past,
            'stats' => $stats,
        ]);
    }

    /**
     * Schedule a new teleconsultation
     */
    #[Route('/schedule', name: 'schedule', methods: ['GET', 'POST'])]
    public function schedule(Request $request): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $consultation = new Teleconsultation();
        $consultation->setInitiator($user);
        
        $form = $this->createForm(TeleconsultationType::class, $consultation);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            // Generate room name
            $roomName = $this->jitsiService->generateRoomName($user, $consultation->getRecipient());
            $consultation->setRoomName($roomName);
            $consultation->setStatus('pending');

            $this->em->persist($consultation);
            $this->em->flush();

            $this->addFlash('success', 'Consultation programmée avec succès!');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        return $this->render('teleconsultation/schedule.html.twig', [
            'form' => $form,
        ]);
    }

    /**
     * View consultation details
     */
    #[Route('/{id}', name: 'show', methods: ['GET'])]
    public function show(Teleconsultation $consultation): Response
    {
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
            $consultation->setStartedAt(new \DateTimeImmutable());
            $this->em->flush();
        }

        $roomUrl = $this->jitsiService->generateRoomUrl($consultation, $user);

        return $this->render('teleconsultation/join.html.twig', [
            'consultation' => $consultation,
            'roomUrl' => $roomUrl,
        ]);
    }

    /**
     * End a teleconsultation
     */
    #[Route('/{id}/end', name: 'end', methods: ['POST'])]
    public function end(Teleconsultation $consultation): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if ($consultation->getInitiator() !== $user && $consultation->getRecipient() !== $user) {
            throw $this->createAccessDeniedException();
        }

        if (!$consultation->isOngoing()) {
            $this->addFlash('warning', 'Cette consultation n\'est pas en cours.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        $consultation->setStatus('completed');
        $consultation->setEndedAt(new \DateTimeImmutable());

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
    public function cancel(Teleconsultation $consultation): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if ($consultation->getInitiator() !== $user) {
            throw $this->createAccessDeniedException('Seul l\'initiateur peut annuler une consultation.');
        }

        if ($consultation->isCompleted() || $consultation->isCancel()) {
            $this->addFlash('warning', 'Cette consultation ne peut pas être annulée.');
            return $this->redirectToRoute('app_teleconsultation_show', ['id' => $consultation->getId()]);
        }

        $consultation->setStatus('cancelled');
        $this->em->flush();

        $this->addFlash('success', 'Consultation annulée.');
        return $this->redirectToRoute('app_teleconsultation_index');
    }

    /**
     * API: Get upcoming consultations
     */
    #[Route('/api/upcoming', name: 'api_upcoming', methods: ['GET'])]
    public function apiUpcoming(): JsonResponse
    {
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
            $now = new \DateTimeImmutable();
            $allowStart = $consultation->getScheduledAt()->modify('-5 minutes');
            return $now >= $allowStart;
        }

        return false;
    }
}
