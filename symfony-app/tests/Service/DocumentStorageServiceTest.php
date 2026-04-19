<?php

namespace App\Tests\Service;

use App\Service\DocumentStorageService;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\File\UploadedFile;

final class DocumentStorageServiceTest extends TestCase
{
    /** @testdox Rejeter les extensions de fichier dangereuses */
    public function testValidateFileRejectsDangerousExtension(): void
    {
        $tmp = tempnam(sys_get_temp_dir(), 'doc_test_');
        file_put_contents($tmp, 'fake executable content');

        $file = new UploadedFile(
            $tmp,
            'malware.exe',
            'application/octet-stream',
            null,
            true
        );

        $service = new DocumentStorageService();
        $errors = $service->validateFile($file);

        self::assertNotEmpty($errors);
        self::assertTrue($this->containsErrorPart($errors, 'extension'));
    }

    /** @testdox Accepter un document PDF conforme */
    public function testValidateFileAcceptsValidPdf(): void
    {
        $tmp = tempnam(sys_get_temp_dir(), 'pdf_test_');
        file_put_contents($tmp, "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n");

        $file = new UploadedFile(
            $tmp,
            'resultat.pdf',
            'application/pdf',
            null,
            true
        );

        $service = new DocumentStorageService();
        $errors = $service->validateFile($file);

        self::assertSame([], $errors);
    }

    private function containsErrorPart(array $errors, string $needle): bool
    {
        foreach ($errors as $error) {
            if (stripos((string) $error, $needle) !== false) {
                return true;
            }
        }

        return false;
    }
}
