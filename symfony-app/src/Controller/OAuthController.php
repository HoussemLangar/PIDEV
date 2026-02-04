<?php

namespace App\Controller;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Security\Http\Authentication\UserAuthenticatorInterface;
use Symfony\Component\Security\Http\Authenticator\FormLoginAuthenticator;

class OAuthController extends AbstractController
{
    #[Route('/oauth/google/callback', name: 'oauth_google_callback')]
    public function googleCallback(Request $request): Response
    {
        $session = $request->getSession();
        $oauthData = $session->get('_oauth_data');

        // Si pas de données OAuth, rediriger vers login
        if (!$oauthData) {
            $this->addFlash('error', 'Erreur lors de la connexion avec Google.');
            return $this->redirectToRoute('login');
        }

        // Afficher la page de confirmation
        return $this->render('security/oauth_confirm.html.twig', [
            'provider' => 'Google',
            'user_data' => $oauthData,
            'provider_icon' => 'fab fa-google',
            'provider_color' => '#DB4437',
        ]);
    }

    #[Route('/oauth/facebook/callback', name: 'oauth_facebook_callback')]
    public function facebookCallback(Request $request): Response
    {
        $session = $request->getSession();
        $oauthData = $session->get('_oauth_data');

        if (!$oauthData) {
            $this->addFlash('error', 'Erreur lors de la connexion avec Facebook.');
            return $this->redirectToRoute('login');
        }

        return $this->render('security/oauth_confirm.html.twig', [
            'provider' => 'Facebook',
            'user_data' => $oauthData,
            'provider_icon' => 'fab fa-facebook-f',
            'provider_color' => '#4267B2',
        ]);
    }

    #[Route('/oauth/confirm', name: 'oauth_confirm', methods: ['POST'])]
    public function confirmOAuth(
        Request $request,
        EntityManagerInterface $entityManager,
        UserAuthenticatorInterface $userAuthenticator,
        FormLoginAuthenticator $formLoginAuthenticator
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
            $user->setPassword(bin2hex(random_bytes(32)));
            
            // Rôle utilisateur standard
            $user->setRole('ROLE_USER');
            
            // Dates
            $user->setCreatedAt(new \DateTimeImmutable());
            $user->setUpdatedAt(new \DateTimeImmutable());
            
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
        return $userAuthenticator->authenticateUser(
            $user,
            $formLoginAuthenticator,
            $request
        );
    }

    #[Route('/oauth/cancel', name: 'oauth_cancel')]
    public function cancelOAuth(Request $request): Response
    {
        $session = $request->getSession();
        $session->remove('_oauth_data');
        $session->remove('_oauth_pending');
        $session->remove('_oauth_existing_user');
        $session->remove('_oauth_needs_confirmation');
        
        $this->addFlash('info', 'Connexion OAuth annulée.');
        return $this->redirectToRoute('login');
    }
}