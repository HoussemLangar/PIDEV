<?php

namespace App\Twig;

use App\Service\DocumentStorageService;
use Twig\Extension\AbstractExtension;
use Twig\TwigFilter;

class DocumentExtension extends AbstractExtension
{
    public function getFilters(): array
    {
        return [
            new TwigFilter('mime_icon', [$this, 'getMimeIcon']),
        ];
    }

    public function getMimeIcon(string $mimeType): string
    {
        return DocumentStorageService::getMimeTypeIcon($mimeType);
    }
}
