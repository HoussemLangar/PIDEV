<?php

namespace App\EventSubscriber;

use App\Entity\Contenu;
use App\Repository\ArticleScoreRepository;
use Doctrine\Common\EventSubscriber;
use Doctrine\ORM\Event\PostFlushEventArgs;
use Doctrine\ORM\Event\PostPersistEventArgs;
use Doctrine\ORM\Events;

class ContentScoreSubscriber implements EventSubscriber
{
    /** @var array<int, true> */
    private array $pendingContentIds = [];
    private bool $processing = false;

    public function __construct(
        private readonly ArticleScoreRepository $articleScoreRepository
    ) {}

    public function getSubscribedEvents(): array
    {
        return [
            Events::postPersist,
            Events::postFlush,
        ];
    }

    public function postPersist(PostPersistEventArgs $args): void
    {
        $object = $args->getObject();
        if (!$object instanceof Contenu) {
            return;
        }

        $contenuId = $object->getId();
        if ($contenuId === null) {
            return;
        }

        $this->pendingContentIds[$contenuId] = true;
    }

    public function postFlush(PostFlushEventArgs $args): void
    {
        if ($this->processing || $this->pendingContentIds === []) {
            return;
        }

        $this->processing = true;
        $contentIds = array_keys($this->pendingContentIds);
        $this->pendingContentIds = [];

        foreach ($contentIds as $contenuId) {
            // Crée/maj la ligne article_scores à la création du contenu,
            // même s'il n'a pas encore de commentaires.
            $this->articleScoreRepository->updateScore((int) $contenuId, false);
        }

        $args->getObjectManager()->flush();
        $this->processing = false;
    }
}
