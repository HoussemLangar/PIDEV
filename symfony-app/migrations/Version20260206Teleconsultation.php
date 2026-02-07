<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Teleconsultation system
 */
final class Version20260206Teleconsultation extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create teleconsultation table for video consultations';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE TABLE teleconsultations (
            id INT AUTO_INCREMENT NOT NULL,
            initiator_id INT NOT NULL,
            recipient_id INT NOT NULL,
            room_name VARCHAR(255) NOT NULL,
            description LONGTEXT,
            scheduled_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            started_at DATETIME NULL COMMENT \'(DC2Type:datetime_immutable)\',
            ended_at DATETIME NULL COMMENT \'(DC2Type:datetime_immutable)\',
            status VARCHAR(50) NOT NULL DEFAULT "pending",
            duration_seconds INT,
            type VARCHAR(50) DEFAULT "general",
            created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            PRIMARY KEY(id),
            KEY IDX_TELECONSULTATIONS_INITIATOR (initiator_id),
            KEY IDX_TELECONSULTATIONS_RECIPIENT (recipient_id),
            CONSTRAINT FK_TELECONSULTATIONS_INITIATOR FOREIGN KEY (initiator_id) REFERENCES users (id) ON DELETE CASCADE,
            CONSTRAINT FK_TELECONSULTATIONS_RECIPIENT FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS teleconsultations');
    }
}
