<?php

namespace App\Service;

use App\Entity\GoogleFitAccount;
use App\Entity\SanteQuotidienne;
use App\Repository\GoogleFitAccountRepository;
use App\Repository\SanteQuotidienneRepository;
use Doctrine\ORM\EntityManagerInterface;
use KnpU\OAuth2ClientBundle\Client\ClientRegistry;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class GoogleFitService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly ClientRegistry $clientRegistry,
        private readonly GoogleFitAccountRepository $googleFitAccountRepository,
        private readonly SanteQuotidienneRepository $santeRepository,
        private readonly EntityManagerInterface $em,
    ) {}

    public function syncForUser(\App\Entity\User $user, int $days = 7): array
    {
        $account = $this->googleFitAccountRepository->findOneBy(['user' => $user]);
        if (!$account) {
            return ['ok' => false, 'message' => 'Google Fit non connecté.'];
        }

        $accessToken = $this->getValidAccessToken($account);
        if (!$accessToken) {
            return ['ok' => false, 'message' => 'Token Google Fit invalide.'];
        }

        $end = new \DateTimeImmutable('now');
        $start = (new \DateTimeImmutable('today'))->modify(sprintf('-%d days', max(1, $days - 1)));

        $payload = [
            'aggregateBy' => [
                ['dataTypeName' => 'com.google.step_count.delta'],
                ['dataTypeName' => 'com.google.calories.expended'],
                ['dataTypeName' => 'com.google.active_minutes'],
            ],
            'bucketByTime' => ['durationMillis' => 86400000],
            'startTimeMillis' => $start->getTimestamp() * 1000,
            'endTimeMillis' => $end->getTimestamp() * 1000,
        ];

        $response = $this->httpClient->request('POST', 'https://www.googleapis.com/fitness/v1/users/me/dataset:aggregate', [
            'headers' => [
                'Authorization' => 'Bearer ' . $accessToken,
                'Content-Type' => 'application/json',
            ],
            'json' => $payload,
        ]);

        $data = $response->toArray(false);
        if (!isset($data['bucket'])) {
            return ['ok' => false, 'message' => 'Réponse Google Fit invalide.'];
        }

        $synced = 0;
        $skipped = 0;
        foreach ($data['bucket'] as $bucket) {
            $dayStart = isset($bucket['startTimeMillis']) ? (int) $bucket['startTimeMillis'] : null;
            if (!$dayStart) {
                continue;
            }
            $date = (new \DateTimeImmutable())->setTimestamp((int) ($dayStart / 1000));
            $entry = $this->santeRepository->findOneByUserAndDate($user, $date);
            if (!$entry) {
                $skipped++;
                continue;
            }

            $steps = 0;
            $calories = 0.0;
            $activeMinutes = 0;

            foreach ($bucket['dataset'] as $dataset) {
                $datasetSource = mb_strtolower((string) ($dataset['dataSourceId'] ?? ''));
                foreach ($dataset['point'] ?? [] as $point) {
                    $value = $point['value'][0] ?? null;
                    $dataType = mb_strtolower((string) ($point['dataTypeName'] ?? ''));
                    if ($dataType === '') {
                        if (str_contains($datasetSource, 'step_count.delta')) {
                            $dataType = 'com.google.step_count.delta';
                        } elseif (str_contains($datasetSource, 'calories.expended')) {
                            $dataType = 'com.google.calories.expended';
                        } elseif (str_contains($datasetSource, 'active_minutes')) {
                            $dataType = 'com.google.active_minutes';
                        }
                    }
                    if ($value === null) {
                        continue;
                    }
                    if ($dataType === 'com.google.step_count.delta') {
                        $steps += (int) ($value['intVal'] ?? 0);
                    } elseif ($dataType === 'com.google.calories.expended') {
                        $calories += (float) ($value['fpVal'] ?? 0);
                    } elseif ($dataType === 'com.google.active_minutes') {
                        $activeMinutes += (int) round((float) (($value['intVal'] ?? $value['fpVal']) ?? 0));
                    }
                }
            }

            $source = $entry->getSourceDonnees();
            if ($source === 'google_fit' || $source === 'manuel') {
                $entry->setPas($steps);
                $entry->setCalories(round($calories, 2));
                $entry->setDureeActiviteMinutes($activeMinutes);
                if ($source === 'manuel' && ($steps || $calories || $activeMinutes)) {
                    $entry->setSourceDonnees('google_fit');
                }
            }

            $this->em->persist($entry);
            $synced++;
        }

        $account->setLastSyncAt(new \DateTimeImmutable());
        $this->em->persist($account);
        $this->em->flush();

        $message = sprintf('Synchronisation terminée (%d jours).', $synced);
        if ($skipped > 0) {
            $message .= sprintf(' %d jours ignorés (aucune entrée de journal).', $skipped);
        }
        return ['ok' => true, 'message' => $message];
    }

    private function getValidAccessToken(GoogleFitAccount $account): ?string
    {
        $expires = $account->getTokenExpiration();
        $now = new \DateTimeImmutable();
        if ($expires && $expires <= $now->modify('+2 minutes')) {
            $refreshToken = $account->getRefreshToken();
            if (!$refreshToken) {
                return null;
            }
            $provider = $this->clientRegistry->getClient('google_fit')->getOAuth2Provider();
            $newToken = $provider->getAccessToken('refresh_token', [
                'refresh_token' => $refreshToken,
            ]);
            $account->setAccessToken($newToken->getToken());
            $account->setRefreshToken($newToken->getRefreshToken() ?: $refreshToken);
            $expires = $newToken->getExpires();
            if ($expires) {
                $account->setTokenExpiration((new \DateTimeImmutable())->setTimestamp($expires));
            }
            $this->googleFitAccountRepository->save($account);
        }

        return $account->getAccessToken();
    }
}
