<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260301060000 extends AbstractMigration
{
    private function renameTableIfExists(string $from, string $to): void
    {
        $tables = $this->connection->createSchemaManager()->listTableNames();
        $normalizedTables = array_map('strtolower', $tables);

        $fromExists = in_array(strtolower($from), $normalizedTables, true);
        $toExists = in_array(strtolower($to), $normalizedTables, true);

        if (!$fromExists || $toExists) {
            return;
        }

        $this->addSql(sprintf('RENAME TABLE `%s` TO `%s`', $from, $to));
    }

    /**
     * @param array<string, string> $renames
     */
    private function renameTables(array $renames): void
    {
        foreach ($renames as $from => $to) {
            $this->renameTableIfExists($from, $to);
        }
    }

    public function getDescription(): string
    {
        return 'Resync DB table names with current entity mappings (singular -> plural rollback)';
    }

    public function up(Schema $schema): void
    {
        $this->renameTables([
            'patient' => 'patients',
            'user_session' => 'user_sessions',
            'teleconsultation' => 'teleconsultations',
            'document_access' => 'document_accesses',
            'shared_document' => 'shared_documents',
            'google_fit_account' => 'google_fit_accounts',
            'health_risk_prediction' => 'health_risk_predictions',
            'password_reset_token' => 'password_reset_tokens',
            'suspicious_login' => 'suspicious_logins',
            'abonnement' => 'abonnements',
            'accompagnement' => 'accompagnements',
            'accompaniment_plan' => 'accompaniment_plans',
            'article_score' => 'article_scores',
            'clinique' => 'cliniques',
            'coach_sportif' => 'coach_sportifs',
            'commentaire' => 'commentaires',
            'conversation' => 'conversations',
            'disponibilite' => 'disponibilites',
            'facture' => 'factures',
            'journal_item' => 'journaux_items',
            'medecin' => 'medecins',
            'medicament' => 'medicaments',
            'message' => 'messages',
            'notification' => 'notifications',
            'nutritionniste' => 'nutritionnistes',
            'partage_analyse' => 'partage_analyses',
            'pharmacien' => 'pharmaciens',
            'pharmacy' => 'pharmacies',
            'plan_exercice' => 'plans_exercices',
            'plan_regime' => 'plans_regimes',
            'rapport_analyse' => 'rapports_analyses',
            'rapport_medical' => 'rapports_medicaux',
            'reponse_medecin' => 'reponses_medecin',
            'reponse_medicament' => 'reponses_medicaments',
            'reservation_medicament' => 'reservations_medicaments',
            'stock_pharmacy' => 'stock_pharmacies',
            'symptome_liste' => 'symptomes_liste',
            'symptome_quotidien' => 'symptomes_quotidiens',
        ]);
    }

    public function down(Schema $schema): void
    {
        $this->renameTables([
            'patients' => 'patient',
            'user_sessions' => 'user_session',
            'teleconsultations' => 'teleconsultation',
            'document_accesses' => 'document_access',
            'shared_documents' => 'shared_document',
            'google_fit_accounts' => 'google_fit_account',
            'health_risk_predictions' => 'health_risk_prediction',
            'password_reset_tokens' => 'password_reset_token',
            'suspicious_logins' => 'suspicious_login',
            'abonnements' => 'abonnement',
            'accompagnements' => 'accompagnement',
            'accompaniment_plans' => 'accompaniment_plan',
            'article_scores' => 'article_score',
            'cliniques' => 'clinique',
            'coach_sportifs' => 'coach_sportif',
            'commentaires' => 'commentaire',
            'conversations' => 'conversation',
            'disponibilites' => 'disponibilite',
            'factures' => 'facture',
            'journaux_items' => 'journal_item',
            'medecins' => 'medecin',
            'medicaments' => 'medicament',
            'messages' => 'message',
            'notifications' => 'notification',
            'nutritionnistes' => 'nutritionniste',
            'partage_analyses' => 'partage_analyse',
            'pharmaciens' => 'pharmacien',
            'pharmacies' => 'pharmacy',
            'plans_exercices' => 'plan_exercice',
            'plans_regimes' => 'plan_regime',
            'rapports_analyses' => 'rapport_analyse',
            'rapports_medicaux' => 'rapport_medical',
            'reponses_medecin' => 'reponse_medecin',
            'reponses_medicaments' => 'reponse_medicament',
            'reservations_medicaments' => 'reservation_medicament',
            'stock_pharmacies' => 'stock_pharmacy',
            'symptomes_liste' => 'symptome_liste',
            'symptomes_quotidiens' => 'symptome_quotidien',
        ]);
    }
}
