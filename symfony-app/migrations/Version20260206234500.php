<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260206234500 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create factures table';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE TABLE factures (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, abonnement_id INT NOT NULL, numero VARCHAR(50) NOT NULL, montant_ht NUMERIC(10, 2) NOT NULL, tva_taux NUMERIC(5, 2) NOT NULL, tva_montant NUMERIC(10, 2) NOT NULL, montant_ttc NUMERIC(10, 2) NOT NULL, devise VARCHAR(10) DEFAULT \'TND\' NOT NULL, pdf_path VARCHAR(255) DEFAULT NULL, created_at DATETIME NOT NULL, UNIQUE INDEX UNIQ_5B2BA6F7F55AE19 (numero), INDEX IDX_5B2BA6F7A76ED395 (user_id), INDEX IDX_5B2BA6F7D5C7ABF2 (abonnement_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('ALTER TABLE factures ADD CONSTRAINT FK_5B2BA6F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE factures ADD CONSTRAINT FK_5B2BA6F7D5C7ABF2 FOREIGN KEY (abonnement_id) REFERENCES abonnements (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE factures DROP FOREIGN KEY FK_5B2BA6F7A76ED395');
        $this->addSql('ALTER TABLE factures DROP FOREIGN KEY FK_5B2BA6F7D5C7ABF2');
        $this->addSql('DROP TABLE factures');
    }
}
