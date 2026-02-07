<?php

namespace App\Command;

use App\Repository\ReservationRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;

#[AsCommand(
    name: 'app:reservations:expire',
    description: 'Marque les réservations en attente comme expirées après 24h.'
)]
class ExpireReservationsCommand extends Command
{
    public function __construct(
        private ReservationRepository $reservationRepository,
        private EntityManagerInterface $em
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $threshold = new \DateTimeImmutable('-24 hours');
        $qb = $this->reservationRepository->createQueryBuilder('r')
            ->andWhere('r.statut = :statut')
            ->andWhere('r.createdAt < :threshold')
            ->setParameter('statut', 'pending')
            ->setParameter('threshold', $threshold);

        $reservations = $qb->getQuery()->getResult();
        foreach ($reservations as $reservation) {
            $reservation->setStatut('expired');
            $reservation->setUpdatedAt(new \DateTimeImmutable());
        }
        $this->em->flush();

        $output->writeln(sprintf('Reservations expirées: %d', count($reservations)));
        return Command::SUCCESS;
    }
}
