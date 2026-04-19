#!/usr/bin/env php
<?php

/**
 * Script de remplissage de la base de données avec des données réalistes
 * ATTENTION: Ce script ne crée PAS les utilisateurs, il utilise ceux existants
 * 
 * Usage: php fill_database.php
 */

use App\Entity\Abonnement;
use App\Entity\Accompagnement;
use App\Entity\AccompanimentPlan;
use App\Entity\Clinique;
use App\Entity\CoachSportif;
use App\Entity\Commentaire;
use App\Entity\Contenu;
use App\Entity\Conversation;
use App\Entity\Disponibilite;
use App\Entity\DocumentAccess;
use App\Entity\Facture;
use App\Entity\JournalItem;
use App\Entity\Like;
use App\Entity\Medecin;
use App\Entity\Medicament;
use App\Entity\Message;
use App\Entity\Notification;
use App\Entity\Nutritionniste;
use App\Entity\Patient;
use App\Entity\Pharmacien;
use App\Entity\Pharmacy;
use App\Entity\PlanExercice;
use App\Entity\PlanRegime;
use App\Entity\RapportAnalyse;
use App\Entity\RapportMedical;
use App\Entity\RendezVous;
use App\Entity\ReponseMedicament;
use App\Entity\ReservationMedicament;
use App\Entity\SanteQuotidienne;
use App\Entity\SharedDocument;
use App\Entity\StockPharmacy;
use App\Entity\SymptomeListe;
use App\Entity\SymptomeQuotidien;
use App\Entity\Teleconsultation;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

require __DIR__.'/vendor/autoload.php';

$kernel = new \App\Kernel('dev', true);
$kernel->boot();
$container = $kernel->getContainer();
$em = $container->get('doctrine.orm.entity_manager');

echo "🚀 Démarrage du remplissage de la base de données...\n\n";

// Récupération des utilisateurs existants
$users = $em->getRepository(User::class)->findAll();
if (empty($users)) {
    echo "❌ ERREUR: Aucun utilisateur trouvé. Créez d'abord des utilisateurs.\n";
    exit(1);
}

// Filtrer les utilisateurs par rôle
$patients = [];
$medecins = [];
$pharmaciens = [];
$coaches = [];
$nutritionnistes = [];
$admins = [];

foreach ($users as $user) {
    if ($user->getPatient()) {
        $patients[] = $user;
    }
    if ($user->getMedecin()) {
        $medecins[] = $user;
    }
    if ($user->getPharmacien()) {
        $pharmaciens[] = $user;
    }
    if ($user->getCoachSportif()) {
        $coaches[] = $user;
    }
    if ($user->getNutritionniste()) {
        $nutritionnistes[] = $user;
    }
    if (in_array('ROLE_ADMIN', $user->getRoles())) {
        $admins[] = $user;
    }
}

echo "👥 Utilisateurs trouvés:\n";
echo "   - Patients: " . count($patients) . "\n";
echo "   - Médecins: " . count($medecins) . "\n";
echo "   - Pharmaciens: " . count($pharmaciens) . "\n";
echo "   - Coaches: " . count($coaches) . "\n";
echo "   - Nutritionnistes: " . count($nutritionnistes) . "\n\n";

// =============================================================================
// 1. CLINIQUES
// =============================================================================
echo "🏥 Création des cliniques...\n";
$cliniquesData = [
    ['Clinique Carthage', 'Centre Ville, Tunis', '71234567', 'contact@clinique-carthage.tn', '09:00-18:00'],
    ['Polyclinique La Soukra', 'La Soukra, Ariana', '71567890', 'info@poly-soukra.tn', '08:00-20:00'],
    ['Centre Médical Hannibal', 'Lafayette, Tunis', '71345678', 'contact@cm-hannibal.tn', '24/7'],
    ['Clinique El Manar', 'El Manar 2, Tunis', '71456789', 'reception@clinique-elmanar.tn', '08:30-19:00'],
    ['Centre Hospitalier Ennakhil', 'Marsa, Tunis', '71678901', 'info@ch-ennakhil.tn', '24/7'],
];

$cliniques = [];
foreach ($cliniquesData as $data) {
    // Vérifier si la clinique existe déjà
    $existing = $em->getRepository(Clinique::class)->findOneBy(['nom' => $data[0]]);
    if ($existing) {
        $cliniques[] = $existing;
        continue;
    }
    
    $clinique = new Clinique();
    $clinique->setNom($data[0]);
    $clinique->setAdresse($data[1]);
    $clinique->setTelephone($data[2]);
    $clinique->setEmail($data[3]);
    $clinique->setHorairesOuverture($data[4]);
    $em->persist($clinique);
    $cliniques[] = $clinique;
}
echo "   ✅ " . count($cliniques) . " cliniques (nouvelles + existantes)\n\n";

// =============================================================================
// 2. SYMPTÔMES LISTE
// =============================================================================
echo "🩺 Création des symptômes...\n";
$symptomesData = [
    ['Fièvre', 'Élévation de la température corporelle au-delà de 38°C', 'infection'],
    ['Maux de tête', 'Douleur localisée au niveau du crâne', 'neurologique'],
    ['Toux', 'Expulsion brusque et bruyante d\'air des poumons', 'respiratoire'],
    ['Fatigue', 'Sensation d\'épuisement et manque d\'énergie', 'general'],
    ['Nausées', 'Sensation de malaise gastrique avec envie de vomir', 'digestif'],
    ['Douleurs abdominales', 'Douleur localisée dans la région abdominale', 'digestif'],
    ['Vertiges', 'Sensation de perte d\'équilibre ou de rotation', 'neurologique'],
    ['Essoufflement', 'Difficulté à respirer normalement', 'respiratoire'],
    ['Douleurs musculaires', 'Douleurs au niveau des muscles', 'musculaire'],
    ['Insomnie', 'Difficulté à s\'endormir ou à maintenir le sommeil', 'neurologique'],
    ['Palpitations', 'Perception anormale des battements cardiaques', 'cardiaque'],
    ['Douleurs articulaires', 'Douleur au niveau des articulations', 'musculaire'],
    ['Éruption cutanée', 'Apparition de lésions sur la peau', 'dermatologique'],
    ['Congestion nasale', 'Obstruction des voies nasales', 'respiratoire'],
    ['Diarrhée', 'Selles fréquentes et liquides', 'digestif'],
];

$symptomes = [];
foreach ($symptomesData as $data) {
    // Vérifier si le symptôme existe déjà
    $existing = $em->getRepository(SymptomeListe::class)->findOneBy(['nom' => $data[0]]);
    if ($existing) {
        $symptomes[] = $existing;
        continue;
    }
    
    $symptome = new SymptomeListe();
    $symptome->setNom($data[0]);
    // SymptomeListe n'a pas de champ description, seulement nom et categorie
    $symptome->setCategorie($data[2]);
    $em->persist($symptome);
    $symptomes[] = $symptome;
}
echo "   ✅ " . count($symptomes) . " symptômes (nouveaux + existants)\n\n";

// =============================================================================
// 3. MÉDICAMENTS
// =============================================================================
echo "💊 Création des médicaments...\n";
$medicamentsData = [
    ['Paracétamol 500mg', 'Antalgique et antipyrétique', 'Boîte de 20 comprimés', 'PARACET500', 'douleur', 5.50],
    ['Ibuprofène 400mg', 'Anti-inflammatoire non stéroïdien', 'Boîte de 30 comprimés', 'IBUPROF400', 'douleur', 8.90],
    ['Amoxicilline 1g', 'Antibiotique de la famille des pénicillines', 'Boîte de 14 comprimés', 'AMOX1000', 'infection', 15.75],
    ['Oméprazole 20mg', 'Inhibiteur de la pompe à protons', 'Boîte de 28 gélules', 'OMEP20', 'digestif', 12.30],
    ['Aspégic 1000mg', 'Antalgique, antipyrétique et anti-inflammatoire', 'Boîte de 20 sachets', 'ASPEG1000', 'douleur', 7.20],
    ['Doliprane 1000mg', 'Antalgique et antipyrétique', 'Boîte de 8 comprimés', 'DOLIP1000', 'douleur', 4.50],
    ['Ventoline', 'Bronchodilatateur pour l\'asthme', 'Flacon de 200 doses', 'VENT200', 'respiratoire', 18.50],
    ['Augmentin 1g', 'Antibiotique à large spectre', 'Boîte de 12 comprimés', 'AUGM1000', 'infection', 22.40],
    ['Nurofen 400mg', 'Anti-inflammatoire', 'Boîte de 12 comprimés', 'NUROF400', 'douleur', 9.80],
    ['Spasfon', 'Antispasmodique', 'Boîte de 30 comprimés', 'SPASF80', 'digestif', 11.20],
    ['Efferalgan 1g', 'Antalgique', 'Boîte de 16 comprimés effervescents', 'EFFER1000', 'douleur', 6.30],
    ['Azithromycine 250mg', 'Antibiotique macrolide', 'Boîte de 6 comprimés', 'AZITHRO250', 'infection', 19.90],
    ['Nexium 40mg', 'Traitement du reflux gastrique', 'Boîte de 28 comprimés', 'NEX40', 'digestif', 24.50],
    ['Loratadine 10mg', 'Antihistaminique', 'Boîte de 30 comprimés', 'LORAT10', 'allergie', 13.80],
    ['Seretide Diskus', 'Traitement de l\'asthme', 'Inhalateur 60 doses', 'SERET250', 'respiratoire', 45.00],
];

$medicaments = [];
foreach ($medicamentsData as $data) {
    // Vérifier si le médicament existe déjà
    $existing = $em->getRepository(Medicament::class)->findOneBy(['nom' => $data[0]]);
    if ($existing) {
        $medicaments[] = $existing;
        continue;
    }
    
    $med = new Medicament();
    $med->setNom($data[0]);
    $med->setDescription($data[1]);
    $med->setDosage($data[2]);
    $med->setCodeBarre($data[3]); // Utiliser codeBarre au lieu de reference
    $med->setType($data[4]); // Utiliser type au lieu de categorie
    $med->setPrix((string)$data[5]);
    $em->persist($med);
    $medicaments[] = $med;
}
echo "   ✅ " . count($medicaments) . " médicaments (nouveaux + existants)\n\n";

$em->flush();
echo "💾 Première sauvegarde (cliniques, symptômes, médicaments)\n\n";

// =============================================================================
// 4. PHARMACIES ET STOCKS
// =============================================================================
echo "💊 Création des pharmacies...\n";
$pharmaciesData = [
    ['Pharmacie Centrale Tunis', 'Avenue Habib Bourguiba, Tunis', '71123456', 'centrale@pharmacy.tn', '08:00-22:00', 36.806389, 10.181667],
    ['Pharmacie Pasteur', 'Rue Charles Nicolle, Tunis', '71234567', 'pasteur@pharmacy.tn', '09:00-20:00', 36.850000, 10.166667],
    ['Pharmacie La Marsa', 'Centre Commercial La Marsa', '71345678', 'marsa@pharmacy.tn', '24/7', 36.876667, 10.326389],
    ['Pharmacie Ariana', 'Place 7 Novembre, Ariana', '71456789', 'ariana@pharmacy.tn', '08:30-21:00', 36.860556, 10.193889],
    ['Pharmacie Carthage', 'Zone Touristique Carthage', '71567890', 'carthage@pharmacy.tn', '08:00-20:00', 36.853056, 10.323333],
    ['Pharmacie Menzah', 'Centre Commercial Menzah 6', '71678901', 'menzah@pharmacy.tn', '09:00-23:00', 36.836389, 10.186667],
    ['Pharmacie Bardo', 'Avenue du Bardo', '71789012', 'bardo@pharmacy.tn', '08:00-19:00', 36.810000, 10.140000],
];

$pharmacies = [];
foreach ($pharmaciesData as $data) {
    $pharmacy = new Pharmacy();
    $pharmacy->setNom($data[0]);
    $pharmacy->setAdresse($data[1]);
    $pharmacy->setTelephone($data[2]);
    $pharmacy->setEmail($data[3]);
    $pharmacy->setHoraires($data[4]);
    $pharmacy->setLatitude((string)$data[5]);
    $pharmacy->setLongitude((string)$data[6]);
    
    // Associer un pharmacien si disponible
    if (!empty($pharmaciens)) {
        $pharmacy->setPharmacien($pharmaciens[array_rand($pharmaciens)]->getPharmacien());
    }
    
    $em->persist($pharmacy);
    $pharmacies[] = $pharmacy;
}
echo "   ✅ " . count($pharmacies) . " pharmacies créées\n";

// Créer les stocks pour chaque pharmacie
echo "📦 Création des stocks...\n";
$stockCount = 0;
foreach ($pharmacies as $pharmacy) {
    // Chaque pharmacie a entre 8 et 15 médicaments différents
    $nbMeds = rand(8, min(15, count($medicaments)));
    $medsToStock = (array)array_rand(array_flip(array_keys($medicaments)), $nbMeds);
    
    foreach ($medsToStock as $medIndex) {
        $stock = new StockPharmacy();
        $stock->setPharmecie($pharmacy);
        $stock->setMedicament($medicaments[$medIndex]);
        $stock->setQuantite(rand(10, 200));
        $stock->setPrixVente((string)((float)$medicaments[$medIndex]->getPrix() * 1.15)); // +15% marge
        $em->persist($stock);
        $stockCount++;
    }
}
echo "   ✅ $stockCount lignes de stock créées\n\n";

$em->flush();
echo "💾 Deuxième sauvegarde (pharmacies et stocks)\n\n";

// =============================================================================
// 5. ABONNEMENTS ET FACTURES
// =============================================================================
if (!empty($patients)) {
    echo "📋 Création des abonnements et factures...\n";
    
    $typesAbo = ['basic', 'premium', 'vip'];
    $durees = [1, 3, 6, 12]; // mois
    $prix = ['basic' => 29.99, 'premium' => 49.99, 'vip' => 99.99];
    
    $abonnements = [];
    
    // Trouver le dernier numéro de facture pour éviter les doublons
    $lastFacture = $em->getRepository(Facture::class)->findOneBy([], ['id' => 'DESC']);
    $factureNum = $lastFacture ? (int)substr($lastFacture->getNumero(), -6) + 1 : 1000;
    
    foreach ($patients as $patient) {
        // Chaque patient a 1 à 3 abonnements
        $nbAbos = rand(1, 3);
        
        for ($i = 0; $i < $nbAbos; $i++) {
            $type = $typesAbo[array_rand($typesAbo)];
            $duree = $durees[array_rand($durees)];
            
            $dateDebut = new DateTime('-' . rand(0, 365) . ' days');
            $dateFin = clone $dateDebut;
            $dateFin->modify('+' . $duree . ' months');
            
            // Statut basé sur les dates
            if ($dateFin < new DateTime()) {
                $statut = 'expired';
            } elseif ($dateDebut > new DateTime()) {
                $statut = 'pending';
            } else {
                $statut = rand(0, 1) ? 'active' : 'expired';
            }
            
            $abonnement = new Abonnement();
            $abonnement->setUser($patient);
            $abonnement->setNom('Abonnement ' . ucfirst($type));
            $abonnement->setTypeAbonnement($type);
            $abonnement->setStatut($statut);
            $abonnement->setDateDebut($dateDebut);
            $abonnement->setDateFin($dateFin);
            $abonnement->setPrix((string)$prix[$type]);
            $abonnement->setDureeMois($duree);
            
            $em->persist($abonnement);
            $abonnements[] = $abonnement;
            
            // Créer une facture pour cet abonnement
            $facture = new Facture();
            $facture->setNumero('INV-' . str_pad($factureNum++, 6, '0', STR_PAD_LEFT));
            $facture->setUser($patient);
            $facture->setAbonnement($abonnement);
            
            $montantHt = $prix[$type] * $duree;
            $tvaTaux = 19.0;
            $montantTva = $montantHt * ($tvaTaux / 100);
            $montantTtc = $montantHt + $montantTva;
            
            $facture->setMontantHt((string)$montantHt);
            $facture->setTvaTaux((string)$tvaTaux);
            $facture->setTvaMontant((string)$montantTva); // setTvaMontant au lieu de setMontantTva
            $facture->setMontantTtc((string)$montantTtc);
            // Facture n'a pas de dateEmission ni statutPaiement, seulement createdAt
            // $facture->setCreatedAt($dateDebut); // Géré automatiquement par le constructeur
            
            $em->persist($facture);
        }
    }
    
    echo "   ✅ " . count($abonnements) . " abonnements créés\n";
    echo "   ✅ " . ($factureNum - 1000) . " factures créées\n\n";
    
    $em->flush();
    echo "💾 Troisième sauvegarde (abonnements et factures)\n\n";
}

// =============================================================================
// 6. ACCOMPANIMENT PLANS
// =============================================================================
if (!empty($patients) && (!empty($coaches) || !empty($nutritionnistes))) {
    echo "📅 Création des plans d'accompagnement...\n";
    
    $plans = [];
    $titres = [
        'Programme Remise en Forme',
        'Plan Nutritionnel Personnalisé',
        'Coaching Sportif Intensif',
        'Rééquilibrage Alimentaire',
        'Préparation Marathon',
        'Perte de Poids Progressive',
        'Renforcement Musculaire',
        'Programme Santé & Bien-être',
        'Nutrition Sportive Optimale',
        'Transformation Physique 90 jours',
    ];
    
    foreach ($patients as $patient) {
        // Chaque patient a 0 à 3 plans
        $nbPlans = rand(0, 3);
        
        for ($i = 0; $i < $nbPlans; $i++) {
            $plan = new AccompanimentPlan();
            $plan->setPatient($patient->getPatient());
            $plan->setTitle($titres[array_rand($titres)]);
            $plan->setDescription('Programme personnalisé adapté aux objectifs du patient avec suivi régulier et ajustements selon les progrès.');
            
            // Assigner un coach OU un nutritionniste (ou les deux)
            if (!empty($coaches) && rand(0, 1)) {
                $plan->setCoach($coaches[array_rand($coaches)]->getCoachSportif());
            }
            if (!empty($nutritionnistes) && rand(0, 1)) {
                $plan->setNutritionist($nutritionnistes[array_rand($nutritionnistes)]->getNutritionniste());
            }
            
            $startDate = new DateTime('-' . rand(1, 180) . ' days');
            $duree = rand(30, 180);
            $endDate = clone $startDate;
            $endDate->modify('+' . $duree . ' days');
            
            $plan->setStartDate($startDate);
            $plan->setEndDate($endDate);
            $plan->setDuration($duree);
            
            // Statut basé sur les dates
            $now = new DateTime();
            if ($startDate > $now) {
                $status = 'pending';
            } elseif ($endDate < $now) {
                $status = rand(0, 1) ? 'completed' : 'cancelled';
            } else {
                $status = 'active';
            }
            $plan->setStatus($status);
            
            // Progression aléatoire (0-100%)
            if ($status === 'completed') {
                $plan->setProgressPercentage(100);
            } elseif ($status === 'active') {
                $plan->setProgressPercentage(rand(10, 90));
            } else {
                $plan->setProgressPercentage(0);
            }
            
            $em->persist($plan);
            $plans[] = $plan;
        }
    }
    
    echo "   ✅ " . count($plans) . " plans d'accompagnement créés\n\n";
    $em->flush();
}

// =============================================================================
// 7. DISPONIBILITÉS ET RENDEZ-VOUS
// =============================================================================
if (!empty($medecins) && !empty($patients)) {
    echo "📆 Création des disponibilités et rendez-vous...\n";
    
    $disponibilites = [];
    $rendezVous = [];
    
    // Créer des disponibilités pour les médecins
    foreach ($medecins as $medUser) {
        $medecin = $medUser->getMedecin();
        
        // Créer 10-20 créneaux de disponibilité pour les 2 prochaines semaines
        $nbCreneaux = rand(10, 20);
        
        for ($i = 0; $i < $nbCreneaux; $i++) {
            $dispo = new Disponibilite();
            $dispo->setMedecin($medecin);
            
            $date = new DateTime('+' . rand(1, 14) . ' days');
            $heure = rand(8, 17); // Entre 8h et 17h
            $date->setTime($heure, rand(0, 1) * 30); // 00 ou 30 minutes
            
            $dispo->setDate($date);
            $dispo->setHeureDebut(clone $date);
            
            $heureFin = clone $date;
            $heureFin->modify('+30 minutes');
            $dispo->setHeureFin($heureFin);
            
            $dispo->setDisponible(rand(0, 100) < 70); // 70% disponibles
            
            $em->persist($dispo);
            $disponibilites[] = $dispo;
        }
    }
    
    echo "   ✅ " . count($disponibilites) . " disponibilités créées\n";
    $em->flush();
    
    // Créer des rendez-vous
    foreach ($patients as $patient) {
        // Chaque patient a 0 à 3 rendez-vous
        $nbRdv = rand(0, 3);
        
        for ($i = 0; $i < $nbRdv; $i++) {
            $medecin = $medecins[array_rand($medecins)]->getMedecin();
            
            $rdv = new RendezVous();
            $rdv->setPatient($patient->getPatient());
            $rdv->setMedecin($medecin);
            
            $date = new DateTime(rand(0, 1) ? '+' : '-' . rand(1, 60) . ' days');
            $rdv->setDate($date);
            
            $heure = rand(8, 17);
            $date->setTime($heure, rand(0, 1) * 30);
            $rdv->setHeure($date);
            
            $motifs = [
                'Consultation générale',
                'Contrôle de routine',
                'Suivi médical',
                'Bilan de santé',
                'Renouvellement ordonnance',
                'Résultats analyses',
            ];
            $rdv->setMotif($motifs[array_rand($motifs)]);
            
            // Statut
            $now = new DateTime();
            if ($date < $now) {
                $rdv->setStatut(rand(0, 100) < 85 ? 'completed' : 'cancelled');
            } else {
                $rdv->setStatut(rand(0, 100) < 90 ? 'confirmed' : 'pending');
            }
            
            $em->persist($rdv);
            $rendezVous[] = $rdv;
        }
    }
    
    echo "   ✅ " . count($rendezVous) . " rendez-vous créés\n\n";
    $em->flush();
}

// =============================================================================
// 8. RAPPORTS MÉDICAUX ET ANALYSES
// =============================================================================
if (!empty($medecins) && !empty($patients)) {
    echo "📄 Création des rapports médicaux et analyses...\n";
    
    $rapportsMedicaux = [];
    $rapportsAnalyses = [];
    
    foreach ($patients as $patient) {
        // 0 à 3 rapports médicaux par patient
        $nbRapports = rand(0, 3);
        
        for ($i = 0; $i < $nbRapports; $i++) {
            $rapport = new RapportMedical();
            $rapport->setPatient($patient->getPatient());
            $rapport->setMedecin($medecins[array_rand($medecins)]->getMedecin());
            
            $titres = [
                'Consultation de routine',
                'Examen médical complet',
                'Suivi pathologie chronique',
                'Bilan de santé annuel',
                'Consultation spécialisée',
            ];
            $rapport->setTitre($titres[array_rand($titres)]);
            $rapport->setDiagnostic('Patient en bonne santé générale. Quelques recommandations d\'hygiène de vie à suivre.');
            $rapport->setTraitement('Repos, hydratation, alimentation équilibrée.');
            $rapport->setRecommandations('Contrôle dans 3 mois. Pratiquer une activité physique régulière.');
            
            $date = new DateTime('-' . rand(1, 365) . ' days');
            $rapport->setDateRapport($date);
            
            $em->persist($rapport);
            $rapportsMedicaux[] = $rapport;
        }
        
        // 0 à 2 rapports d'analyses par patient
        $nbAnalyses = rand(0, 2);
        
        for ($i = 0; $i < $nbAnalyses; $i++) {
            $analyse = new RapportAnalyse();
            $analyse->setPatient($patient->getPatient());
            $analyse->setMedecin($medecins[array_rand($medecins)]->getMedecin());
            
            $types = [
                'Analyse sanguine complète',
                'Bilan lipidique',
                'Glycémie à jeun',
                'Bilan hépatique',
                'Numération formule sanguine',
            ];
            $analyse->setTypeAnalyse($types[array_rand($types)]);
            
            $date = new DateTime('-' . rand(1, 180) . ' days');
            $analyse->setDateAnalyse($date);
            $analyse->setResultats('Résultats dans les normes. Aucune anomalie détectée.');
            $analyse->setCommentaires('RAS. Continuer les contrôles réguliers.');
            
            $em->persist($analyse);
            $rapportsAnalyses[] = $analyse;
        }
    }
    
    echo "   ✅ " . count($rapportsMedicaux) . " rapports médicaux créés\n";
    echo "   ✅ " . count($rapportsAnalyses) . " rapports d'analyses créés\n\n";
    $em->flush();
}

// =============================================================================
// 9. JOURNAL ITEMS ET SYMPTÔMES QUOTIDIENS
// =============================================================================
if (!empty($patients)) {
    echo "📝 Création des journaux et symptômes quotidiens...\n";
    
    $journalItems = [];
    $symptomesQuotidiens = [];
    
    foreach ($patients as $patient) {
        // 3 à 10 entrées de journal par patient
        $nbEntries = rand(3, 10);
        
        for ($i = 0; $i < $nbEntries; $i++) {
            $journal = new JournalItem();
            $journal->setPatient($patient->getPatient());
            
            $types = ['physical', 'mental', 'nutrition', 'medication', 'symptom'];
            $type = $types[array_rand($types)];
            $journal->setType($type);
            
            $titresParType = [
                'physical' => ['Séance de sport', 'Marche quotidienne', 'Yoga matinal'],
                'mental' => ['État d\'esprit positif', 'Stress au travail', 'Bonne humeur'],
                'nutrition' => ['Repas équilibré', 'Petit déjeuner léger', 'Dîner en famille'],
                'medication' => ['Prise de médicament', 'Traitement matin', 'Complément alimentaire'],
                'symptom' => ['Léger mal de tête', 'Fatigue passagère', 'Douleur musculaire'],
            ];
            
            $journal->setTitre($titresParType[$type][array_rand($titresParType[$type])]);
            $journal->setDescription('Notes détaillées sur l\'événement de la journée.');
            
            $date = new DateTime('-' . rand(1, 90) . ' days');
            $journal->setDateJournal($date);
            $journal->setHeure($date);
            
            $em->persist($journal);
            $journalItems[] = $journal;
        }
        
        // 2 à 8 symptômes quotidiens par patient
        $nbSymptomes = rand(2, 8);
        
        for ($i = 0; $i < $nbSymptomes; $i++) {
            $symptomeQuot = new SymptomeQuotidien();
            $symptomeQuot->setPatient($patient->getPatient());
            $symptomeQuot->setSymptome($symptomes[array_rand($symptomes)]);
            
            $date = new DateTime('-' . rand(1, 60) . ' days');
            $symptomeQuot->setDateSymptome($date);
            $symptomeQuot->setIntensite(rand(1, 10));
            $symptomeQuot->setNotes('Observation du symptôme au cours de la journée.');
            
            $em->persist($symptomeQuot);
            $symptomesQuotidiens[] = $symptomeQuot;
        }
    }
    
    echo "   ✅ " . count($journalItems) . " entrées de journal créées\n";
    echo "   ✅ " . count($symptomesQuotidiens) . " symptômes quotidiens créés\n\n";
    $em->flush();
}

// =============================================================================
// 10. CONTENUS, LIKES ET COMMENTAIRES
// =============================================================================
echo "📱 Création des contenus, likes et commentaires...\n";

$contenus = [];
$contenusData = [
    ['article', '10 Conseils pour une Vie Saine', 'Découvrez nos meilleurs conseils pour maintenir une bonne santé au quotidien...'],
    ['article', 'L\'Importance de l\'Hydratation', 'Boire suffisamment d\'eau est essentiel pour le bon fonctionnement de votre corps...'],
    ['video', 'Exercices de Yoga pour Débutants', 'Apprenez les bases du yoga avec cette vidéo complète pour débutants...'],
    ['article', 'Alimentation Équilibrée: Les Bases', 'Un guide complet pour comprendre les principes d\'une alimentation saine...'],
    ['video', 'Comment Gérer le Stress au Travail', 'Techniques et astuces pour mieux gérer le stress professionnel...'],
    ['article', 'Le Sommeil et la Santé', 'Comprendre l\'importance d\'un sommeil de qualité pour votre bien-être...'],
    ['video', 'Cardio Training à la Maison', 'Un programme complet d\'exercices cardio sans équipement...'],
    ['article', 'Prévention des Maladies Cardiovasculaires', 'Les gestes essentiels pour protéger votre cœur...'],
    ['article', 'Nutrition Sportive: Guide Complet', 'Tout ce qu\'il faut savoir sur la nutrition pour optimiser vos performances...'],
    ['video', 'Méditation Guidée 10 Minutes', 'Une session de méditation courte pour commencer la journée en douceur...'],
];

$auteurs = !empty($admins) ? $admins : (!empty($medecins) ? $medecins : $users);

foreach ($contenusData as $data) {
    $contenu = new Contenu();
    $contenu->setAuteur($auteurs[array_rand($auteurs)]);
    $contenu->setType($data[0]);
    $contenu->setTitre($data[1]);
    $contenu->setContenu($data[2]);
    $contenu->setStatut(rand(0, 100) < 90 ? 'published' : 'draft');
    
    $date = new DateTime('-' . rand(1, 180) . ' days');
    $contenu->schedulePublicationAt($date);
    
    $em->persist($contenu);
    $contenus[] = $contenu;
}

echo "   ✅ " . count($contenus) . " contenus créés\n";
$em->flush();

// Créer des likes
$likes = [];
foreach ($contenus as $contenu) {
    // Chaque contenu reçoit des likes (entre 1 et le nombre d'utilisateurs disponibles)
    if (count($users) > 0) {
        $nbLikes = rand(1, min(20, count($users)));
        $usersWhoLiked = (array)array_rand(array_flip(array_keys($users)), $nbLikes);
        
        foreach ($usersWhoLiked as $userIndex) {
            $like = new Like();
            $like->setContenu($contenu);
            $like->setUser($users[$userIndex]);
            $em->persist($like);
            $likes[] = $like;
        }
    }
}

echo "   ✅ " . count($likes) . " likes créés\n";
$em->flush();

// Créer des commentaires
$commentaires = [];
$commentairesTextes = [
    'Très intéressant, merci pour le partage !',
    'Article très instructif 👍',
    'Merci pour ces conseils pratiques',
    'Super contenu, j\'ai appris beaucoup',
    'Excellente présentation du sujet',
    'Cela m\'a beaucoup aidé',
    'Merci pour ces informations précieuses',
    'Très bien expliqué !',
    'Content d\'avoir trouvé cet article',
    'À partager sans modération',
];

foreach ($contenus as $contenu) {
    // Chaque contenu reçoit 2 à 8 commentaires
    $nbComments = rand(2, min(8, count($users)));
    
    for ($i = 0; $i < $nbComments; $i++) {
        $comment = new Commentaire();
        $comment->setContenu($contenu);
        $comment->setUser($users[array_rand($users)]);
        $comment->setCommentaire($commentairesTextes[array_rand($commentairesTextes)]);
        $em->persist($comment);
        $commentaires[] = $comment;
    }
}

echo "   ✅ " . count($commentaires) . " commentaires créés\n\n";
$em->flush();

// =============================================================================
// 11. RÉSERVATIONS MÉDICAMENTS
// =============================================================================
if (!empty($patients) && !empty($pharmacies) && !empty($medicaments)) {
    echo "🛒 Création des réservations de médicaments...\n";
    
    $reservations = [];
    
    foreach ($patients as $patient) {
        // Chaque patient a 0 à 3 réservations
        $nbReservations = rand(0, 3);
        
        for ($i = 0; $i < $nbReservations; $i++) {
            $pharmacy = $pharmacies[array_rand($pharmacies)];
            $medicament = $medicaments[array_rand($medicaments)];
            
            // Trouver le stock correspondant
            $stock = $em->getRepository(StockPharmacy::class)->findOneBy([
                'pharmacie' => $pharmacy,
                'medicament' => $medicament,
            ]);
            
            if ($stock) {
                $reservation = new ReservationMedicament();
                $reservation->setPatient($patient);
                $reservation->setPharmacie($pharmacy);
                $reservation->setMedicament($medicament);
                $reservation->setStock($stock);
                $reservation->setQuantite(rand(1, 3));
                
                // ReservationMedicament n'a pas de dateReservation, seulement createdAt qui est géré automatiquement
                
                $statuts = ['pending', 'confirmed', 'completed', 'cancelled'];
                $reservation->setStatut($statuts[array_rand($statuts)]);
                
                $em->persist($reservation);
                $reservations[] = $reservation;
            }
        }
    }
    
    echo "   ✅ " . count($reservations) . " réservations créées\n\n";
    $em->flush();
}

// =============================================================================
// 12. NOTIFICATIONS
// =============================================================================
echo "🔔 Création des notifications...\n";

$notifications = [];
$notifTypes = ['rdv', 'message', 'alerte', 'rappel', 'systeme'];
$notifMessages = [
    'rdv' => ['Rappel: Rendez-vous demain à 10h', 'Votre rendez-vous a été confirmé', 'Nouveau rendez-vous disponible'],
    'message' => ['Vous avez reçu un nouveau message', 'Réponse à votre question', 'Message du médecin'],
    'alerte' => ['Résultats d\'analyses disponibles', 'Mise à jour importante', 'Action requise'],
    'rappel' => ['N\'oubliez pas de prendre vos médicaments', 'Rappel: Renouvellement ordonnance', 'Suivi médical à faire'],
    'systeme' => ['Mise à jour de la plateforme', 'Nouvelle fonctionnalité disponible', 'Maintenance programmée'],
];

foreach ($users as $user) {
    // Chaque utilisateur reçoit 3 à 10 notifications
    $nbNotifs = rand(3, 10);
    
    for ($i = 0; $i < $nbNotifs; $i++) {
        $notif = new Notification();
        $notif->setUser($user);
        
        $type = $notifTypes[array_rand($notifTypes)];
        $notif->setType($type);
        
        $messages = $notifMessages[$type];
        $message = $messages[array_rand($messages)];
        
        $notif->setTitre('Notification ' . ucfirst($type));
        $notif->setMessage($message);
        $notif->setLu(rand(0, 100) < 60); // 60% lues
        
        $date = new DateTime('-' . rand(1, 30) . ' days');
        $notif->scheduleEnvoiAt($date);
        
        $em->persist($notif);
        $notifications[] = $notif;
    }
}

echo "   ✅ " . count($notifications) . " notifications créées\n\n";
$em->flush();

// =============================================================================
// 13. CONVERSATIONS ET MESSAGES
// =============================================================================
echo "💬 Création des conversations et messages...\n";

$conversations = [];
$messages = [];

// Créer 10-20 conversations aléatoires entre utilisateurs
$nbConversations = rand(10, min(20, floor(count($users) / 2)));

for ($i = 0; $i < $nbConversations; $i++) {
    $userIndexes = array_rand($users, 2);
    $user1 = $users[$userIndexes[0]];
    $user2 = $users[$userIndexes[1]];
    
    $conversation = new Conversation($user1, $user2);
    $em->persist($conversation);
    $conversations[] = $conversation;
}

$em->flush();

// Créer des messages dans chaque conversation
$messagesTextes = [
    'Bonjour, comment allez-vous ?',
    'Merci pour votre réponse rapide',
    'Pouvez-vous me donner plus d\'informations ?',
    'Je voudrais prendre rendez-vous',
    'Quels sont vos disponibilités ?',
    'Parfait, je vous remercie',
    'À bientôt !',
    'J\'ai une question concernant mon traitement',
    'Les résultats sont-ils prêts ?',
    'Merci pour votre aide',
];

foreach ($conversations as $conversation) {
    // Chaque conversation a 3 à 15 messages
    $nbMessages = rand(3, 15);
    
    for ($i = 0; $i < $nbMessages; $i++) {
        $content = $messagesTextes[array_rand($messagesTextes)];
        
        // Alterner entre les deux utilisateurs
        if ($i % 2 === 0) {
            $sender = $conversation->getUserOne();
            $recipient = $conversation->getUserTwo();
        } else {
            $sender = $conversation->getUserTwo();
            $recipient = $conversation->getUserOne();
        }
        
        $message = new Message($conversation, $sender, $recipient, $content);
        
        // 70% des messages sont marqués comme lus
        if (rand(0, 100) < 70) {
            $message->markAsRead();
        }
        
        $em->persist($message);
        $messages[] = $message;
    }
}

echo "   ✅ " . count($conversations) . " conversations créées\n";
echo "   ✅ " . count($messages) . " messages créés\n\n";
$em->flush();

// =============================================================================
// 14. TÉLÉCONSULTATIONS
// =============================================================================
if (!empty($medecins) && !empty($patients)) {
    echo "🎥 Création des téléconsultations...\n";
    
    $teleconsultations = [];
    
    foreach ($patients as $patient) {
        // Chaque patient a 0 à 2 téléconsultations
        if (rand(0, 100) < 60) { // 60% ont au moins une téléconsultation
            $nbTeleco = rand(1, 2);
            
            for ($i = 0; $i < $nbTeleco; $i++) {
                $teleco = new Teleconsultation();
                $teleco->setInitiator($patient);
                $teleco->setRecipient($medecins[array_rand($medecins)]);
                $teleco->setRoomName('room-' . uniqid());
                $teleco->setDescription('Consultation en ligne pour suivi médical');
                
                $date = new DateTimeImmutable(rand(0, 1) ? '+' : '-' . rand(1, 30) . ' days');
                $teleco->scheduleAt($date);
                
                $statuts = ['scheduled', 'in_progress', 'completed', 'cancelled'];
                $weights = [40, 10, 40, 10]; // Pondération
                $statut = $statuts[array_rand(array_flip($weights))];
                $teleco->setStatus($statut);
                
                if ($statut === 'completed') {
                    $duration = rand(10, 45);
                    $teleco->setDuration($duration);
                    $teleco->setNotes('Consultation effectuée. Patient en bonne santé générale.');
                }
                
                $em->persist($teleco);
                $teleconsultations[] = $teleco;
            }
        }
    }
    
    echo "   ✅ " . count($teleconsultations) . " téléconsultations créées\n\n";
    $em->flush();
}

// =============================================================================
// 15. SHARED DOCUMENTS
// =============================================================================
echo "📎 Création des documents partagés...\n";

$documents = [];
$documentTypes = ['analysis', 'prescription', 'report', 'scan', 'other'];
$documentNames = [
    'Analyse sanguine.pdf',
    'Ordonnance médicale.pdf',
    'Rapport consultation.pdf',
    'Radio thorax.jpg',
    'Bilan complet.pdf',
    'Résultats examens.pdf',
    'Compte-rendu hospitalisation.pdf',
    'Certificat médical.pdf',
];

foreach ($users as $user) {
    // Chaque utilisateur a 0 à 3 documents
    if (rand(0, 100) < 50) { // 50% ont des documents
        $nbDocs = rand(1, 3);
        
        for ($i = 0; $i < $nbDocs; $i++) {
            $doc = new SharedDocument();
            $doc->setOwner($user);
            $doc->setFileName($documentNames[array_rand($documentNames)]);
            $doc->setFilePath('/uploads/documents/' . uniqid() . '.pdf');
            $doc->setMimeType('application/pdf');
            $doc->setFileSize(rand(50000, 5000000));
            $doc->setFileContent('Binary content placeholder');
            $doc->setDescription('Document médical important');
            $doc->setDocumentType($documentTypes[array_rand($documentTypes)]);
            $doc->setPublic(rand(0, 100) < 20); // 20% publics
            
            $em->persist($doc);
            $documents[] = $doc;
        }
    }
}

echo "   ✅ " . count($documents) . " documents partagés créés\n\n";
$em->flush();

// =============================================================================
// STATISTIQUES FINALES
// =============================================================================
echo "\n";
echo "═══════════════════════════════════════════════════════════\n";
echo "✅ REMPLISSAGE DE LA BASE DE DONNÉES TERMINÉ AVEC SUCCÈS !\n";
echo "═══════════════════════════════════════════════════════════\n\n";

echo "📊 STATISTIQUES FINALES:\n\n";
echo "   Cliniques:              " . count($cliniques) . "\n";
echo "   Symptômes:              " . count($symptomes) . "\n";
echo "   Médicaments:            " . count($medicaments) . "\n";
echo "   Pharmacies:             " . count($pharmacies) . "\n";
echo "   Stocks:                 $stockCount\n";
if (isset($abonnements)) echo "   Abonnements:            " . count($abonnements) . "\n";
if (isset($plans)) echo "   Plans accompagnement:   " . count($plans) . "\n";
if (isset($disponibilites)) echo "   Disponibilités:         " . count($disponibilites) . "\n";
if (isset($rendezVous)) echo "   Rendez-vous:            " . count($rendezVous) . "\n";
if (isset($rapportsMedicaux)) echo "   Rapports médicaux:      " . count($rapportsMedicaux) . "\n";
if (isset($rapportsAnalyses)) echo "   Rapports analyses:      " . count($rapportsAnalyses) . "\n";
if (isset($journalItems)) echo "   Entrées journal:        " . count($journalItems) . "\n";
if (isset($symptomesQuotidiens)) echo "   Symptômes quotidiens:   " . count($symptomesQuotidiens) . "\n";
echo "   Contenus:               " . count($contenus) . "\n";
echo "   Likes:                  " . count($likes) . "\n";
echo "   Commentaires:           " . count($commentaires) . "\n";
if (isset($reservations)) echo "   Réservations:           " . count($reservations) . "\n";
echo "   Notifications:          " . count($notifications) . "\n";
echo "   Conversations:          " . count($conversations) . "\n";
echo "   Messages:               " . count($messages) . "\n";
if (isset($teleconsultations)) echo "   Téléconsultations:      " . count($teleconsultations) . "\n";
echo "   Documents partagés:     " . count($documents) . "\n";

echo "\n";
echo "🎉 Votre base de données est maintenant remplie avec des données\n";
echo "   réalistes et cohérentes, prêtes pour la démonstration !\n\n";
