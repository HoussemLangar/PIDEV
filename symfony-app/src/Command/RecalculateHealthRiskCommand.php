<?php

namespace App\Command;

use App\Entity\User;
use App\Service\RiskPredictionService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:risk:recalculate',
    description: 'Recalculate health risk predictions for patients',
)]
class RecalculateHealthRiskCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly RiskPredictionService $riskPredictionService
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        $users = $this->em->getRepository(User::class)->findBy(['role' => 'ROLE_PATIENT']);
        $count = 0;
        foreach ($users as $user) {
            if ($user->getPatient() === null) {
                continue;
            }
            $this->riskPredictionService->recalculateForUser($user);
            $count++;
        }

        $io->success(sprintf('Risk predictions recalculated for %d patient(s).', $count));

        return Command::SUCCESS;
    }
}

