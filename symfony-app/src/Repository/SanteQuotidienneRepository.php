<?php

namespace App\Repository;

use App\Entity\SanteQuotidienne;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SanteQuotidienne>
 */
class SanteQuotidienneRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SanteQuotidienne::class);
    }

    // --------------------------------------------------
    // Méthodes utiles très courantes dans ce genre de projet
    // --------------------------------------------------

    /**
     * Récupère l'entrée du jour pour l'utilisateur (s'il y en a une)
     */
    public function findTodayForUser(object $user): ?SanteQuotidienne
    {
        $today = (new \DateTime())->setTime(0, 0, 0);

        return $this->createQueryBuilder('s')
            ->andWhere('s.user = :user')
            ->andWhere('s.date >= :todayStart')
            ->andWhere('s.date < :tomorrow')
            ->setParameter('user', $user)
            ->setParameter('todayStart', $today)
            ->setParameter('tomorrow', (clone $today)->modify('+1 day'))
            ->getQuery()
            ->getOneOrNullResult();
    }

    /**
     * Toutes les entrées d'un utilisateur, triées par date descendante
     */
    public function findByUserOrdered(object $user): array
    {
        return $this->createQueryBuilder('s')
            ->where('s.user = :user')
            ->setParameter('user', $user)
            ->orderBy('s.date', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Moyenne du poids sur les 30 derniers jours
     */
    public function getAverageWeightLast30Days(object $user): ?float
    {
        return $this->createQueryBuilder('s')
            ->select('AVG(s.poids) as avg')
            ->where('s.user = :user')
            ->andWhere('s.date >= :start')
            ->setParameter('user', $user)
            ->setParameter('start', new \DateTime('-30 days'))
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Moyenne de sommeil pour un utilisateur (toutes les entrées)
     */
    public function getAverageSommeilForUser(object $user): ?float
    {
        $qb = $this->createQueryBuilder('s')
            ->select('AVG(s.sommeil) as avgSommeil')
            ->where('s.user = :user')
            ->setParameter('user', $user);

        $result = $qb->getQuery()->getSingleScalarResult();
        return $result !== null ? (float) $result : null;
    }

    /**
     * Entrées d'un mois précis (ex: pour un rapport mensuel)
     */
    public function findByMonth(object $user, int $year, int $month): array
    {
        $start = (new \DateTime("$year-$month-01"))->setTime(0, 0, 0);
        $end = (clone $start)->modify('last day of this month')->setTime(23, 59, 59);

        return $this->createQueryBuilder('s')
            ->where('s.user = :user')
            ->andWhere('s.date BETWEEN :start AND :end')
            ->setParameter('user', $user)
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->orderBy('s.date', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Trouve une entrée par utilisateur et date exacte (jour/mois/année)
     */
    public function findOneByUserAndDate(object $user, \DateTimeInterface $date): ?SanteQuotidienne
    {
        $start = (clone $date)->setTime(0, 0, 0);
        $end = (clone $date)->setTime(23, 59, 59);
        
        return $this->createQueryBuilder('s')
            ->where('s.user = :user')
            ->andWhere('s.date >= :start')
            ->andWhere('s.date <= :end')
            ->setParameter('user', $user)
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->getQuery()
            ->getOneOrNullResult();
    }

    // --------------------------------------------------
    // Méthodes statistiques pour l'évolution du poids
    // --------------------------------------------------

    /**
     * Récupère l'évolution du poids sur les X derniers jours
     */
    public function getWeightEvolution(object $user, int $days = 30): array
    {
        $endDate = new \DateTime();
        $startDate = new \DateTime("-{$days} days");

        // Debug: Afficher les dates pour le debug
        // error_log("Start date: " . $startDate->format('Y-m-d H:i:s'));
        // error_log("End date: " . $endDate->format('Y-m-d H:i:s'));

        return $this->createQueryBuilder('s')
            ->select('s.date', 's.poids')
            ->where('s.user = :user')
            ->andWhere('s.date BETWEEN :startDate AND :endDate')
            ->andWhere('s.poids IS NOT NULL')
            ->andWhere('s.poids > 0')
            ->setParameter('user', $user)
            ->setParameter('startDate', $startDate)
            ->setParameter('endDate', $endDate)
            ->orderBy('s.date', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Calcule les statistiques de poids pour l'utilisateur
     */
    public function getWeightStatistics(object $user): array
    {
        $qb = $this->createQueryBuilder('s')
            ->select(
                'COUNT(s.poids) as total_entries',
                'AVG(s.poids) as avg_weight',
                'MIN(s.poids) as min_weight',
                'MAX(s.poids) as max_weight',
                'MAX(s.date) as last_entry_date'
            )
            ->where('s.user = :user')
            ->andWhere('s.poids IS NOT NULL')
            ->setParameter('user', $user);

        $result = $qb->getQuery()->getOneOrNullResult();

        if (!$result) {
            return [
                'total_entries' => 0,
                'avg_weight' => null,
                'min_weight' => null,
                'max_weight' => null,
                'last_entry_date' => null,
                'weight_change_last_30_days' => null
            ];
        }

        // Calcul de l'évolution sur les 30 derniers jours
        $weightChange = $this->getWeightChangeLast30Days($user);

        return [
            'total_entries' => (int) $result['total_entries'],
            'avg_weight' => $result['avg_weight'] ? round((float) $result['avg_weight'], 1) : null,
            'min_weight' => $result['min_weight'] ? round((float) $result['min_weight'], 1) : null,
            'max_weight' => $result['max_weight'] ? round((float) $result['max_weight'], 1) : null,
            'last_entry_date' => $result['last_entry_date'],
            'weight_change_last_30_days' => $weightChange
        ];
    }

    /**
     * Calcule l'évolution du poids sur les 30 derniers jours
     */
    public function getWeightChangeLast30Days(object $user): ?float
    {
        $startDate = new \DateTime('-30 days');

        $results = $this->createQueryBuilder('s')
            ->select('s.poids', 's.date')
            ->where('s.user = :user')
            ->andWhere('s.date >= :startDate')
            ->andWhere('s.poids IS NOT NULL')
            ->setParameter('user', $user)
            ->setParameter('startDate', $startDate)
            ->orderBy('s.date', 'ASC')
            ->getQuery()
            ->getResult();

        if (count($results) < 2) {
            return null;
        }

        $firstWeight = (float) $results[0]['poids'];
        $lastWeight = (float) end($results)['poids'];

        return round($lastWeight - $firstWeight, 1);
    }

    /**
     * Récupère les données pour le graphique d'évolution du poids
     */
    public function getWeightChartData(object $user, int $days = 30): array
    {
        $evolution = $this->getWeightEvolution($user, $days);

        $labels = [];
        $data = [];

        foreach ($evolution as $entry) {
            // S'assurer que la date est un objet DateTime
            $date = $entry['date'];
            if ($date instanceof \DateTimeInterface) {
                $labels[] = $date->format('d/m');
            } else {
                // Si ce n'est pas un DateTime, essayer de le convertir
                try {
                    $dateObj = new \DateTime($date);
                    $labels[] = $dateObj->format('d/m');
                } catch (\Exception $e) {
                    $labels[] = 'N/A';
                }
            }

            $weight = $entry['poids'];
            $data[] = is_numeric($weight) ? (float) $weight : 0;
        }

        return [
            'labels' => $labels,
            'data' => $data
        ];
    }

    // --------------------------------------------------
    // Méthodes pour les statistiques des boîtes d'action
    // --------------------------------------------------

    /**
     * Calcule les statistiques d'humeur pour l'utilisateur
     */
    public function getMoodStatistics(object $user): array
    {
        // Récupérer toutes les entrées avec humeur non nulle
        $entries = $this->createQueryBuilder('s')
            ->where('s.user = :user')
            ->andWhere('s.humeur IS NOT NULL')
            ->setParameter('user', $user)
            ->getQuery()
            ->getResult();

        $total = count($entries);
        $moodCounts = [];

        // Compter chaque humeur individuellement
        foreach ($entries as $entry) {
            $humeurs = $entry->getHumeur();
            if (is_array($humeurs)) {
                foreach ($humeurs as $humeur) {
                    $moodValue = is_string($humeur) ? $humeur : $humeur->value;
                    $moodCounts[$moodValue] = ($moodCounts[$moodValue] ?? 0) + 1;
                }
            }
        }

        // Calculer les pourcentages pour chaque humeur
        $moodStats = [];
        $moodLabels = [
            'excellente' => 'Excellente',
            'heureuse' => 'Heureuse', 
            'joyeuse' => 'Joyeuse',
            'contente' => 'Contente',
            'calme' => 'Calme',
            'paisible' => 'Paisible',
            'neutre' => 'Neutre',
            'fatiguee' => 'Fatiguée',
            'ennuyee' => 'Ennuyée',
            'stressee' => 'Stressée',
            'anxieuse' => 'Anxieuse',
            'irritee' => 'Irritée',
            'frustree' => 'Frustrée',
            'triste' => 'Triste',
            'deprimee' => 'Déprimée',
            'en_colere' => 'En colère'
        ];

        foreach ($moodLabels as $key => $label) {
            $count = $moodCounts[$key] ?? 0;
            $percentage = $total > 0 ? round(($count / $total) * 100, 1) : 0;
            $moodStats[$key] = [
                'label' => $label,
                'count' => $count,
                'percentage' => $percentage
            ];
        }

        // Calculer le pourcentage d'humeurs négatives (qui devraient déclencher le chatbot)
        $negativeMoods = ['fatiguee', 'ennuyee', 'stressee', 'anxieuse', 'irritee', 'frustree', 'triste', 'deprimee', 'en_colere'];
        $negativePercentage = 0;
        foreach ($negativeMoods as $mood) {
            $negativePercentage += $moodStats[$mood]['percentage'] ?? 0;
        }

        return [
            'total_entries' => $total,
            'mood_stats' => $moodStats,
            'negative_percentage' => $negativePercentage,
            'showRecommendation' => $negativePercentage > 30
        ];
    }

    /**
     * Calcule les statistiques d'activité physique
     */
    public function getActivityStatistics(object $user): array
    {
        $results = $this->createQueryBuilder('s')
            ->select('s.activitePhysique', 'COUNT(s.activitePhysique) as count')
            ->where('s.user = :user')
            ->andWhere('s.activitePhysique IS NOT NULL')
            ->setParameter('user', $user)
            ->groupBy('s.activitePhysique')
            ->getQuery()
            ->getResult();

        $total = array_sum(array_column($results, 'count'));
        $activityCounts = [];
        foreach ($results as $result) {
            $activityValue = is_string($result['activitePhysique']) ? $result['activitePhysique'] : $result['activitePhysique']->value;
            $activityCounts[$activityValue] = $result['count'];
        }

        // Activité sédentaire ou légère = recommande abonnement
        $sedentary = ($activityCounts['sedentaire'] ?? 0) + ($activityCounts['leger'] ?? 0);
        $sedentaryPercentage = $total > 0 ? round(($sedentary / $total) * 100, 1) : 0;

        return [
            'total_entries' => $total,
            'sedentary_percentage' => $sedentaryPercentage,
            'showRecommendation' => $sedentaryPercentage > 50 // Plus de 50% sédentaire/léger
        ];
    }

    /**
     * Calcule les statistiques d'alimentation
     */
    public function getNutritionStatistics(object $user): array
    {
        $results = $this->createQueryBuilder('s')
            ->select('s.alimentation', 'COUNT(s.alimentation) as count')
            ->where('s.user = :user')
            ->andWhere('s.alimentation IS NOT NULL')
            ->setParameter('user', $user)
            ->groupBy('s.alimentation')
            ->getQuery()
            ->getResult();

        $total = array_sum(array_column($results, 'count'));
        $nutritionCounts = [];
        foreach ($results as $result) {
            $alimentationValue = is_string($result['alimentation']) ? $result['alimentation'] : $result['alimentation']->value;
            $nutritionCounts[$alimentationValue] = $result['count'];
        }

        // Alimentation faible ou mauvaise = recommande abonnement
        $poor = ($nutritionCounts['faible'] ?? 0) + ($nutritionCounts['mauvaise'] ?? 0);
        $poorPercentage = $total > 0 ? round(($poor / $total) * 100, 1) : 0;

        return [
            'total_entries' => $total,
            'poor_percentage' => $poorPercentage,
            'showRecommendation' => $poorPercentage > 30 // Plus de 30% faible/mauvaise
        ];
    }

    public function getLatestGoogleFitMetrics(object $user): array
    {
        $result = $this->createQueryBuilder('s')
            ->select('s.pas, s.calories, s.dureeActiviteMinutes, s.date')
            ->where('s.user = :user')
            ->andWhere('s.pas IS NOT NULL OR s.calories IS NOT NULL OR s.dureeActiviteMinutes IS NOT NULL')
            ->setParameter('user', $user)
            ->orderBy('s.date', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return $result ?? [
            'pas' => null,
            'calories' => null,
            'dureeActiviteMinutes' => null,
            'date' => null,
        ];
    }

    public function getAverageStepsLastDays(object $user, int $days = 7): ?float
    {
        $start = (new \DateTimeImmutable('today'))->modify(sprintf('-%d days', max(1, $days - 1)));

        $result = $this->createQueryBuilder('s')
            ->select('AVG(s.pas) as avg_steps')
            ->where('s.user = :user')
            ->andWhere('s.date >= :start')
            ->andWhere('s.pas IS NOT NULL')
            ->setParameter('user', $user)
            ->setParameter('start', $start)
            ->getQuery()
            ->getSingleScalarResult();

        return $result ? (float) $result : null;
    }

    /**
     * Résumé des statistiques affichées dans les cartes du dashboard.
     *
     * @return array{latestWeight:?float,weeklyActivityMinutes:int,averageWater:?float}
     */
    public function getDashboardSummaryStats(object $user): array
    {
        return [
            'latestWeight' => $this->getLatestWeightForUser($user),
            'weeklyActivityMinutes' => $this->getWeeklyActivityMinutesForUser($user, 7),
            'averageWater' => $this->getAverageWaterForUser($user),
        ];
    }

    /**
     * @return array{
     *   avgSleep7:float,
     *   avgWater7:float,
     *   avgTension7:float,
     *   highTensionDays7:int,
     *   nutritionGoodDays7:int,
     *   nutritionPoorDays7:int,
     *   nutritionEntries7:int,
     *   moodNegativeEntries7:int,
     *   moodSevereEntries7:int,
     *   moodPositiveEntries7:int,
     *   moodNegativeDays7:int,
     *   latestImc:float,
     *   weightChange30:float,
     *   activityMinutes7:int
     * }
     */
    public function getRiskFeatureSnapshot(object $user): array
    {
        $start7 = (new \DateTimeImmutable('today'))->modify('-6 days');

        $rows7 = $this->createQueryBuilder('s')
            ->select('s.date AS dateValue, s.sommeil AS sommeil, s.eauBue AS eau, s.tensionArterielle AS tension, s.alimentation AS alimentation, s.humeur AS humeur')
            ->where('s.user = :user')
            ->andWhere('s.date >= :start7')
            ->setParameter('user', $user)
            ->setParameter('start7', $start7)
            ->getQuery()
            ->getArrayResult();

        $sleepSum = 0.0;
        $sleepCount = 0;
        $waterSum = 0.0;
        $waterCount = 0;
        $tensionSum = 0.0;
        $tensionCount = 0;
        $highTensionDays = [];
        $nutritionGoodDays = 0;
        $nutritionPoorDays = 0;
        $nutritionEntries = 0;
        $moodNegativeEntries = 0;
        $moodSevereEntries = 0;
        $moodPositiveEntries = 0;
        $moodNegativeDays = [];
        $negativeMoods = ['fatiguee', 'ennuyee', 'stressee', 'anxieuse', 'irritee', 'frustree', 'triste', 'deprimee', 'en_colere'];
        $severeMoods = ['triste', 'deprimee', 'anxieuse'];
        $positiveMoods = ['excellente', 'heureuse', 'joyeuse', 'contente', 'calme', 'paisible'];

        foreach ($rows7 as $row) {
            if (isset($row['sommeil']) && is_numeric($row['sommeil'])) {
                $sleepSum += (float) $row['sommeil'];
                $sleepCount++;
            }
            if (isset($row['eau']) && is_numeric($row['eau'])) {
                $waterSum += (float) $row['eau'];
                $waterCount++;
            }
            if (isset($row['tension']) && is_numeric($row['tension'])) {
                $normalized = $this->normalizeTension((float) $row['tension']);
                $tensionSum += $normalized;
                $tensionCount++;
                if ($normalized >= 14.0 && isset($row['dateValue'])) {
                    $rawDate = $row['dateValue'];
                    if ($rawDate instanceof \DateTimeInterface) {
                        $highTensionDays[$rawDate->format('Y-m-d')] = true;
                    } else {
                        $highTensionDays[(new \DateTimeImmutable((string) $rawDate))->format('Y-m-d')] = true;
                    }
                }
            }
            if (array_key_exists('alimentation', $row) && $row['alimentation'] !== null) {
                $rawAlimentation = $row['alimentation'];
                $alimentation = $rawAlimentation instanceof \BackedEnum
                    ? mb_strtolower((string) $rawAlimentation->value)
                    : mb_strtolower(trim((string) $rawAlimentation));

                $nutritionEntries++;
                if (in_array($alimentation, ['bonne', 'tres_bonne', 'excellente'], true)) {
                    $nutritionGoodDays++;
                } elseif ($alimentation === 'faible') {
                    $nutritionPoorDays++;
                }
            }
            if (array_key_exists('humeur', $row) && $row['humeur'] !== null) {
                $moods = [];
                if (is_array($row['humeur'])) {
                    $moods = $row['humeur'];
                } elseif ($row['humeur'] instanceof \BackedEnum) {
                    $moods = [$row['humeur']->value];
                } else {
                    $rawMoods = trim((string) $row['humeur']);
                    if ($rawMoods !== '') {
                        $moods = array_map('trim', explode(',', $rawMoods));
                    }
                }

                $dayHasNegativeMood = false;
                foreach ($moods as $moodRaw) {
                    $mood = $moodRaw instanceof \BackedEnum
                        ? mb_strtolower((string) $moodRaw->value)
                        : mb_strtolower(trim((string) $moodRaw));
                    if ($mood === '') {
                        continue;
                    }

                    if (in_array($mood, $negativeMoods, true)) {
                        $moodNegativeEntries++;
                        $dayHasNegativeMood = true;
                    }
                    if (in_array($mood, $severeMoods, true)) {
                        $moodSevereEntries++;
                    }
                    if (in_array($mood, $positiveMoods, true)) {
                        $moodPositiveEntries++;
                    }
                }

                if ($dayHasNegativeMood && isset($row['dateValue'])) {
                    $rawDate = $row['dateValue'];
                    if ($rawDate instanceof \DateTimeInterface) {
                        $moodNegativeDays[$rawDate->format('Y-m-d')] = true;
                    } else {
                        $moodNegativeDays[(new \DateTimeImmutable((string) $rawDate))->format('Y-m-d')] = true;
                    }
                }
            }
        }

        $latestImcRow = $this->createQueryBuilder('s')
            ->select('s.imc AS imc')
            ->where('s.user = :user')
            ->andWhere('s.imc IS NOT NULL')
            ->setParameter('user', $user)
            ->orderBy('s.date', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return [
            'avgSleep7' => $sleepCount > 0 ? round($sleepSum / $sleepCount, 2) : 0.0,
            'avgWater7' => $waterCount > 0 ? round($waterSum / $waterCount, 2) : 0.0,
            'avgTension7' => $tensionCount > 0 ? round($tensionSum / $tensionCount, 2) : 0.0,
            'highTensionDays7' => count($highTensionDays),
            'nutritionGoodDays7' => $nutritionGoodDays,
            'nutritionPoorDays7' => $nutritionPoorDays,
            'nutritionEntries7' => $nutritionEntries,
            'moodNegativeEntries7' => $moodNegativeEntries,
            'moodSevereEntries7' => $moodSevereEntries,
            'moodPositiveEntries7' => $moodPositiveEntries,
            'moodNegativeDays7' => count($moodNegativeDays),
            'latestImc' => (isset($latestImcRow['imc']) && is_numeric($latestImcRow['imc'])) ? (float) $latestImcRow['imc'] : 0.0,
            'weightChange30' => $this->getWeightChangeLast30Days($user) ?? 0.0,
            'activityMinutes7' => $this->getWeeklyActivityMinutesForUser($user, 7),
        ];
    }

    /**
     * Séries de stats (7 jours) pour affichage en graphiques.
     *
     * @return array{
     *   labels: array<int, string>,
     *   weights: array<int, ?float>,
     *   sleep: array<int, ?float>,
     *   tension: array<int, ?float>,
     *   water: array<int, ?float>,
     *   activity: array<int, int>
     * }
     */
    public function getDashboardChartSeries(object $user, int $days = 7): array
    {
        $days = max(3, min(30, $days));
        $today = new \DateTimeImmutable('today');
        $start = $today->modify(sprintf('-%d days', $days - 1));

        $defaultSeries = [
            'labels' => [],
            'weights' => [],
            'sleep' => [],
            'tension' => [],
            'water' => [],
            'activity' => [],
        ];

        $userId = $this->resolveUserId($user);
        if ($userId === null) {
            return $defaultSeries;
        }

        $rows = $this->getEntityManager()->getConnection()->executeQuery(
            'SELECT date, poids, sommeil, tension_arterielle, eau_bue, duree_activite_minutes, activite_physique
             FROM sante_quotidienne
             WHERE user_id = :userId AND date >= :start
             ORDER BY date ASC',
            [
                'userId' => $userId,
                'start' => $start->format('Y-m-d 00:00:00'),
            ]
        )->fetchAllAssociative();

        $byDay = [];
        foreach ($rows as $row) {
            $dateValue = (string) ($row['date'] ?? '');
            $dayKey = substr($dateValue, 0, 10);
            if ($dayKey === '') {
                continue;
            }

            if (!isset($byDay[$dayKey])) {
                $byDay[$dayKey] = [
                    'poids_sum' => 0.0,
                    'poids_count' => 0,
                    'sleep_sum' => 0.0,
                    'sleep_count' => 0,
                    'tension_sum' => 0.0,
                    'tension_count' => 0,
                    'water_sum' => 0.0,
                    'water_count' => 0,
                    'activity' => 0,
                ];
            }

            if (isset($row['poids']) && is_numeric($row['poids'])) {
                $byDay[$dayKey]['poids_sum'] += (float) $row['poids'];
                $byDay[$dayKey]['poids_count']++;
            }
            if (isset($row['sommeil']) && is_numeric($row['sommeil'])) {
                $byDay[$dayKey]['sleep_sum'] += (float) $row['sommeil'];
                $byDay[$dayKey]['sleep_count']++;
            }
            if (isset($row['tension_arterielle']) && is_numeric($row['tension_arterielle'])) {
                $byDay[$dayKey]['tension_sum'] += (float) $row['tension_arterielle'];
                $byDay[$dayKey]['tension_count']++;
            }
            if (isset($row['eau_bue']) && is_numeric($row['eau_bue'])) {
                $byDay[$dayKey]['water_sum'] += (float) $row['eau_bue'];
                $byDay[$dayKey]['water_count']++;
            }

            if (isset($row['duree_activite_minutes']) && is_numeric($row['duree_activite_minutes'])) {
                $byDay[$dayKey]['activity'] += (int) round((float) $row['duree_activite_minutes']);
            } else {
                $level = mb_strtolower((string) ($row['activite_physique'] ?? ''));
                $byDay[$dayKey]['activity'] += match ($level) {
                    'sedentaire' => 10,
                    'leger' => 25,
                    'modere' => 45,
                    'intense' => 70,
                    default => 0,
                };
            }
        }

        for ($i = 0; $i < $days; $i++) {
            $day = $start->modify(sprintf('+%d days', $i));
            $dayKey = $day->format('Y-m-d');
            $stats = $byDay[$dayKey] ?? null;

            $defaultSeries['labels'][] = $day->format('d/m');
            $defaultSeries['weights'][] = $stats && $stats['poids_count'] > 0
                ? round($stats['poids_sum'] / $stats['poids_count'], 1)
                : null;
            $defaultSeries['sleep'][] = $stats && $stats['sleep_count'] > 0
                ? round($stats['sleep_sum'] / $stats['sleep_count'], 1)
                : null;
            $defaultSeries['tension'][] = $stats && $stats['tension_count'] > 0
                ? round($stats['tension_sum'] / $stats['tension_count'], 1)
                : null;
            $defaultSeries['water'][] = $stats && $stats['water_count'] > 0
                ? round($stats['water_sum'] / $stats['water_count'], 1)
                : null;
            $defaultSeries['activity'][] = $stats ? (int) $stats['activity'] : 0;
        }

        return $defaultSeries;
    }

    private function getLatestWeightForUser(object $user): ?float
    {
        $row = $this->createQueryBuilder('s')
            ->select('s.poids AS poids')
            ->where('s.user = :user')
            ->andWhere('s.poids IS NOT NULL')
            ->setParameter('user', $user)
            ->orderBy('s.date', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return isset($row['poids']) && is_numeric($row['poids']) ? (float) $row['poids'] : null;
    }

    private function getAverageWaterForUser(object $user): ?float
    {
        $result = $this->createQueryBuilder('s')
            ->select('AVG(s.eauBue) AS avgWater')
            ->where('s.user = :user')
            ->andWhere('s.eauBue IS NOT NULL')
            ->setParameter('user', $user)
            ->getQuery()
            ->getSingleScalarResult();

        return $result !== null ? (float) $result : null;
    }

    private function resolveUserId(object $user): ?int
    {
        if (method_exists($user, 'getId')) {
            $id = $user->getId();
            return is_numeric($id) ? (int) $id : null;
        }

        return null;
    }

    private function normalizeTension(float $raw): float
    {
        if ($raw <= 0) {
            return 0.0;
        }

        // Autorise saisie type "120" (systolique) ou "12" (échelle locale).
        return $raw > 30.0 ? ($raw / 10.0) : $raw;
    }

    private function getWeeklyActivityMinutesForUser(object $user, int $days = 7): int
    {
        $start = (new \DateTimeImmutable('today'))->modify(sprintf('-%d days', max(1, $days - 1)));

        $rows = $this->createQueryBuilder('s')
            ->select('s.dureeActiviteMinutes AS duration, s.activitePhysique AS level')
            ->where('s.user = :user')
            ->andWhere('s.date >= :start')
            ->andWhere('s.dureeActiviteMinutes IS NOT NULL OR s.activitePhysique IS NOT NULL')
            ->setParameter('user', $user)
            ->setParameter('start', $start)
            ->getQuery()
            ->getArrayResult();

        $fallbackByLevel = [
            'sedentaire' => 10,
            'leger' => 25,
            'modere' => 45,
            'intense' => 70,
        ];

        $total = 0;
        foreach ($rows as $row) {
            $duration = $row['duration'] ?? null;
            if (is_numeric($duration)) {
                $total += (int) round((float) $duration);
                continue;
            }

            $rawLevel = $row['level'] ?? '';
            if ($rawLevel instanceof \BackedEnum) {
                $level = mb_strtolower((string) $rawLevel->value);
            } elseif ($rawLevel instanceof \UnitEnum) {
                $level = mb_strtolower($rawLevel->name);
            } else {
                $level = mb_strtolower((string) $rawLevel);
            }
            $total += $fallbackByLevel[$level] ?? 0;
        }

        return max(0, $total);
    }
}
