<?php
// import_symptomes.php - Place this file in your project root

require_once 'vendor/autoload.php';

use App\Entity\SymptomeListe;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Dotenv\Dotenv;

$dotenv = new Dotenv();
$dotenv->load(__DIR__.'/.env');

$kernel = new \App\Kernel($_SERVER['APP_ENV'] ?? 'dev', false);
$kernel->boot();

$entityManager = $kernel->getContainer()->get(EntityManagerInterface::class);

// Vos données de symptômes
$symptomes = [
    ['nom' => 'Maux de tête', 'categorie' => 'Neurologique'],
    ['nom' => 'Nausées', 'categorie' => 'Digestif'],
    ['nom' => 'Fatigue', 'categorie' => 'Général'],
    ['nom' => 'Douleurs abdominales', 'categorie' => 'Digestif'],
    ['nom' => 'Fièvre', 'categorie' => 'Général'],
    ['nom' => 'Toux', 'categorie' => 'Respiratoire'],
    ['nom' => 'Insomnie', 'categorie' => 'Neurologique'],
    ['nom' => 'Douleurs musculaires', 'categorie' => 'Musculaire'],
    ['nom' => 'Maux de gorge', 'categorie' => 'ORL'],
    ['nom' => 'Vertiges', 'categorie' => 'Neurologique'],
    // Ajoutez tous vos symptômes ici
];

foreach ($symptomes as $data) {
    $symptome = new SymptomeListe();
    $symptome->setNom($data['nom']);
    $symptome->setCategorie($data['categorie']);

    $entityManager->persist($symptome);
}

$entityManager->flush();

echo "Import terminé ! " . count($symptomes) . " symptômes ajoutés.\n";