<?php

namespace App\Repository;

use App\Entity\Patient;
use App\Entity\SymptomeQuotidien;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SymptomeQuotidien>
 */
class SymptomeQuotidienRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SymptomeQuotidien::class);
    }

    /**
     * Find all SymptomeQuotidien entities ordered by ID desc
     * @return SymptomeQuotidien[]
     */
    public function findAll(): array
    {
        return $this->findBy([], ['id' => 'DESC']);
    }

    /**
     * Find SymptomeQuotidien by ID
     */
    public function findById(int $id): ?SymptomeQuotidien
    {
        return $this->find($id);
    }

    /**
     * Save SymptomeQuotidien entity
     */
    public function save(SymptomeQuotidien $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->persist($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Delete SymptomeQuotidien entity
     */
    public function delete(SymptomeQuotidien $entity, bool $flush = true): void
    {
        $em = $this->getEntityManager();
        $em->remove($entity);
        if ($flush) {
            $em->flush();
        }
    }

    /**
     * Flush pending changes
     */
    public function flush(): void
    {
        $this->getEntityManager()->flush();
    }

    /**
     * Trouve les symptômes enregistrés à une date spécifique
     * @param string $date Date au format Y-m-d
     * @return SymptomeQuotidien[]
     */
    public function findByDate(string $date): array
    {
        $startDate = new \DateTime($date . ' 00:00:00');
        $endDate = new \DateTime($date . ' 23:59:59');
        
        return $this->createQueryBuilder('s')
            ->leftJoin('s.symptome', 'sl')
            ->leftJoin('s.patient', 'p')
            ->addSelect('sl', 'p')
            ->where('s.dateSymptome >= :startDate')
            ->andWhere('s.dateSymptome <= :endDate')
            ->setParameter('startDate', $startDate)
            ->setParameter('endDate', $endDate)
            ->orderBy('s.createdAt', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Trouve les symptômes enregistrés à une date spécifique pour un patient
     * @param string $date Date au format Y-m-d
     */
    public function findByDateForPatient(string $date, $patient): array
    {
        $startDate = new \DateTime($date . ' 00:00:00');
        $endDate = new \DateTime($date . ' 23:59:59');

        return $this->createQueryBuilder('s')
            ->leftJoin('s.symptome', 'sl')
            ->leftJoin('s.patient', 'p')
            ->addSelect('sl', 'p')
            ->where('s.dateSymptome >= :startDate')
            ->andWhere('s.dateSymptome <= :endDate')
            ->andWhere('s.patient = :patient')
            ->setParameter('startDate', $startDate)
            ->setParameter('endDate', $endDate)
            ->setParameter('patient', $patient)
            ->orderBy('s.createdAt', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Trouver tous les symptômes d'un patient avec leurs détails
     */
    public function findByPatientWithDetails($patient): array
    {
        return $this->createQueryBuilder('sq')
            ->leftJoin('sq.symptome', 's')
            ->addSelect('s')
            ->where('sq.patient = :patient')
            ->setParameter('patient', $patient)
            ->orderBy('sq.dateSymptome', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Obtenir les statistiques des symptômes les plus courants pour un patient
     */
    public function getTopSymptomStatistics($patient, int $limit = 6): array
    {
        return $this->createQueryBuilder('sq')
            ->select('s.nom as symptome_nom, s.categorie as categorie_nom, COUNT(sq.id) as count, AVG(sq.intensite) as avg_intensite, MAX(sq.dateSymptome) as lastDate')
            ->leftJoin('sq.symptome', 's')
            ->where('sq.patient = :patient')
            ->setParameter('patient', $patient)
            ->groupBy('s.id')
            ->orderBy('count', 'DESC')
            ->addOrderBy('avg_intensite', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Obtenir le nombre total de symptômes pour un patient
     */
    public function getTotalSymptomsCount($patient): int
    {
        return $this->createQueryBuilder('sq')
            ->select('COUNT(sq.id)')
            ->where('sq.patient = :patient')
            ->setParameter('patient', $patient)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Obtenir la moyenne générale d'intensité des symptômes pour un patient
     */
    public function getAverageIntensity($patient): float
    {
        $result = $this->createQueryBuilder('sq')
            ->select('AVG(sq.intensite)')
            ->where('sq.patient = :patient')
            ->setParameter('patient', $patient)
            ->getQuery()
            ->getSingleScalarResult();
            
        return $result ? round($result, 1) : 0.0;
    }

    /**
     * Obtenir les symptômes par période pour un patient
     */
    public function getSymptomsByPeriod($patient, \DateTime $startDate = null, \DateTime $endDate = null): array
    {
        $qb = $this->createQueryBuilder('sq')
            ->leftJoin('sq.symptome', 's')
            ->addSelect('s')
            ->where('sq.patient = :patient')
            ->setParameter('patient', $patient)
            ->orderBy('sq.dateSymptome', 'DESC');

        if ($startDate) {
            $qb->andWhere('sq.dateSymptome >= :startDate')
               ->setParameter('startDate', $startDate);
        }

        if ($endDate) {
            $qb->andWhere('sq.dateSymptome <= :endDate')
               ->setParameter('endDate', $endDate);
        }

        return $qb->getQuery()->getResult();
    }

    /**
     * Obtenir les statistiques des patients qui enregistrent des symptômes
     */
    public function getUserSymptomStatistics(): array
    {
        $conn = $this->getEntityManager()->getConnection();
        
        $sql = "
            SELECT 
                p.id as patient_id,
                u.email as user_email,
                u.nom as patient_nom,
                u.prenom as patient_prenom,
                COUNT(sq.id) as total_symptoms,
                COUNT(DISTINCT sq.date_symptome) as days_tracked,
                AVG(sq.intensite) as avg_intensity,
                MAX(sq.date_symptome) as last_symptom_date
            FROM symptomes_quotidiens sq
            INNER JOIN patients p ON sq.patient_id = p.id
            INNER JOIN users u ON p.user_id = u.id
            GROUP BY p.id, u.email, u.nom, u.prenom
            ORDER BY total_symptoms DESC
        ";
        
        $results = $conn->executeQuery($sql)->fetchAllAssociative();
        
        // Formater les résultats
        foreach ($results as &$result) {
            $nom = trim($result['patient_nom'] ?? '');
            $prenom = trim($result['patient_prenom'] ?? '');
            $result['patient_name'] = !empty($nom) || !empty($prenom) 
                ? trim($nom . ' ' . $prenom) 
                : 'Patient #' . $result['patient_id'];
                
            // Convertir last_symptom_date en DateTime
            if ($result['last_symptom_date']) {
                $result['last_symptom_date'] = new \DateTime($result['last_symptom_date']);
            }
        }
        
        return $results;
    }

    /**
     * Obtenir les symptômes les plus courants trackés par tous les patients
     */
    public function getMostCommonSymptoms(int $limit = 10): array
    {
        $conn = $this->getEntityManager()->getConnection();
        
        $sql = "
            SELECT 
                sl.nom as symptom_name,
                sl.categorie as category,
                COUNT(sq.id) as count
            FROM symptomes_quotidiens sq
            INNER JOIN symptomes_liste sl ON sq.symptome_id = sl.id
            GROUP BY sl.id, sl.nom, sl.categorie
            ORDER BY count DESC
            LIMIT " . (int)$limit . "
        ";
        
        $results = $conn->executeQuery($sql)->fetchAllAssociative();
        
        return $results;
    }

    /**
     * Nombre total de symptômes enregistrés (tous patients)
     */
    public function getTotalTrackedCount(): int
    {
        return (int) $this->createQueryBuilder('sq')
            ->select('COUNT(sq.id)')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Nombre de symptômes enregistrés sur les 7 derniers jours (tous patients)
     */
    public function getWeeklyTrackedCount(): int
    {
        $weekAgo = new \DateTimeImmutable('-7 days');

        return (int) $this->createQueryBuilder('sq')
            ->select('COUNT(sq.id)')
            ->where('sq.dateSymptome >= :weekAgo')
            ->setParameter('weekAgo', $weekAgo)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Intensité moyenne globale (tous patients)
     */
    public function getAverageIntensityAll(): float
    {
        $result = $this->createQueryBuilder('sq')
            ->select('AVG(sq.intensite)')
            ->getQuery()
            ->getSingleScalarResult();

        return $result ? round((float) $result, 1) : 0.0;
    }

    /**
     * @return array{
     *   totalSymptoms:int,
     *   avgIntensity:float,
     *   feverCount:int,
     *   coughCount:int,
     *   fatigueCount:int,
     *   moodCount:int,
     *   feverCoughCoOccurrenceDays:int
     * }
     */
    public function getRiskFeatureSnapshot(Patient $patient, int $days = 7): array
    {
        $days = max(1, min(60, $days));
        $start = (new \DateTimeImmutable('today'))->modify(sprintf('-%d days', $days - 1));

        $rows = $this->createQueryBuilder('sq')
            ->select('sq.dateSymptome AS dateSymptome, sq.intensite AS intensite, sq.notes AS notes, sl.nom AS symptomName')
            ->leftJoin('sq.symptome', 'sl')
            ->where('sq.patient = :patient')
            ->andWhere('sq.dateSymptome >= :start')
            ->setParameter('patient', $patient)
            ->setParameter('start', $start)
            ->getQuery()
            ->getArrayResult();

        $total = count($rows);
        $sumIntensity = 0.0;
        $feverCount = 0;
        $coughCount = 0;
        $fatigueCount = 0;
        $moodCount = 0;
        $daysFlags = [];

        foreach ($rows as $row) {
            $sumIntensity += (float) ($row['intensite'] ?? 0);
            $dayKey = null;
            if (isset($row['dateSymptome'])) {
                $rawDate = $row['dateSymptome'];
                if ($rawDate instanceof \DateTimeInterface) {
                    $dayKey = $rawDate->format('Y-m-d');
                } else {
                    $dayKey = (new \DateTimeImmutable((string) $rawDate))->format('Y-m-d');
                }
            }
            if ($dayKey !== null && !isset($daysFlags[$dayKey])) {
                $daysFlags[$dayKey] = ['fever' => false, 'cough' => false];
            }

            $haystack = mb_strtolower(trim((string) ($row['symptomName'] ?? '')) . ' ' . trim((string) ($row['notes'] ?? '')));

            if ($haystack !== '') {
                if (preg_match('/\b(fievre|fi[eè]vre|fever|temperature)\b/u', $haystack)) {
                    $feverCount++;
                    if ($dayKey !== null) {
                        $daysFlags[$dayKey]['fever'] = true;
                    }
                }
                if (preg_match('/\b(toux|cough|grippe|rhume|gorge)\b/u', $haystack)) {
                    $coughCount++;
                    if ($dayKey !== null) {
                        $daysFlags[$dayKey]['cough'] = true;
                    }
                }
                if (preg_match('/\b(fatigue|fatigu[eé]|epuise|faiblesse)\b/u', $haystack)) {
                    $fatigueCount++;
                }
                if (preg_match('/\b(triste|deprime|depress|anxie|stress|angoiss)\b/u', $haystack)) {
                    $moodCount++;
                }
            }
        }

        $coOccurrence = 0;
        foreach ($daysFlags as $flags) {
            if (($flags['fever'] ?? false) && ($flags['cough'] ?? false)) {
                $coOccurrence++;
            }
        }

        return [
            'totalSymptoms' => $total,
            'avgIntensity' => $total > 0 ? round($sumIntensity / $total, 2) : 0.0,
            'feverCount' => $feverCount,
            'coughCount' => $coughCount,
            'fatigueCount' => $fatigueCount,
            'moodCount' => $moodCount,
            'feverCoughCoOccurrenceDays' => $coOccurrence,
        ];
    }

    /**
     * @return array<int, array{patient_id:int,name:string,email:string}>
     */
    public function getDoctorDashboardPatients(): array
    {
        $conn = $this->getEntityManager()->getConnection();

        $sql = "
            SELECT p.id AS patient_id,
                   u.email AS email,
                   u.nom AS nom,
                   u.prenom AS prenom
            FROM patients p
            INNER JOIN users u ON u.id = p.user_id
            ORDER BY u.nom ASC, u.prenom ASC, u.email ASC
        ";

        $rows = $conn->executeQuery($sql)->fetchAllAssociative();

        return array_map(static function (array $row): array {
            $name = trim((string) ($row['nom'] ?? '') . ' ' . (string) ($row['prenom'] ?? ''));
            if ($name === '') {
                $name = 'Patient #' . (int) ($row['patient_id'] ?? 0);
            }

            return [
                'patient_id' => (int) ($row['patient_id'] ?? 0),
                'name' => $name,
                'email' => (string) ($row['email'] ?? ''),
            ];
        }, $rows);
    }

    /**
     * @return array<int, array{date:string,count:int,avgIntensity:float}>
     */
    public function getDoctorDailyEvolution(?int $patientId = null, int $days = 35): array
    {
        $days = max(7, min(90, $days));
        $start = (new \DateTimeImmutable('today'))->modify(sprintf('-%d days', $days - 1));

        $qb = $this->createQueryBuilder('sq')
            ->select('sq.dateSymptome AS dateSymptome, COUNT(sq.id) AS dayCount, AVG(sq.intensite) AS avgIntensity')
            ->where('sq.dateSymptome >= :start')
            ->setParameter('start', $start)
            ->groupBy('sq.dateSymptome')
            ->orderBy('sq.dateSymptome', 'ASC');

        if ($patientId !== null && $patientId > 0) {
            $qb->andWhere('IDENTITY(sq.patient) = :patientId')
                ->setParameter('patientId', $patientId);
        }

        $rows = $qb->getQuery()->getArrayResult();

        return array_map(static function (array $row): array {
            $rawDate = $row['dateSymptome'] ?? null;
            $date = $rawDate instanceof \DateTimeInterface
                ? $rawDate->format('Y-m-d')
                : (new \DateTimeImmutable((string) $rawDate))->format('Y-m-d');

            return [
                'date' => $date,
                'count' => (int) ($row['dayCount'] ?? 0),
                'avgIntensity' => round((float) ($row['avgIntensity'] ?? 0), 2),
            ];
        }, $rows);
    }

    /**
     * @return array<int, array{week:string,day:int,avgIntensity:float,count:int}>
     */
    public function getDoctorWeeklyHeatmap(?int $patientId = null, int $weeks = 8): array
    {
        $weeks = max(4, min(16, $weeks));
        $start = (new \DateTimeImmutable('monday this week'))->modify(sprintf('-%d weeks', $weeks - 1));

        $qb = $this->createQueryBuilder('sq')
            ->select('sq.dateSymptome AS dateSymptome, AVG(sq.intensite) AS avgIntensity, COUNT(sq.id) AS dayCount')
            ->where('sq.dateSymptome >= :start')
            ->setParameter('start', $start)
            ->groupBy('sq.dateSymptome')
            ->orderBy('sq.dateSymptome', 'ASC');

        if ($patientId !== null && $patientId > 0) {
            $qb->andWhere('IDENTITY(sq.patient) = :patientId')
                ->setParameter('patientId', $patientId);
        }

        $rows = $qb->getQuery()->getArrayResult();
        $heatmap = [];

        foreach ($rows as $row) {
            $rawDate = $row['dateSymptome'] ?? null;
            $date = $rawDate instanceof \DateTimeInterface
                ? \DateTimeImmutable::createFromInterface($rawDate)
                : new \DateTimeImmutable((string) $rawDate);

            $week = $date->modify('monday this week')->format('Y-m-d');
            $heatmap[] = [
                'week' => $week,
                'day' => (int) $date->format('N'),
                'avgIntensity' => round((float) ($row['avgIntensity'] ?? 0), 2),
                'count' => (int) ($row['dayCount'] ?? 0),
            ];
        }

        return $heatmap;
    }
}
