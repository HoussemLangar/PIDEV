<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260301060000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Resync DB table names with current entity mappings (singular -> plural rollback)';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('RENAME TABLE patient TO patients');
        $this->addSql('RENAME TABLE user_session TO user_sessions');
        $this->addSql('RENAME TABLE teleconsultation TO teleconsultations');
        $this->addSql('RENAME TABLE document_access TO document_accesses');
        $this->addSql('RENAME TABLE shared_document TO shared_documents');
        $this->addSql('RENAME TABLE google_fit_account TO google_fit_accounts');
        $this->addSql('RENAME TABLE health_risk_prediction TO health_risk_predictions');
        $this->addSql('RENAME TABLE password_reset_token TO password_reset_tokens');
        $this->addSql('RENAME TABLE suspicious_login TO suspicious_logins');

        $this->addSql('RENAME TABLE abonnement TO abonnements');
        $this->addSql('RENAME TABLE accompagnement TO accompagnements');
        $this->addSql('RENAME TABLE accompaniment_plan TO accompaniment_plans');
        $this->addSql('RENAME TABLE article_score TO article_scores');
        $this->addSql('RENAME TABLE clinique TO cliniques');
        $this->addSql('RENAME TABLE coach_sportif TO coach_sportifs');
        $this->addSql('RENAME TABLE commentaire TO commentaires');
        $this->addSql('RENAME TABLE conversation TO conversations');
        $this->addSql('RENAME TABLE disponibilite TO disponibilites');
        $this->addSql('RENAME TABLE facture TO factures');
        $this->addSql('RENAME TABLE journal_item TO journaux_items');
        $this->addSql('RENAME TABLE medecin TO medecins');
        $this->addSql('RENAME TABLE medicament TO medicaments');
        $this->addSql('RENAME TABLE message TO messages');
        $this->addSql('RENAME TABLE notification TO notifications');
        $this->addSql('RENAME TABLE nutritionniste TO nutritionnistes');
        $this->addSql('RENAME TABLE partage_analyse TO partage_analyses');
        $this->addSql('RENAME TABLE pharmacien TO pharmaciens');
        $this->addSql('RENAME TABLE pharmacy TO pharmacies');
        $this->addSql('RENAME TABLE plan_exercice TO plans_exercices');
        $this->addSql('RENAME TABLE plan_regime TO plans_regimes');
        $this->addSql('RENAME TABLE rapport_analyse TO rapports_analyses');
        $this->addSql('RENAME TABLE rapport_medical TO rapports_medicaux');
        $this->addSql('RENAME TABLE reponse_medecin TO reponses_medecin');
        $this->addSql('RENAME TABLE reponse_medicament TO reponses_medicaments');
        $this->addSql('RENAME TABLE reservation_medicament TO reservations_medicaments');
        $this->addSql('RENAME TABLE stock_pharmacy TO stock_pharmacies');
        $this->addSql('RENAME TABLE symptome_liste TO symptomes_liste');
        $this->addSql('RENAME TABLE symptome_quotidien TO symptomes_quotidiens');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('RENAME TABLE patients TO patient');
        $this->addSql('RENAME TABLE user_sessions TO user_session');
        $this->addSql('RENAME TABLE teleconsultations TO teleconsultation');
        $this->addSql('RENAME TABLE document_accesses TO document_access');
        $this->addSql('RENAME TABLE shared_documents TO shared_document');
        $this->addSql('RENAME TABLE google_fit_accounts TO google_fit_account');
        $this->addSql('RENAME TABLE health_risk_predictions TO health_risk_prediction');
        $this->addSql('RENAME TABLE password_reset_tokens TO password_reset_token');
        $this->addSql('RENAME TABLE suspicious_logins TO suspicious_login');

        $this->addSql('RENAME TABLE abonnements TO abonnement');
        $this->addSql('RENAME TABLE accompagnements TO accompagnement');
        $this->addSql('RENAME TABLE accompaniment_plans TO accompaniment_plan');
        $this->addSql('RENAME TABLE article_scores TO article_score');
        $this->addSql('RENAME TABLE cliniques TO clinique');
        $this->addSql('RENAME TABLE coach_sportifs TO coach_sportif');
        $this->addSql('RENAME TABLE commentaires TO commentaire');
        $this->addSql('RENAME TABLE conversations TO conversation');
        $this->addSql('RENAME TABLE disponibilites TO disponibilite');
        $this->addSql('RENAME TABLE factures TO facture');
        $this->addSql('RENAME TABLE journaux_items TO journal_item');
        $this->addSql('RENAME TABLE medecins TO medecin');
        $this->addSql('RENAME TABLE medicaments TO medicament');
        $this->addSql('RENAME TABLE messages TO message');
        $this->addSql('RENAME TABLE notifications TO notification');
        $this->addSql('RENAME TABLE nutritionnistes TO nutritionniste');
        $this->addSql('RENAME TABLE partage_analyses TO partage_analyse');
        $this->addSql('RENAME TABLE pharmaciens TO pharmacien');
        $this->addSql('RENAME TABLE pharmacies TO pharmacy');
        $this->addSql('RENAME TABLE plans_exercices TO plan_exercice');
        $this->addSql('RENAME TABLE plans_regimes TO plan_regime');
        $this->addSql('RENAME TABLE rapports_analyses TO rapport_analyse');
        $this->addSql('RENAME TABLE rapports_medicaux TO rapport_medical');
        $this->addSql('RENAME TABLE reponses_medecin TO reponse_medecin');
        $this->addSql('RENAME TABLE reponses_medicaments TO reponse_medicament');
        $this->addSql('RENAME TABLE reservations_medicaments TO reservation_medicament');
        $this->addSql('RENAME TABLE stock_pharmacies TO stock_pharmacy');
        $this->addSql('RENAME TABLE symptomes_liste TO symptome_liste');
        $this->addSql('RENAME TABLE symptomes_quotidiens TO symptome_quotidien');
    }
}
