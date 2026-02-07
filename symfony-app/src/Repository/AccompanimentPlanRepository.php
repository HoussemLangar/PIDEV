<?php

namespace App\Repository;

use App\Entity\AccompanimentPlan;
use App\Entity\CoachSportif;
use App\Entity\Nutritionniste;
use App\Entity\Patient;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<AccompanimentPlan>
 */
class AccompanimentPlanRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, AccompanimentPlan::class);
    }

    /**
     * Find all plans for a patient
     */
    public function findByPatient(Patient $patient, string $status = null): array
    {
        $qb = $this->createQueryBuilder('p')
            ->where('p.patient = :patient')
            ->setParameter('patient', $patient);

        if ($status) {
            $qb->andWhere('p.status = :status')
                ->setParameter('status', $status);
        }

        return $qb->orderBy('p.startDate', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Find active plans for a patient
     */
    public function findActivePlans(Patient $patient): array
    {
        return $this->createQueryBuilder('p')
            ->where('p.patient = :patient')
            ->andWhere('p.status = :status')
            ->setParameter('patient', $patient)
            ->setParameter('status', 'active')
            ->getQuery()
            ->getResult();
    }

    /**
     * Find plans assigned to a coach
     */
    public function findByCoach(CoachSportif $coach, string $status = null): array
    {
        $qb = $this->createQueryBuilder('p')
            ->where('p.coach = :coach')
            ->setParameter('coach', $coach);

        if ($status) {
            $qb->andWhere('p.status = :status')
                ->setParameter('status', $status);
        }

        return $qb->orderBy('p.startDate', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Find plans assigned to a nutritionist
     */
    public function findByNutritionist(Nutritionniste $nutritionist, string $status = null): array
    {
        $qb = $this->createQueryBuilder('p')
            ->where('p.nutritionist = :nutritionist')
            ->setParameter('nutritionist', $nutritionist);

        if ($status) {
            $qb->andWhere('p.status = :status')
                ->setParameter('status', $status);
        }

        return $qb->orderBy('p.startDate', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Get statistics for a patient
     */
    public function getPatientStatistics(Patient $patient): array
    {
        $result = $this->createQueryBuilder('p')
            ->select('
                COUNT(CASE WHEN p.status = \'active\' THEN 1 END) as active,
                COUNT(CASE WHEN p.status = \'completed\' THEN 1 END) as completed,
                COUNT(CASE WHEN p.status = \'cancelled\' THEN 1 END) as cancelled
            ')
            ->where('p.patient = :patient')
            ->setParameter('patient', $patient)
            ->getQuery()
            ->getSingleResult();

        return [
            'active' => (int)$result['active'],
            'completed' => (int)$result['completed'],
            'cancelled' => (int)$result['cancelled'],
        ];
    }
}
