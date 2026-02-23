<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260223003350 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE article_scores (score_article DOUBLE PRECISION DEFAULT 0 NOT NULL, nb_commentaires INT DEFAULT 0 NOT NULL, updated_at DATETIME NOT NULL, contenu_id INT NOT NULL, PRIMARY KEY(contenu_id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE reservations_medicaments (id INT AUTO_INCREMENT NOT NULL, quantite INT DEFAULT 1 NOT NULL, prix_unitaire NUMERIC(10, 2) DEFAULT NULL, prix_total NUMERIC(10, 2) DEFAULT NULL, statut VARCHAR(20) DEFAULT \'en_attente\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, expires_at DATETIME DEFAULT NULL, confirmed_at DATETIME DEFAULT NULL, cancelled_at DATETIME DEFAULT NULL, rejected_at DATETIME DEFAULT NULL, patient_id INT NOT NULL, pharmacie_id INT NOT NULL, medicament_id INT NOT NULL, stock_id INT NOT NULL, INDEX IDX_C8F0BB396B899279 (patient_id), INDEX IDX_C8F0BB39BC6D351B (pharmacie_id), INDEX IDX_C8F0BB39AB0D61F7 (medicament_id), INDEX IDX_C8F0BB39DCD6110 (stock_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('ALTER TABLE article_scores ADD CONSTRAINT FK_6D97A0DE3C1CC488 FOREIGN KEY (contenu_id) REFERENCES contenu (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_C8F0BB396B899279 FOREIGN KEY (patient_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_C8F0BB39BC6D351B FOREIGN KEY (pharmacie_id) REFERENCES pharmacies (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_C8F0BB39AB0D61F7 FOREIGN KEY (medicament_id) REFERENCES medicaments (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_C8F0BB39DCD6110 FOREIGN KEY (stock_id) REFERENCES stock_pharmacies (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE abonnements RENAME INDEX idx_9f79d15ba76ed395 TO IDX_4788B767A76ED395');
        $this->addSql('ALTER TABLE accompaniment_plans CHANGE start_date start_date DATE NOT NULL, CHANGE end_date end_date DATE DEFAULT NULL, CHANGE created_at created_at DATETIME NOT NULL, CHANGE updated_at updated_at DATETIME NOT NULL');
        $this->addSql('ALTER TABLE accompaniment_plans RENAME INDEX idx_accompaniment_plans_patient TO IDX_700968A06B899279');
        $this->addSql('ALTER TABLE accompaniment_plans RENAME INDEX idx_accompaniment_plans_coach TO IDX_700968A03C105691');
        $this->addSql('ALTER TABLE accompaniment_plans RENAME INDEX idx_accompaniment_plans_nutritionist TO IDX_700968A0BE035A4B');
        $this->addSql('ALTER TABLE conversations CHANGE created_at created_at DATETIME NOT NULL, CHANGE last_message_at last_message_at DATETIME NOT NULL');
        $this->addSql('ALTER TABLE conversations RENAME INDEX idx_conversation_user_one TO IDX_C2521BF19EC8D52E');
        $this->addSql('ALTER TABLE conversations RENAME INDEX idx_conversation_user_two TO IDX_C2521BF1F59432E1');
        $this->addSql('ALTER TABLE document_accesses CHANGE shared_at shared_at DATETIME NOT NULL, CHANGE expires_at expires_at DATETIME DEFAULT NULL, CHANGE accessed_at accessed_at DATETIME DEFAULT NULL, CHANGE is_active is_active TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE document_accesses RENAME INDEX idx_document_accesses_document TO IDX_6DF53BBBC33F7837');
        $this->addSql('ALTER TABLE document_accesses RENAME INDEX idx_document_accesses_shared_with TO IDX_6DF53BBBD14FE63F');
        $this->addSql('ALTER TABLE factures CHANGE created_at created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL');
        $this->addSql('ALTER TABLE factures RENAME INDEX uniq_5b2ba6f7f55ae19 TO UNIQ_647590BF55AE19E');
        $this->addSql('ALTER TABLE factures RENAME INDEX idx_5b2ba6f7a76ed395 TO IDX_647590BA76ED395');
        $this->addSql('ALTER TABLE factures RENAME INDEX idx_5b2ba6f7d5c7abf2 TO IDX_647590BF1D74413');
        $this->addSql('ALTER TABLE google_fit_accounts CHANGE id id INT AUTO_INCREMENT NOT NULL, ADD PRIMARY KEY (id)');
        $this->addSql('ALTER TABLE google_fit_accounts ADD CONSTRAINT FK_ABCBC1DBA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_ABCBC1DBA76ED395 ON google_fit_accounts (user_id)');
        $this->addSql('ALTER TABLE medicaments ADD code_barre VARCHAR(120) DEFAULT NULL');
        $this->addSql('ALTER TABLE messages CHANGE created_at created_at DATETIME NOT NULL, CHANGE read_at read_at DATETIME DEFAULT NULL, CHANGE is_read is_read TINYINT(1) DEFAULT 0 NOT NULL');
        $this->addSql('ALTER TABLE messages RENAME INDEX idx_message_conversation TO IDX_DB021E969AC0396');
        $this->addSql('ALTER TABLE messages RENAME INDEX idx_message_sender TO IDX_DB021E96F624B39D');
        $this->addSql('ALTER TABLE messages RENAME INDEX idx_message_recipient TO IDX_DB021E96E92F8F78');
        $this->addSql('ALTER TABLE pharmacies ADD latitude NUMERIC(10, 6) DEFAULT NULL, ADD longitude NUMERIC(10, 6) DEFAULT NULL, ADD is_active TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE plans_exercices RENAME INDEX idx_plans_exercices_accompaniment_plan TO IDX_BFF06CFA6300FD5E');
        $this->addSql('ALTER TABLE plans_regimes RENAME INDEX idx_plans_regimes_accompaniment_plan TO IDX_5CFFD3E66300FD5E');
        $this->addSql('ALTER TABLE sante_quotidienne ADD pas INT DEFAULT NULL, ADD calories DOUBLE PRECISION DEFAULT NULL, ADD duree_activite_minutes INT DEFAULT NULL, ADD source_donnees VARCHAR(20) NOT NULL');
        $this->addSql('ALTER TABLE shared_documents CHANGE mime_type mime_type VARCHAR(255) NOT NULL, CHANGE uploaded_at uploaded_at DATETIME NOT NULL, CHANGE document_type document_type VARCHAR(50) NOT NULL, CHANGE is_public is_public TINYINT(1) DEFAULT 0 NOT NULL');
        $this->addSql('ALTER TABLE shared_documents RENAME INDEX idx_shared_documents_owner TO IDX_82270B7E3C61F9');
        $this->addSql('ALTER TABLE suspicious_logins RENAME INDEX idx_1c7f61f0a76ed395 TO IDX_81C86F6BA76ED395');
        $this->addSql('ALTER TABLE teleconsultations CHANGE scheduled_at scheduled_at DATETIME NOT NULL, CHANGE started_at started_at DATETIME DEFAULT NULL, CHANGE ended_at ended_at DATETIME DEFAULT NULL, CHANGE type type VARCHAR(50) DEFAULT NULL, CHANGE created_at created_at DATETIME NOT NULL');
        $this->addSql('ALTER TABLE teleconsultations RENAME INDEX idx_teleconsultations_initiator TO IDX_B9A78C027DB3B714');
        $this->addSql('ALTER TABLE teleconsultations RENAME INDEX idx_teleconsultations_recipient TO IDX_B9A78C02E92F8F78');
        $this->addSql('ALTER TABLE user_sessions RENAME INDEX uniq_7d3d564c8f344c7b TO UNIQ_7AED7913613FECDF');
        $this->addSql('ALTER TABLE user_sessions RENAME INDEX idx_7d3d564ca76ed395 TO IDX_7AED7913A76ED395');
        $this->addSql('DROP INDEX IDX_1483A5E9A31C1D7F ON users');
        $this->addSql('DROP INDEX IDX_1483A5E93A8C5C4 ON users');
        $this->addSql('DROP INDEX IDX_1483A5E9D77B6B73 ON users');
        $this->addSql('DROP INDEX IDX_1483A5E9FBFEBCCE ON users');
        $this->addSql('DROP INDEX IDX_1483A5E9F7E2F7E1 ON users');
        $this->addSql('DROP INDEX IDX_1483A5E98FB9F1F7 ON users');
        $this->addSql('ALTER TABLE users CHANGE created_at created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, CHANGE updated_at updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE article_scores DROP FOREIGN KEY FK_6D97A0DE3C1CC488');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_C8F0BB396B899279');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_C8F0BB39BC6D351B');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_C8F0BB39AB0D61F7');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_C8F0BB39DCD6110');
        $this->addSql('DROP TABLE article_scores');
        $this->addSql('DROP TABLE reservations_medicaments');
        $this->addSql('ALTER TABLE abonnements RENAME INDEX idx_4788b767a76ed395 TO IDX_9F79D15BA76ED395');
        $this->addSql('ALTER TABLE accompaniment_plans CHANGE start_date start_date DATE NOT NULL COMMENT \'(DC2Type:date_immutable)\', CHANGE end_date end_date DATE DEFAULT NULL COMMENT \'(DC2Type:date_immutable)\', CHANGE created_at created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE updated_at updated_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
        $this->addSql('ALTER TABLE accompaniment_plans RENAME INDEX idx_700968a06b899279 TO IDX_ACCOMPANIMENT_PLANS_PATIENT');
        $this->addSql('ALTER TABLE accompaniment_plans RENAME INDEX idx_700968a03c105691 TO IDX_ACCOMPANIMENT_PLANS_COACH');
        $this->addSql('ALTER TABLE accompaniment_plans RENAME INDEX idx_700968a0be035a4b TO IDX_ACCOMPANIMENT_PLANS_NUTRITIONIST');
        $this->addSql('ALTER TABLE conversations CHANGE created_at created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE last_message_at last_message_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
        $this->addSql('ALTER TABLE conversations RENAME INDEX idx_c2521bf19ec8d52e TO IDX_CONVERSATION_USER_ONE');
        $this->addSql('ALTER TABLE conversations RENAME INDEX idx_c2521bf1f59432e1 TO IDX_CONVERSATION_USER_TWO');
        $this->addSql('ALTER TABLE document_accesses CHANGE shared_at shared_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE expires_at expires_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE accessed_at accessed_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE is_active is_active TINYINT(1) DEFAULT 1');
        $this->addSql('ALTER TABLE document_accesses RENAME INDEX idx_6df53bbbc33f7837 TO IDX_DOCUMENT_ACCESSES_DOCUMENT');
        $this->addSql('ALTER TABLE document_accesses RENAME INDEX idx_6df53bbbd14fe63f TO IDX_DOCUMENT_ACCESSES_SHARED_WITH');
        $this->addSql('ALTER TABLE factures CHANGE created_at created_at DATETIME NOT NULL');
        $this->addSql('ALTER TABLE factures RENAME INDEX uniq_647590bf55ae19e TO UNIQ_5B2BA6F7F55AE19');
        $this->addSql('ALTER TABLE factures RENAME INDEX idx_647590ba76ed395 TO IDX_5B2BA6F7A76ED395');
        $this->addSql('ALTER TABLE factures RENAME INDEX idx_647590bf1d74413 TO IDX_5B2BA6F7D5C7ABF2');
        $this->addSql('ALTER TABLE google_fit_accounts MODIFY id INT NOT NULL');
        $this->addSql('ALTER TABLE google_fit_accounts DROP FOREIGN KEY FK_ABCBC1DBA76ED395');
        $this->addSql('DROP INDEX UNIQ_ABCBC1DBA76ED395 ON google_fit_accounts');
        $this->addSql('DROP INDEX `primary` ON google_fit_accounts');
        $this->addSql('ALTER TABLE google_fit_accounts CHANGE id id INT NOT NULL');
        $this->addSql('ALTER TABLE medicaments DROP code_barre');
        $this->addSql('ALTER TABLE messages CHANGE created_at created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE read_at read_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE is_read is_read TINYINT(1) DEFAULT 0');
        $this->addSql('ALTER TABLE messages RENAME INDEX idx_db021e969ac0396 TO IDX_MESSAGE_CONVERSATION');
        $this->addSql('ALTER TABLE messages RENAME INDEX idx_db021e96f624b39d TO IDX_MESSAGE_SENDER');
        $this->addSql('ALTER TABLE messages RENAME INDEX idx_db021e96e92f8f78 TO IDX_MESSAGE_RECIPIENT');
        $this->addSql('ALTER TABLE pharmacies DROP latitude, DROP longitude, DROP is_active');
        $this->addSql('ALTER TABLE plans_exercices RENAME INDEX idx_bff06cfa6300fd5e TO IDX_PLANS_EXERCICES_ACCOMPANIMENT_PLAN');
        $this->addSql('ALTER TABLE plans_regimes RENAME INDEX idx_5cffd3e66300fd5e TO IDX_PLANS_REGIMES_ACCOMPANIMENT_PLAN');
        $this->addSql('ALTER TABLE sante_quotidienne DROP pas, DROP calories, DROP duree_activite_minutes, DROP source_donnees');
        $this->addSql('ALTER TABLE shared_documents CHANGE mime_type mime_type VARCHAR(50) NOT NULL, CHANGE uploaded_at uploaded_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE document_type document_type VARCHAR(50) DEFAULT \'other\' NOT NULL, CHANGE is_public is_public TINYINT(1) DEFAULT 0');
        $this->addSql('ALTER TABLE shared_documents RENAME INDEX idx_82270b7e3c61f9 TO IDX_SHARED_DOCUMENTS_OWNER');
        $this->addSql('ALTER TABLE suspicious_logins RENAME INDEX idx_81c86f6ba76ed395 TO IDX_1C7F61F0A76ED395');
        $this->addSql('ALTER TABLE teleconsultations CHANGE scheduled_at scheduled_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE started_at started_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE ended_at ended_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', CHANGE type type VARCHAR(50) DEFAULT \'general\', CHANGE created_at created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
        $this->addSql('ALTER TABLE teleconsultations RENAME INDEX idx_b9a78c027db3b714 TO IDX_TELECONSULTATIONS_INITIATOR');
        $this->addSql('ALTER TABLE teleconsultations RENAME INDEX idx_b9a78c02e92f8f78 TO IDX_TELECONSULTATIONS_RECIPIENT');
        $this->addSql('ALTER TABLE user_sessions RENAME INDEX uniq_7aed7913613fecdf TO UNIQ_7D3D564C8F344C7B');
        $this->addSql('ALTER TABLE user_sessions RENAME INDEX idx_7aed7913a76ed395 TO IDX_7D3D564CA76ED395');
        $this->addSql('ALTER TABLE users CHANGE created_at created_at DATETIME NOT NULL, CHANGE updated_at updated_at DATETIME NOT NULL');
        $this->addSql('CREATE INDEX IDX_1483A5E9A31C1D7F ON users (is_banned)');
        $this->addSql('CREATE INDEX IDX_1483A5E93A8C5C4 ON users (deleted_at)');
        $this->addSql('CREATE INDEX IDX_1483A5E9D77B6B73 ON users (subscription_status)');
        $this->addSql('CREATE INDEX IDX_1483A5E9FBFEBCCE ON users (email_verified)');
        $this->addSql('CREATE INDEX IDX_1483A5E9F7E2F7E1 ON users (admin_approved)');
        $this->addSql('CREATE INDEX IDX_1483A5E98FB9F1F7 ON users (email_verification_token)');
    }
}
