<?php

namespace App\Controller;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use KnpU\OAuth2ClientBundle\Client\ClientRegistry;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Annotation\Route;

class OAuthController extends AbstractController
{
    /**
     * Lien pour rediriger vers Google OAuth
     */
    #[Route('/connect/google', name: 'connect_google_start')]
    public function connectGoogle(ClientRegistry $clientRegistry): RedirectResponse
    {
        return $clientRegistry
            ->getClient('google')
            ->redirect([
                'email', 'profile'
            ], []);
    }

    /**
     * Google redirige ici après l'authentification
     */
    #[Route('/connect/google/check', name: 'connect_google_check')]
    public function connectGoogleCheck(Request $request, ClientRegistry $clientRegistry)
    {
        // Cette méthode ne sera jamais exécutée
        // L'authenticator intercepte la requête avant
    }

    /**
     * Lien pour rediriger vers Facebook OAuth
     */
    #[Route('/connect/facebook', name: 'connect_facebook_start')]
    public function connectFacebook(ClientRegistry $clientRegistry): RedirectResponse
    {
        return $clientRegistry
            ->getClient('facebook')
            ->redirect([
                'public_profile', 'email'
            ], []);
    }

    #[Route('/oauth/confirm', name: 'oauth_confirm', methods: ['POST'])]
    public function confirmOAuth(
        Request $request,
        EntityManagerInterface $entityManager,
        UserPasswordHasherInterface $passwordHasher,
        Security $security,
    ): Response {
        $session = $request->getSession();
        $oauthData = $session->get('_oauth_data');

        if (!$oauthData) {
            $this->addFlash('error', 'Session expirée. Veuillez réessayer.');
            return $this->redirectToRoute('login');
        }

        // Vérifier le token CSRF
        $submittedToken = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('oauth_confirm', $submittedToken)) {
            $this->addFlash('error', 'Token CSRF invalide.');
            return $this->redirectToRoute('login');
        }

        $action = $request->request->get('action');

        // Si l'utilisateur annule
        if ($action === 'cancel') {
            $session->remove('_oauth_data');
            $session->remove('_oauth_pending');
            $session->remove('_oauth_existing_user');
            $this->addFlash('info', 'Connexion annulée.');
            return $this->redirectToRoute('login');
        }

        // L'utilisateur confirme - créer ou récupérer l'utilisateur
        $email = $oauthData['email'];
        $userRepository = $entityManager->getRepository(User::class);
        $user = $userRepository->findOneBy(['email' => $email]);

        // Créer l'utilisateur s'il n'existe pas
        if (!$user) {
            $user = new User();
            $user->setEmail($email);
            $user->setUsername($oauthData['username'] ?? explode('@', $email)[0]);
            $user->setNom($oauthData['nom'] ?? '');
            $user->setPrenom($oauthData['prenom'] ?? '');
            
            // Mot de passe aléatoire (connexion via OAuth uniquement)
            $randomPassword = bin2hex(random_bytes(32));
            $user->setPassword($passwordHasher->hashPassword($user, $randomPassword));
            
            // Rôle utilisateur standard
            $user->setRole('ROLE_USER');
            
            // Dates
            $user->forceCreatedAt(new \DateTimeImmutable());
            $user->forceUpdatedAt(new \DateTimeImmutable());
            
            // Sauvegarder
            $entityManager->persist($user);
            $entityManager->flush();

            $this->addFlash('success', 'Bienvenue ! Votre compte a été créé avec succès.');
        } else {
            $this->addFlash('success', 'Connexion réussie ! Bienvenue.');
        }

        // Nettoyer la session
        $session->remove('_oauth_data');
        $session->remove('_oauth_pending');
        $session->remove('_oauth_existing_user');
        $session->remove('_oauth_needs_confirmation');

        // Authentifier l'utilisateur manuellement
        $response = $security->login($user, 'main');
        return $response ?? $this->redirectToRoute('app_home');
    }

    /**
     * Facebook redirige ici après l'authentification
     */
    #[Route('/connect/facebook/check', name: 'connect_facebook_check')]
    public function connectFacebookCheck(Request $request, ClientRegistry $clientRegistry)
    {
        // Cette méthode ne sera jamais exécutée
        // L'authenticator intercepte la requête avant
    }
}
