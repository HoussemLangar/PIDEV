<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Accompaniment plans system
 */
final class Version20260206AccompanimentPlan extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create accompaniment plans table with relationships to exercise and diet plans';
    }

    public function up(Schema $schema): void
    {
        // Create accompaniment_plans table
        $this->addSql('CREATE TABLE accompaniment_plans (
            id INT AUTO_INCREMENT NOT NULL,
            patient_id INT NOT NULL,
            coach_id INT,
            nutritionist_id INT,
            title VARCHAR(255) NOT NULL,
            objectives LONGTEXT NOT NULL,
            description LONGTEXT,
            status VARCHAR(50) NOT NULL DEFAULT "active",
            start_date DATE NOT NULL COMMENT \'(DC2Type:date_immutable)\',
            end_date DATE NULL COMMENT \'(DC2Type:date_immutable)\',
            duration_weeks INT NOT NULL,
            created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            updated_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            PRIMARY KEY(id),
            KEY IDX_ACCOMPANIMENT_PLANS_PATIENT (patient_id),
            KEY IDX_ACCOMPANIMENT_PLANS_COACH (coach_id),
            KEY IDX_ACCOMPANIMENT_PLANS_NUTRITIONIST (nutritionist_id),
            CONSTRAINT FK_ACCOMPANIMENT_PLANS_PATIENT FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
            CONSTRAINT FK_ACCOMPANIMENT_PLANS_COACH FOREIGN KEY (coach_id) REFERENCES coach_sportifs (id) ON DELETE SET NULL,
            CONSTRAINT FK_ACCOMPANIMENT_PLANS_NUTRITIONIST FOREIGN KEY (nutritionist_id) REFERENCES nutritionnistes (id) ON DELETE SET NULL
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');

        // Add foreign keys to plans_exercices and plans_regimes
        $this->addSql('ALTER TABLE plans_exercices ADD accompaniment_plan_id INT NULL');
        $this->addSql('ALTER TABLE plans_exercices ADD CONSTRAINT FK_PLANS_EXERCICES_ACCOMPANIMENT_PLAN FOREIGN KEY (accompaniment_plan_id) REFERENCES accompaniment_plans (id) ON DELETE SET NULL');
        $this->addSql('CREATE INDEX IDX_PLANS_EXERCICES_ACCOMPANIMENT_PLAN ON plans_exercices (accompaniment_plan_id)');

        $this->addSql('ALTER TABLE plans_regimes ADD accompaniment_plan_id INT NULL');
        $this->addSql('ALTER TABLE plans_regimes ADD CONSTRAINT FK_PLANS_REGIMES_ACCOMPANIMENT_PLAN FOREIGN KEY (accompaniment_plan_id) REFERENCES accompaniment_plans (id) ON DELETE SET NULL');
        $this->addSql('CREATE INDEX IDX_PLANS_REGIMES_ACCOMPANIMENT_PLAN ON plans_regimes (accompaniment_plan_id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE plans_exercices DROP FOREIGN KEY FK_PLANS_EXERCICES_ACCOMPANIMENT_PLAN');
        $this->addSql('ALTER TABLE plans_exercices DROP KEY IDX_PLANS_EXERCICES_ACCOMPANIMENT_PLAN');
        $this->addSql('ALTER TABLE plans_exercices DROP COLUMN accompaniment_plan_id');

        $this->addSql('ALTER TABLE plans_regimes DROP FOREIGN KEY FK_PLANS_REGIMES_ACCOMPANIMENT_PLAN');
        $this->addSql('ALTER TABLE plans_regimes DROP KEY IDX_PLANS_REGIMES_ACCOMPANIMENT_PLAN');
        $this->addSql('ALTER TABLE plans_regimes DROP COLUMN accompaniment_plan_id');

        $this->addSql('DROP TABLE IF EXISTS accompaniment_plans');
    }
}
