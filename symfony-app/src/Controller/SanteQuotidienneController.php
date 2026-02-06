<?php

namespace App\Controller;

use App\Entity\SanteQuotidienne;
use App\Form\SanteQuotidienneType;
use App\Repository\SanteQuotidienneRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/sante-quotidienne')]
#[IsGranted('IS_AUTHENTICATED_FULLY')] // obligatoire : seul un utilisateur connecté peut accéder
class SanteQuotidienneController extends AbstractController
{
    #[Route('', name: 'app_sante_quotidienne_index', methods: ['GET'])]
    public function index(SanteQuotidienneRepository $repository): Response
    {
        // On récupère uniquement les entrées de l'utilisateur connecté
        $santes = $repository->findBy(
            ['user' => $this->getUser()],
            ['date' => 'DESC']
        );

        return $this->render('sante_quotidienne/index.html.twig', [
            'santes' => $santes,
        ]);
    }

    #[Route('/new', name: 'app_sante_quotidienne_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $em): Response
    {
        $sante = new SanteQuotidienne();
        $sante->setUser($this->getUser());           // ← très important
        $sante->setDate(new \DateTime());             // date du jour par défaut

        $form = $this->createForm(SanteQuotidienneType::class, $sante);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $em->persist($sante);
            $em->flush();

            $this->addFlash('success', 'Données quotidiennes enregistrées avec succès !');

            return $this->redirectToRoute('app_sante_quotidienne_index');
        }

        return $this->render('sante_quotidienne/new.html.twig', [
            'form' => $form,
        ]);
    }

    #[Route('/{id}', name: 'app_sante_quotidienne_show', methods: ['GET'])]
    #[IsGranted('SANTE_VIEW', subject: 'sante')]  // optionnel : sécuriser par voter
    public function show(SanteQuotidienne $sante): Response
    {
        // Vérification supplémentaire que c'est bien l'utilisateur connecté
        if ($sante->getUser() !== $this->getUser()) {
            throw $this->createAccessDeniedException();
        }

        return $this->render('sante_quotidienne/show.html.twig', [
            'sante' => $sante,
        ]);
    }

    #[Route('/{id}/edit', name: 'app_sante_quotidienne_edit', methods: ['GET', 'POST'])]
    #[IsGranted('SANTE_EDIT', subject: 'sante')]
    public function edit(Request $request, SanteQuotidienne $sante, EntityManagerInterface $em): Response
    {
        // Sécurité : on ne peut éditer que ses propres données
        if ($sante->getUser() !== $this->getUser()) {
            throw $this->createAccessDeniedException();
        }

        $form = $this->createForm(SanteQuotidienneType::class, $sante);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $em->flush();

            $this->addFlash('success', 'Données modifiées avec succès.');

            return $this->redirectToRoute('app_sante_quotidienne_index');
        }

        return $this->render('sante_quotidienne/edit.html.twig', [
            'sante' => $sante,
            'form' => $form,
        ]);
    }

    #[Route('/{id}', name: 'app_sante_quotidienne_delete', methods: ['POST'])]
    #[IsGranted('SANTE_DELETE', subject: 'sante')]
    public function delete(Request $request, SanteQuotidienne $sante, EntityManagerInterface $em): Response
    {
        if ($sante->getUser() !== $this->getUser()) {
            throw $this->createAccessDeniedException();
        }

        if ($this->isCsrfTokenValid('delete' . $sante->getId(), $request->request->get('_token'))) {
            $em->remove($sante);
            $em->flush();

            $this->addFlash('success', 'Entrée supprimée.');
        }

        return $this->redirectToRoute('app_sante_quotidienne_index');
    }
}