<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260208093000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add locale preference to users';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("ALTER TABLE users ADD locale VARCHAR(5) DEFAULT 'fr' NOT NULL");
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users DROP locale');
    }
}
