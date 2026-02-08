<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260206213000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add subscription fields to users';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("ALTER TABLE users ADD subscription_status VARCHAR(20) DEFAULT 'PENDING' NOT NULL, ADD subscription_type VARCHAR(50) DEFAULT NULL, ADD subscription_end_at DATETIME DEFAULT NULL");
        $this->addSql('CREATE INDEX IDX_1483A5E9D77B6B73 ON users (subscription_status)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX IDX_1483A5E9D77B6B73 ON users');
        $this->addSql('ALTER TABLE users DROP subscription_status, DROP subscription_type, DROP subscription_end_at');
    }
}
