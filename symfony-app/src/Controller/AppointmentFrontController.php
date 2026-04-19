<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Doctrine\ORM\EntityManagerInterface;
use App\Entity\RendezVous;
use App\Entity\User;
use App\Service\AppointmentService;
use App\Service\NotificationService;

class AppointmentFrontController extends AbstractController
{
    #[Route('/appointments', name: 'app_appointments')]
    #[Route('/rendez-vous', name: 'app_rendez_vous')]
    public function index(): Response
    {
        return $this->render('front/appointments/index.html.twig');
    }

    #[Route('/appointments/doctor/confirm/{id}', name: 'app_doctor_confirm')]
    public function confirmByDoctor(
        int $id,
        EntityManagerInterface $em,
        AppointmentService $appointmentService,
        NotificationService $notificationService,
        MailerInterface $mailer
    ): Response {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User || !$user->getMedecin()) {
            $this->addFlash('error', 'Accès refusé.');
            return $this->redirectToRoute('app_appointments');
        }

        $rdv = $em->getRepository(RendezVous::class)->find($id);
        if (!$rdv || $rdv->getMedecin()->getId() !== $user->getMedecin()->getId()) {
            $this->addFlash('error', 'Rendez-vous introuvable.');
            return $this->redirectToRoute('app_appointments');
        }

        $appointmentService->updateStatusByDoctor($rdv, 'confirme');
        $patientUser = $rdv->getPatient()->getUser();
        $notificationService->notify($patientUser, 'Rendez-vous confirmé', 'Votre rendez-vous a été confirmé par le médecin.', 'rdv', null, 'normal');

        $email = (new TemplatedEmail())
            ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($patientUser->getEmail())
            ->subject('Rendez-vous confirmé')
            ->htmlTemplate('emails/appointment_accepted.html.twig')
            ->context(['rdv' => $rdv, 'user' => $patientUser, 'medecin' => $user->getMedecin()]);
        $mailer->send($email);

        $this->addFlash('success', 'Rendez-vous confirmé.');
        return $this->redirectToRoute('app_appointments');
    }

    #[Route('/appointments/doctor/reschedule/{id}', name: 'app_doctor_reschedule')]
    public function rescheduleByDoctor(int $id): RedirectResponse
    {
        $this->addFlash('info', 'Sélectionnez un nouveau créneau pour replanifier.');
        return $this->redirectToRoute('app_appointments', ['doctor_reschedule' => $id]);
    }
}
