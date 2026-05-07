<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260301221500 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Align database default collation with table collation and keep timezone-aware soft delete mapping';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci');
    }
}
