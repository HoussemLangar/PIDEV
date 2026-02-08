<?php

namespace App\Controller\FrontOffice;

use App\Security\ContentVoter;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/media/contenus', name: 'front_content_media_')]
class ContentMediaController extends AbstractController
{
    #[Route('/{filename}', name: 'show', methods: ['GET'])]
    public function show(string $filename): BinaryFileResponse
    {
        $this->denyAccessUnlessGranted(ContentVoter::VIEW);
        $safeName = basename($filename);
        $filePath = $this->getParameter('kernel.project_dir') . '/var/uploads/contenus/' . $safeName;

        $response = new BinaryFileResponse($filePath);
        $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE, $safeName);

        return $response;
    }
}
