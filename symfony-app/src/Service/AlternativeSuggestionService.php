<?php

namespace App\Service;

use App\Entity\Medicament;
use App\Repository\MedicamentRepository;

class AlternativeSuggestionService
{
    public function __construct(private MedicamentRepository $medicamentRepository) {}

    /**
     * @return Medicament[]
     */
    public function suggestAlternatives(Medicament $medicament, int $limit = 5): array
    {
        $qb = $this->medicamentRepository->createQueryBuilder('m')
            ->andWhere('m.id != :id')
            ->setParameter('id', $medicament->getId())
            ->setMaxResults($limit)
            ->orderBy('m.nom', 'ASC');

        if ($medicament->getMolecule()) {
            $qb->andWhere('m.molecule = :molecule')
               ->setParameter('molecule', $medicament->getMolecule());
        } elseif ($medicament->getEffet()) {
            $qb->andWhere('m.effet = :effet')
               ->setParameter('effet', $medicament->getEffet());
        }

        return $qb->getQuery()->getResult();
    }
}
