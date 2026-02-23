<?php
namespace App\Controller;

use App\Entity\User;
use App\Entity\UserSession;
use App\Form\AdminUserType;
use App\Form\AdminUserResetPasswordType;
use App\Entity\Facture;
use App\Entity\AccompanimentPlan;
use App\Entity\CoachSportif;
use App\Entity\Nutritionniste;
use App\Repository\AbonnementRepository;
use App\Repository\ContenuRepository;
use App\Repository\SuspiciousLoginRepository;
use App\Repository\UserScoreHistoryRepository;
use App\Repository\UserSessionRepository;
use App\Repository\UserRepository;
use App\Service\Ai\AiGatewayService;
use App\Service\UserAiScoreService;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\Pagination\Paginator;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Form\FormError;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\HttpFoundation\StreamedResponse;

#[IsGranted('ROLE_ADMIN')]
class AdminController extends AbstractController
{
    #[Route('/admin/voice-assistant/execute', name: 'admin_voice_assistant_execute', methods: ['POST'])]
    public function voiceAssistantExecute(
        Request $request,
        UserRepository $userRepository,
        UserAiScoreService $userAiScoreService,
        EntityManagerInterface $em,
        AiGatewayService $aiGatewayService
    ): JsonResponse {
        $payload = json_decode((string) $request->getContent(), true);
        $command = trim((string) ($payload['command'] ?? ''));

        if ($command === '') {
            return new JsonResponse([
                'success' => false,
                'type' => 'message',
                'message' => 'Commande vide.',
            ], 400);
        }

        $normalized = mb_strtolower($command);
        $normalized = strtr($normalized, [
            'é' => 'e', 'è' => 'e', 'ê' => 'e', 'ë' => 'e',
            'à' => 'a', 'â' => 'a',
            'î' => 'i', 'ï' => 'i',
            'ô' => 'o',
            'ù' => 'u', 'û' => 'u', 'ü' => 'u',
            'ç' => 'c',
            '’' => '\'',
        ]);
        $normalized = preg_replace('/[^a-z0-9@._%+\-\s\']/u', ' ', $normalized) ?? $normalized;
        $normalized = preg_replace('/\s+/', ' ', $normalized) ?? $normalized;

        $intent = null;
        $target = null;
        $roleInput = null;

        if ($aiGatewayService->isEnabled()) {
            $aiIntent = $aiGatewayService->askForJson(
                'Tu es un classifieur d\'intention pour un assistant vocal admin Symfony. Réponds STRICTEMENT en JSON avec {"intent":"...","target":"...","role":"..."}. Intent possibles: users, users_stats, score_history, subscriptions, revenues, appointments, validation, security, export_dashboard, dashboard, reload, autopilot, ban_user, unban_user, set_role_user, unknown.',
                'Commande: ' . $command
            );
            if (is_array($aiIntent)) {
                $intent = ($aiIntent['intent'] ?? null) ?: null;
                $target = isset($aiIntent['target']) ? trim((string) $aiIntent['target']) : null;
                $roleInput = isset($aiIntent['role']) ? trim((string) $aiIntent['role']) : null;
            }
        }

        if ($intent === null) {
            if (str_contains($normalized, 'tout automatiquement') || str_contains($normalized, 'auto pilote') || str_contains($normalized, 'autopilot')) {
                $intent = 'autopilot';
            } elseif (preg_match('/\b(debannir|deban|retirer le ban|lever le ban)\b/', $normalized)) {
                $intent = 'unban_user';
            } elseif (str_contains($normalized, 'historique') && str_contains($normalized, 'scor')) {
                $intent = 'score_history';
            } elseif (preg_match('/\b(bannir|banner|ban|suspendre)\b/', $normalized)) {
                $intent = 'ban_user';
            } elseif ((str_contains($normalized, 'modifier') || str_contains($normalized, 'changer')) && str_contains($normalized, 'role')) {
                $intent = 'set_role_user';
            } elseif (str_contains($normalized, 'utilisateur')) {
                $intent = 'users';
            } elseif (str_contains($normalized, 'abonnement')) {
                $intent = 'subscriptions';
            } elseif (str_contains($normalized, 'revenu') || str_contains($normalized, 'facture')) {
                $intent = 'revenues';
            } elseif (str_contains($normalized, 'rendez') || str_contains($normalized, 'appointment')) {
                $intent = 'appointments';
            } elseif (str_contains($normalized, 'validation')) {
                $intent = 'validation';
            } elseif (str_contains($normalized, 'securite') || str_contains($normalized, 'suspect')) {
                $intent = 'security';
            } elseif (str_contains($normalized, 'export')) {
                $intent = 'export_dashboard';
            } elseif (str_contains($normalized, 'dashboard')) {
                $intent = 'dashboard';
            } elseif (str_contains($normalized, 'rafraich') || str_contains($normalized, 'actualise') || str_contains($normalized, 'refresh')) {
                $intent = 'reload';
            } else {
                $intent = 'unknown';
            }
        }

        // Priorité absolue aux actions sensibles utilisateur (ne pas laisser l'intention AI les écraser)
        if (preg_match('/\b(debannir|deban|retirer le ban|lever le ban)\b/', $normalized)) {
            $intent = 'unban_user';
        } elseif (preg_match('/\b(bannir|banner|ban|suspendre|bloquer)\b/', $normalized)) {
            $intent = 'ban_user';
        } elseif ((str_contains($normalized, 'modifier') || str_contains($normalized, 'changer') || str_contains($normalized, 'mettre'))
            && (str_contains($normalized, 'role') || str_contains($normalized, 'profil'))
        ) {
            $intent = 'set_role_user';
        }

        if (($intent === 'ban_user' || $intent === 'unban_user' || $intent === 'set_role_user') && ($target === null || $target === '')) {
            if (preg_match('/([a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,})/i', $normalized, $m)) {
                $target = $m[1];
            } elseif (preg_match('/\b(?:utilisateur|user|compte)\s+([a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,})/i', $normalized, $m)) {
                $target = $m[1];
            } elseif (preg_match('/\b(?:utilisateur|user|compte)\s+([a-z0-9._%+\-]{3,})\b/i', $normalized, $m)) {
                $target = $m[1];
            } elseif (preg_match('/\butilisateur\s+(.+)$/i', $normalized, $m)) {
                $target = trim($m[1]);
            } elseif (preg_match('/\b(?:de|du|pour)\s+l?\'?utilisateur\s+(.+)$/i', $normalized, $m)) {
                $target = trim($m[1]);
            } elseif (preg_match('/\b(id\s*#?\s*\d+)\b/i', $normalized, $m)) {
                $target = preg_replace('/\D+/', '', $m[1]) ?: null;
            }
        }

        if ($intent === 'set_role_user' && ($roleInput === null || $roleInput === '')) {
            if (preg_match('/\b(?:en|vers|role|profil)\s+(admin|patient|medecin|pharmacien|coach|nutritionniste|utilisateur|user)\b/i', $normalized, $mRole)) {
                $roleInput = $mRole[1];
            } elseif (preg_match('/\b(admin|patient|medecin|pharmacien|coach|nutritionniste|utilisateur|user)\b/i', $normalized, $mRole)) {
                $roleInput = $mRole[1];
            }
        }

        $resolveUser = function (?string $value) use ($userRepository): array {
            $value = trim((string) $value);
            if ($value === '') {
                return ['user' => null, 'closest' => null, 'score' => -INF];
            }

            $normalizePersonString = static function (string $text): string {
                $text = mb_strtolower($text);
                $text = strtr($text, [
                    'é' => 'e', 'è' => 'e', 'ê' => 'e', 'ë' => 'e',
                    'à' => 'a', 'â' => 'a',
                    'î' => 'i', 'ï' => 'i',
                    'ô' => 'o',
                    'ù' => 'u', 'û' => 'u', 'ü' => 'u',
                    'ç' => 'c',
                    '’' => '\'',
                ]);
                $text = preg_replace('/[^a-z0-9\s]/u', ' ', $text) ?? $text;
                $text = preg_replace('/\s+/', ' ', trim($text)) ?? trim($text);
                return $text;
            };

            $squeezeRepeatedChars = static function (string $text): string {
                return preg_replace('/(.)\1+/u', '$1', $text) ?? $text;
            };

            $targetNormalized = $normalizePersonString($value);
            $targetSqueezed = $squeezeRepeatedChars($targetNormalized);

            if (ctype_digit($value)) {
                $found = $userRepository->find((int) $value);
                return ['user' => ($found instanceof User ? $found : null), 'closest' => null, 'score' => 100];
            }

            if (str_contains($value, '@')) {
                $found = $userRepository->findByEmail($value);
                return ['user' => ($found instanceof User ? $found : null), 'closest' => null, 'score' => 100];
            }

            $needle = $targetNormalized;
            $candidates = $userRepository->createQueryBuilder('u')
                ->andWhere('u.deletedAt IS NULL')
                ->andWhere('LOWER(u.email) LIKE :q OR LOWER(u.username) LIKE :q OR LOWER(u.nom) LIKE :q OR LOWER(u.prenom) LIKE :q')
                ->setParameter('q', '%' . $needle . '%')
                ->setMaxResults(1)
                ->getQuery()
                ->getResult();

            $first = $candidates[0] ?? null;
            if ($first instanceof User) {
                return ['user' => $first, 'closest' => $first, 'score' => 100];
            }

            $pool = $userRepository->createQueryBuilder('u')
                ->andWhere('u.deletedAt IS NULL')
                ->getQuery()
                ->getResult();

            $bestUser = null;
            $bestScore = -INF;

            $targetTokens = array_values(array_filter(explode(' ', $targetSqueezed)));

            foreach ($pool as $candidate) {
                if (!$candidate instanceof User) {
                    continue;
                }

                $fullName = trim(($candidate->getPrenom() ?? '') . ' ' . ($candidate->getNom() ?? ''));
                $fullNameReverse = trim(($candidate->getNom() ?? '') . ' ' . ($candidate->getPrenom() ?? ''));
                $candidateNormalized = $normalizePersonString($fullName);
                $candidateSqueezed = $squeezeRepeatedChars($candidateNormalized);
                $candidateReverseNormalized = $normalizePersonString($fullNameReverse);
                $candidateReverseSqueezed = $squeezeRepeatedChars($candidateReverseNormalized);

                $score = 0;
                if ($candidateNormalized === $targetNormalized) {
                    $score = 100;
                } elseif ($candidateSqueezed === $targetSqueezed) {
                    $score = 98;
                } elseif ($candidateReverseSqueezed === $targetSqueezed) {
                    $score = 96;
                } else {
                    $distance = levenshtein($targetSqueezed, $candidateSqueezed);
                    $maxLen = max(strlen($targetSqueezed), strlen($candidateSqueezed), 1);
                    $similarity = 1 - ($distance / $maxLen);
                    $score = (int) round($similarity * 100);
                }

                if (str_contains($candidateNormalized, $targetNormalized) || str_contains($targetNormalized, $candidateNormalized)) {
                    $score += 8;
                }

                if (!empty($targetTokens)) {
                    $candidateTokenString = $candidateSqueezed;
                    $matchedTokens = 0;
                    foreach ($targetTokens as $token) {
                        if (strlen($token) < 2) {
                            continue;
                        }
                        if (str_contains($candidateTokenString, $token)) {
                            $matchedTokens++;
                        }
                    }
                    $score += $matchedTokens * 6;
                }

                $usernameNormalized = $squeezeRepeatedChars($normalizePersonString((string) $candidate->getUsername()));
                if ($usernameNormalized !== '' && (str_contains($usernameNormalized, $targetSqueezed) || str_contains($targetSqueezed, $usernameNormalized))) {
                    $score += 12;
                }

                if ($score > $bestScore) {
                    $bestScore = $score;
                    $bestUser = $candidate;
                }
            }

            return [
                'user' => ($bestUser instanceof User && $bestScore >= 62) ? $bestUser : null,
                'closest' => $bestUser instanceof User ? $bestUser : null,
                'score' => $bestScore,
            ];
        };

        $normalizeRole = static function (?string $value): ?string {
            $raw = mb_strtolower(trim((string) $value));
            return match ($raw) {
                'admin', 'role_admin' => 'ROLE_ADMIN',
                'patient', 'role_patient' => 'ROLE_PATIENT',
                'medecin', 'médecin', 'doctor', 'role_medecin' => 'ROLE_MEDECIN',
                'pharmacien', 'role_pharmacien' => 'ROLE_PHARMACIEN',
                'coach', 'role_coach' => 'ROLE_COACH',
                'nutritionniste', 'role_nutritionniste' => 'ROLE_NUTRITIONNISTE',
                'utilisateur', 'user', 'role_user' => 'ROLE_USER',
                default => null,
            };
        };

        $routeByIntent = [
            'users' => 'admin_users',
            'users_stats' => 'admin_users_stats',
            'score_history' => 'admin_users_score_history',
            'subscriptions' => 'admin_subscriptions',
            'revenues' => 'admin_revenues',
            'appointments' => 'admin_appointments',
            'validation' => 'admin_validation',
            'security' => 'admin_security_suspicious',
            'dashboard' => 'admin_dashboard',
        ];

        if (isset($routeByIntent[$intent])) {
            return new JsonResponse([
                'success' => true,
                'type' => 'navigate',
                'url' => $this->generateUrl($routeByIntent[$intent]),
                'message' => 'Action exécutée.',
            ]);
        }

        if ($intent === 'export_dashboard') {
            return new JsonResponse([
                'success' => true,
                'type' => 'download',
                'url' => $this->generateUrl('admin_dashboard_export'),
                'message' => 'Export déclenché.',
            ]);
        }

        if ($intent === 'reload') {
            return new JsonResponse([
                'success' => true,
                'type' => 'reload',
                'message' => 'Rechargement en cours.',
            ]);
        }

        if ($intent === 'ban_user') {
            $resolved = $resolveUser($target);
            $targetUser = $resolved['user'] ?? null;
            if (!$targetUser instanceof User) {
                $closest = $resolved['closest'] ?? null;
                $hint = $closest instanceof User ? sprintf(' Utilisateur proche détecté: %s %s (%s).', $closest->getPrenom(), $closest->getNom(), $closest->getEmail()) : '';
                return new JsonResponse([
                    'success' => false,
                    'type' => 'message',
                    'message' => 'Utilisateur introuvable pour bannissement.' . $hint,
                ], 404);
            }

            $targetUser->setIsBanned(true);
            $targetUser->setBanReason('Bannissement via assistant admin');
            $targetUser->setBanUntil(null);
            $targetUser->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();

            return new JsonResponse([
                'success' => true,
                'type' => 'message',
                'message' => sprintf('Utilisateur %s banni avec succès.', $targetUser->getEmail()),
            ]);
        }

        if ($intent === 'unban_user') {
            $resolved = $resolveUser($target);
            $targetUser = $resolved['user'] ?? null;
            if (!$targetUser instanceof User) {
                $closest = $resolved['closest'] ?? null;
                $hint = $closest instanceof User ? sprintf(' Utilisateur proche détecté: %s %s (%s).', $closest->getPrenom(), $closest->getNom(), $closest->getEmail()) : '';
                return new JsonResponse([
                    'success' => false,
                    'type' => 'message',
                    'message' => 'Utilisateur introuvable pour débannissement.' . $hint,
                ], 404);
            }

            $targetUser->setIsBanned(false);
            $targetUser->setBanReason(null);
            $targetUser->setBanUntil(null);
            $targetUser->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();

            return new JsonResponse([
                'success' => true,
                'type' => 'message',
                'message' => sprintf('Utilisateur %s débanni avec succès.', $targetUser->getEmail()),
            ]);
        }

        if ($intent === 'set_role_user') {
            $resolved = $resolveUser($target);
            $targetUser = $resolved['user'] ?? null;
            if (!$targetUser instanceof User) {
                $closest = $resolved['closest'] ?? null;
                $hint = $closest instanceof User ? sprintf(' Utilisateur proche détecté: %s %s (%s).', $closest->getPrenom(), $closest->getNom(), $closest->getEmail()) : '';
                return new JsonResponse([
                    'success' => false,
                    'type' => 'message',
                    'message' => 'Utilisateur introuvable pour changement de rôle.' . $hint,
                ], 404);
            }

            $newRole = $normalizeRole($roleInput);
            if ($newRole === null) {
                return new JsonResponse([
                    'success' => false,
                    'type' => 'message',
                    'message' => 'Rôle non reconnu. Exemples: admin, patient, medecin, pharmacien, coach, nutritionniste.',
                ], 422);
            }

            $targetUser->setRole($newRole);
            $targetUser->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();

            return new JsonResponse([
                'success' => true,
                'type' => 'message',
                'message' => sprintf('Rôle de %s modifié en %s.', $targetUser->getEmail(), $newRole),
            ]);
        }

        if ($intent === 'autopilot') {
            $users = $userRepository->createQueryBuilder('u')
                ->andWhere('u.deletedAt IS NULL')
                ->getQuery()
                ->getResult();

            $approvedCount = 0;
            $historyCount = 0;
            $freeMonthCount = 0;
            $now = new \DateTimeImmutable();

            foreach ($users as $user) {
                if (!$user instanceof User) {
                    continue;
                }

                if (!$user->isAdminApproved()) {
                    $user->setAdminApproved(true);
                    $user->setUpdatedAt($now);
                    $approvedCount++;
                }

                if ($userAiScoreService->grantFreeMonthIfEligible($user)) {
                    $freeMonthCount++;
                }

                if ($userAiScoreService->recordDailyHistory($user)) {
                    $historyCount++;
                }
            }

            $em->flush();

            return new JsonResponse([
                'success' => true,
                'type' => 'message',
                'message' => sprintf(
                    'Mode automatique exécuté : %d validations approuvées, %d mois gratuits attribués, %d snapshots de score créés.',
                    $approvedCount,
                    $freeMonthCount,
                    $historyCount
                ),
            ]);
        }

        return new JsonResponse([
            'success' => false,
            'type' => 'message',
            'message' => 'Commande non reconnue. Dites par exemple : "fais tout automatiquement".',
        ], 422);
    }

    // ========== DASHBOARD ==========
    #[Route('/admin_dashboard', name: 'admin_dashboard')]
    public function dashboard(
        UserRepository $userRepository,
        AbonnementRepository $abonnementRepository,
        ContenuRepository $contenuRepository,
        SuspiciousLoginRepository $suspiciousLoginRepository,
        EntityManagerInterface $em,
        UserAiScoreService $userAiScoreService
    ): Response
    {
        $userStats = $userRepository->getUserStats();
        $validationStats = $userRepository->getValidationStats();
        $subscriptionStats = $abonnementRepository->getSubscriptionStats();

        $monthStart = new \DateTimeImmutable('first day of this month 00:00:00');
        $monthEnd = $monthStart->modify('first day of next month 00:00:00');

        $monthlyRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->andWhere('f.createdAt >= :from')
            ->andWhere('f.createdAt < :to')
            ->setParameter('from', $monthStart)
            ->setParameter('to', $monthEnd)
            ->getQuery()
            ->getSingleScalarResult();

        $totalRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $totalInvoices = (int) $em->createQueryBuilder()
            ->select('COUNT(f.id)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $pendingPayments = (int) $em->createQueryBuilder()
            ->select('COUNT(u.id)')
            ->from(User::class, 'u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.subscriptionStatus = :status')
            ->setParameter('status', 'PENDING')
            ->getQuery()
            ->getSingleScalarResult();

        // Statistiques principales (demandées)
        $stats = [
            'totalUsers' => $userStats['total'],
            'activeSubscriptions' => $subscriptionStats['active'],
            'monthlyRevenue' => $monthlyRevenue,
            'pendingPayments' => $pendingPayments,
            // autres stats disponibles
            'activeUsers' => $userStats['active'],
            'bannedUsers' => $userStats['banned'],
            'emailVerified' => $validationStats['emailVerified'],
            'adminPending' => $validationStats['adminPending'],
            'totalInvoices' => $totalInvoices,
            'totalRevenue' => $totalRevenue,
            'expiredSubscriptions' => $subscriptionStats['expired'],
            'cancelledSubscriptions' => $subscriptionStats['cancelled'],
        ];

        // Utilisateurs par rôle
        $usersByRole = [
            ['name' => 'Patients', 'count' => $userRepository->countByRole('ROLE_PATIENT'), 'icon' => 'fas fa-user'],
            ['name' => 'Médecins', 'count' => $userRepository->countByRole('ROLE_MEDECIN'), 'icon' => 'fas fa-user-md'],
            ['name' => 'Pharmaciens', 'count' => $userRepository->countByRole('ROLE_PHARMACIEN'), 'icon' => 'fas fa-prescription-bottle'],
            ['name' => 'Coach', 'count' => $userRepository->countByRole('ROLE_COACH'), 'icon' => 'fas fa-dumbbell'],
            ['name' => 'Nutritionnistes', 'count' => $userRepository->countByRole('ROLE_NUTRITIONNISTE'), 'icon' => 'fas fa-apple-alt'],
            ['name' => 'Admins', 'count' => $userRepository->countByRole('ROLE_ADMIN'), 'icon' => 'fas fa-shield-alt'],
        ];

        // Validations en attente (existant)
        $pendingValidations = [
            'professionals' => $validationStats['adminPending'],
            'pharmacies' => 0,
            'articles' => $contenuRepository->countPending(),
            'reports' => 0,
        ];

        // Activité récente (placeholder)
        $recentActivity = [
            'newUsers' => 0,
            'newAppointments' => 0,
            'medicationSearches' => 0,
            'trackerEntries' => 0,
        ];

        $registrationsChart = $userRepository->getRegistrationsChart(30);

        $revenueByTypeRows = $em->createQueryBuilder()
            ->select('a.typeAbonnement as type, COALESCE(SUM(f.montantTtc), 0) as total')
            ->from(Facture::class, 'f')
            ->leftJoin('f.abonnement', 'a')
            ->groupBy('a.typeAbonnement')
            ->getQuery()
            ->getResult();

        $revenueByType = [];
        foreach ($revenueByTypeRows as $row) {
            $revenueByType[$row['type'] ?? ''] = (float) $row['total'];
        }

        // Données pour les graphiques
        $chartData = [
            'registrations' => $registrationsChart,
            'revenue' => [
                'labels' => ['Médecin', 'Pharmacien', 'Coach', 'Nutritionniste', 'Patient'],
                'data' => [
                    $revenueByType['ROLE_MEDECIN'] ?? 0,
                    $revenueByType['ROLE_PHARMACIEN'] ?? 0,
                    $revenueByType['ROLE_COACH'] ?? 0,
                    $revenueByType['ROLE_NUTRITIONNISTE'] ?? 0,
                    $revenueByType['ROLE_PATIENT'] ?? 0,
                ],
            ],
        ];

        $suspiciousRecent = $suspiciousLoginRepository->findRecent(6);
        $suspiciousCount = $suspiciousLoginRepository->countBlocked();

        $allUsers = $userRepository->createQueryBuilder('u')
            ->andWhere('u.deletedAt IS NULL')
            ->getQuery()
            ->getResult();

        $scoreTotal = 0;
        $premiumEligibleCount = 0;
        $freeMonthEligibleCount = 0;
        $freeMonthGrantedCount = 0;
        $freeMonthActiveCount = 0;
        $scoreRows = [];
        $now = new \DateTimeImmutable();

        foreach ($allUsers as $dashboardUser) {
            if (!$dashboardUser instanceof User) {
                continue;
            }

            $score = $userAiScoreService->calculateScore($dashboardUser);
            $scoreTotal += $score;

            if ($score >= 70) {
                $premiumEligibleCount++;
            }
            if ($score >= 95) {
                $freeMonthEligibleCount++;
            }
            if ($dashboardUser->getAiFreeMonthGrantedAt() !== null) {
                $freeMonthGrantedCount++;
            }
            if ($dashboardUser->getAiFreeMonthGrantedAt() !== null
                && $dashboardUser->getSubscriptionEndAt() !== null
                && $dashboardUser->getSubscriptionEndAt() > $now
            ) {
                $freeMonthActiveCount++;
            }

            $scoreRows[] = [
                'user' => $dashboardUser,
                'score' => $score,
                'supportPriorityLabel' => $userAiScoreService->getSupportPriorityLabel($dashboardUser),
            ];
        }

        usort($scoreRows, static fn(array $a, array $b) => $b['score'] <=> $a['score']);

        $usersCount = count($allUsers);
        $aiScoreStats = [
            'averageScore' => $usersCount > 0 ? round($scoreTotal / $usersCount, 1) : 0,
            'premiumEligibleCount' => $premiumEligibleCount,
            'freeMonthEligibleCount' => $freeMonthEligibleCount,
            'freeMonthGrantedCount' => $freeMonthGrantedCount,
            'freeMonthActiveCount' => $freeMonthActiveCount,
        ];
        $topAiUsers = array_slice($scoreRows, 0, 6);

        // Activités récentes pour le tableau
        $recentActivities = [
            [
                'createdAt' => new \DateTime('-5 minutes'),
                'type' => 'registration',
                'typeName' => 'Inscription',
                'icon' => 'fas fa-user-plus',
                'user' => ['name' => 'Marie Dubois', 'avatar' => null],
                'description' => 'Nouvel utilisateur enregistré',
                'status' => 'success',
                'statusText' => 'Complété',
            ],
            [
                'createdAt' => new \DateTime('-12 minutes'),
                'type' => 'appointment',
                'typeName' => 'Rendez-vous',
                'icon' => 'fas fa-calendar-check',
                'user' => ['name' => 'Dr. Jean Martin', 'avatar' => null],
                'description' => 'Rendez-vous confirmé',
                'status' => 'success',
                'statusText' => 'Confirmé',
            ],
            [
                'createdAt' => new \DateTime('-25 minutes'),
                'type' => 'subscription',
                'typeName' => 'Abonnement',
                'icon' => 'fas fa-credit-card',
                'user' => ['name' => 'Sophie Laurent', 'avatar' => null],
                'description' => 'Souscription Premium',
                'status' => 'success',
                'statusText' => 'Actif',
            ],
            [
                'createdAt' => new \DateTime('-35 minutes'),
                'type' => 'validation',
                'typeName' => 'Validation',
                'icon' => 'fas fa-user-check',
                'user' => ['name' => 'Dr. Paul Bernard', 'avatar' => null],
                'description' => 'Compte professionnel en attente',
                'status' => 'pending',
                'statusText' => 'En attente',
            ],
            [
                'createdAt' => new \DateTime('-45 minutes'),
                'type' => 'report',
                'typeName' => 'Signalement',
                'icon' => 'fas fa-flag',
                'user' => ['name' => 'Lucas Petit', 'avatar' => null],
                'description' => 'Signalement d\'un contenu',
                'status' => 'warning',
                'statusText' => 'À traiter',
            ],
        ];

        return $this->render('back/dashboard.html.twig', [
            'stats' => $stats,
            'usersByRole' => $usersByRole,
            'pendingValidations' => $pendingValidations,
            'recentActivity' => $recentActivity,
            'chartData' => $chartData,
            'recentActivities' => $recentActivities,
            'suspiciousRecent' => $suspiciousRecent,
            'suspiciousCount' => $suspiciousCount,
            'aiScoreStats' => $aiScoreStats,
            'topAiUsers' => $topAiUsers,
        ]);
    }

    #[Route('/admin_dashboard/export', name: 'admin_dashboard_export')]
    public function exportDashboardStats(
        UserRepository $userRepository,
        AbonnementRepository $abonnementRepository,
        SuspiciousLoginRepository $suspiciousLoginRepository,
        EntityManagerInterface $em
    ): StreamedResponse {
        $userStats = $userRepository->getUserStats();
        $validationStats = $userRepository->getValidationStats();
        $subscriptionStats = $abonnementRepository->getSubscriptionStats();

        $monthStart = new \DateTimeImmutable('first day of this month 00:00:00');
        $monthEnd = $monthStart->modify('first day of next month 00:00:00');

        $monthlyRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->andWhere('f.createdAt >= :from')
            ->andWhere('f.createdAt < :to')
            ->setParameter('from', $monthStart)
            ->setParameter('to', $monthEnd)
            ->getQuery()
            ->getSingleScalarResult();

        $totalRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $totalInvoices = (int) $em->createQueryBuilder()
            ->select('COUNT(f.id)')
            ->from(Facture::class, 'f')
            ->getQuery()
            ->getSingleScalarResult();

        $pendingPayments = (int) $em->createQueryBuilder()
            ->select('COUNT(u.id)')
            ->from(User::class, 'u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.subscriptionStatus = :status')
            ->setParameter('status', 'PENDING')
            ->getQuery()
            ->getSingleScalarResult();
        $suspiciousCount = $suspiciousLoginRepository->countBlocked();

        $filename = 'dashboard_stats_' . (new \DateTimeImmutable())->format('Ymd_His') . '.csv';
        $response = new StreamedResponse(function () use (
            $userStats,
            $validationStats,
            $subscriptionStats,
            $monthlyRevenue,
            $totalRevenue,
            $totalInvoices,
            $pendingPayments
        ) {
            $handle = fopen('php://output', 'w');
            fputcsv($handle, ['KPI', 'Valeur']);
            fputcsv($handle, ['Utilisateurs total', $userStats['total']]);
            fputcsv($handle, ['Utilisateurs actifs', $userStats['active']]);
            fputcsv($handle, ['Utilisateurs bannis', $userStats['banned']]);
            fputcsv($handle, ['Abonnements actifs', $subscriptionStats['active']]);
            fputcsv($handle, ['Abonnements expirés', $subscriptionStats['expired']]);
            fputcsv($handle, ['Abonnements annulés', $subscriptionStats['cancelled']]);
            fputcsv($handle, ['Paiements en attente', $pendingPayments]);
            fputcsv($handle, ['Revenus du mois (TND)', number_format($monthlyRevenue, 2, '.', '')]);
            fputcsv($handle, ['Revenus total (TND)', number_format($totalRevenue, 2, '.', '')]);
            fputcsv($handle, ['Factures total', $totalInvoices]);
            fputcsv($handle, ['Email confirmés', $validationStats['emailVerified']]);
            fputcsv($handle, ['Admin en attente', $validationStats['adminPending']]);
            fputcsv($handle, ['Connexions suspectes', $suspiciousCount]);
            fclose($handle);
        });

        $response->headers->set('Content-Type', 'text/csv; charset=UTF-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="' . $filename . '"');

        return $response;
    }

    // ========== SÉCURITÉ (SESSIONS / ALERTES) ==========
    #[Route('/admin/security/sessions', name: 'admin_security_sessions')]
    public function sessions(Request $request, UserSessionRepository $userSessionRepository): Response
    {
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'status' => (string) $request->query->get('status', ''),
            'ip' => trim((string) $request->query->get('ip', '')),
            'country' => (string) $request->query->get('country', ''),
        ];

        $sessions = $userSessionRepository->createAdminQueryBuilder($filters)
            ->getQuery()
            ->getResult();

        return $this->render('admin/security/sessions.html.twig', [
            'sessions' => $sessions,
            'filters' => $filters,
            'idleMinutes' => 30,
        ]);
    }

    #[Route('/admin/security/sessions/{id}/revoke', name: 'admin_security_sessions_revoke', methods: ['POST'])]
    public function revokeSession(UserSession $session, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('revoke_session_' . $session->getId(), $request->request->get('_token'))) {
            return $this->redirectToRoute('admin_security_sessions');
        }

        if (!$session->isRevoked()) {
            $session->setRevokedAt(new \DateTimeImmutable());
            $em->flush();
        }

        $this->addFlash('success', 'Session révoquée.');
        return $this->redirectToRoute('admin_security_sessions');
    }

    #[Route('/admin/security/suspicious', name: 'admin_security_suspicious')]
    public function suspiciousLogins(Request $request, SuspiciousLoginRepository $suspiciousLoginRepository): Response
    {
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'status' => (string) $request->query->get('status', ''),
            'country' => (string) $request->query->get('country', ''),
        ];

        $logins = $suspiciousLoginRepository->createAdminQueryBuilder($filters)
            ->getQuery()
            ->getResult();

        return $this->render('admin/security/suspicious.html.twig', [
            'logins' => $logins,
            'filters' => $filters,
        ]);
    }
    // ========== GESTION UTILISATEURS ==========
    #[Route('/users', name: 'admin_users')]
    public function users(
        Request $request,
        UserRepository $userRepository,
        UserAiScoreService $userAiScoreService
    ): Response
    {
        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 12;

        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'role' => (string) $request->query->get('role', ''),
            'status' => (string) $request->query->get('status', ''),
            'from' => (string) $request->query->get('from', ''),
            'to' => (string) $request->query->get('to', ''),
        ];

        $qb = $userRepository->createFilteredQueryBuilder($filters);
        $qb->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        $userScores = [];
        $scoreTotal = 0;
        $premiumEligible = 0;
        $freeMonthEligible = 0;
        $supportHighPriority = 0;
        $freeMonthGranted = 0;
        $listedCount = 0;

        foreach ($paginator as $listedUser) {
            if (!$listedUser instanceof User) {
                continue;
            }

            $score = $userAiScoreService->calculateScore($listedUser);
            $supportPriority = $userAiScoreService->getSupportPriorityLabel($listedUser);
            $isPremiumEligible = $userAiScoreService->isPremiumEligible($listedUser);
            $isFreeMonthEligible = $userAiScoreService->isEligibleForFreeMonth($listedUser);
            $hasFreeMonthGranted = $userAiScoreService->hasReceivedFreeMonth($listedUser);

            $userScores[$listedUser->getId()] = [
                'score' => $score,
                'supportPriority' => $supportPriority,
                'premiumEligible' => $isPremiumEligible,
                'freeMonthEligible' => $isFreeMonthEligible,
                'freeMonthGranted' => $hasFreeMonthGranted,
            ];

            $recentHistory = $userAiScoreService->getRecentHistory($listedUser, 2);
            $lastSnapshot = $recentHistory[0] ?? null;
            $previousSnapshot = $recentHistory[1] ?? null;

            $userScores[$listedUser->getId()]['lastSnapshotAt'] = $lastSnapshot?->getCreatedAt();
            $userScores[$listedUser->getId()]['delta'] = $previousSnapshot
                ? ($score - $previousSnapshot->getScore())
                : null;

            $scoreTotal += $score;
            $listedCount++;

            if ($isPremiumEligible) {
                $premiumEligible++;
            }
            if ($isFreeMonthEligible) {
                $freeMonthEligible++;
            }
            if ($supportPriority === 'Haute') {
                $supportHighPriority++;
            }
            if ($hasFreeMonthGranted) {
                $freeMonthGranted++;
            }
        }

        $scoreSummary = [
            'average' => $listedCount > 0 ? round($scoreTotal / $listedCount, 1) : 0,
            'premiumEligible' => $premiumEligible,
            'freeMonthEligible' => $freeMonthEligible,
            'supportHighPriority' => $supportHighPriority,
            'freeMonthGranted' => $freeMonthGranted,
            'listedCount' => $listedCount,
        ];

        return $this->render('admin/users/index.html.twig', [
            'users' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'filters' => $filters,
            'userScores' => $userScores,
            'scoreSummary' => $scoreSummary,
        ]);
    }

    #[Route('/users/new', name: 'admin_users_new')]
    public function createUser(
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher
    ): Response {
        $user = new User();
        $form = $this->createForm(AdminUserType::class, $user, [
            'require_password' => true,
        ]);

        $form->handleRequest($request);
        if ($form->isSubmitted() && $form->isValid()) {
            $plainPassword = (string) $form->get('plainPassword')->getData();
            if ($plainPassword === '') {
                $form->addError(new FormError('Le mot de passe est obligatoire.'));
            } else {
                $user->setPassword($passwordHasher->hashPassword($user, $plainPassword));
                $em->persist($user);
                $em->flush();
                $this->addFlash('success', 'Utilisateur créé avec succès.');
                return $this->redirectToRoute('admin_users');
            }
        }

        return $this->render('admin/users/form.html.twig', [
            'form' => $form->createView(),
            'mode' => 'create',
        ]);
    }

    #[Route('/users/{id}/edit', name: 'admin_users_edit')]
    public function editUser(
        User $user,
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher
    ): Response {
        $form = $this->createForm(AdminUserType::class, $user, [
            'require_password' => false,
        ]);

        $form->handleRequest($request);
        if ($form->isSubmitted() && $form->isValid()) {
            $plainPassword = (string) $form->get('plainPassword')->getData();
            if ($plainPassword !== '') {
                $user->setPassword($passwordHasher->hashPassword($user, $plainPassword));
            }
            $user->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();
            $this->addFlash('success', 'Utilisateur mis à jour.');
            return $this->redirectToRoute('admin_users');
        }

        return $this->render('admin/users/form.html.twig', [
            'form' => $form->createView(),
            'mode' => 'edit',
            'user' => $user,
        ]);
    }

    #[Route('/users/{id}/reset-password', name: 'admin_users_reset_password')]
    public function resetPassword(
        User $user,
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher
    ): Response {
        $form = $this->createForm(AdminUserResetPasswordType::class);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $plainPassword = (string) $form->get('plainPassword')->getData();
            $user->setPassword($passwordHasher->hashPassword($user, $plainPassword));
            $user->setUpdatedAt(new \DateTimeImmutable());
            $em->flush();
            $this->addFlash('success', 'Mot de passe réinitialisé.');
            return $this->redirectToRoute('admin_users_edit', ['id' => $user->getId()]);
        }

        return $this->render('admin/users/reset_password.html.twig', [
            'form' => $form->createView(),
            'user' => $user,
        ]);
    }

    #[Route('/users/{id}/ban', name: 'admin_users_ban', methods: ['POST'])]
    public function banUser(User $user, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('ban_user_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $isBanned = (bool) $request->request->get('is_banned', false);
        $banReason = trim((string) $request->request->get('ban_reason', ''));
        $banUntilRaw = trim((string) $request->request->get('ban_until', ''));

        $banUntil = null;
        if ($banUntilRaw !== '') {
            try {
                $banUntil = new \DateTimeImmutable($banUntilRaw);
            } catch (\Throwable) {
                $banUntil = null;
            }
        }

        $user->setIsBanned($isBanned);
        $user->setBanReason($isBanned ? ($banReason !== '' ? $banReason : null) : null);
        $user->setBanUntil($isBanned ? $banUntil : null);
        $user->setUpdatedAt(new \DateTimeImmutable());

        $em->flush();
        $this->addFlash('success', $isBanned ? 'Utilisateur banni.' : 'Utilisateur débanni.');

        return $this->redirectToRoute('admin_users');
    }

    #[Route('/users/{id}/delete', name: 'admin_users_delete', methods: ['POST'])]
    public function deleteUser(User $user, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('delete_user_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        if ($user->isDeleted()) {
            $this->addFlash('info', 'Utilisateur déjà supprimé.');
            return $this->redirectToRoute('admin_users');
        }

        $user->setDeletedAt(new \DateTimeImmutable());
        $user->setUpdatedAt(new \DateTimeImmutable());
        $em->flush();

        $this->addFlash('success', 'Utilisateur supprimé (soft delete).');
        return $this->redirectToRoute('admin_users');
    }

    #[Route('/users/stats', name: 'admin_users_stats')]
    public function usersStats(UserRepository $userRepository): Response
    {
        $stats = $userRepository->getUserStats();
        $chart = $userRepository->getRegistrationsChart(30);

        return $this->render('admin/users/stats.html.twig', [
            'stats' => $stats,
            'chart' => $chart,
        ]);
    }

    #[Route('/users/score-history', name: 'admin_users_score_history')]
    public function usersScoreHistory(Request $request, UserScoreHistoryRepository $userScoreHistoryRepository): Response
    {
        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 20;
        $q = trim((string) $request->query->get('q', ''));

        $qb = $userScoreHistoryRepository->createQueryBuilder('h')
            ->leftJoin('h.user', 'u')
            ->addSelect('u')
            ->orderBy('h.createdAt', 'DESC');

        if ($q !== '') {
            $qb->andWhere('u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q')
                ->setParameter('q', '%' . $q . '%');
        }

        $qb->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        return $this->render('admin/users/score_history.html.twig', [
            'rows' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'q' => $q,
        ]);
    }

    #[Route('/validation', name: 'admin_validation')]
    public function validation(Request $request, UserRepository $userRepository): Response
    {
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'email_verified' => (string) $request->query->get('email_verified', ''),
            'admin_approved' => (string) $request->query->get('admin_approved', 'no'),
        ];

        $qb = $userRepository->createValidationQueryBuilder($filters);
        $users = $qb->getQuery()->getResult();
        $stats = $userRepository->getValidationStats();
        return $this->render('admin/validation/index.html.twig', [
            'users' => $users,
            'filters' => $filters,
            'stats' => $stats,
        ]);
    }

    #[Route('/validation/{id}/approve', name: 'admin_validation_approve', methods: ['POST'])]
    public function approveValidation(int $id, Request $request, UserRepository $userRepository, EntityManagerInterface $em): RedirectResponse
    {
        $user = $userRepository->find($id);
        if (!$user) {
            throw $this->createNotFoundException('Utilisateur introuvable.');
        }

        if (!$this->isCsrfTokenValid('approve_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $user->setAdminApproved(true);
        $user->setUpdatedAt(new \DateTimeImmutable());
        $em->flush();

        $this->addFlash('success', 'Compte approuvé.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/validation/approve-all', name: 'admin_validation_approve_all', methods: ['POST'])]
    public function approveAllValidation(Request $request, UserRepository $userRepository, EntityManagerInterface $em): RedirectResponse
    {
        if (!$this->isCsrfTokenValid('approve_all', (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $users = $userRepository->findPendingApprovals();
        foreach ($users as $user) {
            $user->setAdminApproved(true);
            $user->setUpdatedAt(new \DateTimeImmutable());
        }

        $em->flush();
        $this->addFlash('success', 'Tous les comptes en attente ont été approuvés.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/validation/{id}/resend', name: 'admin_validation_resend', methods: ['POST'])]
    public function resendValidationEmail(
        int $id,
        Request $request,
        UserRepository $userRepository,
        EntityManagerInterface $em,
        MailerInterface $mailer
    ): RedirectResponse {
        $user = $userRepository->find($id);
        if (!$user) {
            throw $this->createNotFoundException('Utilisateur introuvable.');
        }

        if (!$this->isCsrfTokenValid('resend_' . $user->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $token = bin2hex(random_bytes(32));
        $user->setEmailVerificationToken($token);
        $user->setEmailVerificationExpiresAt((new \DateTimeImmutable())->modify('+2 days'));
        $user->setUpdatedAt(new \DateTimeImmutable());

        $verifyUrl = $this->generateUrl('app_verify_email', [
            'token' => $token,
        ], UrlGeneratorInterface::ABSOLUTE_URL);

        $email = (new TemplatedEmail())
            ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($user->getEmail())
            ->subject('Confirmation de votre email')
            ->htmlTemplate('emails/verify_email.html.twig')
            ->context([
                'user' => $user,
                'verifyUrl' => $verifyUrl,
                'expiresAt' => $user->getEmailVerificationExpiresAt(),
            ]);

        $mailer->send($email);
        $em->flush();

        $this->addFlash('success', 'Email de vérification renvoyé.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/validation/resend-all', name: 'admin_validation_resend_all', methods: ['POST'])]
    public function resendAllValidationEmail(
        Request $request,
        UserRepository $userRepository,
        EntityManagerInterface $em,
        MailerInterface $mailer
    ): RedirectResponse {
        if (!$this->isCsrfTokenValid('resend_all', (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $users = $userRepository->createQueryBuilder('u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.emailVerified = false')
            ->getQuery()
            ->getResult();

        foreach ($users as $user) {
            $token = bin2hex(random_bytes(32));
            $user->setEmailVerificationToken($token);
            $user->setEmailVerificationExpiresAt((new \DateTimeImmutable())->modify('+2 days'));
            $user->setUpdatedAt(new \DateTimeImmutable());

            $verifyUrl = $this->generateUrl('app_verify_email', [
                'token' => $token,
            ], UrlGeneratorInterface::ABSOLUTE_URL);

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Confirmation de votre email')
                ->htmlTemplate('emails/verify_email.html.twig')
                ->context([
                    'user' => $user,
                    'verifyUrl' => $verifyUrl,
                    'expiresAt' => $user->getEmailVerificationExpiresAt(),
                ]);

            $mailer->send($email);
        }

        $em->flush();
        $this->addFlash('success', 'Emails renvoyés à tous les comptes non vérifiés.');
        return $this->redirectToRoute('admin_validation');
    }

    #[Route('/roles', name: 'admin_roles')]
    public function roles(): Response
    {
        return $this->render('admin/roles/index.html.twig');
    }

    // ========== ABONNEMENTS ==========
    #[Route('/subscriptions', name: 'admin_subscriptions')]
    public function subscriptions(Request $request, AbonnementRepository $abonnementRepository): Response
    {
        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 12;

        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'status' => (string) $request->query->get('status', ''),
            'type' => (string) $request->query->get('type', ''),
            'from' => (string) $request->query->get('from', ''),
            'to' => (string) $request->query->get('to', ''),
        ];

        $qb = $abonnementRepository->createFilteredQueryBuilder($filters);
        $qb->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        $stats = $abonnementRepository->getSubscriptionStats();

        return $this->render('admin/subscriptions/index.html.twig', [
            'abonnements' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'filters' => $filters,
            'stats' => $stats,
        ]);
    }

    #[Route('/subscriptions/{id}/update', name: 'admin_subscriptions_update', methods: ['POST'])]
    public function updateSubscription(
        int $id,
        Request $request,
        AbonnementRepository $abonnementRepository,
        EntityManagerInterface $em
    ): RedirectResponse {
        $abonnement = $abonnementRepository->find($id);
        if (!$abonnement) {
            throw $this->createNotFoundException('Abonnement introuvable.');
        }

        if (!$this->isCsrfTokenValid('sub_update_' . $abonnement->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $type = (string) $request->request->get('type', $abonnement->getTypeAbonnement());
        $status = (string) $request->request->get('status', $abonnement->getStatut());
        $dateFinRaw = (string) $request->request->get('date_fin', '');

        if ($type !== '') {
            $abonnement->setTypeAbonnement($type);
            $abonnement->setNom($this->labelFromRole($type));
        }
        if ($status !== '') {
            $abonnement->setStatut($status);
        }
        if ($dateFinRaw !== '') {
            try {
                $abonnement->setDateFin(new \DateTime($dateFinRaw));
            } catch (\Throwable) {
            }
        }

        $user = $abonnement->getUser();
        if ($user) {
            $user->setSubscriptionType($abonnement->getTypeAbonnement());
            $user->setSubscriptionEndAt(\DateTimeImmutable::createFromMutable($abonnement->getDateFin()));
            $user->setSubscriptionStatus($abonnement->getStatut() === 'actif' ? 'ACTIVE' : 'EXPIRED');
            $user->setRole($abonnement->getTypeAbonnement());
            $user->setUpdatedAt(new \DateTimeImmutable());
            $this->ensureRoleEntity($user, $abonnement->getTypeAbonnement(), $em);
        }

        $em->flush();
        $this->addFlash('success', 'Abonnement mis à jour.');
        return $this->redirectToRoute('admin_subscriptions');
    }

    #[Route('/subscriptions/{id}/cancel', name: 'admin_subscriptions_cancel', methods: ['POST'])]
    public function cancelSubscription(
        int $id,
        Request $request,
        AbonnementRepository $abonnementRepository,
        EntityManagerInterface $em
    ): RedirectResponse {
        $abonnement = $abonnementRepository->find($id);
        if (!$abonnement) {
            throw $this->createNotFoundException('Abonnement introuvable.');
        }

        if (!$this->isCsrfTokenValid('sub_cancel_' . $abonnement->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException('CSRF token invalide.');
        }

        $abonnement->setStatut('annule');
        $abonnement->setDateFin(new \DateTime());

        $user = $abonnement->getUser();
        if ($user) {
            $this->removeRoleEntity($user, $em);
            $user->setRole('ROLE_USER');
            $user->setSubscriptionStatus('EXPIRED');
            $user->setSubscriptionType(null);
            $user->setSubscriptionEndAt(null);
            $user->setUpdatedAt(new \DateTimeImmutable());
        }

        $em->flush();
        $this->addFlash('success', 'Abonnement annulé.');
        return $this->redirectToRoute('admin_subscriptions');
    }

    private function labelFromRole(string $role): string
    {
        return match ($role) {
            'ROLE_MEDECIN' => 'Médecin',
            'ROLE_PHARMACIEN' => 'Pharmacien',
            'ROLE_COACH' => 'Coach sportif',
            'ROLE_NUTRITIONNISTE' => 'Nutritionniste',
            'ROLE_PATIENT' => 'Patient',
            default => 'Abonnement',
        };
    }

    private function ensureRoleEntity(User $user, string $role, EntityManagerInterface $em): void
    {
        $this->removeRoleEntity($user, $em);

        switch ($role) {
            case 'ROLE_PATIENT':
                if (!$user->getPatient()) {
                    $patient = new \App\Entity\Patient();
                    $patient->setUser($user);
                    $em->persist($patient);
                }
                break;
            case 'ROLE_MEDECIN':
                if (!$user->getMedecin()) {
                    $medecin = new \App\Entity\Medecin();
                    $medecin->setUser($user);
                    $medecin->setSpecialite('Non défini');
                    $em->persist($medecin);
                }
                break;
            case 'ROLE_PHARMACIEN':
                if (!$user->getPharmacien()) {
                    $pharmacien = new \App\Entity\Pharmacien();
                    $pharmacien->setUser($user);
                    $em->persist($pharmacien);
                }
                break;
            case 'ROLE_COACH':
                if (!$user->getCoachSportif()) {
                    $coach = new \App\Entity\CoachSportif();
                    $coach->setUser($user);
                    $coach->setSpecialite('Non défini');
                    $em->persist($coach);
                }
                break;
            case 'ROLE_NUTRITIONNISTE':
                if (!$user->getNutritionniste()) {
                    $nutritionniste = new \App\Entity\Nutritionniste();
                    $nutritionniste->setUser($user);
                    $nutritionniste->setSpecialite('Non défini');
                    $em->persist($nutritionniste);
                }
                break;
        }
    }

    private function removeRoleEntity(User $user, EntityManagerInterface $em): void
    {
        if ($user->getPatient()) {
            $em->remove($user->getPatient());
            $user->setPatient(null);
        }
        if ($user->getMedecin()) {
            $em->remove($user->getMedecin());
            $user->setMedecin(null);
        }
        if ($user->getPharmacien()) {
            $em->remove($user->getPharmacien());
            $user->setPharmacien(null);
        }
        if ($user->getCoachSportif()) {
            $em->remove($user->getCoachSportif());
            $user->setCoachSportif(null);
        }
        if ($user->getNutritionniste()) {
            $em->remove($user->getNutritionniste());
            $user->setNutritionniste(null);
        }
    }

    #[Route('/payments', name: 'admin_payments')]
    public function payments(Request $request, \Doctrine\ORM\EntityManagerInterface $em): Response
    {
        $repo = $em->getRepository(\App\Entity\Facture::class);

        $page = max(1, (int) $request->query->get('page', 1));
        $limit = 12;
        $filters = [
            'q' => trim((string) $request->query->get('q', '')),
            'from' => (string) $request->query->get('from', ''),
            'to' => (string) $request->query->get('to', ''),
            'min' => (string) $request->query->get('min', ''),
            'max' => (string) $request->query->get('max', ''),
        ];

        $qb = $repo->createQueryBuilder('f')
            ->leftJoin('f.user', 'u')
            ->leftJoin('f.abonnement', 'a')
            ->addSelect('u', 'a');

        if ($filters['q'] !== '') {
            $qb->andWhere('u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q OR f.numero LIKE :q')
                ->setParameter('q', '%' . $filters['q'] . '%');
        }
        if ($filters['from'] !== '') {
            try {
                $qb->andWhere('f.createdAt >= :from')->setParameter('from', new \DateTime($filters['from'] . ' 00:00:00'));
            } catch (\Throwable) {}
        }
        if ($filters['to'] !== '') {
            try {
                $qb->andWhere('f.createdAt <= :to')->setParameter('to', new \DateTime($filters['to'] . ' 23:59:59'));
            } catch (\Throwable) {}
        }
        if ($filters['min'] !== '') {
            $qb->andWhere('f.montantTtc >= :min')->setParameter('min', $filters['min']);
        }
        if ($filters['max'] !== '') {
            $qb->andWhere('f.montantTtc <= :max')->setParameter('max', $filters['max']);
        }

        $qb->orderBy('f.createdAt', 'DESC')
            ->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);

        $paginator = new \Doctrine\ORM\Tools\Pagination\Paginator($qb);
        $total = count($paginator);
        $pages = (int) max(1, ceil($total / $limit));

        $stats = [
            'total' => (int) $repo->createQueryBuilder('f')->select('COUNT(f.id)')->getQuery()->getSingleScalarResult(),
            'revenue' => (string) $repo->createQueryBuilder('f')->select('COALESCE(SUM(f.montantTtc),0)')->getQuery()->getSingleScalarResult(),
            'month' => (string) $repo->createQueryBuilder('f')->select('COALESCE(SUM(f.montantTtc),0)')->andWhere('f.createdAt >= :m')->setParameter('m', new \DateTime('first day of this month 00:00:00'))->getQuery()->getSingleScalarResult(),
        ];

        $chartRows = $repo->createQueryBuilder('f')
            ->select('f.createdAt')
            ->orderBy('f.createdAt', 'ASC')
            ->getQuery()
            ->getResult();

        $labels = [];
        $data = [];
        $start = new \DateTimeImmutable('-30 days');
        $cursor = $start;
        for ($i = 0; $i <= 30; $i++) {
            $labels[] = $cursor->format('Y-m-d');
            $data[$cursor->format('Y-m-d')] = 0.0;
            $cursor = $cursor->modify('+1 day');
        }
        foreach ($chartRows as $row) {
            $key = $row['createdAt']->format('Y-m-d');
            if (isset($data[$key])) {
                $data[$key] += 1;
            }
        }

        $chart = [
            'labels' => $labels,
            'data' => array_values($data),
        ];

        return $this->render('admin/payments/index.html.twig', [
            'factures' => $paginator,
            'page' => $page,
            'pages' => $pages,
            'total' => $total,
            'filters' => $filters,
            'stats' => $stats,
            'chart' => $chart,
        ]);
    }

    #[Route('/payments/{id}/download', name: 'admin_payments_download')]
    public function downloadInvoice(int $id, \Doctrine\ORM\EntityManagerInterface $em): Response
    {
        $facture = $em->getRepository(\App\Entity\Facture::class)->find($id);
        if (!$facture || !$facture->getPdfPath()) {
            throw $this->createNotFoundException('Facture introuvable.');
        }

        return $this->file($facture->getPdfPath(), 'facture-' . $facture->getNumero() . '.pdf');
    }

    #[Route('/revenues', name: 'admin_revenues')]
    public function revenues(Request $request, EntityManagerInterface $em): Response
    {
        // Filtres
        $search = trim((string) $request->query->get('q', ''));
        $status = (string) $request->query->get('status', '');
        $dateFrom = (string) $request->query->get('date_from', '');
        $dateTo = (string) $request->query->get('date_to', '');
        $minAmount = (string) $request->query->get('min_amount', '');
        $maxAmount = (string) $request->query->get('max_amount', '');

        // Query builder pour les factures
        $qb = $em->getRepository(Facture::class)->createQueryBuilder('f')
            ->leftJoin('f.user', 'u')->addSelect('u')
            ->leftJoin('f.abonnement', 'a')->addSelect('a')
            ->orderBy('f.createdAt', 'DESC');

        if ($search) {
            $qb->andWhere('f.numero LIKE :q OR u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }

        if ($dateFrom) {
            try {
                $qb->andWhere('f.createdAt >= :from')
                    ->setParameter('from', new \DateTime($dateFrom . ' 00:00:00'));
            } catch (\Exception $e) {}
        }

        if ($dateTo) {
            try {
                $qb->andWhere('f.createdAt <= :to')
                    ->setParameter('to', new \DateTime($dateTo . ' 23:59:59'));
            } catch (\Exception $e) {}
        }

        if ($minAmount !== '') {
            $qb->andWhere('f.montantTtc >= :min')->setParameter('min', (float)$minAmount);
        }

        if ($maxAmount !== '') {
            $qb->andWhere('f.montantTtc <= :max')->setParameter('max', (float)$maxAmount);
        }

        $factures = $qb->setMaxResults(100)->getQuery()->getResult();

        // Statistiques
        $statsQb = $em->getRepository(Facture::class)->createQueryBuilder('f');
        $totalRevenue = (float) $statsQb->select('COALESCE(SUM(f.montantTtc), 0)')
            ->getQuery()->getSingleScalarResult();

        $monthStart = new \DateTimeImmutable('first day of this month 00:00:00');
        $monthEnd = $monthStart->modify('first day of next month 00:00:00');
        $monthlyRevenue = (float) $em->createQueryBuilder()
            ->select('COALESCE(SUM(f.montantTtc), 0)')
            ->from(Facture::class, 'f')
            ->where('f.createdAt >= :from')
            ->andWhere('f.createdAt < :to')
            ->setParameter('from', $monthStart)
            ->setParameter('to', $monthEnd)
            ->getQuery()->getSingleScalarResult();

        $totalInvoices = (int) $em->createQueryBuilder()
            ->select('COUNT(f.id)')
            ->from(Facture::class, 'f')
            ->getQuery()->getSingleScalarResult();

        $avgInvoice = $totalInvoices > 0 ? $totalRevenue / $totalInvoices : 0;

        // Revenus par type d'abonnement
        $revenueByType = $em->createQueryBuilder()
            ->select('a.typeAbonnement as type, COALESCE(SUM(f.montantTtc), 0) as total, COUNT(f.id) as count')
            ->from(Facture::class, 'f')
            ->leftJoin('f.abonnement', 'a')
            ->groupBy('a.typeAbonnement')
            ->getQuery()->getResult();

        // Revenus mensuels (6 derniers mois)
        $monthlyChart = [];
        for ($i = 5; $i >= 0; $i--) {
            $start = new \DateTimeImmutable("first day of -$i month 00:00:00");
            $end = $start->modify('first day of next month 00:00:00');
            $revenue = (float) $em->createQueryBuilder()
                ->select('COALESCE(SUM(f.montantTtc), 0)')
                ->from(Facture::class, 'f')
                ->where('f.createdAt >= :from')
                ->andWhere('f.createdAt < :to')
                ->setParameter('from', $start)
                ->setParameter('to', $end)
                ->getQuery()->getSingleScalarResult();
            $monthlyChart[] = [
                'month' => $start->format('M Y'),
                'revenue' => $revenue
            ];
        }

        return $this->render('admin/revenues/index.html.twig', [
            'factures' => $factures,
            'stats' => [
                'total' => $totalRevenue,
                'monthly' => $monthlyRevenue,
                'count' => $totalInvoices,
                'average' => $avgInvoice,
            ],
            'revenueByType' => $revenueByType,
            'monthlyChart' => $monthlyChart,
            'filters' => [
                'search' => $search,
                'status' => $status,
                'date_from' => $dateFrom,
                'date_to' => $dateTo,
                'min_amount' => $minAmount,
                'max_amount' => $maxAmount,
            ],
        ]);
    }

    // ========== SERVICES MÉDICAUX ==========
    #[Route('/accompaniments', name: 'admin_accompaniments')]
    public function accompaniments(Request $request, EntityManagerInterface $em): Response
    {
        // Filtres
        $search = trim((string) $request->query->get('q', ''));
        $status = (string) $request->query->get('status', '');
        $coachId = (int) $request->query->get('coach', 0);
        $nutritionistId = (int) $request->query->get('nutritionist', 0);
        $dateFrom = (string) $request->query->get('date_from', '');
        $dateTo = (string) $request->query->get('date_to', '');

        // Query builder pour les plans d'accompagnement
        $qb = $em->getRepository(AccompanimentPlan::class)->createQueryBuilder('ap')
            ->leftJoin('ap.patient', 'p')->addSelect('p')
            ->leftJoin('p.user', 'u')->addSelect('u')
            ->leftJoin('ap.coach', 'c')->addSelect('c')
            ->leftJoin('ap.nutritionist', 'n')->addSelect('n')
            ->orderBy('ap.createdAt', 'DESC');

        if ($search) {
            $qb->andWhere('ap.title LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q OR u.email LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }

        if ($status) {
            $qb->andWhere('ap.status = :status')->setParameter('status', $status);
        }

        if ($coachId) {
            $qb->andWhere('c.id = :cid')->setParameter('cid', $coachId);
        }

        if ($nutritionistId) {
            $qb->andWhere('n.id = :nid')->setParameter('nid', $nutritionistId);
        }

        if ($dateFrom) {
            try {
                $qb->andWhere('ap.startDate >= :from')
                    ->setParameter('from', new \DateTimeImmutable($dateFrom));
            } catch (\Exception $e) {}
        }

        if ($dateTo) {
            try {
                $qb->andWhere('ap.startDate <= :to')
                    ->setParameter('to', new \DateTimeImmutable($dateTo));
            } catch (\Exception $e) {}
        }

        $plans = $qb->setMaxResults(100)->getQuery()->getResult();

        // Statistiques
        $totalPlans = (int) $em->createQueryBuilder()
            ->select('COUNT(ap.id)')
            ->from(AccompanimentPlan::class, 'ap')
            ->getQuery()->getSingleScalarResult();

        $activePlans = (int) $em->createQueryBuilder()
            ->select('COUNT(ap.id)')
            ->from(AccompanimentPlan::class, 'ap')
            ->where('ap.status = :status')
            ->setParameter('status', 'active')
            ->getQuery()->getSingleScalarResult();

        $completedPlans = (int) $em->createQueryBuilder()
            ->select('COUNT(ap.id)')
            ->from(AccompanimentPlan::class, 'ap')
            ->where('ap.status = :status')
            ->setParameter('status', 'completed')
            ->getQuery()->getSingleScalarResult();

        $cancelledPlans = (int) $em->createQueryBuilder()
            ->select('COUNT(ap.id)')
            ->from(AccompanimentPlan::class, 'ap')
            ->where('ap.status = :status')
            ->setParameter('status', 'cancelled')
            ->getQuery()->getSingleScalarResult();

        // Plans par statut
        $plansByStatus = $em->createQueryBuilder()
            ->select('ap.status, COUNT(ap.id) as count')
            ->from(AccompanimentPlan::class, 'ap')
            ->groupBy('ap.status')
            ->getQuery()->getResult();

        // Liste des coaches pour le filtre
        $coaches = $em->getRepository(CoachSportif::class)->createQueryBuilder('c')
            ->leftJoin('c.user', 'u')->addSelect('u')
            ->where('u.deletedAt IS NULL')
            ->orderBy('u.nom', 'ASC')
            ->setMaxResults(50)
            ->getQuery()->getResult();

        // Liste des nutritionnistes pour le filtre
        $nutritionists = $em->getRepository(Nutritionniste::class)->createQueryBuilder('n')
            ->leftJoin('n.user', 'u')->addSelect('u')
            ->where('u.deletedAt IS NULL')
            ->orderBy('u.nom', 'ASC')
            ->setMaxResults(50)
            ->getQuery()->getResult();

        return $this->render('admin/accompaniments/index.html.twig', [
            'plans' => $plans,
            'stats' => [
                'total' => $totalPlans,
                'active' => $activePlans,
                'completed' => $completedPlans,
                'cancelled' => $cancelledPlans,
            ],
            'plansByStatus' => $plansByStatus,
            'coaches' => $coaches,
            'nutritionists' => $nutritionists,
            'filters' => [
                'search' => $search,
                'status' => $status,
                'coach' => $coachId,
                'nutritionist' => $nutritionistId,
                'date_from' => $dateFrom,
                'date_to' => $dateTo,
            ],
        ]);
    }

    #[Route('/admin/appointments', name: 'admin_appointments')]
    public function appointments(
        \Symfony\Component\HttpFoundation\Request $request,
        \Doctrine\ORM\EntityManagerInterface $em
    ): Response
    {
        $status = (string) $request->query->get('status', '');
        $medecinId = (int) $request->query->get('medecin', 0);
        $patientId = (int) $request->query->get('patient', 0);
        $date = (string) $request->query->get('date', '');
        $search = trim((string) $request->query->get('q', ''));

        $repo = $em->getRepository(\App\Entity\RendezVous::class);
        $qb = $repo->createQueryBuilder('r')
            ->leftJoin('r.patient', 'p')->addSelect('p')
            ->leftJoin('p.user', 'pu')->addSelect('pu')
            ->leftJoin('r.medecin', 'm')->addSelect('m')
            ->leftJoin('m.user', 'mu')->addSelect('mu')
            ->orderBy('r.dateRdv', 'DESC');

        if ($status) {
            $qb->andWhere('r.statut = :statut')->setParameter('statut', $status);
        }
        if ($medecinId) {
            $qb->andWhere('m.id = :mid')->setParameter('mid', $medecinId);
        }
        if ($patientId) {
            $qb->andWhere('p.id = :pid')->setParameter('pid', $patientId);
        }
        if ($date) {
            $qb->andWhere('r.dateRdv = :d')->setParameter('d', new \DateTime($date));
        }
        if ($search) {
            $qb->andWhere('pu.email LIKE :q OR pu.nom LIKE :q OR pu.prenom LIKE :q OR mu.email LIKE :q OR mu.nom LIKE :q OR mu.prenom LIKE :q')
                ->setParameter('q', '%' . $search . '%');
        }

        $rdvs = $qb->setMaxResults(200)->getQuery()->getResult();

        $stats = [
            'total' => count($rdvs),
            'confirmed' => 0,
            'cancelled' => 0,
        ];
        foreach ($rdvs as $rdv) {
            if ($rdv->getStatut() === 'confirme') {
                $stats['confirmed']++;
            }
            if ($rdv->getStatut() === 'annule') {
                $stats['cancelled']++;
            }
        }

        $medecins = $em->getRepository(\App\Entity\Medecin::class)->findAll();
        $patients = $em->getRepository(\App\Entity\Patient::class)->findAll();

        return $this->render('admin/appointments/index.html.twig', [
            'rdvs' => $rdvs,
            'stats' => $stats,
            'filters' => [
                'status' => $status,
                'medecin' => $medecinId,
                'patient' => $patientId,
                'date' => $date,
                'q' => $search,
            ],
            'medecins' => $medecins,
            'patients' => $patients,
        ]);
    }

    #[Route('/admin/appointments/{id}/cancel', name: 'admin_appointments_cancel', methods: ['POST'])]
    public function cancelAppointment(
        int $id,
        \Doctrine\ORM\EntityManagerInterface $em,
        \App\Service\AppointmentService $appointmentService
    ): \Symfony\Component\HttpFoundation\Response {
        $rdv = $em->getRepository(\App\Entity\RendezVous::class)->find($id);
        if (!$rdv) {
            throw $this->createNotFoundException('Rendez-vous introuvable.');
        }
        $appointmentService->updateStatusByDoctor($rdv, 'annule');
        $this->addFlash('success', 'Rendez-vous annulé.');
        return $this->redirectToRoute('admin_appointments');
    }

    #[Route('/admin/appointments/{id}/reschedule', name: 'admin_appointments_reschedule', methods: ['POST'])]
    public function rescheduleAppointment(
        int $id,
        \Symfony\Component\HttpFoundation\Request $request,
        \Doctrine\ORM\EntityManagerInterface $em,
        \App\Service\AppointmentService $appointmentService
    ): \Symfony\Component\HttpFoundation\Response {
        $rdv = $em->getRepository(\App\Entity\RendezVous::class)->find($id);
        if (!$rdv) {
            throw $this->createNotFoundException('Rendez-vous introuvable.');
        }

        $date = (string) $request->request->get('date', '');
        $time = (string) $request->request->get('time', '');
        if (!$date || !$time) {
            $this->addFlash('error', 'Date et heure obligatoires.');
            return $this->redirectToRoute('admin_appointments');
        }

        $medecin = $rdv->getMedecin();
        $dispoRepo = $em->getRepository(\App\Entity\Disponibilite::class);
        $dispo = $dispoRepo->createQueryBuilder('d')
            ->andWhere('d.medecin = :m')->setParameter('m', $medecin)
            ->andWhere('d.date = :d')->setParameter('d', new \DateTime($date))
            ->andWhere('d.heureDebut = :h')->setParameter('h', new \DateTime($date . ' ' . $time))
            ->getQuery()->getOneOrNullResult();

        if (!$dispo) {
            $dispo = new \App\Entity\Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date));
            $dispo->setHeureDebut(new \DateTime($date . ' ' . $time));
            $dispo->setHeureFin((new \DateTime($date . ' ' . $time))->modify('+30 minutes'));
            $dispo->setStatut('disponible');
            $em->persist($dispo);
            $em->flush();
        }

        if ($dispo->getRendezvous()) {
            $this->addFlash('error', 'Créneau déjà réservé.');
            return $this->redirectToRoute('admin_appointments');
        }

        $appointmentService->reschedule($rdv, $dispo);
        $this->addFlash('success', 'Rendez-vous replanifié.');
        return $this->redirectToRoute('admin_appointments');
    }

    #[Route('/admin/appointments/create', name: 'admin_appointments_create', methods: ['POST'])]
    public function createAppointment(
        \Symfony\Component\HttpFoundation\Request $request,
        \Doctrine\ORM\EntityManagerInterface $em,
        \App\Service\AppointmentService $appointmentService,
        \Symfony\Component\Mailer\MailerInterface $mailer
    ): \Symfony\Component\HttpFoundation\Response {
        $patientId = (int) $request->request->get('patient_id', 0);
        $medecinId = (int) $request->request->get('medecin_id', 0);
        $date = (string) $request->request->get('date', '');
        $time = (string) $request->request->get('time', '');
        $motif = (string) $request->request->get('motif', '');

        if (!$patientId || !$medecinId || !$date || !$time) {
            $this->addFlash('error', 'Patient, médecin, date et heure sont obligatoires.');
            return $this->redirectToRoute('admin_appointments');
        }

        $patient = $em->getRepository(\App\Entity\Patient::class)->find($patientId);
        $medecin = $em->getRepository(\App\Entity\Medecin::class)->find($medecinId);
        if (!$patient || !$medecin) {
            $this->addFlash('error', 'Patient ou médecin introuvable.');
            return $this->redirectToRoute('admin_appointments');
        }

        $dispoRepo = $em->getRepository(\App\Entity\Disponibilite::class);
        $dispo = $dispoRepo->createQueryBuilder('d')
            ->andWhere('d.medecin = :m')->setParameter('m', $medecin)
            ->andWhere('d.date = :d')->setParameter('d', new \DateTime($date))
            ->andWhere('d.heureDebut = :h')->setParameter('h', new \DateTime($date . ' ' . $time))
            ->getQuery()->getOneOrNullResult();

        if (!$dispo) {
            $dispo = new \App\Entity\Disponibilite();
            $dispo->setMedecin($medecin);
            $dispo->setDate(new \DateTime($date));
            $dispo->setHeureDebut(new \DateTime($date . ' ' . $time));
            $dispo->setHeureFin((new \DateTime($date . ' ' . $time))->modify('+30 minutes'));
            $dispo->setStatut('disponible');
            $em->persist($dispo);
            $em->flush();
        }

        if ($dispo->getRendezvous()) {
            $this->addFlash('error', 'Créneau déjà réservé.');
            return $this->redirectToRoute('admin_appointments');
        }

        $rdv = $appointmentService->book($patient, $medecin, $dispo, $motif ?: null);
        $rdv->setStatut('confirme');
        $em->flush();

        $email = (new \Symfony\Bridge\Twig\Mime\TemplatedEmail())
            ->from(new \Symfony\Component\Mime\Address('houssemlangar17@gmail.com', 'SANTÉA'))
            ->to($patient->getUser()->getEmail())
            ->subject('Rendez-vous confirmé')
            ->htmlTemplate('emails/appointment_accepted.html.twig')
            ->context(['rdv' => $rdv, 'user' => $patient->getUser(), 'medecin' => $medecin]);
        $mailer->send($email);

        $this->addFlash('success', 'Rendez-vous créé.');
        return $this->redirectToRoute('admin_appointments');
    }

    #[Route('/medical', name: 'admin_medical')]
    public function medical(): Response
    {
        return $this->render('admin/medical/index.html.twig');
    }

    // ========== PHARMACIE ==========
    #[Route('/admin/pharmacies', name: 'admin_pharmacies')]
    public function pharmacies(
        \App\Repository\PharmacyRepository $pharmacyRepository,
        \App\Repository\ReservationMedicamentRepository $reservationRepository
    ): Response {
        $pharmacies = $pharmacyRepository->findBy([], ['createdAt' => 'DESC']);
        $stats = [
            'pharmacies_total' => count($pharmacies),
            'pharmacies_active' => (int) $pharmacyRepository->createQueryBuilder('p')
                ->select('COUNT(p.id)')
                ->andWhere('p.isActive = 1')
                ->getQuery()
                ->getSingleScalarResult(),
            'reservations_pending' => (int) $reservationRepository->createQueryBuilder('r')
                ->select('COUNT(r.id)')
                ->andWhere('r.statut = :s')
                ->setParameter('s', 'en_attente')
                ->getQuery()
                ->getSingleScalarResult(),
        ];

        return $this->render('admin/pharmacies/index.html.twig', [
            'pharmacies' => $pharmacies,
            'stats' => $stats,
        ]);
    }

    #[Route('/admin/medications', name: 'admin_medications')]
    public function medications(\App\Repository\MedicamentRepository $medicamentRepository): Response
    {
        $medicaments = $medicamentRepository->findBy([], ['nom' => 'ASC']);
        return $this->render('admin/medications/index.html.twig', [
            'medicaments' => $medicaments,
        ]);
    }

    #[Route('/admin/stocks', name: 'admin_stocks')]
    public function stocks(
        \App\Repository\StockPharmacyRepository $stockRepository,
        \App\Repository\PharmacyRepository $pharmacyRepository,
        \App\Repository\MedicamentRepository $medicamentRepository
    ): Response {
        $stocks = $stockRepository->createQueryBuilder('s')
            ->leftJoin('s.pharmacie', 'p')
            ->addSelect('p')
            ->leftJoin('s.medicament', 'm')
            ->addSelect('m')
            ->orderBy('s.updatedAt', 'DESC')
            ->getQuery()
            ->getResult();
        $pharmacies = $pharmacyRepository->findBy([], ['nom' => 'ASC']);
        $medicaments = $medicamentRepository->findBy([], ['nom' => 'ASC']);
        return $this->render('admin/stocks/index.html.twig', [
            'stocks' => $stocks,
            'pharmacies' => $pharmacies,
            'medicaments' => $medicaments,
        ]);
    }

    // ========== TRACKER SANTÉ ==========
    #[Route('/tracker', name: 'admin_tracker')]
    public function tracker(
        \App\Repository\SymptomeListeRepository $symptomeListeRepository,
        \App\Repository\SymptomeQuotidienRepository $symptomeQuotidienRepository
    ): Response
    {
        $userStats = $symptomeQuotidienRepository->getUserSymptomStatistics();

        $stats = [
            'list_total' => $symptomeListeRepository->getTotalCount(),
            'tracked_total' => $symptomeQuotidienRepository->getTotalTrackedCount(),
            'tracked_weekly' => $symptomeQuotidienRepository->getWeeklyTrackedCount(),
            'avg_intensity' => $symptomeQuotidienRepository->getAverageIntensityAll(),
            'active_patients' => count($userStats),
        ];

        $mostCommonSymptoms = $symptomeQuotidienRepository->getMostCommonSymptoms(10);

        return $this->render('admin/tracker/index.html.twig', [
            'stats' => $stats,
            'user_stats' => $userStats,
            'most_common_symptoms' => $mostCommonSymptoms,
        ]);
    }

    // ========== SYSTÈME ==========
    #[Route('/reports', name: 'admin_reports')]
    public function reports(): Response
    {
        return $this->render('admin/reports/index.html.twig');
    }

    #[Route('/settings', name: 'admin_settings')]
    public function settings(): Response
    {
        return $this->render('admin/settings/index.html.twig');
    }
}
