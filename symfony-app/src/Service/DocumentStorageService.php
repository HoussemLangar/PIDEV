<?php

namespace App\Service;

use App\Entity\SharedDocument;
use App\Entity\User;
use Symfony\Component\HttpFoundation\File\UploadedFile;

class DocumentStorageService
{
    private const ALLOWED_MIME_TYPES = [
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'image/jpeg',
        'image/png',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ];

    private const ALLOWED_EXTENSIONS = ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png', 'xls', 'xlsx'];

    /**
     * Store an uploaded document's content in the database
     */
    public function storeDocument(UploadedFile $file, User $owner): array
    {
        if (!$file->isValid()) {
            throw new \Exception('Le fichier uploadé n\'est pas valide: ' . $file->getErrorMessage());
        }

        $originalFilename = $this->sanitizeFilename($file->getClientOriginalName());
        if (empty($originalFilename)) {
            throw new \Exception('Le nom du fichier est invalide.');
        }

        // Read file content into memory
        $filePath = $file->getRealPath();
        if ($filePath === false) {
            throw new \Exception('Impossible de lire le fichier uploadé.');
        }

        $fileContent = file_get_contents($filePath);
        if ($fileContent === false) {
            throw new \Exception('Impossible de lire le contenu du fichier.');
        }

        $detectedMimeType = $this->detectMimeType($filePath);

        return [
            'fileContent' => $fileContent,
            'originalName' => $originalFilename,
            'mimeType' => $detectedMimeType ?? ($file->getMimeType() ?? 'application/octet-stream'),
            'size' => max(0, (int) ($file->getSize() ?? 0)),
        ];
    }

    /**
     * Get file content from document
     */
    public function getDocumentContent(SharedDocument $document)
    {
        return $document->getFileContent();
    }

    /**
     * Delete a document (no filesystem cleanup needed)
     */
    public function deleteDocument(SharedDocument $document): bool
    {
        // No filesystem operations needed - deletion happens via ORM
        return true;
    }

    /**
     * Check if document has content
     */
    public function documentExists(SharedDocument $document): bool
    {
        return $document->getFileContent() !== null && $document->getFileContent() !== '';
    }

    /**
     * Get file size in human-readable format
     */
    public static function formatFileSize(int $bytes): string
    {
        $units = ['B', 'KB', 'MB', 'GB', 'TB'];
        $bytes = max($bytes, 0);
        $pow = floor(($bytes ? log($bytes) : 0) / log(1024));
        $pow = min($pow, count($units) - 1);
        $bytes /= (1 << (10 * $pow));

        return round($bytes, 2) . ' ' . $units[$pow];
    }

    /**
     * Get MIME type icon class for Bootstrap Icons
     */
    public static function getMimeTypeIcon(string $mimeType): string
    {
        return match (true) {
            strpos($mimeType, 'pdf') !== false => 'bi-file-pdf',
            strpos($mimeType, 'word') !== false || strpos($mimeType, 'document') !== false => 'bi-file-word',
            strpos($mimeType, 'excel') !== false || strpos($mimeType, 'spreadsheet') !== false => 'bi-file-excel',
            strpos($mimeType, 'image') !== false => 'bi-file-image',
            strpos($mimeType, 'text') !== false => 'bi-file-text',
            default => 'bi-file-earmark',
        };
    }

    /**
     * Validate file upload
     */
    public function validateFile(UploadedFile $file, int $maxSize = 52428800): array
    {
        $errors = [];

        $size = (int) ($file->getSize() ?? 0);

        if ($size <= 0) {
            $errors[] = 'Le fichier est vide ou illisible.';
            return $errors;
        }

        if ($size > $maxSize) {
            $errors[] = 'Le fichier est trop volumineux (' . self::formatFileSize($size) . ' > ' . self::formatFileSize($maxSize) . ')';
        }

        $mimeType = $this->detectMimeType((string) $file->getRealPath()) ?? ($file->getMimeType() ?? '');
        $extension = strtolower((string) $file->getClientOriginalExtension());

        if (!in_array($mimeType, self::ALLOWED_MIME_TYPES, true)) {
            $errors[] = 'Le type de fichier n\'est pas accepté';
        }

        if (!in_array($extension, self::ALLOWED_EXTENSIONS, true)) {
            $errors[] = 'L\'extension du fichier n\'est pas acceptée';
        }

        if (!$this->isExtensionCompatibleWithMimeType($extension, $mimeType)) {
            $errors[] = 'Le type MIME et l\'extension du fichier sont incohérents';
        }

        return $errors;
    }

    private function sanitizeFilename(string $filename): string
    {
        $filename = trim($filename);
        $filename = preg_replace('/[\x00-\x1F\x7F]/u', '', $filename) ?? '';
        $filename = preg_replace('/[^A-Za-z0-9._\-\s]/u', '_', $filename) ?? '';
        $filename = preg_replace('/\s+/', ' ', $filename) ?? '';

        return mb_substr($filename, 0, 255);
    }

    private function detectMimeType(string $filePath): ?string
    {
        if ($filePath === '' || !is_file($filePath)) {
            return null;
        }

        $finfo = new \finfo(\FILEINFO_MIME_TYPE);
        $detected = $finfo->file($filePath);

        return is_string($detected) && $detected !== '' ? $detected : null;
    }

    private function isExtensionCompatibleWithMimeType(string $extension, string $mimeType): bool
    {
        if ($extension === '' || $mimeType === '') {
            return false;
        }

        $mapping = [
            'pdf' => ['application/pdf'],
            'doc' => ['application/msword'],
            'docx' => ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
            'jpg' => ['image/jpeg'],
            'jpeg' => ['image/jpeg'],
            'png' => ['image/png'],
            'xls' => ['application/vnd.ms-excel'],
            'xlsx' => ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
        ];

        return in_array($mimeType, $mapping[$extension] ?? [], true);
    }
}
