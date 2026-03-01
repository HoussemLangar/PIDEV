<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Document sharing system
 */
final class Version20260206DocumentSharing extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create document sharing tables for file management system';
    }

    public function up(Schema $schema): void
    {
        // Create shared_documents table
        $this->addSql('CREATE TABLE shared_documents (
            id INT AUTO_INCREMENT NOT NULL,
            owner_id INT NOT NULL,
            file_name VARCHAR(255) NOT NULL,
            file_path VARCHAR(255) NOT NULL,
            mime_type VARCHAR(50) NOT NULL,
            file_size INT NOT NULL,
            description LONGTEXT,
            uploaded_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            document_type VARCHAR(50) NOT NULL DEFAULT "other",
            is_public TINYINT(1) DEFAULT 0,
            PRIMARY KEY(id),
            KEY IDX_SHARED_DOCUMENTS_OWNER (owner_id),
            CONSTRAINT FK_SHARED_DOCUMENTS_OWNER FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');

        // Create document_accesses table
        $this->addSql('CREATE TABLE document_accesses (
            id INT AUTO_INCREMENT NOT NULL,
            document_id INT NOT NULL,
            shared_with_id INT NOT NULL,
            shared_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            expires_at DATETIME NULL COMMENT \'(DC2Type:datetime_immutable)\',
            accessed_at DATETIME NULL COMMENT \'(DC2Type:datetime_immutable)\',
            access_count INT NOT NULL DEFAULT 0,
            is_active TINYINT(1) DEFAULT 1,
            permission VARCHAR(50) NOT NULL DEFAULT "view",
            PRIMARY KEY(id),
            KEY IDX_DOCUMENT_ACCESSES_DOCUMENT (document_id),
            KEY IDX_DOCUMENT_ACCESSES_SHARED_WITH (shared_with_id),
            CONSTRAINT FK_DOCUMENT_ACCESSES_DOCUMENT FOREIGN KEY (document_id) REFERENCES shared_documents (id) ON DELETE CASCADE,
            CONSTRAINT FK_DOCUMENT_ACCESSES_SHARED_WITH FOREIGN KEY (shared_with_id) REFERENCES users (id) ON DELETE CASCADE
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS document_accesses');
        $this->addSql('DROP TABLE IF EXISTS shared_documents');
    }
}
