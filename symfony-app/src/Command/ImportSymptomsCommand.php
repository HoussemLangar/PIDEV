<?php

namespace App\Command;

use App\Entity\SymptomeListe;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:import-symptoms',
    description: 'Import symptoms from CSV dataset',
)]
class ImportSymptomsCommand extends Command
{
    public function __construct(
        private EntityManagerInterface $entityManager,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        // Chemin vers votre fichier CSV
        $csvFile = dirname(__DIR__, 2) . '/dataset_symptomes_1000_plus.csv';

        if (!file_exists($csvFile)) {
            $io->error("Fichier CSV non trouvé : $csvFile");
            return Command::FAILURE;
        }

        $handle = fopen($csvFile, 'r');
        if ($handle === false) {
            $io->error("Impossible d'ouvrir le fichier CSV");
            return Command::FAILURE;
        }

        $io->info("Import des symptômes depuis : $csvFile");

        // Skip header row if present
        $firstLine = fgetcsv($handle, 1000, ',');
        if ($firstLine && (strtolower($firstLine[0]) === 'nom' || strtolower($firstLine[0]) === 'name')) {
            // Header detected, continue with next line
        } else {
            // No header, rewind to beginning
            rewind($handle);
        }

        $imported = 0;
        $errors = 0;
        $batch = 50; // Process in batches for better performance

        $io->progressStart();

        while (($data = fgetcsv($handle, 1000, ',')) !== false) {
            $nom = trim($data[0]);
            $categorie = isset($data[1]) ? trim($data[1]) : null;

            if (!empty($nom)) {
                try {
                    // Check if symptom already exists
                    $existing = $this->entityManager->getRepository(SymptomeListe::class)
                        ->findOneBy(['nom' => $nom]);

                    if (!$existing) {
                        $symptome = new SymptomeListe();
                        $symptome->setNom($nom);
                        $symptome->setCategorie($categorie ?: null);
                        // Don't set createdAt, let the constructor handle it

                        $this->entityManager->persist($symptome);
                        $imported++;

                        // Flush in batches
                        if ($imported % $batch === 0) {
                            $this->entityManager->flush();
                            // Don't clear the entity manager, it causes issues
                        }
                    }

                    $io->progressAdvance();

                } catch (\Exception $e) {
                    $io->warning("Erreur pour '$nom': " . $e->getMessage());
                    $errors++;

                    // Skip if there's an error but continue with the import
                    continue;
                }
            }
        }

        fclose($handle);

        // Final flush
        try {
            $this->entityManager->flush();
            $io->progressFinish();
            
            $io->success("Import terminé ! $imported symptômes importés.");
            
            if ($errors > 0) {
                $io->warning("$errors erreurs rencontrées.");
            }
            
            return Command::SUCCESS;
            
        } catch (\Exception $e) {
            $io->error("Erreur lors de la sauvegarde : " . $e->getMessage());
            return Command::FAILURE;
        }
    }
}