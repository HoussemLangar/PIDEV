<?php

namespace App\Controller\FrontOffice;

use App\Entity\Contenu;
use App\Entity\User;
use App\Form\ContenuType;
use App\Repository\ArticleScoreRepository;
use App\Repository\ContenuRepository;
use App\Security\ContentVoter;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\String\Slugger\SluggerInterface;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Symfony\Component\Validator\Constraints\Url;
use Symfony\Component\Validator\Validator\ValidatorInterface;

#[Route('/contenus', name: 'front_content_')]
class ContentController extends AbstractController
{
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(
        ContenuRepository $contenuRepository,
        ArticleScoreRepository $articleScoreRepository
    ): Response
    {
        $this->denyAccessUnlessGranted(ContentVoter::VIEW);

        /** @var User $user */
        $user = $this->getUser();
        $effectiveRole = $user->getSubscriptionType() ?? $user->getRole();
        $showRecommended = $effectiveRole === 'ROLE_PATIENT';
        $recommended = [];
        if ($showRecommended) {
            $professionalRoles = ['ROLE_MEDECIN', 'ROLE_PHARMACIEN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE'];
            $publishedContents = array_values(array_filter(
                $contenuRepository->findFiltered('article', null, null, ['publie', 'valide']),
                static function (Contenu $content) use ($professionalRoles): bool {
                    $author = $content->getAuteur();
                    if (!$author instanceof User) {
                        return false;
                    }

                    $authorRole = $author->getSubscriptionType() ?? $author->getRole();

                    return in_array($authorRole, $professionalRoles, true)
                        || $author->getMedecin() !== null
                        || $author->getPharmacien() !== null
                        || $author->getCoachSportif() !== null
                        || $author->getNutritionniste() !== null;
                }
            ));
            $contentIds = array_values(array_filter(array_map(
                static fn (Contenu $content): ?int => $content->getId(),
                $publishedContents
            )));
            $scoreMap = $articleScoreRepository->findScoreMapByContenuIds($contentIds);

            usort($publishedContents, static function (Contenu $left, Contenu $right) use ($scoreMap): int {
                $leftId = (int) $left->getId();
                $rightId = (int) $right->getId();
                $leftScore = $scoreMap[$leftId]['score'] ?? 0.0;
                $rightScore = $scoreMap[$rightId]['score'] ?? 0.0;

                $scoreComparison = $rightScore <=> $leftScore;
                if ($scoreComparison !== 0) {
                    return $scoreComparison;
                }

                return $rightId <=> $leftId;
            });

            $recommended = array_slice($publishedContents, 0, 4);
        }
        $likedIds = [];
        foreach ($user->getLikes() as $like) {
            $likedIds[] = $like->getContenu()->getId();
        }

        $canCreate = $this->isGranted(ContentVoter::CREATE);

        return $this->render('front/content/index.html.twig', [
            'recommended' => $recommended,
            'showRecommended' => $showRecommended,
            'canCreate' => $canCreate,
            'likedIds' => $likedIds,
        ]);
    }

    #[Route('/ajouter', name: 'create', methods: ['GET', 'POST'])]
    public function create(
        Request $request,
        EntityManagerInterface $em,
        SluggerInterface $slugger,
        ValidatorInterface $validator,
        MailerInterface $mailer
    ): Response {
        $this->denyAccessUnlessGranted(ContentVoter::CREATE);

        $contenu = new Contenu();
        return $this->handleForm($request, $em, $slugger, $validator, $mailer, $contenu, isEdit: false);
    }

    #[Route('/{id}/modifier', name: 'edit', methods: ['GET', 'POST'], requirements: ['id' => '\\d+'])]
    public function edit(
        Contenu $contenu,
        Request $request,
        EntityManagerInterface $em,
        SluggerInterface $slugger,
        ValidatorInterface $validator,
        MailerInterface $mailer
    ): Response {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User || !$contenu->getAuteur() || $contenu->getAuteur()->getId() !== $user->getId()) {
            throw $this->createAccessDeniedException();
        }

        return $this->handleForm($request, $em, $slugger, $validator, $mailer, $contenu, isEdit: true);
    }

    #[Route('/{id}/supprimer', name: 'delete', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function delete(Contenu $contenu, Request $request, EntityManagerInterface $em): Response
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User || !$contenu->getAuteur() || $contenu->getAuteur()->getId() !== $user->getId()) {
            throw $this->createAccessDeniedException();
        }
        if (!$this->isCsrfTokenValid('content_delete_' . $contenu->getId(), (string) $request->request->get('_token'))) {
            throw $this->createAccessDeniedException();
        }

        $em->remove($contenu);
        $em->flush();

        $this->addFlash('success', 'Contenu supprimé.');
        return $this->redirectToRoute('front_content_index');
    }

    #[Route('/{id}', name: 'show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(Contenu $contenu): Response
    {
        $this->denyAccessUnlessGranted(ContentVoter::VIEW);

        /** @var User $user */
        $user = $this->getUser();
        $isOwner = $user instanceof User && $contenu->getAuteur() && $contenu->getAuteur()->getId() === $user->getId();
        $isAdmin = in_array('ROLE_ADMIN', $user->getRoles(), true);

        if (!$isAdmin && !$isOwner && !in_array($contenu->getStatut(), ['publie', 'valide'], true)) {
            throw $this->createNotFoundException('Contenu introuvable.');
        }

        return $this->render('front/content/show.html.twig', [
            'contenu' => $contenu,
            'isOwner' => $isOwner,
        ]);
    }

    private function handleForm(
        Request $request,
        EntityManagerInterface $em,
        SluggerInterface $slugger,
        ValidatorInterface $validator,
        MailerInterface $mailer,
        Contenu $contenu,
        bool $isEdit
    ): Response {
        $form = $this->createForm(ContenuType::class, $contenu);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            /** @var User $user */
            $user = $this->getUser();
            if (!$isEdit) {
                $contenu->setAuteur($user);
            }
            
            // Sauvegarder le contenu existant en cas de modification
            $existingContenu = $isEdit ? $contenu->getContenu() : null;
            
            $contenu->setStatut('en_attente');
            $contenu->schedulePublicationAt(null);

            $type = $contenu->getType();
            
            // Pour les articles et liens, si le contenu est vide lors d'une modification, restaurer l'ancien
            if ($isEdit && $type === 'article' && (!$contenu->getContenu() || trim($contenu->getContenu()) === '')) {
                $contenu->setContenu($existingContenu);
            }
            
            if ($isEdit && $type === 'lien' && (!$contenu->getContenu() || trim($contenu->getContenu()) === '')) {
                $contenu->setContenu($existingContenu);
            }
            
            if ($type === 'lien' && $contenu->getContenu()) {
                $urlErrors = $validator->validate($contenu->getContenu(), new Url([
                    'message' => 'Le lien fourni est invalide.',
                ]));
                if (count($urlErrors) > 0) {
                    $this->addFlash('error', $urlErrors[0]->getMessage());
                    return $this->render('front/content/create.html.twig', [
                        'form' => $form->createView(),
                        'isEdit' => $isEdit,
                        'contenu' => $contenu,
                    ]);
                }
            }

            /** @var UploadedFile|null $pdfFile */
            $pdfFile = $form->get('pdfFile')->getData();
            /** @var UploadedFile|null $videoFile */
            $videoFile = $form->get('videoFile')->getData();
            if ($type === 'pdf') {
                if (!$pdfFile && !$isEdit) {
                    $this->addFlash('error', 'Veuillez fournir un fichier PDF.');
                    return $this->render('front/content/create.html.twig', [
                        'form' => $form->createView(),
                        'isEdit' => $isEdit,
                        'contenu' => $contenu,
                    ]);
                }
                if ($pdfFile) {
                    $safeFilename = $slugger->slug(pathinfo($pdfFile->getClientOriginalName(), PATHINFO_FILENAME));
                    $newFilename = $safeFilename . '-' . uniqid() . '.' . $pdfFile->guessExtension();
                    $uploadDir = $this->getParameter('kernel.project_dir') . '/var/uploads/contenus';
                    if (!is_dir($uploadDir)) {
                        mkdir($uploadDir, 0775, true);
                    }
                    $pdfFile->move($uploadDir, $newFilename);
                    $contenu->setContenu('/media/contenus/' . $newFilename);
                } elseif ($isEdit && !$contenu->getContenu()) {
                    // Restaurer le contenu existant si aucun nouveau fichier n'est uploadé
                    $contenu->setContenu($existingContenu);
                }
            }

            if ($type === 'video') {
                if ($videoFile) {
                    $safeFilename = $slugger->slug(pathinfo($videoFile->getClientOriginalName(), PATHINFO_FILENAME));
                    $newFilename = $safeFilename . '-' . uniqid() . '.' . $videoFile->guessExtension();
                    $uploadDir = $this->getParameter('kernel.project_dir') . '/var/uploads/contenus';
                    if (!is_dir($uploadDir)) {
                        mkdir($uploadDir, 0775, true);
                    }
                    $videoFile->move($uploadDir, $newFilename);
                    $contenu->setContenu('/media/contenus/' . $newFilename);
                } elseif (!$isEdit) {
                    $this->addFlash('error', 'Veuillez fournir un fichier vidéo.');
                    return $this->render('front/content/create.html.twig', [
                        'form' => $form->createView(),
                        'isEdit' => $isEdit,
                        'contenu' => $contenu,
                    ]);
                } elseif ($isEdit && !$contenu->getContenu()) {
                    // Restaurer le contenu existant si aucun nouveau fichier n'est uploadé
                    $contenu->setContenu($existingContenu);
                }
            }

            $em->persist($contenu);
            $em->flush();

            if (!$isEdit && $user instanceof User) {
                $email = (new TemplatedEmail())
                    ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                    ->to($user->getEmail())
                    ->subject('Votre contenu a bien été soumis')
                    ->htmlTemplate('emails/content_submitted.html.twig')
                    ->context([
                        'user' => $user,
                        'contenu' => $contenu,
                    ]);
                $mailer->send($email);
            }

            $this->addFlash('success', $isEdit ? 'Contenu mis à jour (en attente de validation).' : 'Votre contenu est en attente de validation.');
            return $this->redirectToRoute('front_content_index');
        }

        if ($form->isSubmitted() && !$form->isValid()) {
            $messages = [];
            foreach ($form->getErrors(true) as $error) {
                $messages[] = $error->getMessage();
            }
            if ($messages !== []) {
                $this->addFlash('error', implode(' | ', array_unique($messages)));
            }
        }

        return $this->render('front/content/create.html.twig', [
            'form' => $form->createView(),
            'isEdit' => $isEdit,
            'contenu' => $contenu,
        ]);
    }
}
