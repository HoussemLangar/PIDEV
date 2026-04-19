<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260208090000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add email verification expiration to users';
    }

    public function up(Schema $schema): void
    {
        $schemaManager = $this->connection->createSchemaManager();
        $columns = $schemaManager->listTableColumns('users');

        if (!isset($columns['email_verification_expires_at'])) {
            $this->addSql('ALTER TABLE users ADD email_verification_expires_at DATETIME DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $schemaManager = $this->connection->createSchemaManager();
        $columns = $schemaManager->listTableColumns('users');

        if (isset($columns['email_verification_expires_at'])) {
            $this->addSql('ALTER TABLE users DROP email_verification_expires_at');
        }
    }
}
