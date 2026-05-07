<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Migration: Move documents from disk storage to database
 */
final class Version20260207MigrateDocumentsToDatabase extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add file_content column to store files in database and remove file_path dependency';
    }

    public function up(Schema $schema): void
    {
        // Add file_content column to store binary file data
        $this->addSql('ALTER TABLE shared_documents ADD file_content LONGBLOB NOT NULL AFTER file_size');
    }

    public function down(Schema $schema): void
    {
        // Remove file_content column
        $this->addSql('ALTER TABLE shared_documents DROP COLUMN file_content');
    }
}
