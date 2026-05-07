<?php

namespace App\Controller;

use App\Entity\User;
use App\Repository\AccompanimentPlanRepository;
use App\Repository\CoachSportifRepository;
use App\Repository\ConversationRepository;
use App\Repository\PatientRepository;
use App\Repository\SharedDocumentRepository;
use App\Repository\TeleconsultationRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/account', name: 'app_account')]
#[IsGranted('ROLE_USER')]
class AccountController extends AbstractController
{
    public function __construct(
        private ConversationRepository $conversationRepository,
        private TeleconsultationRepository $teleconsultationRepository,
        private SharedDocumentRepository $documentRepository,
        private AccompanimentPlanRepository $planRepository,
        private PatientRepository $patientRepository,
        private CoachSportifRepository $coachRepository,
    ) {
    }

    public function __invoke(): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $conversations = $this->conversationRepository->findUserConversations($user, 50);
        $unreadCount = $this->conversationRepository->getUnreadCount($user);

        $upcomingConsultations = $this->teleconsultationRepository->findUpcoming($user, 20);
        $ongoingConsultations = $this->teleconsultationRepository->findOngoing($user);
        $pastConsultations = $this->teleconsultationRepository->findPast($user, 20);

        $ownDocuments = $this->documentRepository->findByOwner($user, 50);
        $sharedDocuments = $this->documentRepository->findSharedWithUser($user, 50);

        $patientPlans = [];
        $coachPlans = [];

        $patient = $this->patientRepository->findOneBy(['user' => $user]);
        if ($patient) {
            $patientPlans = $this->planRepository->findByPatient($patient);
        }

        $coach = $this->coachRepository->findOneBy(['user' => $user]);
        if ($coach) {
            $coachPlans = $this->planRepository->findByCoach($coach);
        }

        return $this->render('front/account.html.twig', [
            'conversationCount' => count($conversations),
            'unreadCount' => $unreadCount,
            'upcomingConsultationCount' => count($upcomingConsultations),
            'ongoingConsultationCount' => count($ongoingConsultations),
            'pastConsultationCount' => count($pastConsultations),
            'ownDocumentCount' => count($ownDocuments),
            'sharedDocumentCount' => count($sharedDocuments),
            'patientPlanCount' => count($patientPlans),
            'coachPlanCount' => count($coachPlans),
            'nutritionistPlanCount' => 0,
            'isPatient' => $patient !== null,
            'isCoach' => $coach !== null,
            'isNutritionist' => false,
            'isMedecin' => $this->isGranted('ROLE_MEDECIN'),
        ]);
    }
}
