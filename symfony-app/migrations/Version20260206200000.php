<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260206200000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add ban and soft delete fields to users';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD is_banned TINYINT(1) DEFAULT 0 NOT NULL, ADD ban_reason VARCHAR(255) DEFAULT NULL, ADD ban_until DATETIME DEFAULT NULL, ADD deleted_at DATETIME DEFAULT NULL');
        $this->addSql('CREATE INDEX IDX_1483A5E9A31C1D7F ON users (is_banned)');
        $this->addSql('CREATE INDEX IDX_1483A5E93A8C5C4 ON users (deleted_at)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX IDX_1483A5E9A31C1D7F ON users');
        $this->addSql('DROP INDEX IDX_1483A5E93A8C5C4 ON users');
        $this->addSql('ALTER TABLE users DROP is_banned, DROP ban_reason, DROP ban_until, DROP deleted_at');
    }
}
