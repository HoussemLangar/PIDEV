<?php

namespace App\Service;

use App\Entity\User;
use App\Repository\ContenuRepository;

class ContentRecommendationService
{
    public function __construct(private ContenuRepository $contenuRepository)
    {
    }

    /**
     * @return array<int, \App\Entity\Contenu>
     */
    public function getRecommendedFor(User $user, int $limit = 6): array
    {
        $limit = max(1, min(12, $limit));
        $keywords = $this->buildKeywords($user);

        if ($keywords !== []) {
            $qb = $this->contenuRepository->createValidatedQueryBuilder();
            $orX = $qb->expr()->orX();
            foreach ($keywords as $index => $keyword) {
                $param = 'k' . $index;
                $orX->add("c.titre LIKE :$param");
                $orX->add("c.description LIKE :$param");
                $orX->add("c.tags LIKE :$param");
                $orX->add("c.categorie LIKE :$param");
                $qb->setParameter($param, '%' . $keyword . '%');
            }

            $qb->andWhere($orX)
                ->orderBy('c.datePublication', 'DESC')
                ->addOrderBy('c.createdAt', 'DESC')
                ->setMaxResults($limit);

            $items = $qb->getQuery()->getResult();
            if (count($items) > 0) {
                return $items;
            }
        }

        return $this->contenuRepository->findValidatedPage(1, $limit)['items'];
    }

    /**
     * @return string[]
     */
    private function buildKeywords(User $user): array
    {
        $keywords = [];
        $role = $user->getRole();
        $subscriptionType = $user->getSubscriptionType();

        $map = [
            'ROLE_PATIENT' => ['patient', 'bien-etre', 'sante'],
            'ROLE_MEDECIN' => ['medecin', 'medical', 'clinique'],
            'ROLE_COACH' => ['sport', 'coaching', 'entrainement'],
            'ROLE_NUTRITIONNISTE' => ['nutrition', 'alimentaire', 'dietetique'],
        ];

        if (isset($map[$role])) {
            $keywords = array_merge($keywords, $map[$role]);
        }
        if ($subscriptionType) {
            $keywords[] = $subscriptionType;
        }

        if ($user->getMedecin()) {
            $keywords[] = $user->getMedecin()->getSpecialite();
        }
        if ($user->getCoachSportif()) {
            $keywords[] = $user->getCoachSportif()->getSpecialite();
        }
        if ($user->getNutritionniste()) {
            $keywords[] = $user->getNutritionniste()->getSpecialite();
        }

        foreach ($user->getLikes() as $like) {
            $contenu = $like->getContenu();
            if ($contenu->getCategorie()) {
                $keywords[] = $contenu->getCategorie();
            }
            if ($contenu->getTags()) {
                $keywords = array_merge($keywords, array_map('trim', explode(',', $contenu->getTags())));
            }
        }

        foreach ($user->getCommentaires() as $commentaire) {
            $contenu = $commentaire->getContenu();
            if ($contenu->getCategorie()) {
                $keywords[] = $contenu->getCategorie();
            }
            if ($contenu->getTags()) {
                $keywords = array_merge($keywords, array_map('trim', explode(',', $contenu->getTags())));
            }
        }

        return array_values(array_unique(array_filter(array_map('strtolower', $keywords))));
    }
}
