<?php

namespace App\DataFixtures;

use App\Entity\Abonnement;
use App\Entity\Accompagnement;
use App\Entity\AccompanimentPlan;
use App\Entity\ArticleScore;
use App\Entity\Clinique;
use App\Entity\Contenu;
use App\Entity\DocumentAccess;
use App\Entity\GoogleFitAccount;
use App\Entity\JournalItem;
use App\Entity\Like;
use App\Entity\Medecin;
use App\Entity\Medicament;
use App\Entity\Nutritionniste;
use App\Entity\PartageAnalyse;
use App\Entity\Patient;
use App\Entity\Pharmacien;
use App\Entity\Pharmacy;
use App\Entity\PlanExercice;
use App\Entity\PlanRegime;
use App\Entity\RendezVous;
use App\Entity\RapportAnalyse;
use App\Entity\RapportMedical;
use App\Entity\ReponseMedecin;
use App\Entity\ReponseMedicament;
use App\Entity\ReservationMedicament;
use App\Entity\SanteQuotidienne;
use App\Entity\SharedDocument;
use App\Entity\StockPharmacy;
use App\Entity\SymptomeListe;
use App\Entity\SymptomeQuotidien;
use App\Entity\Teleconsultation;
use App\Entity\User;
use App\Entity\Commentaire;
use App\Enum\Alimentation;
use App\Enum\Humeur;
use App\Enum\NiveauActivite;
use App\Enum\SanteDataSource;
use Doctrine\Bundle\FixturesBundle\Fixture;
use Doctrine\Bundle\FixturesBundle\FixtureGroupInterface;
use Doctrine\Common\DataFixtures\DependentFixtureInterface;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\Persistence\ObjectManager;

class AllEntitiesFixtures extends Fixture implements DependentFixtureInterface, FixtureGroupInterface
{
    public function load(ObjectManager $manager): void
    {
        $users = $manager->getRepository(User::class)->findAll();
        $patients = $manager->getRepository(Patient::class)->findAll();
        $medecins = $manager->getRepository(Medecin::class)->findAll();
        $pharmaciens = $manager->getRepository(Pharmacien::class)->findAll();
        $nutritionnistes = $manager->getRepository(Nutritionniste::class)->findAll();
        $abonnements = $manager->getRepository(Abonnement::class)->findAll();
        $medicaments = $this->seedMedicaments($manager);
        $symptomesListe = $manager->getRepository(SymptomeListe::class)->findAll();

        if (count($users) === 0) {
            return;
        }

        $this->seedCliniques($manager);
        $pharmacies = $this->seedPharmacies($manager, $pharmaciens);
        $this->seedGoogleFitAccounts($manager, $users);

        $accompagnements = $this->seedAccompagnements($manager, $abonnements);
        $plans = $this->seedAccompanimentPlans($manager, $patients, $nutritionnistes);

        $this->seedPlansExercices($manager, $patients, $accompagnements, $plans);
        $this->seedPlansRegimes($manager, $patients, $accompagnements, $plans);

        $this->seedJournalItems($manager, $patients);
        $this->seedRapportsAnalyses($manager, $patients, $medecins);
        $this->seedRapportsMedicaux($manager, $patients, $medecins);

        $partages = $this->seedPartagesAnalyses($manager, $patients, $accompagnements);
        $this->seedReponsesMedecin($manager, $partages, $medecins);
        $this->seedReponsesMedicaments($manager, $users, $medicaments);

        $documents = $this->seedSharedDocuments($manager, $users);
        $this->seedDocumentAccesses($manager, $documents, $users);

        $this->seedSanteQuotidienneFallback($manager, $patients);
        $this->seedSymptomesQuotidiensFallback($manager, $patients, $symptomesListe);
        $this->seedTeleconsultationsFallback($manager, $patients, $medecins);
        $this->seedRendezVousFallback($manager, $patients, $medecins);

        $this->seedStockPharmacyFallback($manager, $pharmacies, $medicaments);
        $this->seedReservationsFallback($manager, $users, $pharmacies, $medicaments);
        $this->seedArticleScores($manager);
        $this->seedCommentairesFallback($manager, $users);
        $this->seedLikesFallback($manager, $users);

        $manager->flush();
    }

    private function seedCliniques(ObjectManager $manager): void
    {
        if ($manager->getRepository(Clinique::class)->findOneBy([]) !== null) {
            return;
        }

        $rows = [
            ['Clinique El Amal', '12 Rue de Marseille, Tunis', '+216 71 200 111', 'contact@elamal.tn'],
            ['Clinique Les Jasmins', '4 Avenue des Fleurs, Sousse', '+216 73 220 222', 'info@jasmins.tn'],
            ['Clinique Ibn Sina', '55 Rue Habib Bourguiba, Sfax', '+216 74 330 333', 'accueil@ibnsina.tn'],
        ];

        foreach ($rows as [$nom, $adresse, $telephone, $email]) {
            $clinique = new Clinique();
            $clinique->setNom($nom);
            $clinique->setAdresse($adresse);
            $clinique->setTelephone($telephone);
            $clinique->setEmail($email);
            $clinique->setHorairesOuverture('Lun-Sam 08:00-20:00');
            $manager->persist($clinique);
        }
    }

    private function seedPharmacies(ObjectManager $manager, array $pharmaciens): array
    {
        $repo = $manager->getRepository(Pharmacy::class);
        if ($repo->findOneBy([]) !== null) {
            return $repo->findAll();
        }

        if (count($pharmaciens) === 0) {
            return [];
        }

        foreach ($pharmaciens as $index => $pharmacien) {
            $pharmacy = new Pharmacy();
            $pharmacy->setPharmacien($pharmacien);
            $pharmacy->setNom(sprintf('Pharmacie %s', $pharmacien->getUser()->getNom()));
            $pharmacy->setAdresse(sprintf('%d Avenue principale, %s', 10 + $index, ['Tunis', 'Sousse', 'Sfax'][$index % 3]));
            $pharmacy->setTelephone(sprintf('+216 7%d%d%d%d%d%d%d', $index, $index + 1, $index + 2, $index + 3, $index + 4, $index + 5, $index + 6));
            $pharmacy->setEmail(sprintf('pharma.%d@santea.tn', $index + 1));
            $pharmacy->setHoraires('08:00-22:00');
            $pharmacy->setLatitude((string) (36.80 + ($index * 0.01)));
            $pharmacy->setLongitude((string) (10.17 + ($index * 0.01)));
            $manager->persist($pharmacy);
        }

        $manager->flush();

        return $repo->findAll();
    }

    private function seedGoogleFitAccounts(ObjectManager $manager, array $users): void
    {
        if ($manager->getRepository(GoogleFitAccount::class)->findOneBy([]) !== null) {
            return;
        }

        $max = min(5, count($users));
        for ($i = 0; $i < $max; $i++) {
            $account = new GoogleFitAccount();
            $account->setUser($users[$i]);
            $account->setGoogleAccountId('gf-' . ($users[$i]->getId() ?? $i + 1));
            $account->setAccessToken('demo-access-token-' . ($i + 1));
            $account->setRefreshToken('demo-refresh-token-' . ($i + 1));
            $account->updateTokenExpiration((new \DateTimeImmutable())->modify('+30 days'));
            $account->markLastSyncAt(new \DateTimeImmutable('-1 day'));
            $account->setScopes('fitness.activity.read fitness.body.read');
            $manager->persist($account);
        }
    }

    private function seedMedicaments(ObjectManager $manager): array
    {
        $repo = $manager->getRepository(Medicament::class);
        if ($repo->findOneBy([]) !== null) {
            return $repo->findAll();
        }

        $rows = [
            ['Paracetamol 500', 'antalgique', 'Comprimé', '500mg', '4.50', 'Lab Pharma'],
            ['Ibuprofene 400', 'anti_inflammatoire', 'Comprimé', '400mg', '6.80', 'MediLab'],
            ['Amoxicilline', 'antibiotique', 'Gélule', '1g', '12.00', 'BioCare'],
            ['Vitamine C', 'supplement', 'Comprimé effervescent', '1000mg', '8.20', 'NutriHealth'],
            ['Omeprazole', 'digestif', 'Gélule', '20mg', '9.90', 'PharmaPlus'],
            ['Loratadine', 'allergie', 'Comprimé', '10mg', '7.30', 'Allergo'],
        ];

        foreach ($rows as $index => [$nom, $type, $forme, $dosage, $prix, $lab]) {
            $medicament = new Medicament();
            $medicament->setNom($nom);
            $medicament->setType($type);
            $medicament->setDescription('Médicament de démonstration pour seed complet des entités.');
            $medicament->setForme($forme);
            $medicament->setDosage($dosage);
            $medicament->setPrix($prix);
            $medicament->setStock(80 + ($index * 10));
            $medicament->setLaboratoire($lab);
            $medicament->setCodeBarre('TN-MED-' . str_pad((string) ($index + 1), 6, '0', STR_PAD_LEFT));
            $manager->persist($medicament);
        }

        $manager->flush();

        return $repo->findAll();
    }

    private function seedAccompagnements(ObjectManager $manager, array $abonnements): array
    {
        $repo = $manager->getRepository(Accompagnement::class);
        if ($repo->findOneBy([]) !== null) {
            return $repo->findAll();
        }

        foreach ($abonnements as $index => $abonnement) {
            $accompagnement = new Accompagnement();
            $accompagnement->setAbonnement($abonnement);
            $accompagnement->setNom('Programme personnalisé ' . ($index + 1));
            $accompagnement->setDescription('Plan de suivi global: activité, nutrition, prévention et objectifs hebdomadaires.');
            $accompagnement->setDateDebut((new \DateTime())->modify('-' . (15 + $index) . ' days'));
            $accompagnement->setDateFin((new \DateTime())->modify('+' . (20 + $index) . ' days'));
            $accompagnement->setTypeAccompagnement('global');
            $accompagnement->setStatut('en_cours');
            $accompagnement->forceCreatedAt(new \DateTime());
            $accompagnement->forceUpdatedAt(new \DateTime());
            $manager->persist($accompagnement);
        }

        $manager->flush();

        return $repo->findAll();
    }

    private function seedAccompanimentPlans(ObjectManager $manager, array $patients, array $nutritionnistes): array
    {
        $repo = $manager->getRepository(AccompanimentPlan::class);
        if ($repo->findOneBy([]) !== null) {
            return $repo->findAll();
        }

        $coachs = $manager->getRepository(\App\Entity\CoachSportif::class)->findAll();
        if (count($patients) === 0) {
            return [];
        }

        foreach ($patients as $index => $patient) {
            $plan = new AccompanimentPlan();
            $plan->setPatient($patient);
            $plan->setCoach($coachs[$index % max(1, count($coachs))] ?? null);
            $plan->setNutritionist($nutritionnistes[$index % max(1, count($nutritionnistes))] ?? null);
            $plan->setTitle('Plan Accompagnement #' . ($index + 1));
            $plan->setObjectives('Améliorer les habitudes de vie, augmenter l’activité physique, stabiliser les indicateurs de santé.');
            $plan->setDescription('Plan défini automatiquement pour les tests d’intégration et les démonstrations.');
            $plan->setStatus('active');
            $plan->setStartDate((new \DateTimeImmutable())->modify('-' . (7 + $index) . ' days'));
            $plan->setEndDate((new \DateTimeImmutable())->modify('+' . (40 + $index) . ' days'));
            $plan->setDurationWeeks(12);
            $manager->persist($plan);
        }

        $manager->flush();

        return $repo->findAll();
    }

    private function seedPlansExercices(ObjectManager $manager, array $patients, array $accompagnements, array $plans): void
    {
        if ($manager->getRepository(PlanExercice::class)->findOneBy([]) !== null || count($patients) === 0) {
            return;
        }

        foreach ($patients as $index => $patient) {
            $ex = new PlanExercice();
            $ex->setPatient($patient);
            $ex->setAccompagnement($accompagnements[$index % max(1, count($accompagnements))] ?? null);
            $ex->setPlan($plans[$index % max(1, count($plans))] ?? null);
            $ex->setTitre('Routine cardio #' . ($index + 1));
            $ex->setDescription('Marche active + exercices de mobilité.');
            $ex->setFrequence('3 fois/semaine');
            $ex->setDureMinutes(40);
            $ex->setNiveau('modere');
            $ex->setObjectifs('Améliorer endurance et récupération.');
            $ex->forceCreatedAt(new \DateTime());
            $ex->forceUpdatedAt(new \DateTime());
            $manager->persist($ex);
        }
    }

    private function seedPlansRegimes(ObjectManager $manager, array $patients, array $accompagnements, array $plans): void
    {
        if ($manager->getRepository(PlanRegime::class)->findOneBy([]) !== null || count($patients) === 0) {
            return;
        }

        foreach ($patients as $index => $patient) {
            $diet = new PlanRegime();
            $diet->setPatient($patient);
            $diet->setAccompagnement($accompagnements[$index % max(1, count($accompagnements))] ?? null);
            $diet->setPlan($plans[$index % max(1, count($plans))] ?? null);
            $diet->setTitre('Régime équilibré #' . ($index + 1));
            $diet->setDescription('Répartition équilibrée glucides/protéines/lipides.');
            $diet->setTypeRegime('equilibre');
            $diet->setObjectif('Stabilité métabolique');
            $diet->setRestrictions('Limiter sucres rapides et sodas.');
            $diet->setCaloriesJour(1900 + ($index % 4) * 120);
            $diet->forceCreatedAt(new \DateTime());
            $diet->forceUpdatedAt(new \DateTime());
            $manager->persist($diet);
        }
    }

    private function seedJournalItems(ObjectManager $manager, array $patients): void
    {
        if ($manager->getRepository(JournalItem::class)->findOneBy([]) !== null || count($patients) === 0) {
            return;
        }

        foreach ($patients as $index => $patient) {
            for ($j = 0; $j < 2; $j++) {
                $item = new JournalItem();
                $item->setPatient($patient);
                $item->setTitre($j === 0 ? 'Suivi tension' : 'Suivi glycémie');
                $item->setDescription('Mesure enregistrée automatiquement pour démonstration.');
                $item->setType($j === 0 ? 'tension' : 'glycemie');
                $item->setDateJournal((new \DateTime())->modify('-' . ($j + 1) . ' day'));
                $item->setHeure(new \DateTime('08:' . str_pad((string) (10 + $index), 2, '0', STR_PAD_LEFT) . ':00'));
                $item->setValeur($j === 0 ? (string) (11 + ($index % 3)) : (string) (0.85 + (($index % 5) * 0.1)));
                $item->setUnite($j === 0 ? 'cmHg' : 'g/L');
                $item->setHumeur(['calme', 'neutre', 'heureux'][$index % 3]);
                $manager->persist($item);
            }
        }
    }

    private function seedRapportsAnalyses(ObjectManager $manager, array $patients, array $medecins): void
    {
        if ($manager->getRepository(RapportAnalyse::class)->findOneBy([]) !== null || count($patients) === 0) {
            return;
        }

        foreach ($patients as $index => $patient) {
            $rapport = new RapportAnalyse();
            $rapport->setPatient($patient);
            $rapport->setMedecin($medecins[$index % max(1, count($medecins))] ?? null);
            $rapport->setTypeAnalyse(['NFS', 'Glycémie', 'Bilan lipidique'][$index % 3]);
            $rapport->setDateAnalyse((new \DateTime())->modify('-' . (3 + $index) . ' days'));
            $rapport->setLaboratoire('Laboratoire Central');
            $rapport->setResultats('Résultats dans les intervalles de référence, contrôle recommandé dans 3 mois.');
            $rapport->setFichierPath('/documents/analyses/rapport-' . ($index + 1) . '.pdf');
            $rapport->forceCreatedAt(new \DateTime());
            $rapport->forceUpdatedAt(new \DateTime());
            $manager->persist($rapport);
        }
    }

    private function seedRapportsMedicaux(ObjectManager $manager, array $patients, array $medecins): void
    {
        if ($manager->getRepository(RapportMedical::class)->findOneBy([]) !== null || count($patients) === 0 || count($medecins) === 0) {
            return;
        }

        foreach ($patients as $index => $patient) {
            $rapport = new RapportMedical();
            $rapport->setPatient($patient);
            $rapport->setMedecin($medecins[$index % count($medecins)]);
            $rapport->setTitre('Consultation de suivi #' . ($index + 1));
            $rapport->setDiagnostic('État stable, amélioration progressive des indicateurs.');
            $rapport->setTraitement('Poursuite du traitement actuel et activité physique régulière.');
            $rapport->setObservations('Aucune alerte majeure. Revoir dans 4 semaines.');
            $rapport->setDateRapport((new \DateTime())->modify('-' . (2 + $index) . ' days'));
            $rapport->setFichierPath('/documents/rapports/medical-' . ($index + 1) . '.pdf');
            $rapport->forceCreatedAt(new \DateTime());
            $rapport->forceUpdatedAt(new \DateTime());
            $manager->persist($rapport);
        }
    }

    private function seedPartagesAnalyses(ObjectManager $manager, array $patients, array $accompagnements): array
    {
        $repo = $manager->getRepository(PartageAnalyse::class);
        if ($repo->findOneBy([]) !== null) {
            return $repo->findAll();
        }

        foreach ($patients as $index => $patient) {
            $partage = new PartageAnalyse();
            $partage->setPatient($patient);
            $partage->setAccompagnement($accompagnements[$index % max(1, count($accompagnements))] ?? null);
            $partage->setTitre('Partage analyse #' . ($index + 1));
            $partage->setDescription('Résultats transmis pour avis médical.');
            $partage->setFichierUrl('/documents/analyses/shared-' . ($index + 1) . '.pdf');
            $partage->markSharedAt((new \DateTime())->modify('-' . ($index % 5) . ' day'));
            $partage->forceCreatedAt(new \DateTime());
            $manager->persist($partage);
        }

        $manager->flush();

        return $repo->findAll();
    }

    private function seedReponsesMedecin(ObjectManager $manager, array $partages, array $medecins): void
    {
        if ($manager->getRepository(ReponseMedecin::class)->findOneBy([]) !== null || count($partages) === 0 || count($medecins) === 0) {
            return;
        }

        foreach ($partages as $index => $partage) {
            $rep = new ReponseMedecin();
            $rep->setPartage($partage);
            $rep->setMedecin($medecins[$index % count($medecins)]);
            $rep->setReponse('Analyse reçue. Ajustement léger recommandé et contrôle dans 2 semaines.');
            $rep->markResponseDate(new \DateTime('-' . ($index % 3) . ' day'));
            $rep->forceCreatedAt(new \DateTime());
            $manager->persist($rep);
        }
    }

    private function seedReponsesMedicaments(ObjectManager $manager, array $users, array $medicaments): void
    {
        if ($manager->getRepository(ReponseMedicament::class)->findOneBy([]) !== null || count($users) === 0 || count($medicaments) === 0) {
            return;
        }

        $max = min(10, count($users));
        for ($i = 0; $i < $max; $i++) {
            $rm = new ReponseMedicament();
            $rm->setUser($users[$i]);
            $rm->setMedicament($medicaments[$i % count($medicaments)]);
            $rm->setQuestion('Comment prendre ce médicament en toute sécurité ?');
            $rm->setReponse('Respecter la posologie indiquée et éviter l’automédication prolongée.');
            $rm->setStatut('repondu');
            $rm->markQuestionDate(new \DateTime('-' . ($i + 1) . ' days'));
            $rm->markResponseDate(new \DateTime('-' . max(0, $i) . ' days'));
            $manager->persist($rm);
        }
    }

    private function seedSharedDocuments(ObjectManager $manager, array $users): array
    {
        $repo = $manager->getRepository(SharedDocument::class);
        if ($repo->findOneBy([]) !== null) {
            return $repo->findAll();
        }

        $types = ['analysis', 'prescription', 'report'];
        $max = min(8, count($users));

        for ($i = 0; $i < $max; $i++) {
            $doc = new SharedDocument();
            $doc->setOwner($users[$i]);
            $doc->setFileName('document_' . ($i + 1) . '.pdf');
            $doc->setFilePath('/uploads/documents/document_' . ($i + 1) . '.pdf');
            $doc->setMimeType('application/pdf');
            $doc->setFileSize(120000 + ($i * 2000));
            $doc->setFileContent($this->createBlobStream('PDF-DUMMY-CONTENT-' . ($i + 1)));
            $doc->setDescription('Document de démonstration pour tests de partage.');
            $doc->setDocumentType($types[$i % count($types)]);
            $doc->setPublic($i % 4 === 0);
            $manager->persist($doc);
        }

        $manager->flush();

        return $repo->findAll();
    }

    private function seedDocumentAccesses(ObjectManager $manager, array $documents, array $users): void
    {
        if ($manager->getRepository(DocumentAccess::class)->findOneBy([]) !== null || count($documents) === 0 || count($users) < 2) {
            return;
        }

        foreach ($documents as $index => $document) {
            $recipient = $users[($index + 1) % count($users)];
            if ($recipient === $document->getOwner()) {
                $recipient = $users[($index + 2) % count($users)];
            }

            $access = new DocumentAccess($document, $recipient);
            $access->setPermission($index % 2 === 0 ? 'view' : 'download');
            $access->expireAt((new \DateTimeImmutable())->modify('+20 days'));
            $access->setAccessCount($index % 3);
            $manager->persist($access);
        }
    }

    private function seedSanteQuotidienneFallback(ObjectManager $manager, array $patients): void
    {
        if ($manager->getRepository(SanteQuotidienne::class)->findOneBy([]) !== null || count($patients) === 0) {
            return;
        }

        $activites = NiveauActivite::cases();
        $humeurs = Humeur::cases();
        $alimentations = Alimentation::cases();

        foreach ($patients as $index => $patient) {
            $entry = new SanteQuotidienne();
            $entry->setUser($patient->getUser());
            $entry->setPoids(65 + ($index % 8));
            $entry->setTaille(168 + ($index % 10));
            $entry->setTensionArterielle(12);
            $entry->setSommeil(7.0);
            $entry->setActivitePhysique($activites[$index % count($activites)]);
            $entry->setHumeur([$humeurs[$index % count($humeurs)]]);
            $entry->setAlimentation($alimentations[$index % count($alimentations)]);
            $entry->setEauBue(1.8);
            $entry->setPas(7000 + ($index * 150));
            $entry->setCalories(2000 + ($index * 15));
            $entry->setDureeActiviteMinutes(35);
            $entry->setSourceDonnees(SanteDataSource::MANUEL);
            $entry->recordDate(new \DateTime('-1 day'));
            $manager->persist($entry);
        }
    }

    private function seedSymptomesQuotidiensFallback(ObjectManager $manager, array $patients, array $symptomesListe): void
    {
        if ($manager->getRepository(SymptomeQuotidien::class)->findOneBy([]) !== null || count($patients) === 0 || count($symptomesListe) === 0) {
            return;
        }

        foreach ($patients as $index => $patient) {
            $symptome = new SymptomeQuotidien();
            $symptome->setPatient($patient);
            $symptome->setSymptome($symptomesListe[$index % count($symptomesListe)]);
            $symptome->setDateSymptome(new \DateTime('-1 day'));
            $symptome->setIntensite(2 + ($index % 3));
            $symptome->setDuree('1 heure');
            $symptome->setNotes('Observation de contrôle pour fixture fallback.');
            $manager->persist($symptome);
        }
    }

    private function seedTeleconsultationsFallback(ObjectManager $manager, array $patients, array $medecins): void
    {
        if ($manager->getRepository(Teleconsultation::class)->findOneBy([]) !== null || count($patients) === 0 || count($medecins) === 0) {
            return;
        }

        $max = min(count($patients), count($medecins));
        for ($i = 0; $i < $max; $i++) {
            $tele = new Teleconsultation();
            $tele->setInitiator($patients[$i]->getUser());
            $tele->setRecipient($medecins[$i % count($medecins)]->getUser());
            $tele->setRoomName('tc-fallback-' . ($i + 1));
            $tele->setDescription('Téléconsultation de fallback pour seed complet.');
            $tele->scheduleAt((new \DateTimeImmutable())->modify('+' . ($i + 1) . ' day'));
            $tele->setStatus('pending');
            $tele->setType('general');
            $manager->persist($tele);
        }
    }

    private function seedStockPharmacyFallback(ObjectManager $manager, array $pharmacies, array $medicaments): void
    {
        if ($manager->getRepository(StockPharmacy::class)->findOneBy([]) !== null || count($pharmacies) === 0 || count($medicaments) === 0) {
            return;
        }

        foreach ($pharmacies as $index => $pharmacy) {
            $stock = new StockPharmacy();
            $stock->setPharmecie($pharmacy);
            $stock->setMedicament($medicaments[$index % count($medicaments)]);
            $stock->setQuantite(20 + ($index * 5));
            $stock->setPrixVente('8.50');
            $stock->setDateExpiration((new \DateTime())->modify('+12 months'));
            $manager->persist($stock);
        }
    }

    private function seedRendezVousFallback(ObjectManager $manager, array $patients, array $medecins): void
    {
        if ($manager->getRepository(RendezVous::class)->findOneBy([]) !== null || count($patients) === 0 || count($medecins) === 0) {
            return;
        }

        $max = min(10, count($patients), count($medecins));
        for ($i = 0; $i < $max; $i++) {
            $rdv = new RendezVous();
            $rdv->setPatient($patients[$i]);
            $rdv->setMedecin($medecins[$i % count($medecins)]);
            $rdv->setDateRdv((new \DateTime())->modify('+' . ($i + 1) . ' day'));
            $rdv->setHeureRdv(new \DateTime('09:' . str_pad((string) (($i % 6) * 10), 2, '0', STR_PAD_LEFT) . ':00'));
            $rdv->setMotif('Contrôle périodique');
            $rdv->setStatut('confirme');
            $rdv->setTypeConsultation('presentiel');
            $rdv->setNotes('Créé automatiquement par fixtures.');
            $manager->persist($rdv);
        }
    }

    private function seedCommentairesFallback(ObjectManager $manager, array $users): void
    {
        if ($manager->getRepository(Commentaire::class)->findOneBy([]) !== null || count($users) === 0) {
            return;
        }

        $contenus = $manager->getRepository(Contenu::class)->findAll();
        if (count($contenus) === 0) {
            return;
        }

        $max = min(8, count($users), count($contenus));
        for ($i = 0; $i < $max; $i++) {
            $comment = new Commentaire();
            $comment->setContenu($contenus[$i % count($contenus)]);
            $comment->setUser($users[$i % count($users)]);
            $comment->setCommentaire('Commentaire de démonstration pour peupler les entités sociales.');
            $comment->setNote(3 + ($i % 3));
            $comment->setStatut('publie');
            $comment->forceCreatedAt((new \DateTime())->modify('-' . ($i % 4) . ' day'));
            $comment->forceUpdatedAt(new \DateTime());
            $manager->persist($comment);
        }
    }

    private function seedLikesFallback(ObjectManager $manager, array $users): void
    {
        if ($manager->getRepository(Like::class)->findOneBy([]) !== null || count($users) === 0) {
            return;
        }

        $contenus = $manager->getRepository(Contenu::class)->findAll();
        if (count($contenus) === 0) {
            return;
        }

        $max = min(8, count($users));
        for ($i = 0; $i < $max; $i++) {
            $like = new Like();
            $like->setUser($users[$i]);
            $like->setContenu($contenus[$i % count($contenus)]);
            $like->forceCreatedAt((new \DateTime())->modify('-' . ($i % 2) . ' day'));
            $manager->persist($like);
        }
    }

    private function seedReservationsFallback(ObjectManager $manager, array $users, array $pharmacies, array $medicaments): void
    {
        if ($manager->getRepository(ReservationMedicament::class)->findOneBy([]) !== null) {
            return;
        }

        $users = $manager->getRepository(User::class)->findAll();
        $pharmacies = $manager->getRepository(Pharmacy::class)->findAll();
        $stocks = $manager->getRepository(StockPharmacy::class)->findAll();

        if (count($users) === 0 || count($pharmacies) === 0 || count($stocks) === 0) {
            return;
        }

        $max = min(8, count($users), count($stocks), count($pharmacies));
        for ($i = 0; $i < $max; $i++) {
            $stock = $stocks[$i % count($stocks)];
            $reservation = new ReservationMedicament();
            $reservation->setPatient($users[$i]);
            $reservation->setPharmacie($pharmacies[$i % count($pharmacies)]);
            $reservation->setMedicament($stock->getMedicament());
            $reservation->setStock($stock);
            $reservation->setQuantite(1 + ($i % 3));
            $reservation->setPrixUnitaire('8.50');
            $reservation->setPrixTotal((string) ((1 + ($i % 3)) * 8.5));
            $reservation->setStatut('en_attente');
            $reservation->expireAt((new \DateTime())->modify('+2 days'));
            $manager->persist($reservation);
        }

        $manager->flush();
    }

    private function seedArticleScores(ObjectManager $manager): void
    {
        if (!$manager instanceof EntityManagerInterface) {
            return;
        }

        $connection = $manager->getConnection();
        $existing = (int) $connection->fetchOne('SELECT COUNT(*) FROM article_scores');
        if ($existing > 0) {
            return;
        }

        $contenuIds = $connection->fetchFirstColumn('SELECT id FROM contenu ORDER BY id ASC');
        if (count($contenuIds) === 0) {
            $now = (new \DateTime())->format('Y-m-d H:i:s');
            $connection->executeStatement(
                'INSERT INTO contenu (auteur_id, titre, type, description, contenu, categorie, tags, statut, date_publication, created_at, updated_at) VALUES (NULL, :titre, :type, :description, :contenu, :categorie, :tags, :statut, :date_publication, :created_at, :updated_at)',
                [
                    'titre' => 'Article Santé Démo',
                    'type' => 'article',
                    'description' => 'Contenu généré automatiquement pour initialiser ArticleScore.',
                    'contenu' => 'Cet article de démonstration sert à valider le scoring des contenus.',
                    'categorie' => 'Prévention',
                    'tags' => 'demo,sante',
                    'statut' => 'publie',
                    'date_publication' => $now,
                    'created_at' => $now,
                    'updated_at' => $now,
                ]
            );
            $contenuIds = $connection->fetchFirstColumn('SELECT id FROM contenu ORDER BY id ASC');
        }

        foreach ($contenuIds as $index => $contenuId) {
            $connection->executeStatement(
                'INSERT INTO article_scores (contenu_id, score_article, nb_commentaires, updated_at) VALUES (:contenu_id, :score_article, :nb_commentaires, :updated_at) ON DUPLICATE KEY UPDATE score_article = VALUES(score_article), nb_commentaires = VALUES(nb_commentaires), updated_at = VALUES(updated_at)',
                [
                    'contenu_id' => (int) $contenuId,
                    'score_article' => (float) (55 + ($index * 7)),
                    'nb_commentaires' => (int) ($index % 6),
                    'updated_at' => (new \DateTime())->format('Y-m-d H:i:s'),
                ]
            );
        }
    }

    private function createBlobStream(string $content)
    {
        $stream = fopen('php://temp', 'r+');
        fwrite($stream, $content);
        rewind($stream);

        return $stream;
    }

    public function getDependencies(): array
    {
        return [
            RealisticDataFixtures::class,
            SymptomeFixtures::class,
            MissingDomainFixtures::class,
        ];
    }

    public static function getGroups(): array
    {
        return ['all-entities'];
    }
}
