<?php

namespace App\Security;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use KnpU\OAuth2ClientBundle\Client\ClientRegistry;
use KnpU\OAuth2ClientBundle\Security\Authenticator\OAuth2Authenticator;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\RouterInterface;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Exception\AuthenticationException;
use Symfony\Component\Security\Http\Authenticator\Passport\Badge\UserBadge;
use Symfony\Component\Security\Http\Authenticator\Passport\Passport;
use Symfony\Component\Security\Http\Authenticator\Passport\SelfValidatingPassport;
use Symfony\Component\Security\Http\EntryPoint\AuthenticationEntryPointInterface;  // ✅ CORRECTION ICI

class FacebookAuthenticator extends OAuth2Authenticator implements AuthenticationEntryPointInterface
{
    public function __construct(
        private ClientRegistry $clientRegistry,
        private EntityManagerInterface $entityManager,
        private RouterInterface $router
    ) {}

    public function supports(Request $request): ?bool
    {
        return $request->attributes->get('_route') === 'connect_facebook_check';
    }

    public function authenticate(Request $request): Passport
    {
        $client = $this->clientRegistry->getClient('facebook');
        $accessToken = $this->fetchAccessToken($client);

        return new SelfValidatingPassport(
            new UserBadge($accessToken->getToken(), function() use ($accessToken, $client) {
                /** @var \League\OAuth2\Client\Provider\FacebookUser $facebookUser */
                $facebookUser = $client->fetchUserFromToken($accessToken);

                $email = $facebookUser->getEmail();
                
                if (!$email) {
                    throw new AuthenticationException('Email non fourni par Facebook');
                }

                // Chercher l'utilisateur existant
                $existingUser = $this->entityManager->getRepository(User::class)
                    ->findOneBy(['email' => $email]);

                if ($existingUser) {
                    return $existingUser;
                }

                // Créer un nouveau utilisateur
                $user = new User();
                $user->setEmail($email);
                
                // Username
                $username = $facebookUser->getName() ?? explode('@', $email)[0];
                $user->setUsername($this->generateUniqueUsername($username));
                
                // Prénom et nom
                $user->setPrenom($facebookUser->getFirstName() ?? 'Utilisateur');
                $user->setNom($facebookUser->getLastName() ?? 'Facebook');
                
                // Mot de passe aléatoire (non utilisé pour OAuth)
                $user->setPassword(bin2hex(random_bytes(32)));
                $user->setRole('ROLE_USER');

                $this->entityManager->persist($user);
                $this->entityManager->flush();

                return $user;
            })
        );
    }

    public function onAuthenticationSuccess(Request $request, TokenInterface $token, string $firewallName): ?Response
    {
        // Rediriger vers la page d'accueil après connexion
        return new RedirectResponse($this->router->generate('app_home'));
    }

    public function onAuthenticationFailure(Request $request, AuthenticationException $exception): ?Response
{
    // Ajouter un message d'erreur dans la session
    $request->getSession()->getFlashBag()->add(
        'error', 
        'Impossible de se connecter avec Facebook. Veuillez réessayer ou utiliser une autre méthode.'
    );
    
    return new RedirectResponse($this->router->generate('login'));
}

    public function start(Request $request, AuthenticationException $authException = null): Response
    {
        return new RedirectResponse(
            $this->router->generate('login'),
            Response::HTTP_TEMPORARY_REDIRECT
        );
    }

    /**
     * Génère un username unique en ajoutant un suffixe si nécessaire
     */
    private function generateUniqueUsername(string $baseUsername): string
    {
        $username = $baseUsername;
        $counter = 1;

        while ($this->entityManager->getRepository(User::class)->findOneBy(['username' => $username])) {
            $username = $baseUsername . $counter;
            $counter++;
        }

        return $username;
    }
}