<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260208IncreaseMimeTypeLength extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Increase mime_type length on shared_documents to 255';
    }

    public function up(Schema $schema): void
    {
        // MySQL syntax used in this project migrations
        $this->addSql('ALTER TABLE shared_documents MODIFY mime_type VARCHAR(255) NOT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE shared_documents MODIFY mime_type VARCHAR(50) NOT NULL');
    }
}
