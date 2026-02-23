<?php

namespace App\Service;

use App\Entity\HealthRiskPrediction;
use App\Entity\User;
use App\Repository\HealthRiskPredictionRepository;
use App\Repository\SanteQuotidienneRepository;
use App\Repository\SymptomeQuotidienRepository;
use Doctrine\ORM\EntityManagerInterface;

class RiskPredictionService
{
    public function __construct(
        private readonly SanteQuotidienneRepository $santeRepository,
        private readonly SymptomeQuotidienRepository $symptomeRepository,
        private readonly HealthRiskPredictionRepository $predictionRepository,
        private readonly EntityManagerInterface $em
    ) {}

    public function recalculateForUser(User $user): HealthRiskPrediction
    {
        $today = new \DateTimeImmutable('today');
        $prediction = $this->predictionRepository->findForUserAndDate($user, $today) ?? (new HealthRiskPrediction())
            ->setUser($user)
            ->setPredictionDate($today);

        $santeFeatures = $this->santeRepository->getRiskFeatureSnapshot($user);
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
                'diabetes' => $diabetes['reasons'],
                'depression' => $depression['reasons'],
                'nutrition' => $nutrition['reasons'],
                // Compatibilité avec d'anciens templates/JSON.
                'respiratory' => $nutrition['reasons'],
            ])
            ->setFeatureSnapshot([
                'sante' => $santeFeatures,
                'symptomes' => $symptomeFeatures,
            ])
            ->setUpdatedAt(new \DateTimeImmutable());

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
     * @return array{score:float,reasons:array<int,string>}
     */
    private function computeHtnRisk(array $s, array $sym): array
    {
        $score = 8.0;
        $reasons = [];

        if (($s['highTensionDays7'] ?? 0) >= 3) {
            $score += 35;
            $reasons[] = 'Tension élevée sur plusieurs jours.';
        }
        if (($s['avgTension7'] ?? 0.0) >= 14.0) {
            $score += 20;
            $reasons[] = 'Moyenne tension au-dessus du seuil.';
        }
        if (($s['latestImc'] ?? 0.0) >= 30.0) {
            $score += 10;
            $reasons[] = 'IMC élevé.';
        }
        if (($sym['avgIntensity'] ?? 0.0) >= 7.0 && ($sym['totalSymptoms'] ?? 0) >= 2) {
            $score += 7;
            $reasons[] = 'Symptômes intenses récents.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons];
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>}
     */
    private function computeDiabetesRisk(array $s, array $sym): array
    {
        $score = 6.0;
        $reasons = [];

        $imc = (float) ($s['latestImc'] ?? 0.0);
        if ($imc >= 30.0) {
            $score += 30;
            $reasons[] = 'IMC dans la zone obésité.';
        } elseif ($imc >= 25.0) {
            $score += 18;
            $reasons[] = 'IMC en surpoids.';
        }
        if (($s['weightChange30'] ?? 0.0) >= 2.0) {
            $score += 15;
            $reasons[] = 'Prise de poids récente.';
        }
        if (($s['avgWater7'] ?? 0.0) > 0 && ($s['avgWater7'] ?? 0.0) < 1.2) {
            $score += 10;
            $reasons[] = 'Hydratation faible.';
        }
        if (($sym['fatigueCount'] ?? 0) >= 2) {
            $score += 8;
            $reasons[] = 'Fatigue récurrente signalée.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons];
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>}
     */
    private function computeDepressionRisk(array $s, array $sym): array
    {
        $score = 5.0;
        $reasons = [];

        if (($s['moodSevereEntries7'] ?? 0) >= 2) {
            $score += 32;
            $reasons[] = 'Humeurs sévères répétées (triste/déprimée/anxieuse).';
        } elseif (($s['moodSevereEntries7'] ?? 0) >= 1) {
            $score += 18;
            $reasons[] = 'Humeur sévère signalée récemment.';
        }
        if (($s['moodNegativeEntries7'] ?? 0) >= 3) {
            $score += 20;
            $reasons[] = 'Humeurs négatives fréquentes.';
        } elseif (($s['moodNegativeEntries7'] ?? 0) >= 1) {
            $score += 10;
            $reasons[] = 'Humeur négative observée.';
        }
        if (($s['moodNegativeDays7'] ?? 0) >= 3) {
            $score += 12;
            $reasons[] = 'Impact émotionnel présent sur plusieurs jours.';
        }
        if (($s['avgSleep7'] ?? 0.0) > 0 && ($s['avgSleep7'] ?? 0.0) < 6.0) {
            $score += 16;
            $reasons[] = 'Sommeil moyen bas.';
        }
        if (($sym['moodCount'] ?? 0) >= 2) {
            $score += 10;
            $reasons[] = 'Symptômes émotionnels répétés.';
        }
        if (($s['avgWater7'] ?? 0.0) > 0 && ($s['avgWater7'] ?? 0.0) < 1.0) {
            $score += 5;
            $reasons[] = 'Hydratation insuffisante.';
        }
        if (($sym['totalSymptoms'] ?? 0) >= 5) {
            $score += 5;
            $reasons[] = 'Charge symptomatique importante.';
        }
        if (($s['moodPositiveEntries7'] ?? 0) >= 3) {
            $score -= 8;
            $reasons[] = 'Présence d’humeurs positives récentes.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons];
    }

    /**
     * @param array<string, mixed> $s
     * @param array<string, mixed> $sym
     * @return array{score:float,reasons:array<int,string>}
     */
    private function computeNutritionBalanceRisk(array $s, array $sym): array
    {
        $score = 8.0;
        $reasons = [];

        if (($s['nutritionPoorDays7'] ?? 0) >= 3) {
            $score += 28;
            $reasons[] = 'Alimentation faible sur plusieurs jours.';
        }
        if (($s['nutritionGoodDays7'] ?? 0) >= 4) {
            $score -= 18;
            $reasons[] = 'Habitudes alimentaires globalement équilibrées.';
        }
        if (($s['avgWater7'] ?? 0.0) > 0 && ($s['avgWater7'] ?? 0.0) < 1.2) {
            $score += 14;
            $reasons[] = 'Hydratation insuffisante.';
        }
        if (($s['latestImc'] ?? 0.0) >= 30.0) {
            $score += 12;
            $reasons[] = 'IMC élevé, vigilance nutritionnelle recommandée.';
        }
        if (($sym['fatigueCount'] ?? 0) >= 2) {
            $score += 6;
            $reasons[] = 'Fatigue répétée, potentiellement liée à l’hygiène de vie.';
        }

        return ['score' => $this->clampScore($score), 'reasons' => $reasons];
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
