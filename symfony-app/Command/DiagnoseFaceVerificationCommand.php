<?php

namespace App\Command;

use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;
use Symfony\Component\EventDispatcher\EventDispatcherInterface;
use Symfony\Component\HttpKernel\KernelEvents;

#[AsCommand(
    name: 'app:diagnose-face-verification',
    description: 'Diagnostiquer le système de vérification faciale',
)]
class DiagnoseFaceVerificationCommand extends Command
{
    public function __construct(
        private EventDispatcherInterface $eventDispatcher
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        
        $io->title('🔍 Diagnostic du système de vérification faciale');

        // 1. Vérifier les listeners sur kernel.request
        $io->section('1. Vérification des Event Subscribers');
        
        $listeners = $this->eventDispatcher->getListeners(KernelEvents::REQUEST);
        
        $faceVerificationFound = false;
        foreach ($listeners as $listener) {
            if (is_array($listener)) {
                $class = is_object($listener[0]) ? get_class($listener[0]) : $listener[0];
                if (str_contains($class, 'FaceVerification')) {
                    $io->success("✅ FaceVerificationSubscriber trouvé: $class");
                    $faceVerificationFound = true;
                }
            }
        }
        
        if (!$faceVerificationFound) {
            $io->error('❌ FaceVerificationSubscriber NON TROUVÉ');
            $io->warning('Le subscriber n\'est pas enregistré. Vérifiez:');
            $io->listing([
                'Le fichier src/EventSubscriber/FaceVerificationSubscriber.php existe',
                'La classe implémente EventSubscriberInterface',
                'La méthode getSubscribedEvents() retourne bien les événements',
                'L\'autowiring est activé dans services.yaml',
                'Le cache a été vidé (php bin/console cache:clear)'
            ]);
            return Command::FAILURE;
        }

        // 2. Vérifier les fichiers
        $io->section('2. Vérification des fichiers');
        
        $files = [
            'src/EventSubscriber/FaceVerificationSubscriber.php',
            'src/EventListener/LoginSuccessListener.php',
            'src/EventListener/LogoutListener.php',
            'src/Controller/SecurityController.php',
            'templates/security/face_verification.html.twig',
        ];
        
        foreach ($files as $file) {
            if (file_exists($file)) {
                $io->success("✅ $file");
            } else {
                $io->error("❌ $file manquant");
            }
        }

        // 3. Vérifier la configuration
        $io->section('3. Vérification de la configuration');
        
        if (file_exists('config/packages/security.yaml')) {
            $io->success('✅ config/packages/security.yaml existe');
            
            $content = file_get_contents('config/packages/security.yaml');
            if (str_contains($content, 'admin_face_verification')) {
                $io->success('✅ Routes de vérification faciale configurées dans security.yaml');
            } else {
                $io->warning('⚠️ Routes de vérification faciale non trouvées dans security.yaml');
            }
        } else {
            $io->error('❌ config/packages/security.yaml manquant');
        }

        // 4. Instructions finales
        $io->section('4. Étapes suivantes');
        
        if ($faceVerificationFound) {
            $io->success('✅ Le système semble correctement configuré');
            
            $io->note('Test manuel recommandé:');
            $io->listing([
                '1. Videz le cache: php bin/console cache:clear',
                '2. Déconnectez-vous complètement',
                '3. Reconnectez-vous en tant qu\'admin',
                '4. Vérifiez la redirection vers /admin/face-verification',
                '5. Essayez d\'accéder à /admin/dashboard directement (sans vérification)',
                '6. Vous devriez être redirigé vers la vérification faciale'
            ]);
        } else {
            $io->error('❌ Configuration incomplète - suivez le GUIDE_COMPLET_FACE_VERIFICATION.md');
        }

        $io->newLine();
        $io->info('Pour voir tous les listeners: php bin/console debug:event-dispatcher kernel.request');
        
        return Command::SUCCESS;
    }
}