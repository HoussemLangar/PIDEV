<?php

namespace App\Service\Ai;

use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\Process\Process;

class DocumentScannerService
{
    public function __construct(
        private readonly AiGatewayService $aiGateway,
    ) {
    }

    public function scan(string $content): array
    {
        $content = $this->normalizeExtractedText($content);
        if ($content === '') {
            return [
                'summary' => 'Aucun contenu fourni.',
                'analysisPrediction' => 'Aucune prediction possible sans contenu exploitable.',
                'abnormalFindings' => [],
                'keyPoints' => [],
                'values' => [],
                'suggestedActions' => [],
            ];
        }

        $cleanForAnalysis = $this->removeAdministrativeNoise($content);
        $fallback = $this->buildFallback($cleanForAnalysis !== '' ? $cleanForAnalysis : $content);

        $ai = $this->aiGateway->askForJson(
            'Tu es un assistant d extraction de document medical. Reponds uniquement en JSON: '
            . '{"summary":string,"analysisPrediction":string,"abnormalFindings":string[],"keyPoints":string[],"values":[{"label":string,"value":string,"unit":string}],"suggestedActions":string[]}. '
            . 'Regles: resume medical synthétique (2 phrases max), ne jamais recopier mot a mot le document, ignorer en-tetes administratifs (nom, page, adresse, heure), '
            . 'extraire valeurs biologiques/constantes en priorite, normaliser les unites, ne pas inventer de donnees absentes. '
            . 'Pour analysisPrediction: donner une interpretation prudente (profil global normal/perturbe) en te basant sur les intervalles presents.',
            "Analyse ce document medical. Contenu OCR/text:\n\n" . ($cleanForAnalysis !== '' ? $cleanForAnalysis : $content)
        );

        if (!is_array($ai)) {
            return $fallback;
        }

        $summary = trim((string)($ai['summary'] ?? ''));
        if ($summary === '' || mb_strlen($summary) > 380) {
            $summary = $fallback['summary'];
        }

        $analysisPrediction = trim((string)($ai['analysisPrediction'] ?? ''));
        if ($analysisPrediction === '' || mb_strlen($analysisPrediction) > 520) {
            $analysisPrediction = (string) ($fallback['analysisPrediction'] ?? 'Prediction non disponible.');
        }

        $abnormalFindings = $this->normalizeStringList($ai['abnormalFindings'] ?? []);
        if ($abnormalFindings === []) {
            $abnormalFindings = $this->normalizeStringList($fallback['abnormalFindings'] ?? []);
        }

        return [
            'summary' => $summary,
            'analysisPrediction' => $analysisPrediction,
            'abnormalFindings' => $abnormalFindings,
            'keyPoints' => $this->normalizeStringList($ai['keyPoints'] ?? $fallback['keyPoints']),
            'values' => $this->normalizeValues($ai['values'] ?? $fallback['values']),
            'suggestedActions' => $this->normalizeStringList($ai['suggestedActions'] ?? $fallback['suggestedActions']),
        ];
    }

    /**
     * @return array{content:string,warning:?string,source:string}
     */
    public function extractContentFromUpload(UploadedFile $file): array
    {
        $ext = strtolower((string) $file->getClientOriginalExtension());
        $mime = strtolower((string) $file->getMimeType());

        $isTextLike = str_starts_with($mime, 'text/') || in_array($ext, ['txt', 'csv', 'log', 'md'], true);
        if ($isTextLike) {
            $raw = file_get_contents($file->getPathname());
            $content = is_string($raw) ? $this->normalizeExtractedText($raw) : '';

            return [
                'content' => $content,
                'warning' => $content === '' ? 'Le fichier texte est vide ou illisible.' : null,
                'source' => 'text',
            ];
        }

        $isPdf = $ext === 'pdf' || $mime === 'application/pdf';
        if ($isPdf) {
            $content = $this->extractTextFromPdf($file->getPathname());

            return [
                'content' => $content,
                'warning' => $content === ''
                    ? 'PDF detecte mais texte non extractible automatiquement. Si c est un scan image, activez OCR (tesseract + pdftoppm/imagick) sur le serveur, ou importez un PDF texte.'
                    : null,
                'source' => 'pdf',
            ];
        }

        return [
            'content' => '',
            'warning' => 'Format non supporte. Utilisez un fichier texte (.txt, .csv, .log, .md) ou PDF.',
            'source' => 'unsupported',
        ];
    }

    private function buildFallback(string $content): array
    {
        $summary = $this->buildSmartSummary($content);
        [$analysisPrediction, $abnormalFindings] = $this->buildHeuristicPrediction($content);

        preg_match_all('/\b\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}\b/', $content, $dateMatches);
        $dates = array_values(array_unique($dateMatches[0] ?? []));

        preg_match_all(
            '/([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \-_]{1,55})\s*(?:[:\-]|=)?\s*([<>]?[0-9]+(?:[.,][0-9]+)?)\s*([A-Za-z%\/µ²³\.]+)?/u',
            $content,
            $valueMatches,
            \PREG_SET_ORDER
        );

        $values = [];
        $seenKeys = [];
        foreach (array_slice($valueMatches, 0, 24) as $match) {
            $label = trim((string)($match[1] ?? ''));
            $value = trim((string)($match[2] ?? ''));
            $unit = trim((string)($match[3] ?? ''));
            if ($label === '' || $value === '') {
                continue;
            }

            if (mb_strlen($label) < 2 || preg_match('/^(date|patient|nom|prenom|age|sexe)$/iu', $label)) {
                continue;
            }

            $key = mb_strtolower($label . '|' . $value . '|' . $unit);
            if (isset($seenKeys[$key])) {
                continue;
            }
            $seenKeys[$key] = true;

            $values[] = [
                'label' => $label,
                'value' => $value,
                'unit' => $unit,
            ];
        }

        $keyPoints = [];
        if ($dates !== []) {
            $keyPoints[] = 'Dates détectées: ' . implode(', ', array_slice($dates, 0, 5));
        }
        if ($values !== []) {
            $keyPoints[] = sprintf('%d mesures numériques détectées.', count($values));
        }

        return [
            'summary' => $summary !== '' ? $summary : 'Document biologique analysé.',
            'analysisPrediction' => $analysisPrediction,
            'abnormalFindings' => $abnormalFindings,
            'keyPoints' => $keyPoints,
            'values' => $values,
            'suggestedActions' => [
                'Verifier les valeurs anormales avec un professionnel de sante.',
                'Comparer avec vos resultats precedents si disponibles.',
                'Conserver ce document dans votre dossier medical personnel.',
            ],
        ];
    }

    private function extractTextFromPdf(string $filePath): string
    {
        $text = $this->extractTextFromPdfWithPdftotext($filePath);
        if ($text !== '') {
            return $text;
        }

        $text = $this->extractTextFromPdfWithPhpParser($filePath);
        if ($text !== '') {
            return $text;
        }

        $text = $this->extractTextFromPdfRawContent($filePath);
        if ($text !== '') {
            return $text;
        }

        $text = $this->extractTextFromPdfWithOcr($filePath);
        return $text;
    }

    private function extractTextFromPdfWithPhpParser(string $filePath): string
    {
        if (!class_exists('Smalot\\PdfParser\\Parser')) {
            return '';
        }

        try {
            $parser = new \Smalot\PdfParser\Parser();
            $pdf = $parser->parseFile($filePath);
            $text = $pdf->getText();

            return $this->normalizeExtractedText((string) $text);
        } catch (\Throwable) {
            return '';
        }
    }

    private function extractTextFromPdfWithPdftotext(string $filePath): string
    {
        try {
            $process = new Process(['pdftotext', '-layout', '-nopgbrk', $filePath, '-']);
            $process->setTimeout(15);
            $process->run();

            if (!$process->isSuccessful()) {
                return '';
            }

            return $this->normalizeExtractedText($process->getOutput());
        } catch (\Throwable) {
            return '';
        }
    }

    private function extractTextFromPdfRawContent(string $filePath): string
    {
        $raw = @file_get_contents($filePath);
        if (!is_string($raw) || $raw === '') {
            return '';
        }

        $chunks = [];
        if (preg_match_all('/\(([^\)]{2,})\)\s*Tj/s', $raw, $matches)) {
            foreach ($matches[1] as $chunk) {
                if (!is_string($chunk)) {
                    continue;
                }
                $chunks[] = stripcslashes($chunk);
            }
        }

        if ($chunks === []) {
            return '';
        }

        return $this->normalizeExtractedText(implode("\n", $chunks));
    }

    private function extractTextFromPdfWithOcr(string $filePath): string
    {
        $text = $this->extractTextFromPdfWithPdftoppmTesseract($filePath);
        if ($text !== '') {
            return $text;
        }

        return $this->extractTextFromPdfWithImagickTesseract($filePath);
    }

    private function extractTextFromPdfWithPdftoppmTesseract(string $filePath): string
    {
        $tmpDir = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'santea_pdf_' . uniqid('', true);
        if (!@mkdir($tmpDir, 0700, true) && !is_dir($tmpDir)) {
            return '';
        }

        try {
            $prefix = $tmpDir . DIRECTORY_SEPARATOR . 'page';

            $render = new Process(['pdftoppm', '-f', '1', '-l', '3', '-r', '220', '-png', $filePath, $prefix]);
            $render->setTimeout(30);
            $render->run();

            if (!$render->isSuccessful()) {
                return '';
            }

            $images = glob($prefix . '-*.png') ?: [];
            if ($images === []) {
                return '';
            }

            $chunks = [];
            foreach (array_slice($images, 0, 3) as $imagePath) {
                $out = $this->runTesseractOnImage($imagePath);
                if ($out !== '') {
                    $chunks[] = $out;
                }
            }

            return $this->normalizeExtractedText(implode("\n", $chunks));
        } catch (\Throwable) {
            return '';
        } finally {
            $this->deleteDirectory($tmpDir);
        }
    }

    private function extractTextFromPdfWithImagickTesseract(string $filePath): string
    {
        if (!class_exists('Imagick')) {
            return '';
        }

        $tmpDir = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'santea_img_' . uniqid('', true);
        if (!@mkdir($tmpDir, 0700, true) && !is_dir($tmpDir)) {
            return '';
        }

        try {
            $imagick = new \Imagick();
            $imagick->setResolution(220, 220);
            $imagick->readImage($filePath . '[0-2]');

            $chunks = [];
            $page = 0;
            foreach ($imagick as $frame) {
                $page++;
                if ($page > 3) {
                    break;
                }

                $frame->setImageFormat('png');
                $imgPath = $tmpDir . DIRECTORY_SEPARATOR . 'page-' . $page . '.png';
                $frame->writeImage($imgPath);

                $out = $this->runTesseractOnImage($imgPath);
                if ($out !== '') {
                    $chunks[] = $out;
                }
            }

            $imagick->clear();
            $imagick->destroy();

            return $this->normalizeExtractedText(implode("\n", $chunks));
        } catch (\Throwable) {
            return '';
        } finally {
            $this->deleteDirectory($tmpDir);
        }
    }

    private function deleteDirectory(string $dir): void
    {
        if (!is_dir($dir)) {
            return;
        }

        $items = scandir($dir);
        if (!is_array($items)) {
            @rmdir($dir);
            return;
        }

        foreach ($items as $item) {
            if ($item === '.' || $item === '..') {
                continue;
            }

            $path = $dir . DIRECTORY_SEPARATOR . $item;
            if (is_dir($path)) {
                $this->deleteDirectory($path);
            } else {
                @unlink($path);
            }
        }

        @rmdir($dir);
    }

    private function runTesseractOnImage(string $imagePath): string
    {
        $commands = [
            ['tesseract', $imagePath, 'stdout', '-l', 'fra+eng'],
            ['tesseract', $imagePath, 'stdout', '-l', 'eng'],
            ['tesseract', $imagePath, 'stdout'],
        ];

        foreach ($commands as $cmd) {
            try {
                $ocr = new Process($cmd);
                $ocr->setTimeout(30);
                $ocr->run();
                if (!$ocr->isSuccessful()) {
                    continue;
                }

                $out = $this->normalizeExtractedText($ocr->getOutput());
                if ($out !== '') {
                    return $out;
                }
            } catch (\Throwable) {
                continue;
            }
        }

        return '';
    }

    private function normalizeExtractedText(string $text): string
    {
        $text = preg_replace('/\x00+/', ' ', $text) ?? $text;
        $text = preg_replace('/\s+/u', ' ', $text) ?? $text;
        return trim($text);
    }

    private function removeAdministrativeNoise(string $text): string
    {
        $patterns = [
            '/\bpage\s*:\s*\d+\s*\/\s*\d+\b/iu',
            '/\bnom\s*:\s*[^\n\r]{2,80}/iu',
            '/\bprenom\s*:\s*[^\n\r]{2,80}/iu',
            '/\bpr[eé]l[eè]vement\s+fait\s+le\s*:\s*[^\n\r]{2,80}/iu',
            '/\b(le|date)\s*:\s*\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}(?:\s*[aà]?\s*\d{1,2}:\d{2})?/iu',
            '/\b(?:ariana|tunis|sfax|sousse|nabeul)\b\s*:?/iu',
            '/\b(?:tel|t[eé]l|fax|adresse|laboratoire)\s*:?\s*[^\n\r]{2,120}/iu',
        ];

        $clean = $text;
        foreach ($patterns as $pattern) {
            $clean = preg_replace($pattern, ' ', $clean) ?? $clean;
        }

        $clean = preg_replace('/\s+/u', ' ', $clean) ?? $clean;

        return trim($clean);
    }

    private function buildSmartSummary(string $content): string
    {
        $flat = trim(preg_replace('/\s+/', ' ', $content) ?? '');
        if ($flat === '') {
            return 'Document biologique analysé.';
        }

        preg_match_all(
            '/([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \-_]{1,55})\s*(?:[:\-]|=)?\s*([<>]?[0-9]+(?:[.,][0-9]+)?)\s*([A-Za-z%\/µ²³\.]+)?/u',
            $flat,
            $valueMatches,
            \PREG_SET_ORDER
        );

        $labels = [];
        foreach ($valueMatches as $match) {
            $label = trim((string)($match[1] ?? ''));
            if ($label === '' || mb_strlen($label) < 3) {
                continue;
            }
            if (preg_match('/^(date|patient|nom|prenom|age|sexe|page)$/iu', $label)) {
                continue;
            }

            $labels[] = ucfirst(mb_strtolower($label));
            if (count($labels) >= 3) {
                break;
            }
        }

        if ($labels !== []) {
            return sprintf(
                'Document de biologie analyse avec %d mesures detectees. Parametres principaux: %s.',
                max(1, count($valueMatches)),
                implode(', ', $labels)
            );
        }

        return 'Document medical analyse. Aucune structure claire de valeurs biologiques na ete detectee automatiquement.';
    }

    /**
     * @return array{0:string,1:array<int,string>}
     */
    private function buildHeuristicPrediction(string $content): array
    {
        preg_match_all(
            '/([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \-_]{2,55})\s+([<>]?[0-9]+(?:[.,][0-9]+)?)\s*([A-Za-z%\/µ²³\.]*)\s*\(?\s*([0-9]+(?:[.,][0-9]+)?)\s*[-–]\s*([0-9]+(?:[.,][0-9]+)?)\s*\)?/u',
            $content,
            $matches,
            \PREG_SET_ORDER
        );

        $findings = [];
        $high = 0;
        $low = 0;
        $normal = 0;

        foreach (array_slice($matches, 0, 30) as $m) {
            $label = trim((string) ($m[1] ?? ''));
            $value = $this->toFloat((string) ($m[2] ?? ''));
            $unit = trim((string) ($m[3] ?? ''));
            $refMin = $this->toFloat((string) ($m[4] ?? ''));
            $refMax = $this->toFloat((string) ($m[5] ?? ''));

            if ($label === '' || $value === null || $refMin === null || $refMax === null) {
                continue;
            }
            if (preg_match('/^(date|patient|nom|prenom|age|sexe|page)$/iu', $label)) {
                continue;
            }

            if ($value < $refMin) {
                $low++;
                $findings[] = sprintf('%s: %.2f%s (en dessous de %.2f).', $label, $value, $unit !== '' ? ' ' . $unit : '', $refMin);
            } elseif ($value > $refMax) {
                $high++;
                $findings[] = sprintf('%s: %.2f%s (au-dessus de %.2f).', $label, $value, $unit !== '' ? ' ' . $unit : '', $refMax);
            } else {
                $normal++;
            }
        }

        if ($high === 0 && $low === 0) {
            if ($normal > 0) {
                return [
                    'Prediction automatique: les parametres avec intervalles detectes semblent globalement dans les bornes usuelles. Interpretation a confirmer cliniquement.',
                    [],
                ];
            }

            return [
                'Prediction automatique limitee: intervalles de reference insuffisants pour conclure un profil anormal ou normal.',
                [],
            ];
        }

        $profile = ($high + $low) >= 4
            ? 'profil biologique possiblement perturbe'
            : 'quelques ecarts biologiques ponctuels';

        return [
            sprintf(
                'Prediction automatique: %s detectes (%d au-dessus, %d en dessous des bornes). Verification medicale recommandee.',
                $profile,
                $high,
                $low
            ),
            array_slice($findings, 0, 6),
        ];
    }

    private function toFloat(string $value): ?float
    {
        $normalized = str_replace(',', '.', trim($value));
        if ($normalized === '' || !is_numeric($normalized)) {
            return null;
        }

        return (float) $normalized;
    }

    private function normalizeStringList(mixed $items): array
    {
        if (!is_array($items)) {
            return [];
        }

        $out = [];
        foreach ($items as $item) {
            if (is_string($item) && trim($item) !== '') {
                $out[] = trim($item);
            }
        }
        return array_values(array_unique($out));
    }

    private function normalizeValues(mixed $items): array
    {
        if (!is_array($items)) {
            return [];
        }

        $out = [];
        $seen = [];
        foreach ($items as $item) {
            if (!is_array($item)) {
                continue;
            }
            $label = trim((string)($item['label'] ?? ''));
            $value = trim((string)($item['value'] ?? ''));
            $unit = trim((string)($item['unit'] ?? ''));
            if ($label === '' || $value === '') {
                continue;
            }

            $k = mb_strtolower($label . '|' . $value . '|' . $unit);
            if (isset($seen[$k])) {
                continue;
            }
            $seen[$k] = true;

            $out[] = ['label' => $label, 'value' => $value, 'unit' => $unit];
        }
        return $out;
    }
}

