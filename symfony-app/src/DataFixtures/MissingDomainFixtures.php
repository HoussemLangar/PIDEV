<?php

namespace App\DataFixtures;

use App\Entity\Medecin;
use App\Entity\Patient;
use App\Entity\SanteQuotidienne;
use App\Entity\SymptomeListe;
use App\Entity\SymptomeQuotidien;
use App\Entity\Teleconsultation;
use App\Entity\User;
use App\Entity\UserScoreHistory;
use App\Enum\Alimentation;
use App\Enum\Humeur;
use App\Enum\NiveauActivite;
use App\Enum\SanteDataSource;
use App\Enum\UserScoreSnapshotType;
use Doctrine\Bundle\FixturesBundle\Fixture;
use Doctrine\Common\DataFixtures\DependentFixtureInterface;
use Doctrine\Bundle\FixturesBundle\FixtureGroupInterface;
use Doctrine\Persistence\ObjectManager;

class MissingDomainFixtures extends Fixture implements DependentFixtureInterface, FixtureGroupInterface
{
    public function load(ObjectManager $manager): void
    {
        $patientRepo = $manager->getRepository(Patient::class);
        $medecinRepo = $manager->getRepository(Medecin::class);
        $userRepo = $manager->getRepository(User::class);
        $symptomeListeRepo = $manager->getRepository(SymptomeListe::class);
        $santeRepo = $manager->getRepository(SanteQuotidienne::class);
        $symptomeQRepo = $manager->getRepository(SymptomeQuotidien::class);
        $scoreHistoryRepo = $manager->getRepository(UserScoreHistory::class);
        $teleRepo = $manager->getRepository(Teleconsultation::class);

        $patients = $patientRepo->findAll();
        $medecins = $medecinRepo->findAll();
        $users = $userRepo->findAll();
        $symptomes = $symptomeListeRepo->findAll();

        if (count($patients) === 0 || count($users) === 0) {
            return;
        }

        $activites = NiveauActivite::cases();
        $humeurs = Humeur::cases();
        $alimentations = Alimentation::cases();

        if ($santeRepo->count([]) === 0) {
            foreach ($patients as $patientIndex => $patient) {
                $patientUser = $patient->getUser();

                for ($day = 0; $day < 7; $day++) {
                    $entry = new SanteQuotidienne();
                    $entry->setUser($patientUser);
                    $entry->setPoids((float) (58 + ($patientIndex % 12) + ($day * 0.1)));
                    $entry->setTaille((float) (160 + ($patientIndex % 22)));
                    $entry->setTensionArterielle((float) (11 + ($patientIndex % 3)));
                    $entry->setSommeil((float) (6.0 + (($day + $patientIndex) % 4)));
                    $entry->setActivitePhysique($activites[($patientIndex + $day) % count($activites)]);
                    $entry->setHumeur([$humeurs[($patientIndex + $day) % count($humeurs)]]);
                    $entry->setAlimentation($alimentations[($patientIndex + $day) % count($alimentations)]);
                    $entry->setEauBue((float) (1.4 + (($day + $patientIndex) % 3) * 0.4));
                    $entry->setPas(4500 + ($day * 900) + ($patientIndex * 120));
                    $entry->setCalories((float) (1750 + ($day * 60) + ($patientIndex * 10)));
                    $entry->setDureeActiviteMinutes(20 + (($day + $patientIndex) % 5) * 10);
                    $entry->setSourceDonnees($day % 2 === 0 ? SanteDataSource::MANUEL : SanteDataSource::GOOGLE_FIT);
                    $entry->recordDate((new \DateTime())->modify(sprintf('-%d day', 6 - $day)));
                    $manager->persist($entry);
                }
            }
        }

        if ($symptomeQRepo->count([]) === 0 && count($symptomes) > 0) {
            foreach ($patients as $index => $patient) {
                for ($i = 0; $i < 2; $i++) {
                    $item = new SymptomeQuotidien();
                    $item->setPatient($patient);
                    $item->setSymptome($symptomes[($index + $i) % count($symptomes)]);
                    $item->setDateSymptome((new \DateTime())->modify(sprintf('-%d day', $i + 1)));
                    $item->setIntensite(1 + (($index + $i) % 5));
                    $item->setDuree(['30 min', '1 heure', '2 heures'][($index + $i) % 3]);
                    $item->setNotes('Surveillance quotidienne via fixture de démonstration.');
                    $manager->persist($item);
                }
            }
        }

        if ($scoreHistoryRepo->count([]) === 0) {
            foreach ($users as $index => $user) {
                for ($day = 0; $day < 5; $day++) {
                    $activity = 16 + (($index + $day) % 10);
                    $seniority = 14 + (($index + $day) % 8);
                    $compliance = 20 + (($index + $day) % 10);
                    $sanctions = 18 + (($index + $day) % 7);
                    $total = min(100, $activity + $seniority + $compliance + $sanctions);

                    $history = new UserScoreHistory();
                    $history->setUser($user);
                    $history->setActivityScore($activity);
                    $history->setSeniorityScore($seniority);
                    $history->setRuleComplianceScore($compliance);
                    $history->setSanctionsHistoryScore($sanctions);
                    $history->setScore($total);
                    $history->setSnapshotType(UserScoreSnapshotType::DAILY);
                    $history->forceCreatedAt((new \DateTimeImmutable())->modify(sprintf('-%d day', 4 - $day)));
                    $manager->persist($history);
                }
            }
        }

        if ($teleRepo->count([]) < 5 && count($medecins) > 0) {
            $max = min(count($patients), count($medecins), 6);

            for ($i = 0; $i < $max; $i++) {
                $patientUser = $patients[$i]->getUser();
                $medecinUser = $medecins[$i % count($medecins)]->getUser();

                $tele = new Teleconsultation();
                $tele->setInitiator($patientUser);
                $tele->setRecipient($medecinUser);
                $tele->setRoomName(sprintf('tele-room-%d-%d', $patientUser->getId() ?? $i, $i + 1));
                $tele->setDescription('Consultation de suivi automatique (fixture).');
                $tele->scheduleAt((new \DateTimeImmutable())->modify(sprintf('+%d day', $i + 1)));
                $tele->setStatus($i % 2 === 0 ? 'pending' : 'requested');
                $tele->setType($i % 2 === 0 ? 'follow_up' : 'general');

                $manager->persist($tele);
            }
        }

        $manager->flush();
    }

    public function getDependencies(): array
    {
        return [
            RealisticDataFixtures::class,
            SymptomeFixtures::class,
        ];
    }

    public static function getGroups(): array
    {
        return ['missing-domain'];
    }
}
