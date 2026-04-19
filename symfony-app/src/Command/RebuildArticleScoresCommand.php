<?php

namespace App\Command;

use App\Entity\Contenu;
use App\Repository\ArticleScoreRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:content:scores:rebuild',
    description: 'Recalcule les scores de tous les contenus dans article_scores',
)]
class RebuildArticleScoresCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $entityManager,
        private readonly ArticleScoreRepository $articleScoreRepository
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        $rows = $this->entityManager->createQueryBuilder()
            ->select('c.id AS id')
            ->from(Contenu::class, 'c')
            ->orderBy('c.id', 'ASC')
            ->getQuery()
            ->getArrayResult();

        if ($rows === []) {
            $io->warning('Aucun contenu trouvé.');
            return Command::SUCCESS;
        }

        $total = count($rows);
        $io->info(sprintf('Recalcul des scores pour %d contenu(s)...', $total));
        $io->progressStart($total);

        foreach ($rows as $index => $row) {
            $contenuId = (int) ($row['id'] ?? 0);
            if ($contenuId <= 0) {
                $io->progressAdvance();
                continue;
            }

            $this->articleScoreRepository->updateScore($contenuId, false);

            // Flush par lot pour limiter la mémoire.
            if ((($index + 1) % 50) === 0) {
                $this->entityManager->flush();
            }

            $io->progressAdvance();
        }

        $this->entityManager->flush();
        $io->progressFinish();
        $io->success('Recalcul terminé.');

        return Command::SUCCESS;
    }
}
