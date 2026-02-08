<?php

namespace App\Service;

use App\Entity\ReservationMedicament;
use App\Entity\StockPharmacy;
use App\Entity\User;
use App\Repository\ReservationMedicamentRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Mime\Address;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mailer\MailerInterface;

class ReservationMedicamentService
{
    public function __construct(
        private EntityManagerInterface $em,
        private NotificationService $notificationService,
        private ReservationMedicamentRepository $repository,
        private MailerInterface $mailer
    ) {}

    public function createReservation(User $patient, StockPharmacy $stock, int $quantite = 1): ReservationMedicament
    {
        $quantite = max(1, $quantite);
        if ($stock->getQuantite() < $quantite) {
            throw new \RuntimeException('Stock insuffisant');
        }

        $reservation = new ReservationMedicament();
        $reservation->setPatient($patient);
        $reservation->setPharmacie($stock->getPharmecie());
        $reservation->setMedicament($stock->getMedicament());
        $reservation->setStock($stock);
        $reservation->setQuantite($quantite);
        $reservation->setPrixUnitaire($stock->getPrixVente());
        if ($stock->getPrixVente() !== null) {
            $reservation->setPrixTotal((string) ((float) $stock->getPrixVente() * $quantite));
        }
        $reservation->setStatut('en_attente');
        $reservation->setExpiresAt((new \DateTime())->modify('+2 hours'));

        $this->em->persist($reservation);
        $this->em->flush();

        $pharmacien = $stock->getPharmecie()->getPharmacien();
        if ($pharmacien) {
            $this->notificationService->notify(
                $pharmacien->getUser(),
                'Nouvelle réservation',
                'Une nouvelle réservation est en attente.',
                'pharmacy',
                null,
                'normal'
            );
            $this->sendEmail(
                $pharmacien->getUser()->getEmail(),
                'Nouvelle réservation en attente',
                'emails/reservation_new_pharmacien.html.twig',
                ['reservation' => $reservation]
            );
        }

        $this->notificationService->notify(
            $patient,
            'Réservation envoyée',
            'Votre réservation est en attente de confirmation.',
            'pharmacy',
            null,
            'normal'
        );

        return $reservation;
    }

    public function confirm(ReservationMedicament $reservation): void
    {
        if ($reservation->getStatut() !== 'en_attente') {
            return;
        }

        $stock = $reservation->getStock();
        if ($stock->getQuantite() < $reservation->getQuantite()) {
            throw new \RuntimeException('Stock insuffisant');
        }

        $stock->setQuantite($stock->getQuantite() - $reservation->getQuantite());
        $reservation->setStatut('confirmee');
        $reservation->setConfirmedAt(new \DateTime());
        $reservation->setUpdatedAt(new \DateTime());

        $this->em->flush();

        $pharmacien = $stock->getPharmecie()->getPharmacien();
        if ($pharmacien && $stock->getQuantite() <= 5) {
            $this->notificationService->notify(
                $pharmacien->getUser(),
                'Alerte stock faible',
                'Le stock d\'un médicament est faible.',
                'pharmacy',
                null,
                'normal'
            );
        }

        $this->notificationService->notify(
            $reservation->getPatient(),
            'Réservation confirmée',
            'Votre réservation a été confirmée.',
            'pharmacy',
            null,
            'normal'
        );
        $this->sendEmail(
            $reservation->getPatient()->getEmail(),
            'Réservation confirmée',
            'emails/reservation_status_patient.html.twig',
            ['reservation' => $reservation, 'status' => 'confirmée']
        );
    }

    public function reject(ReservationMedicament $reservation): void
    {
        if ($reservation->getStatut() !== 'en_attente') {
            return;
        }
        $reservation->setStatut('refusee');
        $reservation->setRejectedAt(new \DateTime());
        $reservation->setUpdatedAt(new \DateTime());
        $this->em->flush();

        $this->notificationService->notify(
            $reservation->getPatient(),
            'Réservation refusée',
            'Votre réservation a été refusée.',
            'pharmacy',
            null,
            'normal'
        );
        $this->sendEmail(
            $reservation->getPatient()->getEmail(),
            'Réservation refusée',
            'emails/reservation_status_patient.html.twig',
            ['reservation' => $reservation, 'status' => 'refusée']
        );
    }

    public function cancel(ReservationMedicament $reservation): void
    {
        if (!in_array($reservation->getStatut(), ['en_attente', 'confirmee'], true)) {
            return;
        }
        $reservation->setStatut('annulee');
        $reservation->setCancelledAt(new \DateTime());
        $reservation->setUpdatedAt(new \DateTime());
        $this->em->flush();
    }

    public function expirePending(): int
    {
        $now = new \DateTime();
        $reservations = $this->repository->createQueryBuilder('r')
            ->andWhere('r.statut = :s')
            ->andWhere('r.expiresAt IS NOT NULL')
            ->andWhere('r.expiresAt < :now')
            ->setParameter('s', 'en_attente')
            ->setParameter('now', $now)
            ->getQuery()
            ->getResult();

        $count = 0;
        foreach ($reservations as $reservation) {
            $reservation->setStatut('expiree');
            $reservation->setUpdatedAt(new \DateTime());
            $count++;
        }
        if ($count > 0) {
            $this->em->flush();
        }
        return $count;
    }

    private function sendEmail(string $to, string $subject, string $template, array $context = []): void
    {
        if ($to === '') {
            return;
        }
        $email = (new TemplatedEmail())
            ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($to)
            ->subject($subject)
            ->htmlTemplate($template)
            ->context($context);
        $this->mailer->send($email);
    }
}
