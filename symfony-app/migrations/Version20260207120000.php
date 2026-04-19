<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260207120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add MFA fields, user sessions, and suspicious logins';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD mfa_enabled TINYINT(1) DEFAULT 0 NOT NULL, ADD google_authenticator_secret VARCHAR(255) DEFAULT NULL');
        $this->addSql('CREATE TABLE user_sessions (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, session_id VARCHAR(128) NOT NULL, ip_address VARCHAR(64) DEFAULT NULL, user_agent VARCHAR(255) DEFAULT NULL, country VARCHAR(64) DEFAULT NULL, created_at DATETIME NOT NULL, last_activity_at DATETIME NOT NULL, revoked_at DATETIME DEFAULT NULL, UNIQUE INDEX UNIQ_7D3D564C8F344C7B (session_id), INDEX IDX_7D3D564CA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        $this->addSql('CREATE TABLE suspicious_logins (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, ip_address VARCHAR(64) DEFAULT NULL, user_agent VARCHAR(255) DEFAULT NULL, country VARCHAR(64) DEFAULT NULL, reason VARCHAR(100) NOT NULL, blocked TINYINT(1) DEFAULT 1 NOT NULL, notified TINYINT(1) DEFAULT 0 NOT NULL, created_at DATETIME NOT NULL, INDEX IDX_1C7F61F0A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        $this->addSql('ALTER TABLE user_sessions ADD CONSTRAINT FK_7D3D564CA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE suspicious_logins ADD CONSTRAINT FK_1C7F61F0A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE user_sessions DROP FOREIGN KEY FK_7D3D564CA76ED395');
        $this->addSql('ALTER TABLE suspicious_logins DROP FOREIGN KEY FK_1C7F61F0A76ED395');
        $this->addSql('DROP TABLE user_sessions');
        $this->addSql('DROP TABLE suspicious_logins');
        $this->addSql('ALTER TABLE users DROP mfa_enabled, DROP google_authenticator_secret');
    }
}
