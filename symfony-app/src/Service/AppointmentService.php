<?php

namespace App\Service;

use App\Entity\Disponibilite;
use App\Entity\Medecin;
use App\Entity\Patient;
use App\Entity\RendezVous;
use App\Repository\DisponibiliteRepository;
use App\Repository\RendezVousRepository;
use Doctrine\ORM\EntityManagerInterface;

class AppointmentService
{
    public function __construct(
        private DisponibiliteRepository $disponibiliteRepository,
        private RendezVousRepository $rendezVousRepository,
        private EntityManagerInterface $em
    ) {}

    public function getAvailableSlots(Medecin $medecin, \DateTimeInterface $date): array
    {
        $qb = $this->disponibiliteRepository->createQueryBuilder('d')
            ->andWhere('d.medecin = :medecin')
            ->andWhere('d.date = :date')
            ->andWhere('d.statut = :statut')
            ->setParameter('medecin', $medecin)
            ->setParameter('date', $date->format('Y-m-d'))
            ->setParameter('statut', 'disponible')
            ->orderBy('d.heureDebut', 'ASC');

        return $qb->getQuery()->getResult();
    }

    public function book(Patient $patient, Medecin $medecin, Disponibilite $disponibilite, ?string $motif = null): RendezVous
    {
        if ($disponibilite->getRendezvous()) {
            throw new \RuntimeException('Créneau déjà réservé.');
        }

        $rdv = new RendezVous();
        $rdv->setPatient($patient);
        $rdv->setMedecin($medecin);
        $rdv->setDisponibilite($disponibilite);
        $rdv->setDateRdv($disponibilite->getDate());
        $rdv->setHeureRdv($disponibilite->getHeureDebut());
        $rdv->setMotif($motif);
        $rdv->setStatut('en_attente');

        $disponibilite->setStatut('reserve');
        $disponibilite->setRendezvous($rdv);

        $this->em->persist($rdv);
        $this->em->persist($disponibilite);
        $this->em->flush();

        return $rdv;
    }

    public function updateStatusByDoctor(RendezVous $rdv, string $status): void
    {
        $rdv->setStatut($status);

        if (in_array($status, ['refuse', 'annule'], true)) {
            $dispo = $rdv->getDisponibilite();
            if ($dispo) {
                $dispo->setStatut('disponible');
                $dispo->setRendezvous(null);
                $this->em->persist($dispo);
            }
        }

        $this->em->flush();
    }

    public function cancel(RendezVous $rdv): void
    {
        $rdv->setStatut('annule');
        $dispo = $rdv->getDisponibilite();
        if ($dispo) {
            $dispo->setStatut('disponible');
            $dispo->setRendezvous(null);
            $this->em->persist($dispo);
        }
        $this->em->flush();
    }

    public function reschedule(RendezVous $rdv, Disponibilite $newDisponibilite): void
    {
        if ($newDisponibilite->getRendezvous()) {
            throw new \RuntimeException('Créneau déjà réservé.');
        }

        $old = $rdv->getDisponibilite();
        if ($old) {
            $old->setStatut('disponible');
            $old->setRendezvous(null);
            $this->em->persist($old);
        }

        $newDisponibilite->setStatut('reserve');
        $newDisponibilite->setRendezvous($rdv);
        $rdv->setDisponibilite($newDisponibilite);
        $rdv->setDateRdv($newDisponibilite->getDate());
        $rdv->setHeureRdv($newDisponibilite->getHeureDebut());
        $rdv->setStatut('confirme');

        $this->em->persist($newDisponibilite);
        $this->em->flush();
    }
}
