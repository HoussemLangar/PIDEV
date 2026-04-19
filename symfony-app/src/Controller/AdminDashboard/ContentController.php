<?php

namespace App\Controller\AdminDashboard;

use App\Entity\Commentaire;
use Symfony\Component\HttpFoundation\RequestStack;
use App\Entity\Contenu;
use App\Repository\CommentaireRepository;
use App\Repository\ContenuRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;

#[Route('/admin', name: 'admin_')]
class ContentController extends AbstractController
{
    public function __construct(
        private ContenuRepository $contenuRepository,
        private CommentaireRepository $commentaireRepository,
        private EntityManagerInterface $em,
        private RequestStack $requestStack,
        private MailerInterface $mailer
    ) {}

    #[Route('/content', name: 'content', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        $status = $request->query->get('status');
        $type = $request->query->get('type');
        $query = trim((string) $request->query->get('q', ''));

        $qb = $this->contenuRepository->createQueryBuilder('c')
            ->leftJoin('c.auteur', 'a')
            ->addSelect('a')
            ->orderBy('c.createdAt', 'DESC');

        if ($status) {
            $qb->andWhere('c.statut = :s')->setParameter('s', $status);
        }
        if ($type) {
            $qb->andWhere('c.type = :t')->setParameter('t', $type);
        }
        if ($query !== '') {
            $qb->andWhere('c.titre LIKE :q OR c.description LIKE :q OR c.tags LIKE :q')
                ->setParameter('q', '%' . $query . '%');
        }

        $contents = $qb->getQuery()->getResult();

        $statsRows = $this->contenuRepository->createQueryBuilder('c')
            ->select('c.statut as statut, COUNT(c.id) as total')
            ->groupBy('c.statut')
            ->getQuery()
            ->getResult();
        $stats = [
            'total' => 0,
            'en_attente' => 0,
            'valide' => 0,
            'publie' => 0,
            'rejete' => 0,
        ];
        foreach ($statsRows as $row) {
            $stats['total'] += (int) $row['total'];
            $stats[$row['statut']] = (int) $row['total'];
        }

        return $this->render('admin/content/index.html.twig', [
            'contents' => $contents,
            'status' => $status,
            'type' => $type,
            'q' => $query,
            'stats' => $stats,
        ]);
    }

    #[Route('/content/{id}/validate', name: 'content_validate', methods: ['POST'])]
    public function validate(Contenu $contenu, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        if (!$this->isCsrfTokenValid('content_action_' . $contenu->getId(), (string) $request->request->get('_token'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide.'], 403);
        }

        $contenu->setStatut('valide');
        if ($contenu->getDatePublication() === null) {
            $contenu->schedulePublicationAt(new \DateTime());
        }
        $contenu->forceUpdatedAt(new \DateTime());
        $this->em->flush();

        $auteur = $contenu->getAuteur();
        if ($auteur) {
            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($auteur->getEmail())
                ->subject('Votre contenu a été validé')
                ->htmlTemplate('emails/content_validated.html.twig')
                ->context([
                    'user' => $auteur,
                    'contenu' => $contenu,
                ]);
            $this->mailer->send($email);
        }

        return new JsonResponse(['success' => true, 'status' => 'valide']);
    }

    #[Route('/content/{id}/reject', name: 'content_reject', methods: ['POST'])]
    public function reject(Contenu $contenu, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        if (!$this->isCsrfTokenValid('content_action_' . $contenu->getId(), (string) $request->request->get('_token'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide.'], 403);
        }

        $contenu->setStatut('rejete');
        $contenu->forceUpdatedAt(new \DateTime());
        $this->em->flush();

        return new JsonResponse(['success' => true, 'status' => 'rejete']);
    }

    #[Route('/content/{id}/toggle-status', name: 'content_toggle_status', methods: ['POST'])]
    public function toggleStatus(Contenu $contenu, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        if (!$this->isCsrfTokenValid('content_action_' . $contenu->getId(), (string) $request->request->get('_token'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide.'], 403);
        }

        $currentStatus = $contenu->getStatut();
        
        // Basculer entre en_attente et publie
        if ($currentStatus === 'publie') {
            $contenu->setStatut('en_attente');
            $newStatus = 'en_attente';
            $statusLabel = 'En attente';
        } else {
            $contenu->setStatut('publie');
            $newStatus = 'publie';
            $statusLabel = 'Publié';
            
            // Définir la date de publication si ce n'est pas déjà fait
            if ($contenu->getDatePublication() === null) {
                $contenu->schedulePublicationAt(new \DateTime());
            }
        }
        
        $contenu->forceUpdatedAt(new \DateTime());
        $this->em->flush();

        return new JsonResponse([
            'success' => true, 
            'status' => $newStatus,
            'statusLabel' => $statusLabel
        ]);
    }

    #[Route('/content/{id}/update-status', name: 'content_update_status', methods: ['POST'])]
    public function updateStatus(Contenu $contenu, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        if (!$this->isCsrfTokenValid('content_action_' . $contenu->getId(), (string) $request->request->get('_token'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide.'], 403);
        }

        $newStatus = $request->request->get('status');
        
        if (!in_array($newStatus, ['en_attente', 'publie', 'rejete', 'valide'], true)) {
            return new JsonResponse(['success' => false, 'message' => 'Statut invalide.'], 400);
        }
        
        $contenu->setStatut($newStatus);
        
        // Définir la date de publication si le statut est publie ou valide
        if (in_array($newStatus, ['publie', 'valide'], true) && $contenu->getDatePublication() === null) {
            $contenu->schedulePublicationAt(new \DateTime());
        }
        
        $contenu->forceUpdatedAt(new \DateTime());
        $this->em->flush();

        $statusLabels = [
            'en_attente' => 'En attente',
            'publie' => 'Publié',
            'rejete' => 'Rejeté',
            'valide' => 'Validé',
        ];

        return new JsonResponse([
            'success' => true, 
            'status' => $newStatus,
            'statusLabel' => $statusLabels[$newStatus]
        ]);
    }

    #[Route('/content/{id}/delete', name: 'content_delete', methods: ['POST'])]
    public function delete(Contenu $contenu, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        if (!$this->isCsrfTokenValid('content_action_' . $contenu->getId(), (string) $request->request->get('_token'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide.'], 403);
        }

        $this->em->remove($contenu);
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }

    #[Route('/moderation', name: 'moderation', methods: ['GET'])]
    public function moderation(): Response
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        $request = $this->requestStack->getCurrentRequest();
        $type = $request ? $request->query->get('type') : null;
        $query = $request ? trim((string) $request->query->get('q', '')) : '';

        $qb = $this->commentaireRepository->createQueryBuilder('c')
            ->leftJoin('c.contenu', 'co')
            ->addSelect('co')
            ->leftJoin('c.user', 'u')
            ->addSelect('u')
            ->orderBy('c.createdAt', 'DESC');

        if ($type) {
            $qb->andWhere('co.type = :t')->setParameter('t', $type);
        }
        if ($query !== '') {
            $qb->andWhere('c.commentaire LIKE :q OR co.titre LIKE :q OR u.email LIKE :q')
                ->setParameter('q', '%' . $query . '%');
        }

        $comments = $qb->getQuery()->getResult();

        $stats = [
            'total' => (int) $this->commentaireRepository->createQueryBuilder('c')
                ->select('COUNT(c.id)')
                ->getQuery()
                ->getSingleScalarResult(),
            'last7' => (int) $this->commentaireRepository->createQueryBuilder('c')
                ->select('COUNT(c.id)')
                ->andWhere('c.createdAt >= :d')
                ->setParameter('d', new \DateTime('-7 days'))
                ->getQuery()
                ->getSingleScalarResult(),
            'uniqueContents' => (int) $this->commentaireRepository->createQueryBuilder('c')
                ->select('COUNT(DISTINCT c.contenu)')
                ->getQuery()
                ->getSingleScalarResult(),
        ];

        return $this->render('admin/moderation/index.html.twig', [
            'comments' => $comments,
            'type' => $type,
            'q' => $query,
            'stats' => $stats,
        ]);
    }

    #[Route('/moderation/comments/{id}/delete', name: 'moderation_comment_delete', methods: ['POST'])]
    public function deleteComment(Commentaire $commentaire, Request $request): JsonResponse
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        if (!$this->isCsrfTokenValid('comment_action_' . $commentaire->getId(), (string) $request->request->get('_token'))) {
            return new JsonResponse(['success' => false, 'message' => 'Token CSRF invalide.'], 403);
        }

        $this->em->remove($commentaire);
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }
}
