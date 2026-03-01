<?php

namespace App\DataFixtures;

use App\Entity\Abonnement;
use App\Entity\CoachSportif;
use App\Entity\Medecin;
use App\Entity\Nutritionniste;
use App\Entity\Patient;
use App\Entity\Pharmacien;
use App\Entity\User;
use Doctrine\Bundle\FixturesBundle\Fixture;
use Doctrine\Persistence\ObjectManager;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

class RealisticDataFixtures extends Fixture
{
    public function __construct(private readonly UserPasswordHasherInterface $passwordHasher)
    {
    }

    public function load(ObjectManager $manager): void
    {
        $existingAdmin = $manager->getRepository(User::class)->findOneBy(['username' => 'admin.santea']);
        if ($existingAdmin !== null) {
            return;
        }

        $plainPassword = 'Santea2026!';

        $cities = ['Tunis', 'Sfax', 'Sousse', 'Monastir', 'Nabeul', 'Bizerte', 'Kairouan', 'Gabès'];
        $adresses = [
            '12 Rue de Marseille',
            '45 Avenue Habib Bourguiba',
            '8 Rue de la République',
            '22 Avenue de la Liberté',
            '90 Rue des Orangers',
            '14 Rue Ibn Khaldoun',
            '6 Rue du Lac',
        ];

        $patients = [
            ['nom' => 'Yassmine', 'prenom' => 'Yassmine'],
            ['nom' => 'Ben Ali', 'prenom' => 'Amira'],
            ['nom' => 'Trabelsi', 'prenom' => 'Karim'],
            ['nom' => 'Khlifi', 'prenom' => 'Ines'],
            ['nom' => 'Mansouri', 'prenom' => 'Hichem'],
            ['nom' => 'Mejri', 'prenom' => 'Nour'],
            ['nom' => 'Ayari', 'prenom' => 'Sarra'],
            ['nom' => 'Chakroun', 'prenom' => 'Walid'],
            ['nom' => 'Jaziri', 'prenom' => 'Lina'],
            ['nom' => 'Gharbi', 'prenom' => 'Youssef'],
            ['nom' => 'Haddad', 'prenom' => 'Rania'],
            ['nom' => 'Baccar', 'prenom' => 'Moez'],
        ];

        $medecins = [
            ['nom' => 'Dridi', 'prenom' => 'Aymen', 'specialite' => 'Cardiologie'],
            ['nom' => 'Kallel', 'prenom' => 'Rim', 'specialite' => 'Pédiatrie'],
            ['nom' => 'Belhaj', 'prenom' => 'Sami', 'specialite' => 'Dermatologie'],
            ['nom' => 'Saidi', 'prenom' => 'Meriem', 'specialite' => 'Médecine générale'],
            ['nom' => 'Zouari', 'prenom' => 'Ahmed', 'specialite' => 'Endocrinologie'],
        ];

        $pharmaciens = [
            ['nom' => 'Kacem', 'prenom' => 'Hiba', 'pharmacie' => 'Pharmacie Centrale'],
            ['nom' => 'Hamdi', 'prenom' => 'Omar', 'pharmacie' => 'Pharmacie du Lac'],
            ['nom' => 'Bouzid', 'prenom' => 'Maya', 'pharmacie' => 'Pharmacie El Wifek'],
        ];

        $coachs = [
            ['nom' => 'Ben Salem', 'prenom' => 'Aziz', 'specialite' => 'Remise en forme'],
            ['nom' => 'Makni', 'prenom' => 'Houda', 'specialite' => 'Perte de poids'],
            ['nom' => 'Chaabane', 'prenom' => 'Nader', 'specialite' => 'Préparation physique'],
        ];

        $nutritionnistes = [
            ['nom' => 'Ferjani', 'prenom' => 'Mouna', 'specialite' => 'Nutrition clinique'],
            ['nom' => 'Kooli', 'prenom' => 'Imen', 'specialite' => 'Diabète et nutrition'],
        ];

        $allUsers = [];

        $admin = $this->createUser(
            nom: 'Admin',
            prenom: 'Principal',
            role: 'ROLE_ADMIN',
            plainPassword: $plainPassword,
            email: 'admin@santea.tn',
            username: 'admin.santea',
            city: 'Tunis',
            address: '1 Avenue de Carthage'
        );
        $admin->setAdminApproved(true);
        $admin->setEmailVerified(true);
        $manager->persist($admin);
        $allUsers[] = $admin;

        foreach ($patients as $index => $row) {
            $user = $this->createUser(
                nom: $row['nom'],
                prenom: $row['prenom'],
                role: 'ROLE_PATIENT',
                plainPassword: $plainPassword,
                email: sprintf('patient%d@santea.tn', $index + 1),
                username: sprintf('patient.%d', $index + 1),
                city: $cities[$index % count($cities)],
                address: $adresses[$index % count($adresses)]
            );

            $patient = new Patient();
            $patient->setUser($user);
            $patient->setNumeroSecu('TN-P-' . str_pad((string) ($index + 1), 6, '0', STR_PAD_LEFT));
            $patient->setGroupeSanguin(['A+', 'A-', 'B+', 'O+', 'AB+'][$index % 5]);
            $patient->setAllergies($index % 3 === 0 ? 'Pollen' : null);

            $manager->persist($user);
            $manager->persist($patient);
            $allUsers[] = $user;

            $this->attachSubscriptionIfNeeded($manager, $user, 'ROLE_PATIENT', $index % 4 !== 0);
        }

        foreach ($medecins as $index => $row) {
            $user = $this->createUser(
                nom: $row['nom'],
                prenom: $row['prenom'],
                role: 'ROLE_MEDECIN',
                plainPassword: $plainPassword,
                email: sprintf('medecin%d@santea.tn', $index + 1),
                username: sprintf('medecin.%d', $index + 1),
                city: $cities[$index % count($cities)],
                address: $adresses[$index % count($adresses)]
            );

            $medecin = new Medecin();
            $medecin->setUser($user);
            $medecin->setSpecialite($row['specialite']);
            $medecin->setNumeroOrdre('OM-' . str_pad((string) ($index + 1), 5, '0', STR_PAD_LEFT));
            $medecin->setCabinetAdresse($adresses[$index % count($adresses)]);
            $medecin->setCabinetVille($cities[$index % count($cities)]);
            $medecin->setTelephoneCabinet('+216 5' . random_int(1000000, 9999999));
            $medecin->setTarifConsultation((string) (70 + ($index * 10)));

            $manager->persist($user);
            $manager->persist($medecin);
            $allUsers[] = $user;

            $this->attachSubscriptionIfNeeded($manager, $user, 'ROLE_MEDECIN', true);
        }

        foreach ($pharmaciens as $index => $row) {
            $user = $this->createUser(
                nom: $row['nom'],
                prenom: $row['prenom'],
                role: 'ROLE_PHARMACIEN',
                plainPassword: $plainPassword,
                email: sprintf('pharmacien%d@santea.tn', $index + 1),
                username: sprintf('pharmacien.%d', $index + 1),
                city: $cities[$index % count($cities)],
                address: $adresses[$index % count($adresses)]
            );

            $pharmacien = new Pharmacien();
            $pharmacien->setUser($user);
            $pharmacien->setNumeroOrdre('OP-' . str_pad((string) ($index + 1), 5, '0', STR_PAD_LEFT));
            $pharmacien->setPharmacieNom($row['pharmacie']);
            $pharmacien->setPharmacieAdresse($adresses[$index % count($adresses)] . ', ' . $cities[$index % count($cities)]);

            $manager->persist($user);
            $manager->persist($pharmacien);
            $allUsers[] = $user;

            $this->attachSubscriptionIfNeeded($manager, $user, 'ROLE_PHARMACIEN', true);
        }

        foreach ($coachs as $index => $row) {
            $user = $this->createUser(
                nom: $row['nom'],
                prenom: $row['prenom'],
                role: 'ROLE_COACH',
                plainPassword: $plainPassword,
                email: sprintf('coach%d@santea.tn', $index + 1),
                username: sprintf('coach.%d', $index + 1),
                city: $cities[$index % count($cities)],
                address: $adresses[$index % count($adresses)]
            );

            $coach = new CoachSportif();
            $coach->setUser($user);
            $coach->setSpecialite($row['specialite']);
            $coach->setJustificatif('certificat-coach-' . ($index + 1) . '.pdf');

            $manager->persist($user);
            $manager->persist($coach);
            $allUsers[] = $user;

            $this->attachSubscriptionIfNeeded($manager, $user, 'ROLE_COACH', true);
        }

        foreach ($nutritionnistes as $index => $row) {
            $user = $this->createUser(
                nom: $row['nom'],
                prenom: $row['prenom'],
                role: 'ROLE_NUTRITIONNISTE',
                plainPassword: $plainPassword,
                email: sprintf('nutrition%d@santea.tn', $index + 1),
                username: sprintf('nutrition.%d', $index + 1),
                city: $cities[$index % count($cities)],
                address: $adresses[$index % count($adresses)]
            );

            $nutrition = new Nutritionniste();
            $nutrition->setUser($user);
            $nutrition->setSpecialite($row['specialite']);
            $nutrition->setJustificatif('certificat-nutrition-' . ($index + 1) . '.pdf');

            $manager->persist($user);
            $manager->persist($nutrition);
            $allUsers[] = $user;

            $this->attachSubscriptionIfNeeded($manager, $user, 'ROLE_NUTRITIONNISTE', true);
        }

        $manager->flush();
    }

    private function createUser(
        string $nom,
        string $prenom,
        string $role,
        string $plainPassword,
        string $email,
        string $username,
        string $city,
        string $address
    ): User {
        $user = new User();
        $user->setNom($nom);
        $user->setPrenom($prenom);
        $user->setEmail($email);
        $user->setUsername($username);
        $user->setRole($role);
        $user->setAdresse($address . ', ' . $city);
        $user->setTelephone('+216 2' . random_int(1000000, 9999999));
        $user->setDateNaissance(new \DateTime(sprintf('-%d years', random_int(22, 58))));
        $user->setEmailVerified(true);
        $user->setAdminApproved(true);
        $user->setPassword($this->passwordHasher->hashPassword($user, $plainPassword));
        $user->forceUpdatedAt(new \DateTimeImmutable());

        return $user;
    }

    private function attachSubscriptionIfNeeded(ObjectManager $manager, User $user, string $role, bool $active): void
    {
        if (!$active) {
            $user->setSubscriptionStatus('PENDING');
            $user->setSubscriptionType(null);
            $user->defineSubscriptionEndAt(null);
            return;
        }

        $start = new \DateTimeImmutable('-' . random_int(5, 60) . ' days');
        $end = $start->modify('+1 month');

        $abonnement = new Abonnement();
        $abonnement->setNom($this->labelFromRole($role));
        $abonnement->setTypeAbonnement($role);
        $abonnement->setPrix('10.00');
        $abonnement->setDureeMois(1);
        $abonnement->setDateDebut(\DateTime::createFromImmutable($start));
        $abonnement->setDateFin(\DateTime::createFromImmutable($end));
        $abonnement->setStatut('actif');
        $abonnement->setDescription('Abonnement de démonstration réaliste pour environnement de développement.');
        $abonnement->setAvantages('Accès module métier, IA, support prioritaire');
        $abonnement->setUser($user);

        $user->setSubscriptionStatus('ACTIVE');
        $user->setSubscriptionType($role);
        $user->defineSubscriptionEndAt($end);

        $manager->persist($abonnement);
    }

    private function labelFromRole(string $role): string
    {
        return match ($role) {
            'ROLE_MEDECIN' => 'Médecin',
            'ROLE_PHARMACIEN' => 'Pharmacien',
            'ROLE_COACH' => 'Coach sportif',
            'ROLE_NUTRITIONNISTE' => 'Nutritionniste',
            'ROLE_PATIENT' => 'Patient',
            default => 'Utilisateur',
        };
    }
}
