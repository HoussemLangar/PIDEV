<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260208190000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add Google Fit account table and enrich sante_quotidienne with steps/source/calories/active minutes';
    }

    public function up(Schema $schema): void
    {
        $schemaManager = $this->connection->createSchemaManager();

        if (!$schemaManager->tablesExist(['google_fit_accounts'])) {
            $this->addSql('CREATE TABLE google_fit_accounts (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, google_account_id VARCHAR(255) NOT NULL, access_token LONGTEXT NOT NULL, refresh_token LONGTEXT DEFAULT NULL, token_expiration DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', last_sync_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', scopes LONGTEXT DEFAULT NULL, UNIQUE INDEX UNIQ_2E7F8B6FA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            $this->addSql('ALTER TABLE google_fit_accounts ADD CONSTRAINT FK_2E7F8B6FA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        }

        $columns = $schemaManager->listTableColumns('sante_quotidienne');
        if (!isset($columns['pas'])) {
            $this->addSql('ALTER TABLE sante_quotidienne ADD pas INT DEFAULT NULL');
        }
        if (!isset($columns['calories'])) {
            $this->addSql('ALTER TABLE sante_quotidienne ADD calories DOUBLE PRECISION DEFAULT NULL');
        }
        if (!isset($columns['duree_activite_minutes'])) {
            $this->addSql('ALTER TABLE sante_quotidienne ADD duree_activite_minutes INT DEFAULT NULL');
        }
        if (!isset($columns['source_donnees'])) {
            $this->addSql("ALTER TABLE sante_quotidienne ADD source_donnees VARCHAR(20) NOT NULL DEFAULT 'manuel'");
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE google_fit_accounts DROP FOREIGN KEY FK_2E7F8B6FA76ED395');
        $this->addSql('DROP TABLE google_fit_accounts');

        $this->addSql('ALTER TABLE sante_quotidienne DROP pas, DROP calories, DROP duree_activite_minutes, DROP source_donnees');
    }
}
