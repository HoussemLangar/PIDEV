<?php
// import_csv_symptomes.php

require_once 'vendor/autoload.php';

use App\Entity\SymptomeListe;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Dotenv\Dotenv;

$dotenv = new Dotenv();
$dotenv->load(__DIR__.'/.env');

$kernel = new \App\Kernel($_SERVER['APP_ENV'] ?? 'dev', false);
$kernel->boot();

$entityManager = $kernel->getContainer()->get(EntityManagerInterface::class);

// Chemin vers votre fichier CSV
$csvFile = __DIR__ . '/dataset_symptomes_1000_plus.csv';

if (!file_exists($csvFile)) {
    die("Fichier CSV non trouvé : $csvFile\n");
}

$handle = fopen($csvFile, 'r');
if ($handle === false) {
    die("Impossible d'ouvrir le fichier CSV\n");
}

// Skip header row
fgetcsv($handle, 1000, ',');

$imported = 0;
$errors = 0;

while (($data = fgetcsv($handle, 1000, ',')) !== false) {
    if (count($data) >= 2) {
        $nom = trim($data[0]);
        $categorie = trim($data[1]);

        if (!empty($nom)) {
            try {
                $symptome = new SymptomeListe();
                $symptome->setNom($nom);
                $symptome->setCategorie($categorie ?: null);
                $entityManager->persist($symptome);
                $imported++;
            } catch (Exception $e) {
                echo "Erreur pour '$nom': " . $e->getMessage() . "\n";
                $errors++;
            }
        }
    }
}

fclose($handle);

try {
    $entityManager->flush();
    echo "Import terminé ! $imported symptômes importés";
    if ($errors > 0) {
        echo ", $errors erreurs";
    }
    echo ".\n";
} catch (Exception $e) {
    echo "Erreur lors de la sauvegarde : " . $e->getMessage() . "\n";
}