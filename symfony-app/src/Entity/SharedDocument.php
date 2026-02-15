<?php

namespace App\Entity;

use App\Repository\SharedDocumentRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: SharedDocumentRepository::class)]
#[ORM\Table(name: 'shared_documents')]
class SharedDocument
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(name: 'owner_id', nullable: false, onDelete: 'CASCADE')]
    private User $owner;

    #[ORM\Column(type: 'string', length: 255)]
    #[Assert\NotBlank(message: "Le nom du document est obligatoire", groups: ['post_upload'])]
    private string $fileName;

    #[ORM\Column(type: 'string', length: 255)]
    private string $filePath;

    #[ORM\Column(type: 'string', length: 255)]
    private string $mimeType;

    #[ORM\Column(type: 'integer')]
    private int $fileSize;

    #[ORM\Column(type: Types::BLOB)]
    private $fileContent;

    #[ORM\Column(type: Types::TEXT, nullable: true)]
    #[Assert\Length(max: 2000, maxMessage: "La description ne peut pas dépasser {{ limit }} caractères")]
    private ?string $description = null;

    #[ORM\Column(type: Types::DATETIME_IMMUTABLE)]
    private \DateTimeImmutable $uploadedAt;

    #[ORM\Column(type: 'string', length: 50)]
    #[Assert\NotBlank(message: "Le type de document est obligatoire")]
    private string $documentType = 'other'; // analysis, prescription, report, etc.

    #[ORM\Column(type: 'boolean', options: ['default' => 0], name: 'is_public')]
    private bool $public = false;

    #[ORM\OneToMany(mappedBy: 'document', targetEntity: DocumentAccess::class, cascade: ['persist', 'remove'])]
    private Collection $accesses;

    public function __construct()
    {
        $this->uploadedAt = new \DateTimeImmutable();
        $this->accesses = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getOwner(): User
    {
        return $this->owner;
    }

    public function setOwner(User $owner): self
    {
        $this->owner = $owner;
        return $this;
    }

    public function getFileName(): string
    {
        return $this->fileName;
    }

    public function setFileName(string $fileName): self
    {
        $this->fileName = $fileName;
        return $this;
    }

    public function getFilePath(): string
    {
        return $this->filePath;
    }

    public function setFilePath(string $filePath): self
    {
        $this->filePath = $filePath;
        return $this;
    }

    public function getMimeType(): string
    {
        return $this->mimeType;
    }

    public function setMimeType(string $mimeType): self
    {
        $this->mimeType = $mimeType;
        return $this;
    }

    public function getFileSize(): int
    {
        return $this->fileSize;
    }

    public function setFileSize(int $fileSize): self
    {
        $this->fileSize = $fileSize;
        return $this;
    }

    public function getFileContent()
    {
        return $this->fileContent;
    }

    public function setFileContent($fileContent): self
    {
        $this->fileContent = $fileContent;
        return $this;
    }

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): self
    {
        $this->description = $description;
        return $this;
    }

    public function getUploadedAt(): \DateTimeImmutable
    {
        return $this->uploadedAt;
    }

    public function getDocumentType(): string
    {
        return $this->documentType;
    }

    public function setDocumentType(string $documentType): self
    {
        $this->documentType = $documentType;
        return $this;
    }

    public function isPublic(): bool
    {
        return $this->public;
    }

    public function setPublic(bool $public): self
    {
        $this->public = $public;
        return $this;
    }

    public function getAccesses(): Collection
    {
        return $this->accesses;
    }

    public function addAccess(DocumentAccess $access): self
    {
        if (!$this->accesses->contains($access)) {
            $this->accesses->add($access);
            $access->setDocument($this);
        }
        return $this;
    }

    public function removeAccess(DocumentAccess $access): self
    {
        if ($this->accesses->removeElement($access)) {
            if ($access->getDocument() === $this) {
                $access->setDocument(null);
            }
        }
        return $this;
    }

    public function hasUserAccess(User $user): bool
    {
        if ($user === $this->owner || $this->public) {
            return true;
        }

        return $this->accesses->filter(function (DocumentAccess $access) use ($user) {
            return $access->getSharedWith() === $user && $access->canAccess();
        })->count() > 0;
    }

    public function getFileSizeFormatted(): string
    {
        $bytes = $this->fileSize;
        $units = ['B', 'KB', 'MB', 'GB'];
        $bytes = max($bytes, 0);
        $pow = floor(($bytes ? log($bytes) : 0) / log(1024));
        $pow = min($pow, count($units) - 1);
        $bytes /= (1 << (10 * $pow));

        return round($bytes, 2) . ' ' . $units[$pow];
    }
}
