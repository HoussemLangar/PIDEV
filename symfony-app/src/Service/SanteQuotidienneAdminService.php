<?php

namespace App\Service;

use App\Entity\SanteQuotidienne;
use App\Entity\User;
use App\Repository\SanteQuotidienneRepository;
use Symfony\Component\HttpFoundation\Request;

class SanteQuotidienneAdminService
{
    public function __construct(
        private readonly SanteQuotidienneRepository $repository
    ) {}

    /**
     * Récupère les données avec filtres, tri et pagination
     */
    public function getFilteredData(Request $request): array
    {
        $page = (int) $request->query->get('page', 1);
        $limit = 20;
        $offset = ($page - 1) * $limit;
        
        $search = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'date');
        $order = $request->query->get('order', 'DESC');
        $dateFrom = $request->query->get('date_from', '');
        $dateTo = $request->query->get('date_to', '');
        
        $qb = $this->repository->createQueryBuilder('s')
            ->orderBy("s.$sortBy", $order);
        
        // Filtre recherche (email utilisateur)
        if (!empty($search)) {
            $qb->leftJoin('s.user', 'u')
               ->andWhere('u.email LIKE :search')
               ->setParameter('search', '%' . $search . '%');
        } else {
            $qb->leftJoin('s.user', 'u');
        }
        
        // Filtre par date
        if (!empty($dateFrom)) {
            $qb->andWhere('s.date >= :dateFrom')
               ->setParameter('dateFrom', new \DateTime($dateFrom));
        }
        
        if (!empty($dateTo)) {
            $end = new \DateTime($dateTo);
            $end->setTime(23, 59, 59);
            $qb->andWhere('s.date <= :dateTo')
               ->setParameter('dateTo', $end);
        }
        
        $total = count($qb->getQuery()->getResult());
        
        $qb->setFirstResult($offset)
           ->setMaxResults($limit);
        
        return [
            'data' => $qb->getQuery()->getResult(),
            'total' => $total,
            'page' => $page,
            'limit' => $limit,
            'pages' => ceil($total / $limit),
            'filters' => [
                'search' => $search,
                'sort' => $sortBy,
                'order' => $order,
                'dateFrom' => $dateFrom,
                'dateTo' => $dateTo
            ]
        ];
    }

    /**
     * Statistiques globales
     */
    public function getStatistics(): array
    {
        $qb = $this->repository->createQueryBuilder('s');
        
        $stats = [
            'total_entries' => count($qb->getQuery()->getResult()),
            'users_count' => 0,
            'avg_weight' => 0,
            'avg_sleep' => 0,
            'entries_today' => 0,
            'entries_this_week' => 0,
            'entries_this_month' => 0
        ];
        
        // Nombre d'utilisateurs uniques
        $users = $qb->select('DISTINCT IDENTITY(s.user) as user_id')
                   ->getQuery()
                   ->getResult();
        $stats['users_count'] = count($users);
        
        // Poids moyen
        $avgWeight = $this->repository->createQueryBuilder('s')
            ->select('AVG(s.poids) as avg_weight')
            ->andWhere('s.poids IS NOT NULL')
            ->getQuery()
            ->getSingleScalarResult();
        $stats['avg_weight'] = $avgWeight ? round((float) $avgWeight, 1) : 0;
        
        // Sommeil moyen
        $avgSleep = $this->repository->createQueryBuilder('s')
            ->select('AVG(s.sommeil) as avg_sleep')
            ->andWhere('s.sommeil IS NOT NULL')
            ->getQuery()
            ->getSingleScalarResult();
        $stats['avg_sleep'] = $avgSleep ? round((float) $avgSleep, 1) : 0;
        
        // Entrées aujourd'hui
        $today = (new \DateTime())->setTime(0, 0, 0);
        $tomorrow = (clone $today)->modify('+1 day');
        $stats['entries_today'] = $this->repository->createQueryBuilder('s')
            ->where('s.date >= :today')
            ->andWhere('s.date < :tomorrow')
            ->setParameter('today', $today)
            ->setParameter('tomorrow', $tomorrow)
            ->getQuery()
            ->getResult();
        $stats['entries_today'] = count($stats['entries_today']);
        
        // Entrées cette semaine
        $weekAgo = new \DateTime('-7 days');
        $stats['entries_this_week'] = count($this->repository->createQueryBuilder('s')
            ->where('s.date >= :week')
            ->setParameter('week', $weekAgo)
            ->getQuery()
            ->getResult());
        
        // Entrées ce mois
        $monthAgo = new \DateTime('-30 days');
        $stats['entries_this_month'] = count($this->repository->createQueryBuilder('s')
            ->where('s.date >= :month')
            ->setParameter('month', $monthAgo)
            ->getQuery()
            ->getResult());
        
        return $stats;
    }

    /**
     * Export en format tableau pour Excel
     */
    public function getExportData(): array
    {
        return $this->repository->createQueryBuilder('s')
            ->leftJoin('s.user', 'u')
            ->select('u.email', 's.date', 's.poids', 's.sommeil', 's.humeur', 's.motivation', 's.activitePhysique')
            ->orderBy('s.date', 'DESC')
            ->getQuery()
            ->getResult();
    }
}
