<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260208120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add pharmacy geo/active, medicament barcode, reservations, and datetime fixes';
    }

    public function up(Schema $schema): void
    {
        $schemaManager = $this->connection->createSchemaManager();

        $pharmacyColumns = $schemaManager->listTableColumns('pharmacies');
        if (!isset($pharmacyColumns['latitude'])) {
            $this->addSql('ALTER TABLE pharmacies ADD latitude NUMERIC(10, 6) DEFAULT NULL');
        }
        if (!isset($pharmacyColumns['longitude'])) {
            $this->addSql('ALTER TABLE pharmacies ADD longitude NUMERIC(10, 6) DEFAULT NULL');
        }
        if (!isset($pharmacyColumns['is_active'])) {
            $this->addSql('ALTER TABLE pharmacies ADD is_active TINYINT(1) DEFAULT 1 NOT NULL');
        }

        $medicamentColumns = $schemaManager->listTableColumns('medicaments');
        if (!isset($medicamentColumns['code_barre'])) {
            $this->addSql('ALTER TABLE medicaments ADD code_barre VARCHAR(120) DEFAULT NULL');
        }

        if (!$schemaManager->tablesExist(['reservations_medicaments'])) {
            $this->addSql('CREATE TABLE reservations_medicaments (id INT AUTO_INCREMENT NOT NULL, patient_id INT NOT NULL, pharmacie_id INT NOT NULL, medicament_id INT NOT NULL, stock_id INT NOT NULL, quantite INT DEFAULT 1 NOT NULL, prix_unitaire NUMERIC(10, 2) DEFAULT NULL, prix_total NUMERIC(10, 2) DEFAULT NULL, statut VARCHAR(20) DEFAULT \'en_attente\' NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, expires_at DATETIME DEFAULT NULL, confirmed_at DATETIME DEFAULT NULL, cancelled_at DATETIME DEFAULT NULL, rejected_at DATETIME DEFAULT NULL, INDEX IDX_RESERVATIONS_PATIENT (patient_id), INDEX IDX_RESERVATIONS_PHARMACIE (pharmacie_id), INDEX IDX_RESERVATIONS_MEDICAMENT (medicament_id), INDEX IDX_RESERVATIONS_STOCK (stock_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_RESERVATIONS_PATIENT FOREIGN KEY (patient_id) REFERENCES users (id) ON DELETE CASCADE');
            $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_RESERVATIONS_PHARMACIE FOREIGN KEY (pharmacie_id) REFERENCES pharmacies (id) ON DELETE CASCADE');
            $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_RESERVATIONS_MEDICAMENT FOREIGN KEY (medicament_id) REFERENCES medicaments (id) ON DELETE CASCADE');
            $this->addSql('ALTER TABLE reservations_medicaments ADD CONSTRAINT FK_RESERVATIONS_STOCK FOREIGN KEY (stock_id) REFERENCES stock_pharmacies (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_RESERVATIONS_PATIENT');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_RESERVATIONS_PHARMACIE');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_RESERVATIONS_MEDICAMENT');
        $this->addSql('ALTER TABLE reservations_medicaments DROP FOREIGN KEY FK_RESERVATIONS_STOCK');
        $this->addSql('DROP TABLE reservations_medicaments');
        $this->addSql('ALTER TABLE medicaments DROP code_barre');
        $this->addSql('ALTER TABLE pharmacies DROP latitude, DROP longitude, DROP is_active');
    }
}
