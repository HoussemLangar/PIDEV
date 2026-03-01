<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260301051339 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE document_access (id INT AUTO_INCREMENT NOT NULL, shared_at DATETIME NOT NULL, expires_at DATETIME DEFAULT NULL, accessed_at DATETIME DEFAULT NULL, access_count INT DEFAULT 0 NOT NULL, is_active TINYINT(1) DEFAULT 1 NOT NULL, permission VARCHAR(50) DEFAULT \'view\' NOT NULL, document_id INT NOT NULL, shared_with_id INT NOT NULL, INDEX IDX_B80B9A32C33F7837 (document_id), INDEX IDX_B80B9A32D14FE63F (shared_with_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE google_fit_account (id INT AUTO_INCREMENT NOT NULL, google_account_id VARCHAR(255) NOT NULL, access_token LONGTEXT NOT NULL, refresh_token LONGTEXT DEFAULT NULL, token_expiration DATETIME DEFAULT NULL, last_sync_at DATETIME DEFAULT NULL, scopes LONGTEXT DEFAULT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_1596F27AA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE health_risk_prediction (id INT AUTO_INCREMENT NOT NULL, prediction_date DATE NOT NULL, risk_htn DOUBLE PRECISION NOT NULL, risk_diabetes DOUBLE PRECISION NOT NULL, risk_depression DOUBLE PRECISION NOT NULL, risk_respiratory DOUBLE PRECISION NOT NULL, level_htn VARCHAR(20) DEFAULT \'LOW\' NOT NULL, level_diabetes VARCHAR(20) DEFAULT \'LOW\' NOT NULL, level_depression VARCHAR(20) DEFAULT \'LOW\' NOT NULL, level_respiratory VARCHAR(20) DEFAULT \'LOW\' NOT NULL, explanations_json JSON NOT NULL, feature_snapshot JSON DEFAULT NULL, updated_at DATETIME NOT NULL, user_id INT NOT NULL, INDEX IDX_814B912CA76ED395 (user_id), UNIQUE INDEX uniq_health_risk_user_day (user_id, prediction_date), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE password_reset_token (id INT AUTO_INCREMENT NOT NULL, token VARCHAR(64) NOT NULL, expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL, is_used TINYINT(1) NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_6B7BA4B65F37A13B (token), INDEX IDX_6B7BA4B6A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE shared_document (id INT AUTO_INCREMENT NOT NULL, file_name VARCHAR(255) NOT NULL, file_path VARCHAR(255) NOT NULL, mime_type VARCHAR(255) NOT NULL, file_size INT NOT NULL, file_content LONGTEXT DEFAULT NULL, description LONGTEXT DEFAULT NULL, uploaded_at DATETIME NOT NULL, document_type VARCHAR(50) NOT NULL, is_public TINYINT(1) DEFAULT 0 NOT NULL, owner_id INT NOT NULL, INDEX IDX_8D4D7B557E3C61F9 (owner_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE suspicious_login (id INT AUTO_INCREMENT NOT NULL, ip_address VARCHAR(64) DEFAULT NULL, user_agent VARCHAR(255) DEFAULT NULL, country VARCHAR(64) DEFAULT NULL, reason VARCHAR(100) NOT NULL, blocked TINYINT(1) DEFAULT 1 NOT NULL, notified TINYINT(1) DEFAULT 0 NOT NULL, created_at DATETIME NOT NULL, user_id INT NOT NULL, INDEX IDX_7913D681A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('ALTER TABLE document_access ADD CONSTRAINT FK_B80B9A32C33F7837 FOREIGN KEY (document_id) REFERENCES shared_document (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE document_access ADD CONSTRAINT FK_B80B9A32D14FE63F FOREIGN KEY (shared_with_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE google_fit_account ADD CONSTRAINT FK_1596F27AA76ED395 FOREIGN KEY (user_id) REFERENCES users (id)');
        $this->addSql('ALTER TABLE health_risk_prediction ADD CONSTRAINT FK_814B912CA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE password_reset_token ADD CONSTRAINT FK_6B7BA4B6A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE shared_document ADD CONSTRAINT FK_8D4D7B557E3C61F9 FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE suspicious_login ADD CONSTRAINT FK_7913D681A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE document_accesses DROP FOREIGN KEY FK_DOCUMENT_ACCESSES_SHARED_WITH');
        $this->addSql('ALTER TABLE document_accesses DROP FOREIGN KEY FK_DOCUMENT_ACCESSES_DOCUMENT');
        $this->addSql('ALTER TABLE google_fit_accounts DROP FOREIGN KEY FK_ABCBC1DBA76ED395');
        $this->addSql('ALTER TABLE health_risk_predictions DROP FOREIGN KEY FK_7FDCD3D5A76ED395');
        $this->addSql('ALTER TABLE password_reset_tokens DROP FOREIGN KEY FK_3967A216A76ED395');
        $this->addSql('ALTER TABLE shared_documents DROP FOREIGN KEY FK_SHARED_DOCUMENTS_OWNER');
        $this->addSql('ALTER TABLE suspicious_logins DROP FOREIGN KEY FK_1C7F61F0A76ED395');
        $this->addSql('DROP TABLE document_accesses');
        $this->addSql('DROP TABLE google_fit_accounts');
        $this->addSql('DROP TABLE health_risk_predictions');
        $this->addSql('DROP TABLE password_reset_tokens');
        $this->addSql('DROP TABLE shared_documents');
        $this->addSql('DROP TABLE suspicious_logins');
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('ALTER TABLE sante_quotidienne CHANGE poids poids DOUBLE PRECISION DEFAULT NULL, CHANGE taille taille DOUBLE PRECISION DEFAULT NULL, CHANGE humeur humeur JSON NOT NULL, CHANGE date date DATETIME DEFAULT NULL, CHANGE calories calories INT DEFAULT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE teleconsultation RENAME INDEX idx_b9a78c027db3b714 TO IDX_CE327B237DB3B714');
        $this->addSql('ALTER TABLE teleconsultation RENAME INDEX idx_b9a78c02e92f8f78 TO IDX_CE327B23E92F8F78');
        $this->addSql('ALTER TABLE user_session RENAME INDEX uniq_7aed7913613fecdf TO UNIQ_8849CBDE613FECDF');
        $this->addSql('ALTER TABLE user_session RENAME INDEX idx_7aed7913a76ed395 TO IDX_8849CBDEA76ED395');
        $this->addSql('ALTER TABLE users CHANGE avatar_data avatar_data LONGTEXT DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE document_accesses (id INT AUTO_INCREMENT NOT NULL, document_id INT NOT NULL, shared_with_id INT NOT NULL, shared_at DATETIME NOT NULL, expires_at DATETIME DEFAULT NULL, accessed_at DATETIME DEFAULT NULL, access_count INT DEFAULT 0 NOT NULL, is_active TINYINT(1) DEFAULT 1 NOT NULL, permission VARCHAR(50) CHARACTER SET utf8mb4 DEFAULT \'view\' NOT NULL COLLATE `utf8mb4_unicode_ci`, INDEX IDX_6DF53BBBC33F7837 (document_id), INDEX IDX_6DF53BBBD14FE63F (shared_with_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('CREATE TABLE google_fit_accounts (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, google_account_id VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, access_token LONGTEXT CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, refresh_token LONGTEXT CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, token_expiration DATETIME DEFAULT NULL, last_sync_at DATETIME DEFAULT NULL, scopes LONGTEXT CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, UNIQUE INDEX UNIQ_ABCBC1DBA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('CREATE TABLE health_risk_predictions (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, prediction_date DATE NOT NULL, risk_htn DOUBLE PRECISION NOT NULL, risk_diabetes DOUBLE PRECISION NOT NULL, risk_depression DOUBLE PRECISION NOT NULL, risk_respiratory DOUBLE PRECISION NOT NULL, level_htn VARCHAR(20) CHARACTER SET utf8mb4 DEFAULT \'LOW\' NOT NULL COLLATE `utf8mb4_unicode_ci`, level_diabetes VARCHAR(20) CHARACTER SET utf8mb4 DEFAULT \'LOW\' NOT NULL COLLATE `utf8mb4_unicode_ci`, level_depression VARCHAR(20) CHARACTER SET utf8mb4 DEFAULT \'LOW\' NOT NULL COLLATE `utf8mb4_unicode_ci`, level_respiratory VARCHAR(20) CHARACTER SET utf8mb4 DEFAULT \'LOW\' NOT NULL COLLATE `utf8mb4_unicode_ci`, explanations_json JSON NOT NULL, feature_snapshot JSON DEFAULT NULL, updated_at DATETIME NOT NULL, INDEX IDX_2957E879A76ED395 (user_id), UNIQUE INDEX uniq_health_risk_user_day (user_id, prediction_date), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('CREATE TABLE password_reset_tokens (id INT AUTO_INCREMENT NOT NULL, token VARCHAR(64) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL, is_used TINYINT(1) NOT NULL, user_id INT NOT NULL, INDEX IDX_3967A216A76ED395 (user_id), UNIQUE INDEX UNIQ_3967A2165F37A13B (token), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('CREATE TABLE shared_documents (id INT AUTO_INCREMENT NOT NULL, owner_id INT NOT NULL, file_name VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, file_path VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, mime_type VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, file_size INT NOT NULL, file_content LONGBLOB NOT NULL, description LONGTEXT CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, uploaded_at DATETIME NOT NULL, document_type VARCHAR(50) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, is_public TINYINT(1) DEFAULT 0 NOT NULL, INDEX IDX_82270B7E3C61F9 (owner_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('CREATE TABLE suspicious_logins (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, ip_address VARCHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, user_agent VARCHAR(255) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, country VARCHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL COLLATE `utf8mb4_unicode_ci`, reason VARCHAR(100) CHARACTER SET utf8mb4 NOT NULL COLLATE `utf8mb4_unicode_ci`, blocked TINYINT(1) DEFAULT 1 NOT NULL, notified TINYINT(1) DEFAULT 0 NOT NULL, created_at DATETIME NOT NULL, INDEX IDX_81C86F6BA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB COMMENT = \'\' ');
        $this->addSql('ALTER TABLE document_accesses ADD CONSTRAINT FK_DOCUMENT_ACCESSES_SHARED_WITH FOREIGN KEY (shared_with_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE document_accesses ADD CONSTRAINT FK_DOCUMENT_ACCESSES_DOCUMENT FOREIGN KEY (document_id) REFERENCES shared_documents (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE google_fit_accounts ADD CONSTRAINT FK_ABCBC1DBA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE NO ACTION');
        $this->addSql('ALTER TABLE health_risk_predictions ADD CONSTRAINT FK_7FDCD3D5A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE password_reset_tokens ADD CONSTRAINT FK_3967A216A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE shared_documents ADD CONSTRAINT FK_SHARED_DOCUMENTS_OWNER FOREIGN KEY (owner_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE suspicious_logins ADD CONSTRAINT FK_1C7F61F0A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE document_access DROP FOREIGN KEY FK_B80B9A32C33F7837');
        $this->addSql('ALTER TABLE document_access DROP FOREIGN KEY FK_B80B9A32D14FE63F');
        $this->addSql('ALTER TABLE google_fit_account DROP FOREIGN KEY FK_1596F27AA76ED395');
        $this->addSql('ALTER TABLE health_risk_prediction DROP FOREIGN KEY FK_814B912CA76ED395');
        $this->addSql('ALTER TABLE password_reset_token DROP FOREIGN KEY FK_6B7BA4B6A76ED395');
        $this->addSql('ALTER TABLE shared_document DROP FOREIGN KEY FK_8D4D7B557E3C61F9');
        $this->addSql('ALTER TABLE suspicious_login DROP FOREIGN KEY FK_7913D681A76ED395');
        $this->addSql('DROP TABLE document_access');
        $this->addSql('DROP TABLE google_fit_account');
        $this->addSql('DROP TABLE health_risk_prediction');
        $this->addSql('DROP TABLE password_reset_token');
        $this->addSql('DROP TABLE shared_document');
        $this->addSql('DROP TABLE suspicious_login');
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('ALTER TABLE sante_quotidienne CHANGE poids poids DOUBLE PRECISION NOT NULL, CHANGE taille taille DOUBLE PRECISION NOT NULL, CHANGE humeur humeur LONGTEXT NOT NULL, CHANGE calories calories DOUBLE PRECISION DEFAULT NULL, CHANGE date date DATETIME NOT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE NO ACTION');
        $this->addSql('ALTER TABLE teleconsultation RENAME INDEX idx_ce327b237db3b714 TO IDX_B9A78C027DB3B714');
        $this->addSql('ALTER TABLE teleconsultation RENAME INDEX idx_ce327b23e92f8f78 TO IDX_B9A78C02E92F8F78');
        $this->addSql('ALTER TABLE user_session RENAME INDEX idx_8849cbdea76ed395 TO IDX_7AED7913A76ED395');
        $this->addSql('ALTER TABLE user_session RENAME INDEX uniq_8849cbde613fecdf TO UNIQ_7AED7913613FECDF');
        $this->addSql('ALTER TABLE users CHANGE avatar_data avatar_data LONGBLOB DEFAULT NULL');
    }
}
