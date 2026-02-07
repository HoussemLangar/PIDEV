<?php

namespace App\Service;

use App\Entity\Medicament;
use App\Entity\Pharmacy;
use App\Entity\Reservation;
use App\Entity\User;
use App\Repository\ReservationRepository;
use App\Repository\StockPharmacyRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Mime\Email;

class ReservationService
{
    public function __construct(
        private ReservationRepository $reservationRepository,
        private StockPharmacyRepository $stockRepository,
        private StockService $stockService,
        private NotificationService $notificationService,
        private EntityManagerInterface $em,
        private ?MailerInterface $mailer = null
    ) {}

    public function createReservation(User $patient, Pharmacy $pharmacy, Medicament $medicament, int $quantite = 1): Reservation
    {
        $reservation = new Reservation();
        $reservation->setPatient($patient);
        $reservation->setPharmacy($pharmacy);
        $reservation->setMedicament($medicament);
        $reservation->setQuantite($quantite);
        $reservation->setStatut('pending');
        $reservation->setUpdatedAt(new \DateTimeImmutable());

        $this->reservationRepository->save($reservation);

        $this->notificationService->create(
            $pharmacy->getPharmacien()?->getUser() ?? $patient,
            'reservation',
            'Nouvelle réservation',
            'Une nouvelle réservation a été effectuée.'
        );

        return $reservation;
    }

    public function confirmReservation(Reservation $reservation, ?User $actor = null): void
    {
        if ($reservation->getStatut() !== 'pending') {
            throw new \RuntimeException('Réservation déjà traitée.');
        }
        $reservation->setStatut('confirmed');
        $reservation->setUpdatedAt(new \DateTimeImmutable());

        $stock = $this->stockRepository->findOneByPharmacyAndMedicament(
            $reservation->getPharmacy()->getId(),
            $reservation->getMedicament()->getId()
        );
        if ($stock) {
            $this->stockService->decreaseStock($stock, $reservation->getQuantite());
        }

        $this->em->flush();

        $this->notificationService->create(
            $reservation->getPatient(),
            'reservation',
            'Réservation confirmée',
            'Votre réservation a été confirmée.'
        );

        $this->sendEmail($reservation->getPatient()->getEmail(), 'Réservation confirmée', 'Votre réservation a été confirmée.');
    }

    public function refuseReservation(Reservation $reservation, ?string $commentaire = null): void
    {
        if ($reservation->getStatut() !== 'pending') {
            throw new \RuntimeException('Réservation déjà traitée.');
        }
        $reservation->setStatut('refused');
        $reservation->setCommentaire($commentaire);
        $reservation->setUpdatedAt(new \DateTimeImmutable());
        $this->em->flush();

        $this->notificationService->create(
            $reservation->getPatient(),
            'reservation',
            'Réservation refusée',
            'Votre réservation a été refusée.'
        );

        $this->sendEmail($reservation->getPatient()->getEmail(), 'Réservation refusée', 'Votre réservation a été refusée.');
    }

    private function sendEmail(?string $to, string $subject, string $content): void
    {
        if (!$this->mailer || !$to) {
            return;
        }
        $email = (new Email())
            ->to($to)
            ->subject($subject)
            ->text($content);
        $this->mailer->send($email);
    }
}
