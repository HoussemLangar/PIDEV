<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260207123000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add theme, locale, and avatar fields to users';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD theme_preference VARCHAR(10) DEFAULT \'light\' NOT NULL, ADD locale VARCHAR(5) DEFAULT \'fr\' NOT NULL, ADD avatar_data LONGBLOB DEFAULT NULL, ADD avatar_mime VARCHAR(50) DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users DROP theme_preference, DROP locale, DROP avatar_data, DROP avatar_mime');
    }
}
