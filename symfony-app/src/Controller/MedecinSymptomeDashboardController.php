<?php

namespace App\Controller;

use App\Entity\User;
use App\Repository\SymptomeQuotidienRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/medecin/symptomes/dashboard')]
class MedecinSymptomeDashboardController extends AbstractController
{
    #[Route('', name: 'app_medecin_symptomes_dashboard', methods: ['GET'])]
    public function index(Request $request, SymptomeQuotidienRepository $repository): Response
    {
        if (!$this->isGranted('ROLE_MEDECIN') && !$this->isGranted('ROLE_ADMIN')) {
            throw $this->createAccessDeniedException('Acces reserve aux medecins et administrateurs.');
        }

        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('Acces refuse.');
        }

        $patients = $repository->getDoctorDashboardPatients();
        $patientId = (int) $request->query->get('patient', 0);
        $patientId = $patientId > 0 ? $patientId : null;

        $dailyEvolution = $repository->getDoctorDailyEvolution($patientId, 35);
        $heatmap = $repository->getDoctorWeeklyHeatmap($patientId, 8);

        $alerts = $this->buildPeakAlerts($dailyEvolution);

        return $this->render('front/medecin/symptomes_dashboard.html.twig', [
            'patients' => $patients,
            'selectedPatientId' => $patientId,
            'dailyEvolution' => $dailyEvolution,
            'heatmap' => $heatmap,
            'alerts' => $alerts,
        ]);
    }

    /**
     * @param array<int, array{date:string,count:int,avgIntensity:float}> $daily
     * @return array<int, array{date:string,count:int,avgIntensity:float,threshold:float}>
     */
    private function buildPeakAlerts(array $daily): array
    {
        if (count($daily) < 4) {
            return [];
        }

        $intensities = array_map(static fn(array $d): float => (float) ($d['avgIntensity'] ?? 0.0), $daily);
        $mean = array_sum($intensities) / count($intensities);

        $varianceAccumulator = 0.0;
        foreach ($intensities as $value) {
            $varianceAccumulator += ($value - $mean) ** 2;
        }
        $stdDev = sqrt($varianceAccumulator / count($intensities));

        $threshold = max(7.0, $mean + (1.5 * $stdDev));
        $alerts = [];

        foreach ($daily as $point) {
            $count = (int) ($point['count'] ?? 0);
            $avg = (float) ($point['avgIntensity'] ?? 0.0);
            if ($count >= 2 && $avg >= $threshold) {
                $alerts[] = [
                    'date' => (string) ($point['date'] ?? ''),
                    'count' => $count,
                    'avgIntensity' => round($avg, 2),
                    'threshold' => round($threshold, 2),
                ];
            }
        }

        return array_reverse($alerts);
    }
}
