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
        private MailerInterface $mailer
    ) {}

    #[Route('/doctors', name: 'doctors', methods: ['GET'])]
    public function doctors(Request $request): JsonResponse
    {
        $city = $request->query->get('city');
        $medecins = $this->medecinRepository->findByCity($city);
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
}
