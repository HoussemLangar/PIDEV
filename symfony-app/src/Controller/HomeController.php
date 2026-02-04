<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class HomeController extends AbstractController
{
    #[Route('/', name: 'app_home')]
    public function index(): Response
    {
        // Exemple de formulaire de contact
        // $contactForm = $this->createForm(ContactType::class);
        
        return $this->render('front/index.html.twig', [
            // 'contact_form' => $contactForm->createView(),
        ]);
    }
}
