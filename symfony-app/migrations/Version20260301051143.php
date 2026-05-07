<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260301051143 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE teleconsultation (id INT AUTO_INCREMENT NOT NULL, room_name VARCHAR(255) NOT NULL, description LONGTEXT DEFAULT NULL, scheduled_at DATETIME NOT NULL, started_at DATETIME DEFAULT NULL, ended_at DATETIME DEFAULT NULL, status VARCHAR(50) DEFAULT \'pending\' NOT NULL, duration_seconds INT DEFAULT NULL, type VARCHAR(50) DEFAULT NULL, created_at DATETIME NOT NULL, initiator_id INT NOT NULL, recipient_id INT NOT NULL, INDEX IDX_CE327B237DB3B714 (initiator_id), INDEX IDX_CE327B23E92F8F78 (recipient_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE user_session (id INT AUTO_INCREMENT NOT NULL, session_id VARCHAR(128) NOT NULL, ip_address VARCHAR(64) DEFAULT NULL, user_agent VARCHAR(255) DEFAULT NULL, country VARCHAR(64) DEFAULT NULL, created_at DATETIME NOT NULL, last_activity_at DATETIME NOT NULL, revoked_at DATETIME DEFAULT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_8849CBDE613FECDF (session_id), INDEX IDX_8849CBDEA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('ALTER TABLE teleconsultation ADD CONSTRAINT FK_CE327B237DB3B714 FOREIGN KEY (initiator_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE teleconsultation ADD CONSTRAINT FK_CE327B23E92F8F78 FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE user_session ADD CONSTRAINT FK_8849CBDEA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE teleconsultations DROP FOREIGN KEY FK_TELECONSULTATIONS_RECIPIENT');
        $this->addSql('ALTER TABLE teleconsultations DROP FOREIGN KEY FK_TELECONSULTATIONS_INITIATOR');
        $this->addSql('ALTER TABLE user_sessions DROP FOREIGN KEY FK_7D3D564CA76ED395');
        $this->addSql('DROP TABLE teleconsultations');
        $this->addSql('DROP TABLE user_sessions');
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('ALTER TABLE sante_quotidienne CHANGE poids poids DOUBLE PRECISION DEFAULT NULL, CHANGE taille taille DOUBLE PRECISION DEFAULT NULL, CHANGE humeur humeur JSON NOT NULL, CHANGE date date DATETIME DEFAULT NULL, CHANGE calories calories INT DEFAULT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE shared_documents CHANGE file_content file_content LONGTEXT DEFAULT NULL');
        $this->addSql('ALTER TABLE users CHANGE avatar_data avatar_data LONGTEXT DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE teleconsultations (id INT AUTO_INCREMENT NOT NULL, initiator_id INT NOT NULL, recipient_id INT NOT NULL, room_name VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, description LONGTEXT CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, scheduled_at DATETIME NOT NULL, started_at DATETIME DEFAULT NULL, ended_at DATETIME DEFAULT NULL, status VARCHAR(50) CHARACTER SET utf8mb4 DEFAULT \'pending\' NOT NULL COLLATE `utf8mb4_unicode_ci`, duration_seconds INT DEFAULT NULL, type VARCHAR(50) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, created_at DATETIME NOT NULL, INDEX IDX_B9A78C027DB3B714 (initiator_id), INDEX IDX_B9A78C02E92F8F78 (recipient_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('CREATE TABLE user_sessions (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, session_id VARCHAR(128) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, ip_address VARCHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, user_agent VARCHAR(255) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, country VARCHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, created_at DATETIME NOT NULL, last_activity_at DATETIME NOT NULL, revoked_at DATETIME DEFAULT NULL, INDEX IDX_7AED7913A76ED395 (user_id), UNIQUE INDEX UNIQ_7AED7913613FECDF (session_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('ALTER TABLE teleconsultations ADD CONSTRAINT FK_TELECONSULTATIONS_RECIPIENT FOREIGN KEY (recipient_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE teleconsultations ADD CONSTRAINT FK_TELECONSULTATIONS_INITIATOR FOREIGN KEY (initiator_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE user_sessions ADD CONSTRAINT FK_7D3D564CA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE teleconsultation DROP FOREIGN KEY FK_CE327B237DB3B714');
        $this->addSql('ALTER TABLE teleconsultation DROP FOREIGN KEY FK_CE327B23E92F8F78');
        $this->addSql('ALTER TABLE user_session DROP FOREIGN KEY FK_8849CBDEA76ED395');
        $this->addSql('DROP TABLE teleconsultation');
        $this->addSql('DROP TABLE user_session');
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('ALTER TABLE sante_quotidienne CHANGE poids poids DOUBLE PRECISION NOT NULL, CHANGE taille taille DOUBLE PRECISION NOT NULL, CHANGE humeur humeur LONGTEXT NOT NULL, CHANGE calories calories DOUBLE PRECISION DEFAULT NULL, CHANGE date date DATETIME NOT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE NO ACTION');
        $this->addSql('ALTER TABLE shared_documents CHANGE file_content file_content LONGBLOB NOT NULL');
        $this->addSql('ALTER TABLE users CHANGE avatar_data avatar_data LONGBLOB DEFAULT NULL');
    }
}
