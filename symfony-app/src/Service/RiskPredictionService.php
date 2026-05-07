<?php

namespace App\Service;

use App\Entity\HealthRiskPrediction;
use App\Entity\User;
use App\Repository\HealthRiskPredictionRepository;
use App\Repository\SanteQuotidienneRepository;
use App\Repository\SymptomeQuotidienRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class RiskPredictionService
{
    public function __construct(
        private readonly SanteQuotidienneRepository $santeRepository,
        private readonly SymptomeQuotidienRepository $symptomeRepository,
        private readonly HealthRiskPredictionRepository $predictionRepository,
        private readonly EntityManagerInterface $em,
        private readonly HttpClientInterface $httpClient,
        private readonly string $riskAiApiUrl = '',
        private readonly string $riskAiApiToken = '',
        private readonly string $riskAiModel = 'MoritzLaurer/mDeBERTa-v3-base-mnli-xnli',
        private readonly float $riskAiWeight = 0.35,
        private readonly float $riskAiTimeout = 8.0
    ) {}

    public function recalculateForUser(User $user): HealthRiskPrediction
    {
        $today = new \DateTimeImmutable('today');
        $prediction = $this->predictionRepository->findForUserAndDate($user, $today) ?? (new HealthRiskPrediction())
            ->setUser($user)
            ->setPredictionDate($today);

        $santeFeatures = $this->santeRepository->getRiskFeatureSnapshot($user);
        $santeFeatures['age'] = $user->getAge() ?? 0;
        $santeFeatures = $this->sanitizeSanteFeatures($santeFeatures);
        $symptomeFeatures = $user->getPatient() !== null
            ? $this->symptomeRepository->getRiskFeatureSnapshot($user->getPatient(), 7)
            : [
                'totalSymptoms' => 0,
                'avgIntensity' => 0.0,
                'feverCount' => 0,
                'coughCount' => 0,
                'fatigueCount' => 0,
                'moodCount' => 0,
                'feverCoughCoOccurrenceDays' => 0,
            ];

        $htn = $this->computeHtnRisk($santeFeatures, $symptomeFeatures);
        $diabetes = $this->computeDiabetesRisk($santeFeatures, $symptomeFeatures);
        $depression = $this->computeDepressionRisk($santeFeatures, $symptomeFeatures);
        $nutrition = $this->computeNutritionBalanceRisk($santeFeatures, $symptomeFeatures);
        $aiScores = $this->predictWithAi($santeFeatures, $symptomeFeatures);
        if ($aiScores !== null) {
            $blend = max(0.0, min(0.8, $this->riskAiWeight));
            $htn['score'] = $this->blendScores($htn['score'], (float) ($aiScores['htn'] ?? $htn['score']), $blend);
            $diabetes['score'] = $this->blendScores($diabetes['score'], (float) ($aiScores['diabetes'] ?? $diabetes['score']), $blend);
            $depression['score'] = $this->blendScores($depression['score'], (float) ($aiScores['depression'] ?? $depression['score']), $blend);
            $nutrition['score'] = $this->blendScores($nutrition['score'], (float) ($aiScores['nutrition'] ?? $nutrition['score']), $blend);
            $htn['reasons'][] = 'Ajustement IA des signaux de risque.';
            $diabetes['reasons'][] = 'Ajustement IA des signaux métaboliques.';
            $depression['reasons'][] = 'Ajustement IA du profil émotionnel.';
            $nutrition['reasons'][] = 'Ajustement IA des habitudes de vie.';
        }

        $prediction
            ->setRiskHtn($htn['score'])
            ->setRiskDiabetes($diabetes['score'])
            ->setRiskDepression($depression['score'])
            ->setRiskRespiratory($nutrition['score'])
            ->setLevelHtn($this->levelFromScore($htn['score']))
            ->setLevelDiabetes($this->levelFromScore($diabetes['score']))
            ->setLevelDepression($this->levelFromScore($depression['score']))
            ->setLevelRespiratory($this->levelFromScore($nutrition['score']))
            ->setExplanationsJson([
                'htn' => $htn['reasons'],
                'htn_details' => $htn['details'],
                'diabetes' => $diabetes['reasons'],
                'diabetes_details' => $diabetes['details'],
                'depression' => $depression['reasons'],
                'depression_details' => $depression['details'],
                'nutrition' => $nutrition['reasons'],
                'nutrition_details' => $nutrition['details'],
                // Compatibilité avec d'anciens templates/JSON.
                'respiratory' => $nutrition['reasons'],
                'respiratory_details' => $nutrition['details'],
            ])
            ->setFeatureSnapshot([
                'sante' => $santeFeatures,
                'symptomes' => $symptomeFeatures,
            ])
            ->forceUpdatedAt(new \DateTimeImmutable());

        $this->em->persist($prediction);
        $this->em->flush();

        return $prediction;
    }

    public function getOrRecalculateForToday(User $user): HealthRiskPrediction
    {
        $today = new \DateTimeImmutable('today');
        $existing = $this->predictionRepository->findForUserAndDate($user, $today);
        if ($existing !== null) {
            return $existing;
        }

        return $this->recalculateForUser($user);
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>,details:array<int,string>}
     */
    private function computeHtnRisk(array $s, array $sym): array
    {
        $score = 0.0;
        $reasons = [];
        $details = [];

        $avgTension = (float) ($s['avgTension7'] ?? 0.0);
        $highTensionDays = (int) ($s['highTensionDays7'] ?? 0);
        $latestTension = (float) ($s['latestTension7'] ?? 0.0);
        $maxTension = (float) ($s['maxTension7'] ?? 0.0);
        $imc = (float) ($s['latestImc'] ?? 0.0);
        $age = (int) ($s['age'] ?? 0);

        // Barème inspiré ACC/AHA (version simplifiée à partir des valeurs disponibles).
        $bpReference = max($avgTension, $latestTension);
        $bpPoints = match (true) {
            $bpReference >= 14.0 => 65.0, // HTA stade 2 (>= 140 mmHg systolique approx)
            $bpReference >= 13.0 => 45.0, // HTA stade 1
            $bpReference >= 12.0 => 20.0, // tension élevée
            default => 0.0,
        };
        $daysPoints = $this->norm((float) $highTensionDays, 0.0, 7.0) * 15.0;
        $peakPoints = $this->norm($maxTension, 14.0, 20.0) * 10.0;
        $imcPoints = $imc > 0.0 ? $this->norm($imc, 30.0, 40.0) * 7.0 : 0.0;
        $agePoints = $age >= 55 ? 3.0 : 0.0;

        $score += $bpPoints + $daysPoints + $peakPoints + $imcPoints + $agePoints;
        $details[] = sprintf('Catégorie tension (ACC/AHA simplifiée): +%.1f/65', $bpPoints);
        $details[] = sprintf('Jours tension haute: +%.1f/15', $daysPoints);
        $details[] = sprintf('Pic tension 7j: +%.1f/10', $peakPoints);
        if ($imc > 0.0) {
            $details[] = sprintf('IMC (impact HTA): +%.1f/7', $imcPoints);
        }
        if ($age > 0) {
            $details[] = sprintf('Age (>=55 ans): +%.1f/3', $agePoints);
        }

        if ($highTensionDays >= 3) {
            $reasons[] = 'Tension élevée sur plusieurs jours.';
        }
        if ($avgTension >= 13.5) {
            $reasons[] = 'Moyenne tension au-dessus du seuil.';
        }
        if ($latestTension >= 16.0) {
            $reasons[] = 'Dernière tension très élevée.';
        }
        if ($imc >= 30.0) {
            $reasons[] = 'IMC élevé.';
        }
        if (($sym['avgIntensity'] ?? 0.0) >= 7.0 && ($sym['totalSymptoms'] ?? 0) >= 2) {
            $reasons[] = 'Symptômes intenses récents.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons, 'details' => $details];
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>,details:array<int,string>}
     */
    private function computeDiabetesRisk(array $s, array $sym): array
    {
        $score = 0.0;
        $reasons = [];
        $details = [];

        $imc = (float) ($s['latestImc'] ?? 0.0);
        $weightChange = (float) ($s['weightChange30'] ?? 0.0);
        $activityMinutes = (int) ($s['activityMinutes7'] ?? 0);
        $avgTension = (float) ($s['avgTension7'] ?? 0.0);
        $highTensionDays = (int) ($s['highTensionDays7'] ?? 0);
        $age = (int) ($s['age'] ?? 0);
        $fatigueCount = (int) ($sym['fatigueCount'] ?? 0);

        // Score inspiré FINDRISC (simplifié selon variables disponibles).
        $agePoints = match (true) {
            $age >= 65 => 15.0,
            $age >= 55 => 13.0,
            $age >= 45 => 8.0,
            $age > 0 => 0.0,
            default => 0.0,
        };
        $imcPoints = match (true) {
            $imc >= 30.0 => 20.0,
            $imc >= 25.0 => 10.0,
            $imc > 0.0 => 0.0,
            default => 0.0,
        };
        $activityPoints = match (true) {
            $activityMinutes < 75 => 12.0,
            $activityMinutes < 150 => 8.0,
            default => 0.0,
        };
        $htnPoints = ($avgTension >= 13.0 || $highTensionDays >= 3) ? 10.0 : 0.0;
        $weightPoints = match (true) {
            $weightChange >= 5.0 => 10.0,
            $weightChange >= 2.0 => 6.0,
            default => 0.0,
        };
        $fatiguePoints = match (true) {
            $fatigueCount >= 3 => 5.0,
            $fatigueCount >= 1 => 2.0,
            default => 0.0,
        };

        $raw = $agePoints + $imcPoints + $activityPoints + $htnPoints + $weightPoints + $fatiguePoints;
        // Normalisation sur 72 points max.
        $score = ($raw / 72.0) * 100.0;

        if ($age > 0) {
            $details[] = sprintf('Age (FINDRISC): +%.1f/15', $agePoints);
        }
        if ($imc > 0.0) {
            $details[] = sprintf('IMC (FINDRISC): +%.1f/20', $imcPoints);
        }
        $details[] = sprintf('Activité (<150 min/semaine): +%.1f/12', $activityPoints);
        $details[] = sprintf('Signal tension élevée: +%.1f/10', $htnPoints);
        $details[] = sprintf('Prise de poids récente: +%.1f/10', $weightPoints);
        $details[] = sprintf('Fatigue (non spécifique): +%.1f/5', $fatiguePoints);

        if ($imc >= 30.0) {
            $reasons[] = 'IMC dans la zone obésité.';
        } elseif ($imc >= 25.0) {
            $reasons[] = 'IMC en surpoids.';
        }
        if ($age >= 45) {
            $reasons[] = 'Age dans une tranche à risque métabolique plus élevé.';
        }
        if ($activityMinutes < 150) {
            $reasons[] = 'Activité physique hebdomadaire sous le seuil recommandé.';
        }
        if ($avgTension >= 13.0 || $highTensionDays >= 3) {
            $reasons[] = 'Tension élevée associée au risque cardiométabolique.';
        }

        if ($weightChange >= 2.0) {
            $reasons[] = 'Prise de poids récente.';
        }

        if ($fatigueCount >= 2) {
            $reasons[] = 'Fatigue récurrente signalée.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons, 'details' => $details];
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>,details:array<int,string>}
     */
    private function computeDepressionRisk(array $s, array $sym): array
    {
        $score = 5.0;
        $reasons = [];
        $details = ['Base: +5.0/100'];

        if (($s['moodSevereEntries7'] ?? 0) >= 2) {
            $score += 32;
            $details[] = 'Humeurs sévères: +32.0';
            $reasons[] = 'Humeurs sévères répétées (triste/déprimée/anxieuse).';
        } elseif (($s['moodSevereEntries7'] ?? 0) >= 1) {
            $score += 18;
            $details[] = 'Humeur sévère: +18.0';
            $reasons[] = 'Humeur sévère signalée récemment.';
        }
        if (($s['moodNegativeEntries7'] ?? 0) >= 3) {
            $score += 20;
            $details[] = 'Humeurs négatives fréquentes: +20.0';
            $reasons[] = 'Humeurs négatives fréquentes.';
        } elseif (($s['moodNegativeEntries7'] ?? 0) >= 1) {
            $score += 10;
            $details[] = 'Humeur négative: +10.0';
            $reasons[] = 'Humeur négative observée.';
        }
        if (($s['moodNegativeDays7'] ?? 0) >= 3) {
            $score += 12;
            $details[] = 'Jours négatifs répétés: +12.0';
            $reasons[] = 'Impact émotionnel présent sur plusieurs jours.';
        }
        if (($s['avgSleep7'] ?? 0.0) > 0 && ($s['avgSleep7'] ?? 0.0) < 6.0) {
            $score += 16;
            $details[] = 'Sommeil bas: +16.0';
            $reasons[] = 'Sommeil moyen bas.';
        }
        if (($sym['moodCount'] ?? 0) >= 2) {
            $score += 10;
            $details[] = 'Symptômes émotionnels: +10.0';
            $reasons[] = 'Symptômes émotionnels répétés.';
        }
        if (($s['avgWater7'] ?? 0.0) > 0 && ($s['avgWater7'] ?? 0.0) < 1.0) {
            $score += 5;
            $details[] = 'Hydratation faible: +5.0';
            $reasons[] = 'Hydratation insuffisante.';
        }
        if (($sym['totalSymptoms'] ?? 0) >= 5) {
            $score += 5;
            $details[] = 'Charge symptômes: +5.0';
            $reasons[] = 'Charge symptomatique importante.';
        }
        if (($s['moodPositiveEntries7'] ?? 0) >= 3) {
            $score -= 8;
            $details[] = 'Humeurs positives récentes: -8.0';
            $reasons[] = 'Présence d’humeurs positives récentes.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons, 'details' => $details];
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>,details:array<int,string>}
     */
    private function computeNutritionBalanceRisk(array $s, array $sym): array
    {
        $score = 0.0;
        $reasons = [];
        $details = [];

        $poorDays = (int) ($s['nutritionPoorDays7'] ?? 0);
        $goodDays = (int) ($s['nutritionGoodDays7'] ?? 0);
        $entries = (int) ($s['nutritionEntries7'] ?? 0);
        $avgWater = (float) ($s['avgWater7'] ?? 0.0);
        $imc = (float) ($s['latestImc'] ?? 0.0);
        $fatigueCount = (int) ($sym['fatigueCount'] ?? 0);

        if ($entries > 0) {
            $poorRatio = $poorDays / $entries;
            $goodRatio = $goodDays / $entries;
            $poorPoints = $poorRatio * 50.0;
            $lackGoodPoints = (1.0 - $goodRatio) * 20.0;
            $score += $poorPoints + $lackGoodPoints;
            $details[] = sprintf('Jours alimentation faible: +%.1f/50', $poorPoints);
            $details[] = sprintf('Manque de jours alimentation bonne: +%.1f/20', $lackGoodPoints);
        }
        if ($avgWater > 0.0) {
            $waterPoints = $this->inverseNorm($avgWater, 2.2, 0.8) * 15.0;
            $score += $waterPoints;
            $details[] = sprintf('Hydratation: +%.1f/15', $waterPoints);
        }
        // Ecart d'IMC par rapport a la zone cible (22), seulement si IMC disponible.
        if ($imc > 0.0) {
            $imcPoints = $this->norm(abs($imc - 22.0), 2.0, 12.0) * 10.0;
            $score += $imcPoints;
            $details[] = sprintf('IMC hors zone cible: +%.1f/10', $imcPoints);
        }
        $fatiguePoints = $this->norm((float) $fatigueCount, 0.0, 4.0) * 5.0;
        $score += $fatiguePoints;
        $details[] = sprintf('Fatigue liée hygiène de vie: +%.1f/5', $fatiguePoints);

        if ($poorDays >= 3) {
            $reasons[] = 'Alimentation faible sur plusieurs jours.';
        }
        if ($goodDays >= 4) {
            $reasons[] = 'Habitudes alimentaires globalement équilibrées.';
        }

        if ($avgWater > 0.0 && $avgWater < 1.2) {
            $reasons[] = 'Hydratation insuffisante.';
        }

        if ($imc > 0.0 && ($imc >= 30.0 || $imc <= 18.5)) {
            $reasons[] = 'IMC éloigné de la zone d’équilibre.';
        }
        if ($fatigueCount >= 2) {
            $reasons[] = 'Fatigue répétée, potentiellement liée à l’hygiène de vie.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons, 'details' => $details];
    }

    private function norm(float $value, float $min, float $max): float
    {
        if ($max <= $min) {
            return 0.0;
        }
        $ratio = ($value - $min) / ($max - $min);

        return max(0.0, min(1.0, $ratio));
    }

    private function inverseNorm(float $value, float $best, float $worst): float
    {
        return $this->norm($best - $value, 0.0, $best - $worst);
    }

    private function blendScores(float $ruleScore, float $aiScore, float $aiWeight): float
    {
        $ruleScore = $this->clampScore($ruleScore);
        $aiScore = $this->clampScore($aiScore);

        return $this->clampScore(($ruleScore * (1.0 - $aiWeight)) + ($aiScore * $aiWeight));
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{htn:float,diabetes:float,depression:float,nutrition:float}|null
     */
    private function predictWithAi(array $s, array $sym): ?array
    {
        $endpoint = trim($this->riskAiApiUrl);
        if ($endpoint === '') {
            $model = trim($this->riskAiModel);
            if ($model === '') {
                return null;
            }
            $endpoint = 'https://router.huggingface.co/hf-inference/models/' . rawurlencode($model);
        }

        $headers = ['Content-Type' => 'application/json'];
        $token = trim($this->riskAiApiToken);
        if ($token !== '') {
            $headers['Authorization'] = 'Bearer ' . $token;
        }

        $summary = sprintf(
            'tension_moyenne=%.2f; tension_derniere=%.2f; tension_max=%.2f; jours_tension_haute=%d; imc=%.2f; eau=%.2f; variation_poids_30j=%.2f; sommeil=%.2f; humeur_negative=%d; humeur_severe=%d; humeur_positive=%d; alimentation_faible=%d; alimentation_bonne=%d; fatigue=%d; symptomes_total=%d; intensite=%.2f',
            (float) ($s['avgTension7'] ?? 0.0),
            (float) ($s['latestTension7'] ?? 0.0),
            (float) ($s['maxTension7'] ?? 0.0),
            (int) ($s['highTensionDays7'] ?? 0),
            (float) ($s['latestImc'] ?? 0.0),
            (float) ($s['avgWater7'] ?? 0.0),
            (float) ($s['weightChange30'] ?? 0.0),
            (float) ($s['avgSleep7'] ?? 0.0),
            (int) ($s['moodNegativeEntries7'] ?? 0),
            (int) ($s['moodSevereEntries7'] ?? 0),
            (int) ($s['moodPositiveEntries7'] ?? 0),
            (int) ($s['nutritionPoorDays7'] ?? 0),
            (int) ($s['nutritionGoodDays7'] ?? 0),
            (int) ($sym['fatigueCount'] ?? 0),
            (int) ($sym['totalSymptoms'] ?? 0),
            (float) ($sym['avgIntensity'] ?? 0.0)
        );

        $candidateLabels = [
            'hypertension_risk',
            'diabetes_risk',
            'depression_risk',
            'nutrition_imbalance_risk',
        ];

        try {
            $response = $this->httpClient->request('POST', $endpoint, [
                'headers' => $headers,
                'json' => [
                    'inputs' => $summary,
                    'parameters' => [
                        'candidate_labels' => $candidateLabels,
                        'multi_label' => true,
                        'hypothesis_template' => 'Ce profil présente {}.',
                    ],
                    'options' => ['wait_for_model' => true],
                ],
                'timeout' => max(2.0, $this->riskAiTimeout),
            ]);
            $data = $response->toArray(false);
        } catch (TransportExceptionInterface|\Throwable) {
            return null;
        }

        if (!is_array($data) || isset($data['error'])) {
            return null;
        }
        $labels = $data['labels'] ?? null;
        $scores = $data['scores'] ?? null;
        if (!is_array($labels) || !is_array($scores) || count($labels) !== count($scores)) {
            return null;
        }

        $mapped = [
            'htn' => null,
            'diabetes' => null,
            'depression' => null,
            'nutrition' => null,
        ];
        foreach ($labels as $i => $labelRaw) {
            $label = mb_strtolower((string) $labelRaw);
            $scoreRaw = $scores[$i] ?? null;
            if (!is_numeric($scoreRaw)) {
                continue;
            }
            $score = (float) $scoreRaw;
            if ($score > 1.0) {
                $score /= 100.0;
            }
            $score100 = $this->clampScore($score * 100.0);
            if (str_contains($label, 'hypertension')) {
                $mapped['htn'] = $score100;
            } elseif (str_contains($label, 'diabetes')) {
                $mapped['diabetes'] = $score100;
            } elseif (str_contains($label, 'depression')) {
                $mapped['depression'] = $score100;
            } elseif (str_contains($label, 'nutrition')) {
                $mapped['nutrition'] = $score100;
            }
        }

        foreach (['htn', 'diabetes', 'depression', 'nutrition'] as $key) {
            if (!is_numeric($mapped[$key])) {
                return null;
            }
        }

        return [
            'htn' => (float) $mapped['htn'],
            'diabetes' => (float) $mapped['diabetes'],
            'depression' => (float) $mapped['depression'],
            'nutrition' => (float) $mapped['nutrition'],
        ];
    }

    /**
     * @param mixed $data
     */
    private function extractGeneratedText(mixed $data): ?string
    {
        if (!is_array($data) || isset($data['error'])) {
            return null;
        }
        if (isset($data['generated_text']) && is_string($data['generated_text'])) {
            return $data['generated_text'];
        }
        if (array_is_list($data) && isset($data[0]) && is_array($data[0])) {
            if (isset($data[0]['generated_text']) && is_string($data[0]['generated_text'])) {
                return $data[0]['generated_text'];
            }
            if (isset($data[0]['summary_text']) && is_string($data[0]['summary_text'])) {
                return $data[0]['summary_text'];
            }
        }

        return null;
    }

    private function extractJsonObject(string $text): ?string
    {
        $start = strpos($text, '{');
        $end = strrpos($text, '}');
        if ($start === false || $end === false || $end <= $start) {
            return null;
        }

        return substr($text, $start, $end - $start + 1);
    }

    /**
     * @param array<string, mixed> $features
     * @return array<string, mixed>
     */
    private function sanitizeSanteFeatures(array $features): array
    {
        $features['age'] = (int) $this->clampFloat((float) ($features['age'] ?? 0), 0.0, 120.0);
        $features['latestImc'] = $this->sanitizeOptionalFloat($features['latestImc'] ?? 0.0, 15.0, 50.0);
        $features['avgTension7'] = $this->sanitizeOptionalFloat($features['avgTension7'] ?? 0.0, 6.0, 25.0);
        $features['latestTension7'] = $this->sanitizeOptionalFloat($features['latestTension7'] ?? 0.0, 6.0, 25.0);
        $features['maxTension7'] = $this->sanitizeOptionalFloat($features['maxTension7'] ?? 0.0, 6.0, 25.0);
        $features['avgWater7'] = $this->sanitizeOptionalFloat($features['avgWater7'] ?? 0.0, 0.0, 6.0);
        $features['weightChange30'] = $this->clampFloat((float) ($features['weightChange30'] ?? 0.0), -15.0, 15.0);
        $features['highTensionDays7'] = (int) $this->clampFloat((float) ($features['highTensionDays7'] ?? 0), 0.0, 7.0);
        $features['activityMinutes7'] = (int) $this->clampFloat((float) ($features['activityMinutes7'] ?? 0), 0.0, 3000.0);

        return $features;
    }

    private function sanitizeOptionalFloat(mixed $value, float $min, float $max): float
    {
        if (!is_numeric($value)) {
            return 0.0;
        }

        $floatValue = (float) $value;
        if ($floatValue <= 0.0) {
            return 0.0;
        }

        return $this->clampFloat($floatValue, $min, $max);
    }

    private function clampFloat(float $value, float $min, float $max): float
    {
        return max($min, min($max, $value));
    }

    private function clampScore(float $score): float
    {
        return round(max(0.0, min(100.0, $score)), 1);
    }

    private function levelFromScore(float $score): string
    {
        if ($score >= 65.0) {
            return 'HIGH';
        }
        if ($score >= 35.0) {
            return 'MODERATE';
        }

        return 'LOW';
    }
}
