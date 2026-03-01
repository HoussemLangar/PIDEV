<?php

namespace App\EventSubscriber;

use App\Entity\Commentaire;
use App\Repository\ArticleScoreRepository;
use Doctrine\Common\EventSubscriber;
use Doctrine\ORM\Event\PostPersistEventArgs;
use Doctrine\ORM\Event\PostFlushEventArgs;
use Doctrine\ORM\Event\PostRemoveEventArgs;
use Doctrine\ORM\Event\PostUpdateEventArgs;
use Doctrine\ORM\Events;

class CommentScoreSubscriber implements EventSubscriber
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
            Events::postUpdate,
            Events::postRemove,
            Events::postFlush,
        ];
    }

    public function postPersist(PostPersistEventArgs $args): void
    {
        $this->collectIfComment($args->getObject());
    }

    public function postUpdate(PostUpdateEventArgs $args): void
    {
        $this->collectIfComment($args->getObject());
    }

    public function postRemove(PostRemoveEventArgs $args): void
    {
        $this->collectIfComment($args->getObject());
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
            $this->articleScoreRepository->updateScore((int) $contenuId, false);
        }

        $args->getObjectManager()->flush();
        $this->processing = false;
    }

    private function collectIfComment(object $object): void
    {
        if (!$object instanceof Commentaire) {
            return;
        }

        $contenu = $object->getContenu();
        if ($contenu->getId() === null) {
            return;
        }

        $this->pendingContentIds[$contenu->getId()] = true;
    }
}
