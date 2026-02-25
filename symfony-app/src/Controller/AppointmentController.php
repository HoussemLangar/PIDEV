<?php

namespace App\Controller;

use App\Entity\Disponibilite;
use App\Entity\Medecin;
use App\Entity\Patient;
use App\Entity\RendezVous;
use App\Entity\User;
use App\Repository\DisponibiliteRepository;
use App\Repository\MedecinRepository;
use App\Repository\RendezVousRepository;
use App\Service\AppointmentService;
use App\Service\Ai\AiGatewayService;
use App\Service\NotificationService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Component\Validator\Validator\ValidatorInterface;

#[Route('/api/appointments', name: 'api_appointments_')]
class AppointmentController extends AbstractController
{
    public function __construct(
        private AppointmentService $appointmentService,
        private DisponibiliteRepository $disponibiliteRepository,
        private MedecinRepository $medecinRepository,
        private RendezVousRepository $rendezVousRepository,
        private EntityManagerInterface $em,
        private NotificationService $notificationService,
        private MailerInterface $mailer,
        private AiGatewayService $aiGatewayService,
    ) {}

    #[Route('/doctors', name: 'doctors', methods: ['GET'])]
    public function doctors(Request $request): JsonResponse
    {
        $city = $request->query->get('city');
        $q = trim((string) $request->query->get('q', ''));
        $medecins = $this->medecinRepository->findByCity($city);

        if ($q !== '') {
            $qLower = mb_strtolower($q);
            $medecins = array_values(array_filter($medecins, function (Medecin $medecin) use ($qLower) {
                $user = $medecin->getUser();
                $haystack = mb_strtolower(trim(($user->getNom() ?? '') . ' ' . ($user->getPrenom() ?? '') . ' ' . ($medecin->getSpecialite() ?? '')));
                return str_contains($haystack, $qLower);
            }));
        }

        $data = [];
        foreach ($medecins as $medecin) {
            $user = $medecin->getUser();
            $data[] = [
                'id' => $medecin->getId(),
                'nom' => $user->getNom(),
                'prenom' => $user->getPrenom(),
                'specialite' => $medecin->getSpecialite(),
                'ville' => $medecin->getCabinetVille(),
                'lat' => $medecin->getCabinetLat(),
                'lng' => $medecin->getCabinetLng(),
            ];
        }

        return new JsonResponse($data);
    }

    #[Route('/slots', name: 'slots', methods: ['GET'])]
    public function slots(Request $request): JsonResponse
    {
        $medecinId = (int) $request->query->get('medecin');
        $dateStr = (string) $request->query->get('date');
        if (!$medecinId || !$dateStr) {
            return new JsonResponse(['slots' => []]);
        }

        $medecin = $this->medecinRepository->find($medecinId);
        if (!$medecin) {
            return new JsonResponse(['slots' => []]);
        }

        $date = new \DateTime($dateStr);
        $this->ensureDefaultDisponibilites($medecin, $date);
        $dispos = $this->appointmentService->getAvailableSlots($medecin, $date);

        $slots = array_map(function (Disponibilite $d) {
            return [
                'id' => $d->getId(),
                'label' => $d->getHeureDebut()->format('H:i') . ' - ' . $d->getHeureFin()->format('H:i'),
            ];
        }, $dispos);

        return new JsonResponse(['slots' => $slots]);
    }

    #[Route('/book', name: 'book', methods: ['POST'])]
    public function book(Request $request, ValidatorInterface $validator): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['success' => false, 'message' => 'Non authentifié'], 401);
        }
        if (!$this->canAccessPatient($user)) {
            return new JsonResponse(['success' => false, 'message' => 'Abonnement patient requis'], 403);
        }
        $patient = $this->ensurePatient($user);
        if (!$patient) {
            return new JsonResponse(['success' => false, 'message' => 'Accès patient requis'], 403);
        }

        $data = json_decode($request->getContent(), true) ?: [];
        $constraints = new Assert\Collection([
            'fields' => [
                'medecin_id' => new Assert\NotBlank(['message' => 'Médecin requis']),
                'dispo_id' => new Assert\NotBlank(['message' => 'Créneau requis']),
            ],
            'allowExtraFields' => true,
        ]);
        $errors = $validator->validate($data, $constraints);
        if (count($errors) > 0) {
            $messages = [];
            foreach ($errors as $error) {
                $messages[] = $error->getMessage();
            }
            return new JsonResponse([
                'success' => false,
                'message' => 'Données invalides',
                'errors' => $messages,
            ], 422);
        }

        $medecin = $this->medecinRepository->find((int) $data['medecin_id']);
        $dispo = $this->disponibiliteRepository->find((int) $data['dispo_id']);
        if (!$medecin || !$dispo) {
            return new JsonResponse(['success' => false, 'message' => 'Créneau introuvable'], 404);
        }
        if ($dispo->getMedecin()->getId() !== $medecin->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Créneau invalide pour ce médecin'], 422);
        }

        try {
            $rdv = $this->appointmentService->book($patient, $medecin, $dispo, $data['motif'] ?? null);
        } catch (\RuntimeException $e) {
            return new JsonResponse(['success' => false, 'message' => $e->getMessage()], 409);
        }

        $this->notificationService->notify($user, 'Rendez-vous en attente', 'Votre demande de rendez-vous est en attente de confirmation.', 'rdv', null, 'normal');
        $doctorUser = $medecin->getUser();
        $this->notificationService->notify($doctorUser, 'Nouvelle demande', 'Une demande de rendez-vous est en attente.', 'rdv', null, 'normal');

        $confirmUrl = $this->generateUrl('app_doctor_confirm', ['id' => $rdv->getId()], \Symfony\Component\Routing\Generator\UrlGeneratorInterface::ABSOLUTE_URL);
        $rescheduleUrl = $this->generateUrl('app_doctor_reschedule', ['id' => $rdv->getId()], \Symfony\Component\Routing\Generator\UrlGeneratorInterface::ABSOLUTE_URL);
        $email = (new TemplatedEmail())
            ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($doctorUser->getEmail())
            ->subject('Nouvelle demande de rendez-vous')
            ->htmlTemplate('emails/appointment_request_doctor.html.twig')
            ->context([
                'rdv' => $rdv,
                'medecin' => $medecin,
                'patient' => $user,
                'confirm_url' => $confirmUrl,
                'reschedule_url' => $rescheduleUrl,
            ]);
        $this->mailer->send($email);

        return new JsonResponse(['success' => true]);
    }

    #[Route('/my', name: 'my', methods: ['GET'])]
    public function myAppointments(): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['items' => []]);
        }
        if (!$this->canAccessPatient($user)) {
            return new JsonResponse(['items' => []]);
        }
        $patient = $this->ensurePatient($user);
        if (!$patient) {
            return new JsonResponse(['items' => []]);
        }

        $items = [];
        $rdvs = $this->rendezVousRepository->createQueryBuilder('r')
            ->leftJoin('r.patient', 'p')
            ->addSelect('p')
            ->andWhere('p.id = :pid')
            ->setParameter('pid', $patient->getId())
            ->orderBy('r.dateRdv', 'ASC')
            ->getQuery()
            ->getResult();
        foreach ($rdvs as $rdv) {
            $items[] = [
                'id' => $rdv->getId(),
                'medecin' => $rdv->getMedecin()->getUser()->getFullName() ?? $rdv->getMedecin()->getUser()->getEmail(),
                'date' => $rdv->getDateRdv()->format('d/m/Y'),
                'heure' => $rdv->getHeureRdv()->format('H:i'),
                'statut' => $rdv->getStatut(),
            ];
        }
        return new JsonResponse(['items' => $items]);
    }

    #[Route('/{id}/cancel', name: 'cancel', methods: ['POST'])]
    public function cancel(int $id): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['success' => false], 403);
        }
        if (!$this->canAccessPatient($user)) {
            return new JsonResponse(['success' => false, 'message' => 'Abonnement patient requis'], 403);
        }
        $patient = $this->ensurePatient($user);
        if (!$patient) {
            return new JsonResponse(['success' => false], 403);
        }
        $rdv = $this->rendezVousRepository->find($id);
        if (!$rdv || $rdv->getPatient()->getId() !== $patient->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Introuvable'], 404);
        }
        $this->appointmentService->cancel($rdv);
        $this->notificationService->notify($user, 'Rendez-vous annulé', 'Votre rendez-vous a été annulé.', 'rdv', null, 'normal');
        $this->notificationService->notify($rdv->getMedecin()->getUser(), 'Rendez-vous annulé', 'Un rendez-vous a été annulé.', 'rdv', null, 'normal');
        return new JsonResponse(['success' => true, 'message' => 'Rendez-vous annulé']);
    }

    #[Route('/{id}/reschedule', name: 'reschedule', methods: ['POST'])]
    public function reschedule(int $id, Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['success' => false], 403);
        }
        if (!$this->canAccessPatient($user)) {
            return new JsonResponse(['success' => false, 'message' => 'Abonnement patient requis'], 403);
        }
        $patient = $this->ensurePatient($user);
        if (!$patient) {
            return new JsonResponse(['success' => false], 403);
        }
        $rdv = $this->rendezVousRepository->find($id);
        if (!$rdv || $rdv->getPatient()->getId() !== $patient->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Introuvable'], 404);
        }

        $data = json_decode($request->getContent(), true) ?: [];
        $dispo = $this->disponibiliteRepository->find((int) ($data['dispo_id'] ?? 0));
        if (!$dispo) {
            return new JsonResponse(['success' => false, 'message' => 'Créneau introuvable'], 404);
        }
        if ($dispo->getMedecin()->getId() !== $rdv->getMedecin()->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Créneau invalide pour ce médecin'], 422);
        }
        try {
            $this->appointmentService->reschedule($rdv, $dispo);
        } catch (\RuntimeException $e) {
            return new JsonResponse(['success' => false, 'message' => $e->getMessage()], 409);
        }

        $this->notificationService->notify($user, 'Rendez-vous replanifié', 'Votre rendez-vous a été replanifié.', 'rdv', null, 'normal');
        $this->notificationService->notify($rdv->getMedecin()->getUser(), 'Rendez-vous replanifié', 'Un rendez-vous a été replanifié.', 'rdv', null, 'normal');
        return new JsonResponse(['success' => true]);
    }

    #[Route('/{id}/patient-reschedule', name: 'patient_reschedule', methods: ['POST'])]
    public function patientReschedule(int $id, Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['success' => false], 403);
        }
        if (!$this->canAccessPatient($user)) {
            return new JsonResponse(['success' => false, 'message' => 'Abonnement patient requis'], 403);
        }
        $patient = $this->ensurePatient($user);
        if (!$patient) {
            return new JsonResponse(['success' => false], 403);
        }
        $rdv = $this->rendezVousRepository->find($id);
        if (!$rdv || $rdv->getPatient()->getId() !== $patient->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Introuvable'], 404);
        }

        $data = json_decode($request->getContent(), true) ?: [];
        $date = (string) ($data['date'] ?? '');
        $time = (string) ($data['time'] ?? '');
        if (!$date || !$time) {
            return new JsonResponse(['success' => false, 'message' => 'Date et heure requises'], 422);
        }

        $medecin = $rdv->getMedecin();
        $dispo = $this->disponibiliteRepository->createQueryBuilder('d')
            ->andWhere('d.medecin = :m')->setParameter('m', $medecin)
            ->andWhere('d.date = :d')->setParameter('d', new \DateTime($date))
            ->andWhere('d.heureDebut = :h')->setParameter('h', new \DateTime($date . ' ' . $time))
            ->getQuery()->getOneOrNullResult();

        if (!$dispo) {
            $dispo = new Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date));
            $dispo->setHeureDebut(new \DateTime($date . ' ' . $time));
            $dispo->setHeureFin((new \DateTime($date . ' ' . $time))->modify('+30 minutes'));
            $dispo->setStatut('disponible');
            $this->em->persist($dispo);
            $this->em->flush();
        }

        if ($dispo->getRendezvous() && $dispo->getRendezvous()->getId() !== $rdv->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Ce créneau est déjà réservé'], 409);
        }

        $this->appointmentService->reschedule($rdv, $dispo);
        // Override to en_attente so the doctor must approve
        $rdv->setStatut('en_attente');
        $this->em->flush();

        $doctorUser = $medecin->getUser();
        $this->notificationService->notify($doctorUser, 'Replanification demandée', 'Un patient demande une replanification. Veuillez confirmer ou refuser.', 'rdv', null, 'normal');
        $this->notificationService->notify($user, 'Replanification envoyée', 'Votre demande de replanification est en attente d\'approbation du médecin.', 'rdv', null, 'normal');

        return new JsonResponse(['success' => true]);
    }

    #[Route('/doctor', name: 'doctor', methods: ['GET'])]
    public function doctorAppointments(): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return new JsonResponse(['items' => []]);
        }
        if (!$this->canAccessMedecin($user)) {
            return new JsonResponse(['items' => []], 403);
        }
        $medecin = $user->getMedecin();
        if (!$medecin) {
            return new JsonResponse(['items' => []], 403);
        }

        $items = [];
        $rdvs = $this->rendezVousRepository->createQueryBuilder('r')
            ->leftJoin('r.medecin', 'm')
            ->addSelect('m')
            ->andWhere('m.id = :mid')
            ->setParameter('mid', $medecin->getId())
            ->orderBy('r.dateRdv', 'ASC')
            ->getQuery()
            ->getResult();
        foreach ($rdvs as $rdv) {
            $patientUser = $rdv->getPatient()->getUser();
            $items[] = [
                'id' => $rdv->getId(),
                'patient' => $patientUser->getFullName() ?? $patientUser->getEmail(),
                'date' => $rdv->getDateRdv()->format('d/m/Y'),
                'heure' => $rdv->getHeureRdv()->format('H:i'),
                'motif' => $rdv->getMotif() ?? '',
                'statut' => $rdv->getStatut(),
            ];
        }
        return new JsonResponse(['items' => $items]);
    }

    public function doctorStatus(int $id, Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User || !$this->canAccessMedecin($user)) {
            return new JsonResponse(['success' => false], 403);
        }
        $medecin = $user->getMedecin();
        if (!$medecin) {
            return new JsonResponse(['success' => false], 403);
        }

        $rdv = $this->rendezVousRepository->find($id);
        if (!$rdv || $rdv->getMedecin()->getId() !== $medecin->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Introuvable'], 404);
        }

        $data = json_decode($request->getContent(), true) ?: [];
        $status = $data['status'] ?? '';
        if (!in_array($status, ['confirme', 'refuse', 'annule'], true)) {
            return new JsonResponse(['success' => false, 'message' => 'Statut invalide'], 422);
        }

        $this->appointmentService->updateStatusByDoctor($rdv, $status);

        $patientUser = $rdv->getPatient()->getUser();
        if ($status === 'confirme') {
            $this->notificationService->notify($patientUser, 'Rendez-vous confirmé', 'Votre rendez-vous a été confirmé par le médecin.', 'rdv', null, 'normal');
            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($patientUser->getEmail())
                ->subject('Rendez-vous confirmé')
                ->htmlTemplate('emails/appointment_accepted.html.twig')
                ->context(['rdv' => $rdv, 'user' => $patientUser, 'medecin' => $medecin]);
            $this->mailer->send($email);
        } else {
            $this->notificationService->notify($patientUser, 'Rendez-vous refusé', 'Votre rendez-vous a été refusé par le médecin.', 'rdv', null, 'normal');
            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($patientUser->getEmail())
                ->subject('Rendez-vous refusé')
                ->htmlTemplate('emails/appointment_refused.html.twig')
                ->context(['rdv' => $rdv, 'user' => $patientUser, 'medecin' => $medecin]);
            $this->mailer->send($email);
        }

        return new JsonResponse(['success' => true]);
    }

    #[Route('/{id}/doctor-reschedule', name: 'doctor_reschedule', methods: ['POST'])]
    public function doctorReschedule(int $id, Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User || !$this->canAccessMedecin($user)) {
            return new JsonResponse(['success' => false], 403);
        }
        $medecin = $user->getMedecin();
        if (!$medecin) {
            return new JsonResponse(['success' => false], 403);
        }

        $rdv = $this->rendezVousRepository->find($id);
        if (!$rdv || $rdv->getMedecin()->getId() !== $medecin->getId()) {
            return new JsonResponse(['success' => false, 'message' => 'Introuvable'], 404);
        }

        $data = json_decode($request->getContent(), true) ?: [];
        $date = (string) ($data['date'] ?? '');
        $time = (string) ($data['time'] ?? '');
        if (!$date || !$time) {
            return new JsonResponse(['success' => false, 'message' => 'Date et heure requises'], 422);
        }

        $dispo = $this->disponibiliteRepository->createQueryBuilder('d')
            ->andWhere('d.medecin = :m')->setParameter('m', $medecin)
            ->andWhere('d.date = :d')->setParameter('d', new \DateTime($date))
            ->andWhere('d.heureDebut = :h')->setParameter('h', new \DateTime($date . ' ' . $time))
            ->getQuery()->getOneOrNullResult();

        if (!$dispo) {
            $dispo = new Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date));
            $dispo->setHeureDebut(new \DateTime($date . ' ' . $time));
            $dispo->setHeureFin((new \DateTime($date . ' ' . $time))->modify('+30 minutes'));
            $dispo->setStatut('disponible');
            $this->em->persist($dispo);
            $this->em->flush();
        }

        if ($dispo->getRendezvous()) {
            return new JsonResponse(['success' => false, 'message' => 'Créneau déjà réservé'], 409);
        }

        $this->appointmentService->reschedule($rdv, $dispo);

        $patientUser = $rdv->getPatient()->getUser();
        $this->notificationService->notify($patientUser, 'Rendez-vous replanifié', 'Votre rendez-vous a été replanifié.', 'rdv', null, 'normal');

        return new JsonResponse(['success' => true]);
    }

    #[Route('/voice-intent', name: 'voice_intent', methods: ['POST'])]
    public function voiceIntent(Request $request): JsonResponse
    {
        try {
            /** @var User $user */
            $user = $this->getUser();
            if (!$user instanceof User) {
                return new JsonResponse(['success' => false, 'message' => 'Non authentifié'], 401);
            }
            if (!$this->canAccessPatient($user)) {
                return new JsonResponse(['success' => false, 'message' => 'Abonnement patient requis'], 403);
            }
            $patient = $this->ensurePatient($user);
            if (!$patient) {
                return new JsonResponse(['success' => false, 'message' => 'Accès patient requis'], 403);
            }

            $data = json_decode($request->getContent(), true) ?: [];
            $rawText = trim((string) ($data['text'] ?? ''));
            if ($rawText === '') {
                return new JsonResponse(['success' => false, 'message' => 'Commande vocale vide'], 422);
            }

            $aiExtraction = $this->extractVoiceIntentWithAi($rawText);
            $normalizedText = $this->normalizeVoiceText($rawText);
            $date = null;
            if (is_array($aiExtraction) && isset($aiExtraction['date']) && is_string($aiExtraction['date'])) {
                $date = $this->parseIsoDate($aiExtraction['date']);
            }
            $date ??= $this->extractDateFromVoiceText($normalizedText);
            if (!$date) {
                return new JsonResponse([
                    'success' => false,
                    'message' => 'Date non détectée. Exemple: 24/02/2026',
                ], 422);
            }

            $doctorHint = $this->extractDoctorNameFromVoiceText($normalizedText);
            if (is_array($aiExtraction) && isset($aiExtraction['doctor_query']) && is_string($aiExtraction['doctor_query'])) {
                $doctorHint = trim($aiExtraction['doctor_query']) !== '' ? trim($aiExtraction['doctor_query']) : $doctorHint;
            }
            $doctor = $this->matchDoctorFromVoiceText($doctorHint ?: $normalizedText);
            if (!$doctor) {
                return new JsonResponse([
                    'success' => false,
                    'message' => 'Médecin non trouvé. Essayez de préciser le nom complet.',
                ], 404);
            }

            $bookingDate = new \DateTime($date->format('Y-m-d'));
            $this->ensureDefaultDisponibilites($doctor, $bookingDate);
            $slots = $this->appointmentService->getAvailableSlots($doctor, $bookingDate);
            if (count($slots) === 0) {
                return new JsonResponse([
                    'success' => false,
                    'message' => 'Aucun créneau disponible à cette date pour ce médecin.',
                    'doctor_id' => $doctor->getId(),
                    'doctor_label' => $doctor->getUser()->getNom() . ' ' . $doctor->getUser()->getPrenom() . ' · ' . $doctor->getSpecialite(),
                    'date' => $date->format('Y-m-d'),
                ], 409);
            }

            $requestedTime = null;
            if (is_array($aiExtraction) && isset($aiExtraction['requested_time']) && is_string($aiExtraction['requested_time'])) {
                $requestedTime = $this->normalizeAiTime($aiExtraction['requested_time']);
            }
            $requestedTime ??= $this->extractTimeFromVoiceText($normalizedText, $date);
            $selectedSlot = $this->pickVoiceSlot($slots, $requestedTime);
            if (!$selectedSlot) {
                return new JsonResponse([
                    'success' => false,
                    'message' => 'Impossible de sélectionner un créneau.',
                ], 409);
            }

            /** @var Disponibilite $selectedSlot */

            try {
                $rdv = $this->appointmentService->book($patient, $doctor, $selectedSlot, 'Commande vocale: ' . mb_substr($rawText, 0, 180));
            } catch (\RuntimeException $e) {
                return new JsonResponse(['success' => false, 'message' => $e->getMessage()], 409);
            }

            $doctorUser = $doctor->getUser();
            $this->notificationService->notify($user, 'Rendez-vous en attente', 'Votre demande de rendez-vous vocale est en attente de confirmation.', 'rdv', null, 'normal');
            $this->notificationService->notify($doctorUser, 'Nouvelle demande', 'Une demande vocale de rendez-vous est en attente.', 'rdv', null, 'normal');

            $confirmUrl = $this->generateUrl('app_doctor_confirm', ['id' => $rdv->getId()], \Symfony\Component\Routing\Generator\UrlGeneratorInterface::ABSOLUTE_URL);
            $rescheduleUrl = $this->generateUrl('app_doctor_reschedule', ['id' => $rdv->getId()], \Symfony\Component\Routing\Generator\UrlGeneratorInterface::ABSOLUTE_URL);
            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($doctorUser->getEmail())
                ->subject('Nouvelle demande de rendez-vous')
                ->htmlTemplate('emails/appointment_request_doctor.html.twig')
                ->context([
                    'rdv' => $rdv,
                    'medecin' => $doctor,
                    'patient' => $user,
                    'confirm_url' => $confirmUrl,
                    'reschedule_url' => $rescheduleUrl,
                ]);
            $this->mailer->send($email);

            return new JsonResponse([
                'success' => true,
                'message' => 'Commande vocale comprise. Rendez-vous créé avec succès.',
                'doctor_id' => $doctor->getId(),
                'doctor_label' => $doctorUser->getNom() . ' ' . $doctorUser->getPrenom() . ' · ' . $doctor->getSpecialite(),
                'date' => $date->format('Y-m-d'),
                'requested_time' => $requestedTime,
                'slot_id' => $selectedSlot->getId(),
                'slot_label' => $selectedSlot->getHeureDebut()->format('H:i') . ' - ' . $selectedSlot->getHeureFin()->format('H:i'),
                'appointment_id' => $rdv->getId(),
            ]);
        } catch (\Throwable $exception) {
            return new JsonResponse([
                'success' => false,
                'message' => 'Erreur interne pendant l\'analyse vocale.',
                'debug' => $exception->getMessage(),
            ], 500);
        }
    }

    private function ensurePatient(User $user): ?Patient
    {
        if ($user->getPatient()) {
            return $user->getPatient();
        }

        $role = $user->getRole();
        if ($this->canAccessPatient($user) && ($role === 'ROLE_PATIENT' || $user->getSubscriptionType() === 'ROLE_PATIENT')) {
            $patient = new Patient();
            $patient->setUser($user);
            $this->em->persist($patient);
            $this->em->flush();
            return $patient;
        }

        return null;
    }

    private function canAccessPatient(User $user): bool
    {
        $type = $user->getSubscriptionType() ?: $user->getRole();
        return $user->getSubscriptionStatus() === 'ACTIVE' && $type === 'ROLE_PATIENT';
    }

    private function canAccessMedecin(User $user): bool
    {
        $type = $user->getSubscriptionType() ?: $user->getRole();
        return $user->getSubscriptionStatus() === 'ACTIVE' && $type === 'ROLE_MEDECIN';
    }

    private function ensureDefaultDisponibilites(Medecin $medecin, \DateTimeInterface $date): void
    {
        $existing = $this->disponibiliteRepository->findBy([
            'medecin' => $medecin,
            'date' => $date,
        ]);
        if ($existing) {
            return;
        }

        $start = new \DateTime($date->format('Y-m-d') . ' 09:00:00');
        $end = new \DateTime($date->format('Y-m-d') . ' 17:00:00');
        $cursor = clone $start;
        while ($cursor < $end) {
            $slotEnd = (clone $cursor)->modify('+30 minutes');
            $dispo = new Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date->format('Y-m-d')));
            $dispo->setHeureDebut(new \DateTime($cursor->format('Y-m-d H:i:s')));
            $dispo->setHeureFin(new \DateTime($slotEnd->format('Y-m-d H:i:s')));
            $dispo->setStatut('disponible');
            $this->em->persist($dispo);
            $cursor = $slotEnd;
        }
        $this->em->flush();
    }

    private function normalizeVoiceText(string $text): string
    {
        $arabicDigits = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩', '٫', '،'];
        $latinDigits = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ','];
        $text = str_replace($arabicDigits, $latinDigits, $text);
        $text = str_replace(['أ', 'إ', 'آ', 'ٱ'], 'ا', $text);
        $text = str_replace(['ى'], 'ي', $text);
        $text = str_replace(['ة'], 'ه', $text);
        $text = str_replace(['ـ'], '', $text);
        $text = preg_replace('/\s+/u', ' ', $text) ?? $text;
        return trim(mb_strtolower($text));
    }

    /**
     * @return array{date?:string,doctor_query?:string,requested_time?:string}|null
     */
    private function extractVoiceIntentWithAi(string $rawText): ?array
    {
        if (!$this->aiGatewayService->isEnabled()) {
            return null;
        }

        $payload = $this->aiGatewayService->askForJson(
            'Tu extrais les informations de prise de rendez-vous médical depuis une commande vocale (fr/ar dialecte). Reponds STRICTEMENT en JSON: {"date":"YYYY-MM-DD|", "doctor_query":"nom du medecin ou specialite|", "requested_time":"HH:MM|"}. Si inconnu, laisse chaine vide.',
            'Commande vocale: ' . $rawText
        );

        if (!is_array($payload)) {
            return null;
        }

        return [
            'date' => isset($payload['date']) ? trim((string) $payload['date']) : '',
            'doctor_query' => isset($payload['doctor_query']) ? trim((string) $payload['doctor_query']) : '',
            'requested_time' => isset($payload['requested_time']) ? trim((string) $payload['requested_time']) : '',
        ];
    }

    private function parseIsoDate(string $value): ?\DateTimeImmutable
    {
        $value = trim($value);
        if ($value === '') {
            return null;
        }

        if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $value)) {
            return null;
        }

        $date = \DateTimeImmutable::createFromFormat('Y-m-d', $value);
        if (!$date) {
            return null;
        }

        return $date;
    }

    private function normalizeAiTime(string $value): ?string
    {
        $value = trim($value);
        if ($value === '') {
            return null;
        }

        if (preg_match('/^(\d{1,2})[:h](\d{2})$/i', $value, $m)) {
            $hours = (int) $m[1];
            $minutes = (int) $m[2];
            if ($hours >= 0 && $hours <= 23 && $minutes >= 0 && $minutes <= 59) {
                return sprintf('%02d:%02d', $hours, $minutes);
            }
        }

        return null;
    }

    private function extractDateFromVoiceText(string $text): ?\DateTimeImmutable
    {
        $today = new \DateTimeImmutable('today');

        if (preg_match('/\b(lyoum|today|aujourd\'hui|اليوم)\b/u', $text)) {
            return $today;
        }
        if (preg_match('/\b(ghodwa|ghodwaa|ghodwa|demain|tomorrow|غدوه|غدوه|غدا)\b/u', $text)) {
            return $today->modify('+1 day');
        }
        if (preg_match('/\b(baad\s*ghodwa|apres\s*demain|after\s*tomorrow|بعد\s*غدوه|بعد\s*غدوه)\b/u', $text)) {
            return $today->modify('+2 day');
        }

        if (preg_match('/\b(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4})\b/u', $text, $m)) {
            $day = (int) $m[1];
            $month = (int) $m[2];
            $year = (int) $m[3];
            if (checkdate($month, $day, $year)) {
                return new \DateTimeImmutable(sprintf('%04d-%02d-%02d', $year, $month, $day));
            }
        }

        if (preg_match('/\b(\d{4})[\/\-.](\d{1,2})[\/\-.](\d{1,2})\b/u', $text, $m)) {
            $year = (int) $m[1];
            $month = (int) $m[2];
            $day = (int) $m[3];
            if (checkdate($month, $day, $year)) {
                return new \DateTimeImmutable(sprintf('%04d-%02d-%02d', $year, $month, $day));
            }
        }

        if (preg_match('/\b(\d{1,2})[\/\-.](\d{1,2})\b/u', $text, $m)) {
            $day = (int) $m[1];
            $month = (int) $m[2];
            $year = (int) $today->format('Y');
            if (checkdate($month, $day, $year)) {
                $candidate = new \DateTimeImmutable(sprintf('%04d-%02d-%02d', $year, $month, $day));
                if ($candidate < $today) {
                    $nextYear = $year + 1;
                    if (checkdate($month, $day, $nextYear)) {
                        return new \DateTimeImmutable(sprintf('%04d-%02d-%02d', $nextYear, $month, $day));
                    }
                }
                return $candidate;
            }
        }

        // ── Arabic ordinals → digit replacement ──────────────────────────────
        $ordinals = [
            'الحادي\s+والثلاثين' => '31', 'الثلاثين'  => '30', 'الثلاثون' => '30',
            'التاسع\s+والعشرين'  => '29', 'الثامن\s+والعشرين' => '28',
            'السابع\s+والعشرين'  => '27', 'السادس\s+والعشرين' => '26',
            'الخامس\s+والعشرين'  => '25', 'الرابع\s+والعشرين'  => '24',
            'الثالث\s+والعشرين'  => '23', 'الثاني\s+والعشرين'  => '22',
            'الحادي\s+والعشرين'  => '21', 'العشرين' => '20', 'العشرون' => '20',
            'التاسع\s+عشر' => '19', 'الثامن\s+عشر'  => '18',
            'السابع\s+عشر' => '17', 'السادس\s+عشر'  => '16',
            'الخامس\s+عشر' => '15', 'الرابع\s+عشر'   => '14',
            'الثالث\s+عشر' => '13', 'الثاني\s+عشر'   => '12',
            'الحادي\s+عشر' => '11', 'العاشر'          => '10',
            'التاسع' => '9', 'الثامن'  => '8', 'السابع' => '7',
            'السادس' => '6', 'الخامس'  => '5', 'الرابع' => '4',
            'الثالث' => '3', 'الثاني'  => '2', 'الأول'  => '1', 'الاول' => '1',
        ];
        foreach ($ordinals as $pattern => $digit) {
            $text = preg_replace('/\b' . $pattern . '\b/u', $digit, $text) ?? $text;
        }

        $monthNames = [
            'janvier' => 1, 'janv' => 1, 'january' => 1, 'jan' => 1,
            'جانفي' => 1, 'جانف' => 1,
            'fevrier' => 2, 'février' => 2, 'fev' => 2, 'fév' => 2, 'february' => 2, 'feb' => 2,
            'fivri' => 2, 'fivry' => 2, 'fevri' => 2, 'fivrih' => 2,
            'fevriy' => 2, 'fivriy' => 2, 'fivry' => 2, 'fefri' => 2, 'febre' => 2,
            'فيفري' => 2, 'فيفرى' => 2, 'فيفريه' => 2,
            'فيفي'  => 2, 'فيفا'  => 2, 'فيفر' => 2, 'فيف' => 2,
            'mars' => 3, 'march' => 3, 'mar' => 3,
            'مارس' => 3, 'مارص' => 3,
            'avril' => 4, 'april' => 4, 'avr' => 4,
            'افريل' => 4, 'أفريل' => 4, 'افريلا' => 4,
            'mai' => 5, 'may' => 5,
            'ماي' => 5, 'ماييو' => 5,
            'juin' => 6, 'june' => 6, 'jun' => 6,
            'جوان' => 6, 'جون' => 6,
            'juillet' => 7, 'july' => 7, 'juil' => 7,
            'جويلية' => 7, 'جوليه' => 7, 'جويليه' => 7, 'جويلي' => 7,
            'aout' => 8, 'août' => 8, 'august' => 8, 'aug' => 8,
            'اوت' => 8, 'أوت' => 8,
            'septembre' => 9, 'september' => 9, 'sep' => 9, 'sept' => 9,
            'سبتمبر' => 9, 'سبتمبار' => 9,
            'octobre' => 10, 'october' => 10, 'oct' => 10,
            'اكتوبر' => 10, 'أكتوبر' => 10,
            'novembre' => 11, 'november' => 11, 'nov' => 11,
            'نوفمبر' => 11, 'نوفمبار' => 11,
            'decembre' => 12, 'décembre' => 12, 'december' => 12, 'dec' => 12, 'déc' => 12,
            'ديسمبر' => 12, 'ديسمبار' => 12,
        ];

        // ── Pattern 1 : "24 فيفي [2026]" ─────────────────────────────────────
        if (preg_match('/\b(\d{1,2})\s+([\p{L}]+)(?:\s+(\d{4}))?\b/u', $text, $m)) {
            $day = (int) $m[1];
            $monthRawLabel    = $this->normalizeVoiceText((string) $m[2]);
            $monthSimpleLabel = $this->simplifyForMatch((string) $m[2]);
            $year = isset($m[3]) && $m[3] !== '' ? (int) $m[3] : (int) $today->format('Y');
            $month = $this->resolveMonthFromWord($monthNames, $monthRawLabel, $monthSimpleLabel);
            if ($month !== null && checkdate($month, $day, $year)) {
                return new \DateTimeImmutable(sprintf('%04d-%02d-%02d', $year, $month, $day));
            }
        }

        // ── Pattern 2 : "مت فيفري" / "فيفري العاشر(→10)" with ordinals already replaced ─
        if (preg_match('/\b([\p{L}]+)\s+(\d{1,2})(?:\s+(\d{4}))?\b/u', $text, $m)) {
            $monthRawLabel    = $this->normalizeVoiceText((string) $m[1]);
            $monthSimpleLabel = $this->simplifyForMatch((string) $m[1]);
            $day  = (int) $m[2];
            $year = isset($m[3]) && $m[3] !== '' ? (int) $m[3] : (int) $today->format('Y');
            $month = $this->resolveMonthFromWord($monthNames, $monthRawLabel, $monthSimpleLabel);
            if ($month !== null && checkdate($month, $day, $year)) {
                return new \DateTimeImmutable(sprintf('%04d-%02d-%02d', $year, $month, $day));
            }
        }

        return null;
    }

    /** Resolve month number from a spoken word using exact + simplified + fuzzy matching. */
    private function resolveMonthFromWord(array $monthNames, string $rawLabel, string $simpleLabel): ?int
    {
        // Exact match
        if (isset($monthNames[$rawLabel]))   return (int) $monthNames[$rawLabel];
        if (isset($monthNames[$simpleLabel])) return (int) $monthNames[$simpleLabel];

        // Fuzzy: Levenshtein ≤ 2 on all simplified keys (avoid very short keys like 'fev' causing false hits)
        $best = null;
        $bestDist = 99;
        foreach ($monthNames as $key => $num) {
            $keySimple = $this->simplifyForMatch($key);
            if (mb_strlen($keySimple) < 3) continue;
            $dist = levenshtein($simpleLabel, $keySimple);
            if ($dist < $bestDist && $dist <= 2) {
                $bestDist = $dist;
                $best = $num;
            }
        }
        if ($best !== null) return (int) $best;

        return null;
    }

    private function extractDoctorNameFromVoiceText(string $text): ?string
    {
        if (!preg_match('/(?:docteur|doctor|dr\.?|medecin|médecin|الدكتور|دكتور|طبيب)\s+([^\d,،.;]+)/iu', $text, $m)) {
            if (!preg_match('/(?:avec|ma3|m3a|مع)\s+([^\d,،.;]+)/iu', $text, $m2)) {
                return null;
            }
            $m = $m2;
        }

        $value = trim((string) $m[1]);
        $value = preg_replace('/\b(le|la|el|fi|avec|ma3|m3a|مع|نهار|بتاريخ|date|nhar|fel|fil)\b/iu', ' ', $value) ?? $value;
        $value = preg_replace('/\s+/u', ' ', $value) ?? $value;

        return trim($value);
    }

    private function matchDoctorFromVoiceText(string $hint): ?Medecin
    {
        $needleRaw = $this->normalizeVoiceText($hint);
        if ($needleRaw === '') {
            return null;
        }
        $needleSimple = $this->simplifyForMatch($needleRaw);
        $needleTransliterated = $this->transliterateArabicToLatin($needleRaw);

        $needleCandidates = array_values(array_unique(array_filter([
            $needleRaw,
            $needleSimple,
            $needleTransliterated,
        ])));

        foreach ($this->expandDoctorAliases($needleCandidates) as $alias) {
            $needleCandidates[] = $alias;
        }
        $needleCandidates = array_values(array_unique(array_filter($needleCandidates)));

        $doctors = $this->medecinRepository->findAll();
        $best = null;
        $bestScore = -1;

        foreach ($doctors as $doctor) {
            $user = $doctor->getUser();
            $fullName = trim(($user->getNom() ?? '') . ' ' . ($user->getPrenom() ?? ''));
            $haystackRaw = $this->normalizeVoiceText(trim($fullName . ' ' . ($doctor->getSpecialite() ?? '')));
            $haystackSimple = $this->simplifyForMatch($haystackRaw);
            $haystackTransliterated = $this->transliterateArabicToLatin($haystackRaw);

            $score = 0;
            foreach ($needleCandidates as $candidate) {
                $candidateSimple = $this->simplifyForMatch($candidate);
                if ($candidate === $haystackRaw || ($candidateSimple !== '' && $candidateSimple === $haystackSimple)) {
                    $score += 120;
                }
                if (str_contains($haystackRaw, $candidate)) {
                    $score += 70;
                }
                if ($candidateSimple !== '' && str_contains($haystackSimple, $candidateSimple)) {
                    $score += 70;
                }
                if ($candidateSimple !== '' && str_contains($haystackTransliterated, $candidateSimple)) {
                    $score += 55;
                }
            }

            $rawTokens = array_filter(explode(' ', preg_replace('/\s+/u', ' ', $needleRaw) ?? $needleRaw));
            foreach ($rawTokens as $token) {
                if (mb_strlen($token) < 2) {
                    continue;
                }
                if (str_contains($haystackRaw, $token)) {
                    $score += 10;
                }
            }

            $simpleTokens = [];
            foreach ($needleCandidates as $candidate) {
                $candidateSimple = $this->simplifyForMatch($candidate);
                if ($candidateSimple === '') {
                    continue;
                }
                foreach (array_filter(explode(' ', preg_replace('/\s+/u', ' ', $candidateSimple) ?? $candidateSimple)) as $token) {
                    $simpleTokens[] = $token;
                }
            }
            $simpleTokens = array_values(array_unique($simpleTokens));
            $haystackWords = array_filter(explode(' ', $haystackSimple));
            foreach ($simpleTokens as $token) {
                if (strlen($token) < 2) {
                    continue;
                }
                if (str_contains($haystackSimple, $token)) {
                    $score += 10;
                    continue;
                }
                if (strlen($token) < 4) {
                    continue;
                }
                foreach ($haystackWords as $word) {
                    if (strlen($word) < 4) {
                        continue;
                    }
                    $distance = levenshtein($token, $word);
                    if ($distance <= 1) {
                        $score += 7;
                        break;
                    }
                    if ($distance === 2) {
                        $score += 4;
                    }
                }
            }

            if ($score > 0 && str_contains($haystackRaw, 'houssem') && str_contains($needleRaw, 'houssem')) {
                $score += 6;
            }

            if ($score > $bestScore) {
                $best = $doctor;
                $bestScore = $score;
            }
        }

        return $bestScore > 0 ? $best : null;
    }

    private function simplifyForMatch(string $value): string
    {
        $value = trim(mb_strtolower($value));
        if (function_exists('iconv')) {
            $converted = @iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $value);
            if ($converted !== false) {
                $value = strtolower($converted);
            }
        }
        $value = preg_replace('/[^a-z0-9\s]/', ' ', $value) ?? $value;
        $value = preg_replace('/\s+/', ' ', $value) ?? $value;
        return trim($value);
    }

    private function extractTimeFromVoiceText(string $text, ?\DateTimeInterface $detectedDate = null): ?string
    {
        $hour = null;
        $minute = 0;

        // Tunisian / Arabic spoken shortcuts
        if (preg_match('/\b(نص\s*النهار\s*(?:و\s*نص|ونص)?)\b/u', $text, $m)) {
            return str_contains($m[1], 'نص') && preg_match('/(و\s*نص|ونص)/u', $m[1]) ? '12:30' : '12:00';
        }
        if (preg_match('/\b(نص\s*الليل\s*(?:و\s*نص|ونص)?)\b/u', $text, $m)) {
            return str_contains($m[1], 'نص') && preg_match('/(و\s*نص|ونص)/u', $m[1]) ? '00:30' : '00:00';
        }

        if (preg_match('/\b(\d{1,2})\s*[:h]\s*(\d{2})\b/u', $text, $m)) {
            $hour = (int) $m[1];
            $minute = (int) $m[2];
        } elseif (preg_match('/\b(\d{1,2})\s*[:h]\s*(\d{2})\s*(am|pm|matin|morning|soir|apres\s*midi|after\s*noon|صباح|صباحا|مساء|العشيه|العشية)\b/u', $text, $m)) {
            $hour = (int) $m[1];
            $minute = (int) $m[2];
        } elseif (preg_match('/\b(\d{1,2})\s*(am|pm|matin|morning|soir|apres\s*midi|after\s*noon|صباح|صباحا|مساء|العشيه|العشية)\b/u', $text, $m)) {
            $hour = (int) $m[1];
            $minute = 0;
        } elseif (preg_match('/\b(\d{1,2})\s*(?:و\s*نص|ونص|et\s*demi|half)\b/u', $text, $m)) {
            $hour = (int) $m[1];
            $minute = 30;
        } elseif (preg_match('/(?:\b(heure|sa3a|saa|clock|الساعة)\b)\s*(\d{1,2})(?::(\d{2}))?/u', $text, $m)) {
            $hour = (int) $m[2];
            $minute = isset($m[3]) && $m[3] !== '' ? (int) $m[3] : 0;
        } elseif (
            $detectedDate instanceof \DateTimeInterface
            && preg_match('/\b(\d{1,2})\s+(\d{1,2})\s+([\p{L}]+)\b/u', $text, $m)
        ) {
            // Example: "مع دكتور حسام 8 24 فيفري" => first number is hour, second+word is date
            $candidateHour = (int) $m[1];
            $candidateDay = (int) $m[2];
            $detectedDay = (int) $detectedDate->format('d');
            if ($candidateDay === $detectedDay) {
                $hour = $candidateHour;
                $minute = 0;
            }
        }

        if ($hour === null) {
            return null;
        }

        if ($hour > 24 || $minute > 59) {
            return null;
        }

        $isPm = (bool) preg_match('/\b(pm|soir|apres\s*midi|after\s*noon|مساء|العشيه|العشية)\b/u', $text);
        $isAm = (bool) preg_match('/\b(am|matin|morning|صباح|صباحا)\b/u', $text);

        if ($isPm && $hour >= 1 && $hour <= 11) {
            $hour += 12;
        }
        if ($isAm && $hour === 12) {
            $hour = 0;
        }

        if ($hour === 24) {
            $hour = 0;
        }

        return sprintf('%02d:%02d', $hour, $minute);
    }

    /**
     * @param Disponibilite[] $slots
     */
    private function pickVoiceSlot(array $slots, ?string $requestedTime): ?Disponibilite
    {
        $validSlots = array_values(array_filter($slots, static function ($slot): bool {
            return $slot instanceof Disponibilite
                && $slot->getStatut() === 'disponible'
                && $slot->getRendezvous() === null;
        }));

        if (count($validSlots) === 0) {
            return null;
        }
        if (!$requestedTime) {
            return $validSlots[0];
        }

        $targetMinutes = $this->timeToMinutes($requestedTime);
        if ($targetMinutes === null) {
            return $validSlots[0];
        }

        $best = null;
        $bestGap = PHP_INT_MAX;
        foreach ($validSlots as $slot) {
            $slotMinutes = (int) $slot->getHeureDebut()->format('H') * 60 + (int) $slot->getHeureDebut()->format('i');
            $gap = abs($slotMinutes - $targetMinutes);
            if ($gap < $bestGap) {
                $best = $slot;
                $bestGap = $gap;
            }
        }

        return $best ?? $validSlots[0];
    }

    private function timeToMinutes(string $time): ?int
    {
        if (!preg_match('/^(\d{2}):(\d{2})$/', $time, $m)) {
            return null;
        }
        $hour = (int) $m[1];
        $minute = (int) $m[2];
        if ($hour > 23 || $minute > 59) {
            return null;
        }
        return $hour * 60 + $minute;
    }

    private function transliterateArabicToLatin(string $value): string
    {
        $map = [
            'ا' => 'a', 'ب' => 'b', 'ت' => 't', 'ث' => 'th', 'ج' => 'j', 'ح' => 'h', 'خ' => 'kh',
            'د' => 'd', 'ذ' => 'dh', 'ر' => 'r', 'ز' => 'z', 'س' => 's', 'ش' => 'sh', 'ص' => 's',
            'ض' => 'd', 'ط' => 't', 'ظ' => 'z', 'ع' => 'a', 'غ' => 'gh', 'ف' => 'f', 'ق' => 'q',
            'ك' => 'k', 'ل' => 'l', 'م' => 'm', 'ن' => 'n', 'ه' => 'h', 'و' => 'w', 'ي' => 'y',
            'ء' => '', 'ؤ' => 'w', 'ئ' => 'y', 'ى' => 'a', 'ة' => 'a', ' ' => ' ',
        ];

        $result = strtr($value, $map);
        $result = preg_replace('/\s+/', ' ', $result) ?? $result;
        return trim($this->simplifyForMatch($result));
    }

    /**
     * @param string[] $candidates
     * @return string[]
     */
    private function expandDoctorAliases(array $candidates): array
    {
        $aliases = [];
        $joined = ' ' . implode(' ', $candidates) . ' ';

        $rules = [
            ' hsam ' => ['houssem', 'hossam', 'houssam'],
            ' hsamh ' => ['houssem', 'hossam', 'houssam'],
            ' hossam ' => ['houssem', 'houssam'],
            ' housam ' => ['houssem', 'hossam'],
            ' doctor ' => ['docteur'],
            ' docteur ' => ['doctor'],
        ];

        foreach ($rules as $pattern => $values) {
            if (!str_contains($joined, $pattern)) {
                continue;
            }
            foreach ($values as $value) {
                $aliases[] = $value;
            }
        }

        return array_values(array_unique($aliases));
    }
}
