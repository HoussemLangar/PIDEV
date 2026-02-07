<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260206233000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add email verification and admin approval to users';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("ALTER TABLE users ADD email_verified TINYINT(1) DEFAULT 0 NOT NULL, ADD admin_approved TINYINT(1) DEFAULT 0 NOT NULL, ADD email_verification_token VARCHAR(255) DEFAULT NULL, ADD email_verification_expires_at DATETIME DEFAULT NULL");
        $this->addSql('CREATE INDEX IDX_1483A5E9FBFEBCCE ON users (email_verified)');
        $this->addSql('CREATE INDEX IDX_1483A5E9F7E2F7E1 ON users (admin_approved)');
        $this->addSql('CREATE INDEX IDX_1483A5E98FB9F1F7 ON users (email_verification_token)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX IDX_1483A5E9FBFEBCCE ON users');
        $this->addSql('DROP INDEX IDX_1483A5E9F7E2F7E1 ON users');
        $this->addSql('DROP INDEX IDX_1483A5E98FB9F1F7 ON users');
        $this->addSql('ALTER TABLE users DROP email_verified, DROP admin_approved, DROP email_verification_token, DROP email_verification_expires_at');
    }
}
