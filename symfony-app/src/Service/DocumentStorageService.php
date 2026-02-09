<?php

namespace App\Service;

use App\Entity\SharedDocument;
use App\Entity\User;
use Symfony\Component\HttpFoundation\File\UploadedFile;

class DocumentStorageService
{
    /**
     * Store an uploaded document's content in the database
     */
    public function storeDocument(UploadedFile $file, User $owner): array
    {
        if (!$file->isValid()) {
            throw new \Exception('Le fichier uploadé n\'est pas valide: ' . $file->getErrorMessage());
        }

        $originalFilename = $file->getClientOriginalName();
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

        return [
            'fileContent' => $fileContent,
            'originalName' => $originalFilename,
            'mimeType' => $file->getMimeType() ?? 'application/octet-stream',
            'size' => $file->getSize(),
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

        if ($file->getSize() > $maxSize) {
            $errors[] = 'Le fichier est trop volumineux (' . self::formatFileSize($file->getSize()) . ' > ' . self::formatFileSize($maxSize) . ')';
        }

        $allowedMimeTypes = [
            'application/pdf',
            'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'image/jpeg',
            'image/png',
            'application/vnd.ms-excel',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        ];

        if (!in_array($file->getMimeType() ?? '', $allowedMimeTypes)) {
            $errors[] = 'Le type de fichier n\'est pas accepté';
        }

        return $errors;
    }
}
