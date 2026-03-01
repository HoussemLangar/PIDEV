<?php

namespace App\Controller;

use App\Entity\GoogleFitAccount;
use App\Repository\GoogleFitAccountRepository;
use Doctrine\ORM\EntityManagerInterface;
use KnpU\OAuth2ClientBundle\Client\ClientRegistry;
use League\OAuth2\Client\Provider\Exception\IdentityProviderException;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use App\Service\GoogleFitService;

#[IsGranted('ROLE_USER')]
class GoogleFitController extends AbstractController
{
    #[Route('/google-fit/connect', name: 'google_fit_connect')]
    public function connect(ClientRegistry $clientRegistry): RedirectResponse
    {
        return $clientRegistry
            ->getClient('google_fit')
            ->redirect([
                'https://www.googleapis.com/auth/fitness.activity.read',
                'https://www.googleapis.com/auth/fitness.location.read',
            ], [
                'access_type' => 'offline',
                'prompt' => 'consent',
            ]);
    }

    #[Route('/google-fit/callback', name: 'google_fit_check')]
    public function callback(
        Request $request,
        ClientRegistry $clientRegistry,
        GoogleFitAccountRepository $repository,
        EntityManagerInterface $em,
        GoogleFitService $googleFitService
    ): Response {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User) {
            return $this->redirectToRoute('login');
        }

        $client = $clientRegistry->getClient('google_fit');
        try {
            $accessToken = $client->getAccessToken();
            $googleUser = $client->fetchUserFromToken($accessToken);
        } catch (IdentityProviderException $e) {
            $message = $e->getMessage();
            if (str_contains($message, 'invalid_grant')) {
                $this->addFlash('error', 'Session Google expirée ou code déjà utilisé. Veuillez reconnecter Google Fit.');
            } else {
                $this->addFlash('error', 'Connexion Google Fit échouée: ' . $message);
            }

            return $this->redirectToRoute('app_sante_quotidienne_index');
        }

        $account = $repository->findOneBy(['user' => $user]) ?? new GoogleFitAccount();
        $account->setUser($user);
        $account->setGoogleAccountId((string) $googleUser->getId());
        $account->setAccessToken($accessToken->getToken());
        $account->setRefreshToken($accessToken->getRefreshToken());

        $expires = $accessToken->getExpires();
        if ($expires) {
            $account->setTokenExpiration((new \DateTimeImmutable())->setTimestamp($expires));
        }

        $scopeValue = $accessToken->getValues()['scope'] ?? [];
        if (is_string($scopeValue)) {
            $scopeValue = preg_split('/\s+/', trim($scopeValue)) ?: [];
        }
        $account->setScopes(implode(' ', $scopeValue));

        $repository->save($account);
        $em->flush();

        $syncResult = $googleFitService->syncForUser($user, 7);
        if ($syncResult['ok']) {
            $this->addFlash('success', 'Google Fit connecté avec succès. ' . $syncResult['message']);
        } else {
            $this->addFlash('success', 'Google Fit connecté avec succès.');
            $this->addFlash('error', 'Synchronisation automatique impossible: ' . $syncResult['message']);
        }

        return $this->redirectToRoute('app_sante_quotidienne_index');
    }

    #[Route('/google-fit/disconnect', name: 'google_fit_disconnect', methods: ['POST'])]
    public function disconnect(GoogleFitAccountRepository $repository): RedirectResponse
    {
        $user = $this->getUser();
        if ($user instanceof \App\Entity\User) {
            $account = $repository->findOneBy(['user' => $user]);
            if ($account) {
                $repository->delete($account);
                $this->addFlash('success', 'Google Fit déconnecté.');
            }
        }

        return $this->redirectToRoute('app_sante_quotidienne_index');
    }

    #[Route('/google-fit/sync', name: 'google_fit_sync', methods: ['POST'])]
    public function sync(GoogleFitService $googleFitService, Request $request): RedirectResponse
    {
        $user = $this->getUser();
        if (!$user instanceof \App\Entity\User) {
            return $this->redirectToRoute('login');
        }

        $days = (int) $request->request->get('days', 7);
        $result = $googleFitService->syncForUser($user, $days);
        $this->addFlash($result['ok'] ? 'success' : 'error', $result['message']);
        return $this->redirectToRoute('app_sante_quotidienne_index');
    }
}
