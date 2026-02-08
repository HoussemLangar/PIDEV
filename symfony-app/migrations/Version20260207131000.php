<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260207131000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add reminder preference to users and geo fields to medecins';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD reminder_enabled TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE medecins ADD cabinet_ville VARCHAR(100) DEFAULT NULL, ADD cabinet_lat NUMERIC(10, 6) DEFAULT NULL, ADD cabinet_lng NUMERIC(10, 6) DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users DROP reminder_enabled');
        $this->addSql('ALTER TABLE medecins DROP cabinet_ville, DROP cabinet_lat, DROP cabinet_lng');
    }
}
