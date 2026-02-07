<?php

namespace App\Command;

use App\Repository\RendezVousRepository;
use App\Service\NotificationService;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Mime\Address;

#[AsCommand(name: 'app:appointments:send-reminders')]
class SendAppointmentRemindersCommand extends Command
{
    public function __construct(
        private RendezVousRepository $rendezVousRepository,
        private NotificationService $notificationService,
        private MailerInterface $mailer
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $target = new \DateTimeImmutable('+24 hours');
        $date = $target->format('Y-m-d');

        $qb = $this->rendezVousRepository->createQueryBuilder('r')
            ->leftJoin('r.patient', 'p')
            ->leftJoin('p.user', 'u')
            ->addSelect('u')
            ->andWhere('r.dateRdv = :date')
            ->andWhere('r.statut = :statut')
            ->setParameter('date', $date)
            ->setParameter('statut', 'confirme');

        $items = $qb->getQuery()->getResult();
        foreach ($items as $rdv) {
            $user = $rdv->getPatient()->getUser();
            if (!$user->isReminderEnabled()) {
                continue;
            }

            $this->notificationService->notify(
                $user,
                'Rappel de rendez-vous',
                'Votre rendez-vous est prévu demain à ' . $rdv->getHeureRdv()->format('H:i'),
                'rdv',
                null,
                'normal'
            );

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Rappel de rendez-vous')
                ->htmlTemplate('emails/appointment_reminder.html.twig')
                ->context(['rdv' => $rdv, 'user' => $user]);
            $this->mailer->send($email);
        }

        $output->writeln('Rappels envoyés: ' . count($items));
        return Command::SUCCESS;
    }
}
