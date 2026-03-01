<?php

namespace App\Controller;

use App\Entity\DocumentAccess;
use App\Entity\SharedDocument;
use App\Entity\User;
use App\Form\DocumentAccessType;
use App\Form\SharedDocumentType;
use App\Repository\DocumentAccessRepository;
use App\Repository\SharedDocumentRepository;
use App\Service\DocumentStorageService;
use Doctrine\ORM\EntityManagerInterface;
use Psr\Log\LoggerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Mime\Email;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/documents', name: 'app_document_', requirements: ['_format' => 'html'])]
#[IsGranted('ROLE_USER')]
class DocumentController extends AbstractController
{
    public function __construct(
        private SharedDocumentRepository $documentRepository,
        private DocumentAccessRepository $accessRepository,
        private DocumentStorageService $storageService,
        private EntityManagerInterface $em,
        private LoggerInterface $logger,
        private MailerInterface $mailer,
    ) {
    }

    /**
     * List all user's documents
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $ownDocuments = $this->documentRepository->findByOwner($user);
        $sharedDocuments = $this->documentRepository->findSharedWithUser($user);
        $totalStorage = $this->documentRepository->getTotalStorageByUser($user);

        return $this->render('document/index.html.twig', [
            'ownDocuments' => $ownDocuments,
            'sharedDocuments' => $sharedDocuments,
            'totalStorage' => DocumentStorageService::formatFileSize($totalStorage),
        ]);
    }

    /**
     * Upload a new document
     */
    #[Route('/upload', name: 'upload', methods: ['GET', 'POST'])]
    public function upload(Request $request): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);
        $isPatient = $this->isPatientRole($user);

        $document = new SharedDocument();
        $form = $this->createForm(SharedDocumentType::class, $document, [
            'is_patient' => $isPatient,
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted()) {
            $this->logger->info('Document upload form submitted');
            
            if (!$form->isValid()) {
                // Form validation failed, log and re-render with errors
                $errors = $form->getErrors(true);
                $errorMessage = '';
                foreach ($errors as $error) {
                    $errorMessage .= $error->getMessage() . ' | ';
                }
                $this->logger->error('Form validation failed', ['errors' => $errorMessage]);
                $this->addFlash('error', 'Erreur de validation: ' . ($errorMessage ?: 'Erreur inconnue'));
                return $this->render('document/upload.html.twig', [
                    'form' => $form,
                ]);
            }

            $file = $form->get('file')->getData();
            $this->logger->info('File data retrieved from form', ['file' => $file ? 'present' : 'null']);

            if ($file) {
                try {
                    if ($isPatient && !in_array($document->getDocumentType(), ['analysis', 'report', 'lab_results', 'imaging'], true)) {
                        $this->addFlash('error', 'Les patients peuvent uploader uniquement des analyses, rapports médicaux, résultats de laboratoire ou imagerie médicale.');
                        return $this->render('document/upload.html.twig', [
                            'form' => $form,
                        ]);
                    }

                    $validationErrors = $this->storageService->validateFile($file);
                    if (!empty($validationErrors)) {
                        $message = implode(' | ', $validationErrors);
                        $this->logger->warning('File validation failed', ['errors' => $message]);
                        $this->addFlash('error', 'Erreur de validation: ' . $message);
                        return $this->render('document/upload.html.twig', [
                            'form' => $form,
                        ]);
                    }

                    // Store file content in database
                    $this->logger->info('Starting file storage');
                    $fileInfo = $this->storageService->storeDocument($file, $user);
                    $this->logger->info('File stored successfully', [
                        'originalName' => $fileInfo['originalName'],
                        'mimeType' => $fileInfo['mimeType'],
                        'size' => $fileInfo['size'],
                    ]);

                    // Create document entity
                    $document->setOwner($user);
                    $document->setFileName($fileInfo['originalName']);
                    $document->setFileContent($fileInfo['fileContent']);
                    $document->setMimeType($fileInfo['mimeType']);
                    $document->setFileSize($fileInfo['size']);
                    // Keep filePath as empty - it's no longer used but kept for compatibility
                    $document->setFilePath('');
                    
                    $this->logger->info('Document entity created', [
                        'fileName' => $fileInfo['originalName'],
                        'mimeType' => $fileInfo['mimeType'],
                        'fileSize' => $fileInfo['size'],
                    ]);

                    $this->em->persist($document);
                    $this->logger->info('Document persisted');
                    
                    $this->em->flush();
                    $this->logger->info('Document flushed to database', ['id' => $document->getId()]);

                    $this->addFlash('success', 'Document uploadé avec succès!');
                    return $this->redirectToRoute('app_document_show', ['id' => $document->getId()]);
                } catch (\Exception $e) {
                    $this->logger->error('Upload failed', ['exception' => $e->getMessage()]);
                    $this->addFlash('error', 'Une erreur est survenue lors de l\'upload: ' . $e->getMessage());
                }
            } else {
                $this->logger->warning('No file provided in form');
            }
        }

        return $this->render('document/upload.html.twig', [
            'form' => $form,
        ]);
    }

    /**
     * View document details
     */
    #[Route('/{id}', name: 'show', methods: ['GET'])]
    public function show(SharedDocument $document): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Check access
        if (!$document->hasUserAccess($user)) {
            throw $this->createAccessDeniedException('Vous n\'avez pas accès à ce document.');
        }

        $isOwner = $document->getOwner() === $user;
        $accesses = $isOwner ? $this->accessRepository->findAccessHistory($document) : [];
        $stats = $isOwner ? $this->accessRepository->getAccessStats($document) : [];

        return $this->render('document/show.html.twig', [
            'document' => $document,
            'isOwner' => $isOwner,
            'accesses' => $accesses,
            'stats' => $stats,
        ]);
    }

    /**
     * Download a document
     */
    #[Route('/{id}/download', name: 'download', methods: ['GET'])]
    public function download(SharedDocument $document): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Check access permission
        if (!$document->hasUserAccess($user)) {
            throw $this->createAccessDeniedException('Vous n\'avez pas accès à ce document.');
        }

        // Check if document still has download permission or is owner
        if ($user !== $document->getOwner()) {
            $access = $this->accessRepository->findOneBy([
                'document' => $document,
                'sharedWith' => $user,
            ]);

            if ($access && !$access->canAccess()) {
                throw $this->createAccessDeniedException('Votre accès a expiré.');
            }

            if ($access && $access->getPermission() !== 'download') {
                throw $this->createAccessDeniedException('Vous n\'avez pas la permission de télécharger ce document.');
            }

            // Track access
            if ($access) {
                $access->incrementAccessCount();
                $this->em->flush();
            }
        }

        // Check file exists in database
        if (!$this->storageService->documentExists($document)) {
            throw $this->createNotFoundException('Le fichier n\'existe plus.');
        }

        // Get file content from database
        $fileContent = $this->storageService->getDocumentContent($document);
        if (is_resource($fileContent)) {
            $fileContent = stream_get_contents($fileContent);
        }
        if (!is_string($fileContent)) {
            throw $this->createNotFoundException('Le fichier est indisponible.');
        }

        // Create response with file content
        $response = new Response($fileContent);
        $response->headers->set('Content-Type', $document->getMimeType());
        $response->headers->set('Content-Disposition', sprintf('attachment; filename="%s"', $document->getFileName()));
        $response->headers->set('Content-Length', (string) strlen($fileContent));

        return $response;
    }

    /**
     * Share a document with a user
     */
    #[Route('/{id}/share', name: 'share', methods: ['GET', 'POST'])]
    public function share(Request $request, SharedDocument $document): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Verify ownership
        if ($document->getOwner() !== $user) {
            throw $this->createAccessDeniedException('Vous ne pouvez partager que vos propres documents.');
        }

        $documentAccess = new DocumentAccess($document, $user);
        $targetRoles = $this->isPatientRole($user)
            ? ['ROLE_MEDECIN', 'ROLE_PHARMACIEN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE']
            : ['ROLE_PATIENT'];

        $form = $this->createForm(DocumentAccessType::class, $documentAccess, [
            'target_roles' => $targetRoles,
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $recipient = $documentAccess->getSharedWith();
            if ($this->isPatientRole($user) && !$this->isProfessionalRole($recipient)) {
                throw $this->createAccessDeniedException('Un patient peut partager uniquement avec un professionnel de santé.');
            }

            // Check if already shared with this user
            $existing = $this->accessRepository->findOneBy([
                'document' => $document,
                'sharedWith' => $recipient,
            ]);

            if ($existing) {
                // Update existing access
                $existing->setExpiresAt($documentAccess->getExpiresAt());
                $existing->setPermission($documentAccess->getPermission());
                $existing->setActive(true);
            } else {
                // Create new access
                $this->em->persist($documentAccess);
            }

            $this->em->flush();

            if ($this->isProfessionalRole($recipient)) {
                $email = (new Email())
                    ->from('noreply@santea.local')
                    ->to($recipient->getEmail())
                    ->subject('Nouveau document patient partagé')
                    ->text(sprintf(
                        "%s %s a partagé un document (%s) avec vous.",
                        $user->getPrenom(),
                        $user->getNom(),
                        $document->getFileName()
                    ));
                $this->mailer->send($email);
            }

            $this->addFlash('success', 'Document partagé avec succès!');

            return $this->redirectToRoute('app_document_show', ['id' => $document->getId()]);
        }

        $activeAccesses = $this->accessRepository->findActiveAccesses($document);

        return $this->render('document/share.html.twig', [
            'document' => $document,
            'form' => $form,
            'activeAccesses' => $activeAccesses,
        ]);
    }

    /**
     * Revoke access to a document
     */
    #[Route('/{id}/access/{accessId}/revoke', name: 'revoke_access', methods: ['POST'])]
    public function revokeAccess(SharedDocument $document, DocumentAccess $access): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Verify ownership
        if ($document->getOwner() !== $user) {
            throw $this->createAccessDeniedException();
        }

        // Verify access belongs to this document
        if ($access->getDocument() !== $document) {
            throw $this->createAccessDeniedException();
        }

        $access->setActive(false);
        $this->em->flush();

        $this->addFlash('success', 'Accès révoqué avec succès.');

        return $this->redirectToRoute('app_document_show', ['id' => $document->getId()]);
    }

    /**
     * Delete a document
     */
    #[Route('/{id}/delete', name: 'delete', methods: ['POST'])]
    public function delete(SharedDocument $document): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Verify ownership
        if ($document->getOwner() !== $user) {
            throw $this->createAccessDeniedException('Vous ne pouvez supprimer que vos propres documents.');
        }

        $fileName = $document->getFileName();

        // Delete file
        $this->storageService->deleteDocument($document);

        // Delete from database
        $this->em->remove($document);
        $this->em->flush();

        $this->addFlash('success', "Le document \"$fileName\" a été supprimé.");

        return $this->redirectToRoute('app_document_index');
    }

    /**
     * API: Get access history
     */
    #[Route('/{id}/history', name: 'history', methods: ['GET'])]
    public function getAccessHistory(SharedDocument $document): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if ($document->getOwner() !== $user) {
            return $this->json(['error' => 'Non autorisé'], 403);
        }

        $accesses = $this->accessRepository->findAccessHistory($document);

        $data = array_map(function (DocumentAccess $access) {
            return [
                'id' => $access->getId(),
                'sharedWith' => $access->getSharedWith()->getUsername(),
                'sharedAt' => $access->getSharedAt()->format('Y-m-d H:i:s'),
                'accessCount' => $access->getAccessCount(),
                'lastAccess' => $access->getAccessedAt()?->format('Y-m-d H:i:s'),
                'expiresAt' => $access->getExpiresAt()?->format('Y-m-d H:i:s'),
                'permission' => $access->getPermission(),
                'isActive' => $access->isActive(),
            ];
        }, $accesses);

        return $this->json(['history' => $data]);
    }

    /**
     * Search documents
     */
    #[Route('/search', name: 'search', methods: ['POST'])]
    public function search(Request $request): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $query = $request->request->get('q', '');
        $type = $request->request->get('type', '');

        if (empty($query) || strlen($query) < 2) {
            return $this->json(['results' => []]);
        }

        // Build query
        $qb = $this->documentRepository->createQueryBuilder('d')
            ->where('d.owner = :owner')
            ->andWhere('d.fileName LIKE :query OR d.description LIKE :query')
            ->setParameter('owner', $user)
            ->setParameter('query', '%' . $query . '%');

        if (!empty($type)) {
            $qb->andWhere('d.documentType = :type')
                ->setParameter('type', $type);
        }

        $documents = $qb->orderBy('d.uploadedAt', 'DESC')
            ->setMaxResults(20)
            ->getQuery()
            ->getResult();

        $results = array_map(function (SharedDocument $doc) {
            return [
                'id' => $doc->getId(),
                'fileName' => $doc->getFileName(),
                'type' => $doc->getDocumentType(),
                'size' => DocumentStorageService::formatFileSize($doc->getFileSize()),
                'uploadedAt' => $doc->getUploadedAt()->format('Y-m-d H:i:s'),
            ];
        }, $documents);

        return $this->json(['results' => $results]);
    }

    private function isPatientRole(User $user): bool
    {
        return ($user->getSubscriptionType() ?: $user->getRole()) === 'ROLE_PATIENT';
    }

    private function isProfessionalRole(User $user): bool
    {
        return in_array($user->getSubscriptionType() ?: $user->getRole(), ['ROLE_MEDECIN', 'ROLE_PHARMACIEN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE'], true);
    }
}
