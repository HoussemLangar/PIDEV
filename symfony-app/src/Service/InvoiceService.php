<?php

namespace App\Service;

use App\Entity\Facture;
use Dompdf\Dompdf;
use Dompdf\Options;
use Twig\Environment;

class InvoiceService
{
    public function __construct(
        private Environment $twig,
        private string $projectDir
    ) {}

    public function generatePdf(Facture $facture): string
    {
        $options = new Options();
        $options->set('isRemoteEnabled', true);
        $options->set('defaultFont', 'DejaVu Sans');

        $dompdf = new Dompdf($options);

        $html = $this->twig->render('invoices/invoice.html.twig', [
            'facture' => $facture,
        ]);

        $dompdf->loadHtml($html);
        $dompdf->setPaper('A4');
        $dompdf->render();

        $dir = $this->projectDir . '/var/invoices';
        if (!is_dir($dir)) {
            mkdir($dir, 0777, true);
        }

        $filename = $dir . '/facture-' . $facture->getNumero() . '.pdf';
        file_put_contents($filename, $dompdf->output());

        return $filename;
    }
}
