<?php

namespace App\Controller;

use App\Entity\User;
use App\Form\ProfileType;
use App\Repository\SuspiciousLoginRepository;
use App\Service\UserAiScoreService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Scheb\TwoFactorBundle\Security\TwoFactor\Provider\Google\GoogleAuthenticatorInterface;


#[Route('/profile', name: 'profile_')]
#[IsGranted('ROLE_USER')]
class ProfileController extends AbstractController
{
    #[Route('', name: 'edit', methods: ['GET', 'POST'])]
    public function edit(
        Request $request,
        EntityManagerInterface $em,
        UserPasswordHasherInterface $passwordHasher,
        UserAiScoreService $userAiScoreService
    ): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException();
        }

        $form = $this->createForm(ProfileType::class, $user);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $avatarFile = $form->get('avatarFile')->getData();
            if ($avatarFile) {
                $mime = $avatarFile->getMimeType();
                $data = @file_get_contents($avatarFile->getPathname());
                if ($data !== false && $mime) {
                    $user->setAvatarMime($mime);
                    $user->setAvatarData($data);
                } else {
                    $this->addFlash('error', 'Upload avatar échoué. Réessayez avec une image valide.');
                }
            }
            if ($form->get('plainPassword')->getData()) {
                $hashedPassword = $passwordHasher->hashPassword($user, $form->get('plainPassword')->getData());
                $user->setPassword($hashedPassword);
            }

            $em->persist($user);
            $em->flush();

            $this->addFlash('success', 'Profil mis à jour avec succès.');
            return $this->redirectToRoute('profile_edit');
        }

        return $this->render('front/profile/edit.html.twig', [
            'form' => $form->createView(),
            'aiScore' => $userAiScoreService->getProfileSummary($user),
            'scoreHistory' => $userAiScoreService->getRecentHistory($user, 10),
        ]);
    }


    #[Route('/delete', name: 'delete', methods: ['POST'])]
    public function delete(Request $request, EntityManagerInterface $em): Response
    {
        /** @var User $user */
        $user = $this->getUser();

        if ($this->isCsrfTokenValid('delete-account', $request->request->get('_token'))) {
            $em->remove($user);
            $em->flush();

            $this->addFlash('success', 'Compte supprimé avec succès.');

            return $this->redirectToRoute('app_home');
        }

        return $this->redirectToRoute('profile_edit');
    }

    #[Route('/mfa', name: 'mfa', methods: ['GET', 'POST'])]
    public function mfa(
        Request $request,
        EntityManagerInterface $em,
        GoogleAuthenticatorInterface $googleAuthenticator,
        UserPasswordHasherInterface $passwordHasher
    ): Response
    {
        /** @var User $user */
        $user = $this->getUser();

        if ($request->isMethod('POST')) {
            // Always require password for both enable and disable
            $currentPassword = (string) $request->request->get('current_password', '');
            if ($currentPassword === '' || !$passwordHasher->isPasswordValid($user, $currentPassword)) {
                $this->addFlash('error', 'Mot de passe du compte incorrect. Action MFA refusée.');
                return $this->redirectToRoute('profile_mfa');
            }

            if ($request->request->get('disable_mfa')) {
                $user->setMfaEnabled(false);
                $user->setGoogleAuthenticatorSecret(null);
                $em->flush();
                $this->addFlash('success', 'MFA désactivé.');
                return $this->redirectToRoute('profile_mfa');
            }

            if (!$user->getGoogleAuthenticatorSecret()) {
                $user->setGoogleAuthenticatorSecret($googleAuthenticator->generateSecret());
            }
            $user->setMfaEnabled(true);
            $em->flush();
            $this->addFlash('success', 'MFA activé.');
            return $this->redirectToRoute('profile_mfa');
        }

        $qrContent = null;
        if ($user->getGoogleAuthenticatorSecret()) {
            $qrContent = $googleAuthenticator->getQRContent($user);
        }

        return $this->render('front/profile/mfa.html.twig', [
            'qrContent' => $qrContent,
        ]);
    }

    #[Route('/notifications', name: 'notifications', methods: ['GET'])]
    public function notifications(SuspiciousLoginRepository $suspiciousLoginRepository): Response
    {
        /** @var User $user */
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('login');
        }

        $notifications = $suspiciousLoginRepository->findForUser($user, 20);

        return $this->render('front/profile/notifications.html.twig', [
            'securityNotifications' => $notifications,
        ]);
    }
}
