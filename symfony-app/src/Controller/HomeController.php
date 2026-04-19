<?php

namespace App\Controller;

use App\Repository\ContenuRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class HomeController extends AbstractController
{
    public function __construct(
        private ContenuRepository $contenuRepository
    ) {}

    #[Route('/', name: 'app_home')]
    public function index(): Response
    {
        // Get latest validated/published forum posts
        $forumPosts = $this->contenuRepository->findBy(
            ['statut' => 'publie'],
            ['datePublication' => 'DESC', 'createdAt' => 'DESC'],
            6
        );
        
        return $this->render('front/index.html.twig', [
            'forumPosts' => $forumPosts,
        ]);
    }
    #[Route('/settings', name: 'app_settings')]
    public function settings(): Response
    {
        return $this->render('user/settings.html.twig');
    }

}
