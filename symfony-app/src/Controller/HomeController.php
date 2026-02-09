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
        // Get latest validated/published forum posts with their comments
        $forumPosts = $this->contenuRepository->createValidatedQueryBuilder()
            ->leftJoin('c.commentaires', 'com')
            ->addSelect('com')
            ->leftJoin('com.user', 'comUser')
            ->addSelect('comUser')
            ->orderBy('c.datePublication', 'DESC')
            ->addOrderBy('c.createdAt', 'DESC')
            ->setMaxResults(6)
            ->getQuery()
            ->getResult();
        
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
