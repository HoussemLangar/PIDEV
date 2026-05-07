<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260301223000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Align doctrine_migration_versions table collation with database default utf8mb4_unicode_ci';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE doctrine_migration_versions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE doctrine_migration_versions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci');
    }
}
