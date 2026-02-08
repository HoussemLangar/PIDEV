<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260204154126 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE abonnements (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(100) NOT NULL, type_abonnement VARCHAR(50) NOT NULL, prix NUMERIC(10, 2) NOT NULL, duree_mois INT NOT NULL, avantages LONGTEXT DEFAULT NULL, description LONGTEXT DEFAULT NULL, date_debut DATE NOT NULL, date_fin DATE NOT NULL, statut VARCHAR(20) DEFAULT \'actif\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE accompagnements (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(100) NOT NULL, description LONGTEXT DEFAULT NULL, date_debut DATE NOT NULL, date_fin DATE DEFAULT NULL, type_accompagnement VARCHAR(50) DEFAULT NULL, statut VARCHAR(20) DEFAULT \'en_cours\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, abonnement_id INT NOT NULL, INDEX IDX_E79677D7F1D74413 (abonnement_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE cliniques (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(150) NOT NULL, adresse VARCHAR(255) NOT NULL, telephone VARCHAR(20) DEFAULT NULL, email VARCHAR(100) DEFAULT NULL, horaires_ouverture LONGTEXT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE coach_sportifs (id INT AUTO_INCREMENT NOT NULL, specialite VARCHAR(100) NOT NULL, justificatif VARCHAR(255) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_F069A598A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE commentaires (id INT AUTO_INCREMENT NOT NULL, commentaire LONGTEXT NOT NULL, note INT DEFAULT NULL, statut VARCHAR(20) DEFAULT \'publie\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, contenu_id INT NOT NULL, user_id INT NOT NULL, parent_id INT DEFAULT NULL, INDEX IDX_D9BEC0C43C1CC488 (contenu_id), INDEX IDX_D9BEC0C4A76ED395 (user_id), INDEX IDX_D9BEC0C4727ACA70 (parent_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE contenu (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, type VARCHAR(50) NOT NULL, contenu LONGTEXT NOT NULL, categorie VARCHAR(100) DEFAULT NULL, tags VARCHAR(255) DEFAULT NULL, statut VARCHAR(20) DEFAULT \'publie\' NOT NULL, date_publication DATETIME DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, auteur_id INT DEFAULT NULL, INDEX IDX_89C2003F60BB6FE6 (auteur_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE disponibilites (id INT AUTO_INCREMENT NOT NULL, date DATE NOT NULL, heure_debut TIME NOT NULL, heure_fin TIME NOT NULL, statut VARCHAR(20) DEFAULT \'disponible\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, medecin_id INT NOT NULL, INDEX IDX_B0F3489C4F31A84 (medecin_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE journaux_items (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT DEFAULT NULL, type VARCHAR(50) NOT NULL, date_journal DATE NOT NULL, heure TIME DEFAULT NULL, valeur VARCHAR(100) DEFAULT NULL, unite VARCHAR(20) DEFAULT NULL, humeur VARCHAR(50) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, INDEX IDX_D9903A266B899279 (patient_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE likes (id INT AUTO_INCREMENT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, contenu_id INT NOT NULL, user_id INT NOT NULL, INDEX IDX_49CA4E7D3C1CC488 (contenu_id), INDEX IDX_49CA4E7DA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE medecins (id INT AUTO_INCREMENT NOT NULL, specialite VARCHAR(100) NOT NULL, numero_ordre VARCHAR(50) DEFAULT NULL, cabinet_adresse VARCHAR(255) DEFAULT NULL, telephone_cabinet VARCHAR(20) DEFAULT NULL, tarif_consultation NUMERIC(10, 2) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_691272DDDC26B9F4 (numero_ordre), UNIQUE INDEX UNIQ_691272DDA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE medicaments (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(150) NOT NULL, type VARCHAR(50) DEFAULT NULL, description LONGTEXT DEFAULT NULL, forme VARCHAR(50) DEFAULT NULL, dosage VARCHAR(50) DEFAULT NULL, prix NUMERIC(10, 2) DEFAULT NULL, stock INT DEFAULT 0 NOT NULL, laboratoire VARCHAR(100) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE notifications (id INT AUTO_INCREMENT NOT NULL, type VARCHAR(50) NOT NULL, titre VARCHAR(255) NOT NULL, message LONGTEXT NOT NULL, lu TINYINT(1) DEFAULT 0 NOT NULL, date_envoi DATETIME NOT NULL, lien VARCHAR(255) DEFAULT NULL, priorite VARCHAR(20) DEFAULT \'normal\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, user_id INT NOT NULL, INDEX IDX_6000B0D3A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE nutritionnistes (id INT AUTO_INCREMENT NOT NULL, specialite VARCHAR(100) NOT NULL, justificatif VARCHAR(255) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_FFFBEA3A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE partage_analyses (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT DEFAULT NULL, fichier_url VARCHAR(255) DEFAULT NULL, date_partage DATETIME DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, accompagnement_id INT DEFAULT NULL, patient_id INT NOT NULL, INDEX IDX_20CDB2A78E768805 (accompagnement_id), INDEX IDX_20CDB2A76B899279 (patient_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE patients (id INT AUTO_INCREMENT NOT NULL, numero_secu VARCHAR(50) DEFAULT NULL, groupe_sanguin VARCHAR(5) DEFAULT NULL, allergies LONGTEXT DEFAULT NULL, antecedents_medicaux LONGTEXT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_2CCC2E2C2D5D15DB (numero_secu), UNIQUE INDEX UNIQ_2CCC2E2CA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE pharmaciens (id INT AUTO_INCREMENT NOT NULL, numero_ordre VARCHAR(50) DEFAULT NULL, pharmacie_nom VARCHAR(150) DEFAULT NULL, pharmacie_adresse VARCHAR(255) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_3FE38F3BDC26B9F4 (numero_ordre), UNIQUE INDEX UNIQ_3FE38F3BA76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE pharmacies (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(150) NOT NULL, adresse VARCHAR(255) NOT NULL, telephone VARCHAR(20) DEFAULT NULL, email VARCHAR(100) DEFAULT NULL, horaires VARCHAR(255) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, pharmacien_id INT DEFAULT NULL, INDEX IDX_3AE5FA2ACFDB96BE (pharmacien_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE plans_exercices (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT DEFAULT NULL, frequence VARCHAR(100) DEFAULT NULL, dure_minutes INT DEFAULT NULL, niveau VARCHAR(20) DEFAULT NULL, objectifs LONGTEXT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, accompagnement_id INT DEFAULT NULL, INDEX IDX_BFF06CFA6B899279 (patient_id), INDEX IDX_BFF06CFA8E768805 (accompagnement_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE plans_regimes (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT DEFAULT NULL, type_regime VARCHAR(50) DEFAULT NULL, objectif VARCHAR(100) DEFAULT NULL, restrictions LONGTEXT DEFAULT NULL, calories_jour INT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, accompagnement_id INT DEFAULT NULL, INDEX IDX_5CFFD3E66B899279 (patient_id), INDEX IDX_5CFFD3E68E768805 (accompagnement_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE rapports_analyses (id INT AUTO_INCREMENT NOT NULL, type_analyse VARCHAR(100) NOT NULL, date_analyse DATE NOT NULL, laboratoire VARCHAR(150) DEFAULT NULL, resultats LONGTEXT DEFAULT NULL, fichier_path VARCHAR(255) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, medecin_id INT DEFAULT NULL, INDEX IDX_D4450CEE6B899279 (patient_id), INDEX IDX_D4450CEE4F31A84 (medecin_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE rapports_medicaux (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, diagnostic LONGTEXT DEFAULT NULL, traitement LONGTEXT DEFAULT NULL, observations LONGTEXT DEFAULT NULL, date_rapport DATE NOT NULL, fichier_path VARCHAR(255) DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, medecin_id INT NOT NULL, INDEX IDX_E4D658A46B899279 (patient_id), INDEX IDX_E4D658A44F31A84 (medecin_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE rendez_vous (id INT AUTO_INCREMENT NOT NULL, date_rdv DATE NOT NULL, heure_rdv TIME NOT NULL, motif LONGTEXT DEFAULT NULL, statut VARCHAR(20) DEFAULT \'confirme\' NOT NULL, type_consultation VARCHAR(50) DEFAULT NULL, notes LONGTEXT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, medecin_id INT NOT NULL, disponibilite_id INT DEFAULT NULL, INDEX IDX_65E8AA0A6B899279 (patient_id), INDEX IDX_65E8AA0A4F31A84 (medecin_id), UNIQUE INDEX UNIQ_65E8AA0A2B9D6493 (disponibilite_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE reponses_medecin (id INT AUTO_INCREMENT NOT NULL, reponse LONGTEXT NOT NULL, date_reponse DATETIME DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, partage_id INT NOT NULL, medecin_id INT NOT NULL, INDEX IDX_54DB4C7BD5CB766D (partage_id), INDEX IDX_54DB4C7B4F31A84 (medecin_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE reponses_medicaments (id INT AUTO_INCREMENT NOT NULL, question LONGTEXT NOT NULL, reponse LONGTEXT DEFAULT NULL, date_question DATETIME NOT NULL, date_reponse DATETIME DEFAULT NULL, statut VARCHAR(20) DEFAULT \'en_attente\' NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, medicament_id INT NOT NULL, user_id INT NOT NULL, INDEX IDX_DA829581AB0D61F7 (medicament_id), INDEX IDX_DA829581A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE stock_pharmacies (id INT AUTO_INCREMENT NOT NULL, quantite INT DEFAULT 0 NOT NULL, prix_vente NUMERIC(10, 2) DEFAULT NULL, date_expiration DATE DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, pharmacie_id INT NOT NULL, medicament_id INT NOT NULL, INDEX IDX_94AC0D86BC6D351B (pharmacie_id), INDEX IDX_94AC0D86AB0D61F7 (medicament_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE symptomes_liste (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(100) NOT NULL, categorie VARCHAR(50) DEFAULT NULL, description LONGTEXT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, UNIQUE INDEX UNIQ_A8E6DF3E6C6E55B5 (nom), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE symptomes_quotidiens (id INT AUTO_INCREMENT NOT NULL, date_symptome DATE NOT NULL, intensite INT NOT NULL, duree VARCHAR(50) DEFAULT NULL, notes LONGTEXT DEFAULT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, patient_id INT NOT NULL, symptome_id INT NOT NULL, INDEX IDX_FF359B846B899279 (patient_id), INDEX IDX_FF359B8412B83D77 (symptome_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE users (id INT AUTO_INCREMENT NOT NULL, username VARCHAR(180) NOT NULL, email VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, nom VARCHAR(100) NOT NULL, prenom VARCHAR(100) NOT NULL, date_naissance DATE NOT NULL, adresse VARCHAR(255) DEFAULT NULL, telephone VARCHAR(20) DEFAULT NULL, role VARCHAR(50) NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, UNIQUE INDEX UNIQ_1483A5E9F85E0677 (username), UNIQUE INDEX UNIQ_1483A5E9E7927C74 (email), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('CREATE TABLE messenger_messages (id BIGINT AUTO_INCREMENT NOT NULL, body LONGTEXT NOT NULL, headers LONGTEXT NOT NULL, queue_name VARCHAR(190) NOT NULL, created_at DATETIME NOT NULL, available_at DATETIME NOT NULL, delivered_at DATETIME DEFAULT NULL, INDEX IDX_75EA56E0FB7336F0E3BD61CE16BA31DBBF396750 (queue_name, available_at, delivered_at, id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci`');
        $this->addSql('ALTER TABLE accompagnements ADD CONSTRAINT FK_E79677D7F1D74413 FOREIGN KEY (abonnement_id) REFERENCES abonnements (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE coach_sportifs ADD CONSTRAINT FK_F069A598A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE commentaires ADD CONSTRAINT FK_D9BEC0C43C1CC488 FOREIGN KEY (contenu_id) REFERENCES contenu (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE commentaires ADD CONSTRAINT FK_D9BEC0C4A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE commentaires ADD CONSTRAINT FK_D9BEC0C4727ACA70 FOREIGN KEY (parent_id) REFERENCES commentaires (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE contenu ADD CONSTRAINT FK_89C2003F60BB6FE6 FOREIGN KEY (auteur_id) REFERENCES users (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE disponibilites ADD CONSTRAINT FK_B0F3489C4F31A84 FOREIGN KEY (medecin_id) REFERENCES medecins (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE journaux_items ADD CONSTRAINT FK_D9903A266B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE likes ADD CONSTRAINT FK_49CA4E7D3C1CC488 FOREIGN KEY (contenu_id) REFERENCES contenu (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE likes ADD CONSTRAINT FK_49CA4E7DA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE medecins ADD CONSTRAINT FK_691272DDA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE notifications ADD CONSTRAINT FK_6000B0D3A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE nutritionnistes ADD CONSTRAINT FK_FFFBEA3A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE partage_analyses ADD CONSTRAINT FK_20CDB2A78E768805 FOREIGN KEY (accompagnement_id) REFERENCES accompagnements (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE partage_analyses ADD CONSTRAINT FK_20CDB2A76B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE patients ADD CONSTRAINT FK_2CCC2E2CA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE pharmaciens ADD CONSTRAINT FK_3FE38F3BA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE pharmacies ADD CONSTRAINT FK_3AE5FA2ACFDB96BE FOREIGN KEY (pharmacien_id) REFERENCES pharmaciens (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE plans_exercices ADD CONSTRAINT FK_BFF06CFA6B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE plans_exercices ADD CONSTRAINT FK_BFF06CFA8E768805 FOREIGN KEY (accompagnement_id) REFERENCES accompagnements (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE plans_regimes ADD CONSTRAINT FK_5CFFD3E66B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE plans_regimes ADD CONSTRAINT FK_5CFFD3E68E768805 FOREIGN KEY (accompagnement_id) REFERENCES accompagnements (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE rapports_analyses ADD CONSTRAINT FK_D4450CEE6B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE rapports_analyses ADD CONSTRAINT FK_D4450CEE4F31A84 FOREIGN KEY (medecin_id) REFERENCES medecins (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE rapports_medicaux ADD CONSTRAINT FK_E4D658A46B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE rapports_medicaux ADD CONSTRAINT FK_E4D658A44F31A84 FOREIGN KEY (medecin_id) REFERENCES medecins (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE rendez_vous ADD CONSTRAINT FK_65E8AA0A6B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE rendez_vous ADD CONSTRAINT FK_65E8AA0A4F31A84 FOREIGN KEY (medecin_id) REFERENCES medecins (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE rendez_vous ADD CONSTRAINT FK_65E8AA0A2B9D6493 FOREIGN KEY (disponibilite_id) REFERENCES disponibilites (id) ON DELETE SET NULL');
        $this->addSql('ALTER TABLE reponses_medecin ADD CONSTRAINT FK_54DB4C7BD5CB766D FOREIGN KEY (partage_id) REFERENCES partage_analyses (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reponses_medecin ADD CONSTRAINT FK_54DB4C7B4F31A84 FOREIGN KEY (medecin_id) REFERENCES medecins (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reponses_medicaments ADD CONSTRAINT FK_DA829581AB0D61F7 FOREIGN KEY (medicament_id) REFERENCES medicaments (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reponses_medicaments ADD CONSTRAINT FK_DA829581A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE stock_pharmacies ADD CONSTRAINT FK_94AC0D86BC6D351B FOREIGN KEY (pharmacie_id) REFERENCES pharmacies (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE stock_pharmacies ADD CONSTRAINT FK_94AC0D86AB0D61F7 FOREIGN KEY (medicament_id) REFERENCES medicaments (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE symptomes_quotidiens ADD CONSTRAINT FK_FF359B846B899279 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE symptomes_quotidiens ADD CONSTRAINT FK_FF359B8412B83D77 FOREIGN KEY (symptome_id) REFERENCES symptomes_liste (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE accompagnements DROP FOREIGN KEY FK_E79677D7F1D74413');
        $this->addSql('ALTER TABLE coach_sportifs DROP FOREIGN KEY FK_F069A598A76ED395');
        $this->addSql('ALTER TABLE commentaires DROP FOREIGN KEY FK_D9BEC0C43C1CC488');
        $this->addSql('ALTER TABLE commentaires DROP FOREIGN KEY FK_D9BEC0C4A76ED395');
        $this->addSql('ALTER TABLE commentaires DROP FOREIGN KEY FK_D9BEC0C4727ACA70');
        $this->addSql('ALTER TABLE contenu DROP FOREIGN KEY FK_89C2003F60BB6FE6');
        $this->addSql('ALTER TABLE disponibilites DROP FOREIGN KEY FK_B0F3489C4F31A84');
        $this->addSql('ALTER TABLE journaux_items DROP FOREIGN KEY FK_D9903A266B899279');
        $this->addSql('ALTER TABLE likes DROP FOREIGN KEY FK_49CA4E7D3C1CC488');
        $this->addSql('ALTER TABLE likes DROP FOREIGN KEY FK_49CA4E7DA76ED395');
        $this->addSql('ALTER TABLE medecins DROP FOREIGN KEY FK_691272DDA76ED395');
        $this->addSql('ALTER TABLE notifications DROP FOREIGN KEY FK_6000B0D3A76ED395');
        $this->addSql('ALTER TABLE nutritionnistes DROP FOREIGN KEY FK_FFFBEA3A76ED395');
        $this->addSql('ALTER TABLE partage_analyses DROP FOREIGN KEY FK_20CDB2A78E768805');
        $this->addSql('ALTER TABLE partage_analyses DROP FOREIGN KEY FK_20CDB2A76B899279');
        $this->addSql('ALTER TABLE patients DROP FOREIGN KEY FK_2CCC2E2CA76ED395');
        $this->addSql('ALTER TABLE pharmaciens DROP FOREIGN KEY FK_3FE38F3BA76ED395');
        $this->addSql('ALTER TABLE pharmacies DROP FOREIGN KEY FK_3AE5FA2ACFDB96BE');
        $this->addSql('ALTER TABLE plans_exercices DROP FOREIGN KEY FK_BFF06CFA6B899279');
        $this->addSql('ALTER TABLE plans_exercices DROP FOREIGN KEY FK_BFF06CFA8E768805');
        $this->addSql('ALTER TABLE plans_regimes DROP FOREIGN KEY FK_5CFFD3E66B899279');
        $this->addSql('ALTER TABLE plans_regimes DROP FOREIGN KEY FK_5CFFD3E68E768805');
        $this->addSql('ALTER TABLE rapports_analyses DROP FOREIGN KEY FK_D4450CEE6B899279');
        $this->addSql('ALTER TABLE rapports_analyses DROP FOREIGN KEY FK_D4450CEE4F31A84');
        $this->addSql('ALTER TABLE rapports_medicaux DROP FOREIGN KEY FK_E4D658A46B899279');
        $this->addSql('ALTER TABLE rapports_medicaux DROP FOREIGN KEY FK_E4D658A44F31A84');
        $this->addSql('ALTER TABLE rendez_vous DROP FOREIGN KEY FK_65E8AA0A6B899279');
        $this->addSql('ALTER TABLE rendez_vous DROP FOREIGN KEY FK_65E8AA0A4F31A84');
        $this->addSql('ALTER TABLE rendez_vous DROP FOREIGN KEY FK_65E8AA0A2B9D6493');
        $this->addSql('ALTER TABLE reponses_medecin DROP FOREIGN KEY FK_54DB4C7BD5CB766D');
        $this->addSql('ALTER TABLE reponses_medecin DROP FOREIGN KEY FK_54DB4C7B4F31A84');
        $this->addSql('ALTER TABLE reponses_medicaments DROP FOREIGN KEY FK_DA829581AB0D61F7');
        $this->addSql('ALTER TABLE reponses_medicaments DROP FOREIGN KEY FK_DA829581A76ED395');
        $this->addSql('ALTER TABLE stock_pharmacies DROP FOREIGN KEY FK_94AC0D86BC6D351B');
        $this->addSql('ALTER TABLE stock_pharmacies DROP FOREIGN KEY FK_94AC0D86AB0D61F7');
        $this->addSql('ALTER TABLE symptomes_quotidiens DROP FOREIGN KEY FK_FF359B846B899279');
        $this->addSql('ALTER TABLE symptomes_quotidiens DROP FOREIGN KEY FK_FF359B8412B83D77');
        $this->addSql('DROP TABLE abonnements');
        $this->addSql('DROP TABLE accompagnements');
        $this->addSql('DROP TABLE cliniques');
        $this->addSql('DROP TABLE coach_sportifs');
        $this->addSql('DROP TABLE commentaires');
        $this->addSql('DROP TABLE contenu');
        $this->addSql('DROP TABLE disponibilites');
        $this->addSql('DROP TABLE journaux_items');
        $this->addSql('DROP TABLE likes');
        $this->addSql('DROP TABLE medecins');
        $this->addSql('DROP TABLE medicaments');
        $this->addSql('DROP TABLE notifications');
        $this->addSql('DROP TABLE nutritionnistes');
        $this->addSql('DROP TABLE partage_analyses');
        $this->addSql('DROP TABLE patients');
        $this->addSql('DROP TABLE pharmaciens');
        $this->addSql('DROP TABLE pharmacies');
        $this->addSql('DROP TABLE plans_exercices');
        $this->addSql('DROP TABLE plans_regimes');
        $this->addSql('DROP TABLE rapports_analyses');
        $this->addSql('DROP TABLE rapports_medicaux');
        $this->addSql('DROP TABLE rendez_vous');
        $this->addSql('DROP TABLE reponses_medecin');
        $this->addSql('DROP TABLE reponses_medicaments');
        $this->addSql('DROP TABLE stock_pharmacies');
        $this->addSql('DROP TABLE symptomes_liste');
        $this->addSql('DROP TABLE symptomes_quotidiens');
        $this->addSql('DROP TABLE users');
        $this->addSql('DROP TABLE messenger_messages');
    }
}
