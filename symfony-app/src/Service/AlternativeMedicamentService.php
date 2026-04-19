<?php

namespace App\Service;

use App\Entity\Medicament;
use App\Repository\MedicamentRepository;
use App\Repository\StockPharmacyRepository;

class AlternativeMedicamentService
{
    public function __construct(
        private MedicamentRepository $medicamentRepository,
        private StockPharmacyRepository $stockRepository
    ) {}

    /**
     * @return array<int, array<string, mixed>>
     */
    public function suggestAlternatives(Medicament $medicament, int $limit = 5): array
    {
        $qb = $this->medicamentRepository->createQueryBuilder('m')
            ->andWhere('m.id != :id')
            ->setParameter('id', $medicament->getId())
            ->setMaxResults($limit);

        if ($medicament->getType()) {
            $qb->andWhere('m.type = :t')->setParameter('t', $medicament->getType());
        }
        if ($medicament->getForme()) {
            $qb->andWhere('m.forme = :f')->setParameter('f', $medicament->getForme());
        }
        if ($medicament->getDosage()) {
            $qb->andWhere('m.dosage = :d')->setParameter('d', $medicament->getDosage());
        }

        $alts = $qb->getQuery()->getResult();
        $result = [];
        foreach ($alts as $alt) {
            $stocks = $this->stockRepository->findAvailableByMedicament($alt->getId());
            if ($stocks === []) {
                continue;
            }
            $result[] = [
                'id' => $alt->getId(),
                'nom' => $alt->getNom(),
                'type' => $alt->getType(),
                'forme' => $alt->getForme(),
                'dosage' => $alt->getDosage(),
                'prix' => $stocks[0]->getPrixVente(),
                'pharmacie' => $stocks[0]->getPharmecie()->getNom(),
            ];
        }
        return $result;
    }
}
