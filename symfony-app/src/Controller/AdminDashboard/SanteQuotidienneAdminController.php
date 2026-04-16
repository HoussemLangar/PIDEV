<?php

namespace App\Controller\AdminDashboard;

use App\Service\SanteQuotidienneAdminService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/admin/sante-quotidienne')]
#[IsGranted('ROLE_ADMIN')]
class SanteQuotidienneAdminController extends AbstractController
{
    public function __construct(
        private readonly SanteQuotidienneAdminService $santeQuotidienneAdminService
    ) {}

    #[Route('', name: 'admin_sante_quotidienne_index', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $data = $this->santeQuotidienneAdminService->getFilteredData($request);
        $stats = $this->santeQuotidienneAdminService->getStatistics();

        return $this->render('admin/sante_quotidienne/index.html.twig', [
            'data' => $data['data'],
            'stats' => $stats,
            'pagination' => [
                'page' => $data['page'],
                'pages' => $data['pages'],
                'limit' => $data['limit'],
                'total' => $data['total']
            ],
            'filters' => $data['filters']
        ]);
    }

    #[Route('/export', name: 'admin_sante_quotidienne_export', methods: ['POST'])]
    public function export(): StreamedResponse
    {
        $data = $this->santeQuotidienneAdminService->getExportData();

        $response = new StreamedResponse(function () use ($data) {
            $handle = fopen('php://output', 'w');

            // En-têtes
            fputcsv($handle, ['Email', 'Date', 'Poids', 'Sommeil', 'Humeur', 'Motivation', 'Activité Physique'], ';');

            // Données
            foreach ($data as $row) {
                fputcsv($handle, [
                    $row['email'] ?? '',
                    $row['date']?->format('Y-m-d H:i:s') ?? '',
                    $row['poids'] ?? '',
                    $row['sommeil'] ?? '',
                    $row['humeur'] ?? '',
                    $row['motivation'] ?? '',
                    $row['activitePhysique'] ?? ''
                ], ';');
            }

            fclose($handle);
        });

        $response->headers->set('Content-Type', 'text/csv; charset=utf-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="sante_quotidienne_' . date('Y-m-d_H-i-s') . '.csv"');

        return $response;
    }
}
