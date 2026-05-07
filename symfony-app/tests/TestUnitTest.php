<?php

namespace App\Tests;

use App\Entity\SharedDocument;
use App\Entity\User;
use App\Service\DocumentStorageService;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\File\UploadedFile;

class TestUnitTest extends TestCase
{
    /** @testdox Rejeter un fichier avec une extension dangereuse */
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

    /** @testdox Accepter un PDF valide */
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

    /** @testdox Nettoyer le nom du fichier lors du stockage */
    public function testStoreDocumentSanitizesOriginalFilename(): void
    {
        $tmp = tempnam(sys_get_temp_dir(), 'name_test_');
        file_put_contents($tmp, "%PDF-1.4\n");

        $file = new UploadedFile(
            $tmp,
            'résumé final?.pdf',
            'application/pdf',
            null,
            true
        );

        $service = new DocumentStorageService();
        $stored = $service->storeDocument($file, $this->createMock(User::class));

        self::assertArrayHasKey('originalName', $stored);
        self::assertStringNotContainsString('?', $stored['originalName']);
        self::assertNotSame('résumé final?.pdf', $stored['originalName']);
    }

    /** @testdox Lever une exception si l'upload est invalide */
    public function testStoreDocumentThrowsOnInvalidUpload(): void
    {
        $tmp = tempnam(sys_get_temp_dir(), 'invalid_test_');
        file_put_contents($tmp, 'x');

        $file = new UploadedFile(
            $tmp,
            'invalid.pdf',
            'application/pdf',
            \UPLOAD_ERR_INI_SIZE,
            true
        );

        $service = new DocumentStorageService();

        $this->expectException(\Exception::class);
        $this->expectExceptionMessage('n\'est pas valide');
        $service->storeDocument($file, $this->createMock(User::class));
    }

    /** @testdox Détecter un document existant quand le contenu est présent */
    public function testDocumentExistsReturnsTrueWhenContentPresent(): void
    {
        $document = new SharedDocument();
        $document->setFileContent('abc');

        $service = new DocumentStorageService();
        self::assertTrue($service->documentExists($document));
    }

    /** @testdox Détecter un document absent quand le contenu est nul */
    public function testDocumentExistsReturnsFalseWhenContentIsNull(): void
    {
        $document = new SharedDocument();
        $document->setFileContent(null);

        $service = new DocumentStorageService();
        self::assertFalse($service->documentExists($document));
    }

    /** @testdox Convertir correctement les octets en kilooctets */
    public function testFormatFileSizeConvertsKilobytes(): void
    {
        self::assertSame('1.5 KB', DocumentStorageService::formatFileSize(1536));
    }

    /** @testdox Retourner l'icône correspondant au type MIME */
    public function testGetMimeTypeIconReturnsExpectedIcons(): void
    {
        self::assertSame('bi-file-image', DocumentStorageService::getMimeTypeIcon('image/png'));
        self::assertSame('bi-file-earmark', DocumentStorageService::getMimeTypeIcon('application/json'));
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
