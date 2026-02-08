<?php

namespace App\Repository;

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
}
