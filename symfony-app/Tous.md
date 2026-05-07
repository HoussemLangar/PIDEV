a# Reperage complet Symfony App pour portage vers un autre langage

Date de reference: 2026-04-09
Projet analyse: symfony-app
Objectif: decrire toutes les fonctionnalites, tous les modeles, outils, IA, metiers, APIs, workflows et dependances pour reimplementer la meme application dans une autre stack.

## 1) Vision globale du produit

Application sante multi-modules avec:
- suivi quotidien de sante et symptomes
- rendez-vous medicaux + disponibilites + rappels
- teleconsultation video (Jitsi)
- pharmacie: stock, reservations, alternatives
- documents medicaux partages
- messagerie patient/professionnels en temps reel (Mercure)
- contenu communaute (posts, commentaires, likes, scoring)
- abonnement/paiement (Stripe) + factures PDF
- administration avancee (validation comptes, securite, moderation)
- outils IA (scanner doc, nutrition, workout, explication resultats)
- securite renforcee (OAuth, 2FA, face verification admin, suspicious login)

---

## 2) Modules metier et fonctionnalites detaillees

### 2.1 Authentification, comptes et profils
- inscription, connexion, deconnexion
- reset mot de passe par token
- OAuth Google/Facebook
- 2FA Google Authenticator
- profil utilisateur, preferences theme/langue
- gestion roles (patient, medecin, pharmacien, coach, nutritionniste, admin)
- validation/approbation de comptes (admin)
- bannissement utilisateur

Fichiers pivots:
- src/Controller/RegistrationController.php
- src/Controller/SecurityController.php
- src/Controller/OAuthController.php
- src/Controller/ProfileController.php
- src/Controller/AccountController.php
- src/Controller/ResetPasswordController.php
- src/Security/*
- src/EventSubscriber/AccountApprovalSubscriber.php
- src/EventSubscriber/BannedUserSubscriber.php

### 2.2 Sante quotidienne et symptomes
- saisie de donnees sante quotidiennes
- suivi des symptomes par date/categorie
- stats symptomes
- import de donnees Google Fit
- prediction de risque sante (service dedie)

Fichiers pivots:
- src/Controller/SanteQuotidienneController.php
- src/Controller/SymptomeQuotidienController.php
- src/Controller/FrontSymptomeController.php
- src/Controller/SymptomeListeController.php
- src/Controller/AdminSymptomeController.php
- src/Service/GoogleFitService.php
- src/Service/RiskPredictionService.php

### 2.3 Rendez-vous et disponibilites
- annuaire medecins
- consultation des disponibilites
- prise, annulation, reprogrammation de rendez-vous
- statut cote medecin
- rappels automatiques

Fichiers pivots:
- src/Controller/AppointmentController.php
- src/Controller/AppointmentFrontController.php
- src/Controller/RendezVousController.php
- src/Controller/DisponibiliteController.php
- src/Service/AppointmentService.php
- src/Command/SendAppointmentRemindersCommand.php

### 2.4 Teleconsultation
- creation/planning de teleconsultations
- lien salle video Jitsi
- suivi statut et duree

Fichiers pivots:
- src/Controller/TeleconsultationController.php
- src/Service/JitsiService.php

### 2.5 Pharmacie, medicaments et reservations
- gestion pharmacies
- recherche pharmacies/medicaments
- comparaison/prix/alternatives
- gestion stock par pharmacie
- reservation medicament + cycle de statut
- reponses pharmaciens

Fichiers pivots:
- src/Controller/PharmacyController.php
- src/Controller/PharmacyFrontController.php
- src/Controller/FrontOffice/PharmacyApiController.php
- src/Controller/FrontOffice/PharmacyFrontController.php
- src/Controller/FrontOffice/PharmacienApiController.php
- src/Controller/FrontOffice/PharmacienDashboardController.php
- src/Controller/StockPharmacyController.php
- src/Controller/MedicamentController.php
- src/Controller/ReponseMedicamentController.php
- src/Controller/PharmacienController.php
- src/Service/PharmacySearchService.php
- src/Service/AlternativeMedicamentService.php
- src/Service/ReservationMedicamentService.php

### 2.6 Rapports et analyse medicale
- CRUD rapports d analyses
- CRUD rapports medicaux
- partage analyse avec professionnels
- reponses medecins

Fichiers pivots:
- src/Controller/RapportAnalyseController.php
- src/Controller/RapportMedicalController.php
- src/Controller/PartageAnalyseController.php
- src/Controller/ReponseMedecinController.php

### 2.7 Documents medicaux partages
- upload document
- stockage physique + metadonnees
- partage avec permissions
- historique d acces
- download et suppression

Fichiers pivots:
- src/Controller/DocumentController.php
- src/Service/DocumentStorageService.php
- src/Form/SharedDocumentType.php
- src/Form/DocumentAccessType.php

### 2.8 Messagerie et conversations
- conversations 1:1
- envoi/lecture messages
- compteur non lus
- diffusion temps reel via Mercure

Fichiers pivots:
- src/Controller/MessageController.php
- src/Entity/Conversation.php
- src/Entity/Message.php
- src/Service/MessagingService.php
- src/Service/MessageRealtimePublisher.php

### 2.9 Contenu, communaute et moderation
- creation/publication contenus
- commentaires/likes
- moderation auto + filtre mots interdits
- score article/commentaires
- recommandations de contenu

Fichiers pivots:
- src/Controller/ContenuController.php
- src/Controller/CommentaireController.php
- src/Controller/LikeController.php
- src/Controller/FrontOffice/ContentController.php
- src/Controller/FrontOffice/ContentApiController.php
- src/Controller/FrontOffice/ContentMediaController.php
- src/Controller/AdminDashboard/ContentController.php
- src/Service/CommentModerationService.php
- src/Service/CommentSentimentScoringService.php
- src/Service/ContentRecommendationService.php
- src/Service/ForbiddenWordsFilterService.php
- src/EventSubscriber/ContentScoreSubscriber.php
- src/EventSubscriber/CommentScoreSubscriber.php

### 2.10 Accompagnement sportif/nutrition
- plans d accompagnement patient
- plans exercice/regime
- attribution coach/nutritionniste
- suggestions IA de plan

Fichiers pivots:
- src/Controller/AccompagnementController.php
- src/Controller/AccompanimentPlanController.php
- src/Controller/PlanExerciceController.php
- src/Controller/PlanRegimeController.php
- src/Controller/CoachSportifController.php
- src/Controller/NutritionnisteController.php
- src/Service/Accompaniment/AccompanimentAiAdvisorService.php

### 2.11 Paiement, abonnement, facturation
- choix plan abonnement
- checkout Stripe
- webhook confirmation paiement
- generation facture PDF
- gestion abonnements cote admin

Fichiers pivots:
- src/Controller/SubscriptionController.php
- src/Controller/AbonnementController.php
- src/Service/Payment/StripeCheckoutService.php
- src/Service/InvoiceService.php

### 2.12 Administration et securite operationnelle
- dashboard global
- export CSV (stats/users/paiements)
- gestion sessions actives et revocation
- suivi connexions suspectes
- validation comptes professionnels
- moderation contenu
- suivi score utilisateurs
- assistant vocal admin (API)

Fichiers pivots:
- src/Controller/AdminController.php
- src/Controller/AdminSymptomeController.php
- src/Controller/AdminDashboard/PharmacyAdminApiController.php
- src/Entity/UserSession.php
- src/Entity/SuspiciousLogin.php

---

## 3) Inventaire complet des modeles (entites)

Total detecte: 45 entites Doctrine

1. Accompagnement
2. AccompanimentPlan
3. Abonnement
4. ArticleScore
5. Clinique
6. CoachSportif
7. Commentaire
8. Contenu
9. Conversation
10. Disponibilite
11. DocumentAccess
12. FaceData
13. Facture
14. GoogleFitAccount
15. HealthRiskPrediction
16. JournalItem
17. Like
18. Medecin
19. Medicament
20. Message
21. Notification
22. Nutritionniste
23. PartageAnalyse
24. PasswordResetToken
25. Patient
26. Pharmacy
27. Pharmacien
28. PlanExercice
29. PlanRegime
30. RapportAnalyse
31. RapportMedical
32. RendezVous
33. ReponseMedecin
34. ReponseMedicament
35. ReservationMedicament
36. SanteQuotidienne
37. SharedDocument
38. StockPharmacy
39. SymptomeListe
40. SymptomeQuotidien
41. SuspiciousLogin
42. Teleconsultation
43. User
44. UserScoreHistory
45. UserSession

Enums detectes (5):
- Alimentation
- Humeur
- NiveauActivite
- SanteDataSource
- UserScoreSnapshotType

---

## 4) Inventaire complet des controllers

Total detecte: 60 controllers

- AbonnementController
- AccountController
- AccompagnementController
- AccompanimentPlanController
- AdminController
- AdminSymptomeController
- AiToolsController
- AppointmentController
- AppointmentFrontController
- CliniqueController
- CoachSportifController
- CommentaireController
- ContenuController
- DisponibiliteController
- DocumentController
- FrontSymptomeController
- GoogleFitController
- HomeController
- JournalItemController
- LikeController
- MedecinController
- MedicamentController
- MessageController
- NotificationController
- NutritionnisteController
- OAuthController
- PartageAnalyseController
- PatientController
- PharmacyController
- PharmacyFrontController
- PharmacienController
- PlanExerciceController
- PlanRegimeController
- PreferenceController
- ProfileController
- RapportAnalyseController
- RapportMedicalController
- RegistrationController
- RendezVousController
- ReponseMedecinController
- ReponseMedicamentController
- ResetPasswordController
- SanteQuotidienneController
- SecurityController
- StockPharmacyController
- SubscriptionController
- SymptomeListeController
- SymptomeQuotidienController
- TeleconsultationController
- TestMailController
- UserController
- AdminDashboard/ContentController
- AdminDashboard/PharmacyAdminApiController
- FrontOffice/ContentApiController
- FrontOffice/ContentController
- FrontOffice/ContentMediaController
- FrontOffice/PharmacienApiController
- FrontOffice/PharmacienDashboardController
- FrontOffice/PharmacyApiController

---

## 5) Inventaire complet des services

Total detecte: 25 services

1. AlternativeMedicamentService
2. AppointmentService
3. CommentModerationService
4. CommentSentimentScoringService
5. ContentRecommendationService
6. DocumentStorageService
7. ForbiddenWordsFilterService
8. GoogleFitService
9. InvoiceService
10. JitsiService
11. MentalHealthChatbotService
12. MessageRealtimePublisher
13. MessagingService
14. NotificationService
15. PharmacySearchService
16. ReservationMedicamentService
17. RiskPredictionService
18. UserAiScoreService
19. Payment/StripeCheckoutService
20. Ai/AiGatewayService
21. Ai/DocumentScannerService
22. Ai/NutritionPlannerService
23. Ai/ResultExplainerService
24. Ai/WorkoutPlannerService
25. Accompaniment/AccompanimentAiAdvisorService

---

## 6) Inventaire securite, controle d acces, evenements

### 6.1 Composants securite
- Security/FacebookAuthenticator.php
- Security/GoogleAuthenticator.php
- Security/LoginSuccessHandler.php
- Security/ContentVoter.php
- Security/PharmacyVoter.php
- Security/Voter/SanteQuotidienneVoter.php
- Security/GeoIpService.php

### 6.2 Event subscribers
1. AccountApprovalSubscriber
2. BannedUserSubscriber
3. CommentScoreSubscriber
4. ContentScoreSubscriber
5. FaceVerificationSubscriber
6. LocaleSubscriber
7. LoginSuccessListener
8. LogoutListener
9. SubscriptionGateSubscriber
10. UserSessionSubscriber

### 6.3 Flows critiques a reproduire
- verification faciale obligatoire pour routes admin
- tracking sessions et revocation
- detection login suspect (geoip)
- moderation auto contenu/commentaires
- gate abonnement pour routes premium/IA

---

## 7) Outils IA (complet)

### 7.1 Outils IA exposes a l utilisateur
- scanner de document medical
- planificateur nutrition
- planificateur workout
- explainer de resultats
- suggestions IA d accompagnement
- chatbot sante mentale

### 7.2 Services IA et details techniques
- AiGatewayService: gateway LLM generique base URL/API key/model
- DocumentScannerService: extraction texte + OCR/fallback + structuration
- WorkoutPlannerService: plan entrainement personalise
- NutritionPlannerService: plan alimentaire personalize
- ResultExplainerService: explication resultats medicaux
- AccompanimentAiAdvisorService: fusion nutrition + workout en plan actionable
- MentalHealthChatbotService: emotion/intention/reponse + safety rules
- RiskPredictionService: prediction risque hybride regles + IA
- CommentSentimentScoringService: scoring sentiment commentaires

### 7.3 Integrations IA externes
- API LLM custom compatible OpenAI (config AI_API_BASE_URL)
- Hugging Face (emotion/intent/chat)
- systeme de fallback local si API indisponible

---

## 8) APIs, routes et surface d integration

### 8.1 Convention
- Routes par attributs dans controllers (config/routes.yaml)
- JSON APIs + pages Twig
- Endpoints supplementaires declares dans config/routes.yaml:
  - POST /api/appointments/{id}/doctor-status
  - GET  /api/pharmacies/{id}/stocks

### 8.2 Groupes API a reproduire
- /api/users
- /api/appointments
- /api/pharmacies
- /api/content
- /api/rapport-medical
- /api/rapport-analyse
- /api/symptome-quotidien
- /api/* (plusieurs CRUD generiques sur modules)

### 8.3 Routes front metier a reproduire
- /login, /register, /logout
- /profile/*
- /subscription/*
- /appointments, /rendez-vous/*
- /teleconsultations/*
- /messages/*
- /documents/*
- /pharmacies/*
- /ai-tools/*
- /admin/*, /admin_dashboard*

### 8.4 Recommandation de migration API
Pour une parite totale:
1. exporter la table exacte des routes depuis la stack Symfony (debug router)
2. mapper chaque route vers controller action dans la cible
3. garder memes codes HTTP, payloads JSON, regles validation

---

## 9) Outils techniques, scripts, tests, donnees

### 9.1 Commandes Symfony
1. RebuildArticleScoresCommand
2. ImportSymptomsCommand
3. RecalculateHealthRiskCommand
4. SendAppointmentRemindersCommand

Commande hors src:
- Command/DiagnoseFaceVerificationCommand.php

### 9.2 Scripts
- scripts/download_face_models.php
- import_csv_symptomes.php
- import_symptomes.php
- fill_database.php
- run_fill_database.sh

### 9.3 Outils auxiliaires
- tools/seed_varied_comments.php
- tools/check_article_score.php

### 9.4 Jeux de donnees
- dataset_symptomes_1000_plus.csv
- dataset_symptomes_avec_description.csv

### 9.5 Tests
- tests/Controller/AdminControllerTest.php
- tests/Controller/AdminSymptomeControllerTest.php
- tests/Service/DocumentStorageServiceTest.php
- tests/Service/Payment/StripeCheckoutServiceTest.php
- tests/TestUnitTest.php

### 9.6 Migrations
- 49 fichiers de migration detectes dans migrations/
- themes importants: document sharing, teleconsultation, message/conversation, accompagnement, scoring, securite

---

## 10) Configurations et dependances externes

### 10.1 Packages de configuration Symfony
- asset_mapper.yaml
- cache.yaml
- debug.yaml
- doctrine.yaml
- doctrine_migrations.yaml
- framework.yaml
- knpu_oauth2_client.yaml
- mailer.yaml
- mercure.yaml
- messenger.yaml
- monolog.yaml
- notifier.yaml
- reset_password.yaml
- routing.yaml
- scheb_2fa.yaml
- security.yaml
- translation.yaml
- twig.yaml
- twig_component.yaml
- validator.yaml
- dev/doctrine_doctor.yaml
- dev/web_profiler.yaml
- test/framework.yaml

### 10.2 Integrations externes
- MySQL/MariaDB (Doctrine)
- Stripe
- Google OAuth + Google Fit
- Facebook OAuth
- Mercure Hub
- Jitsi
- Hugging Face
- GeoIP (MaxMind + API fallback)
- SMTP (Google mailer)

### 10.3 Variables env metier (selection)
- AI_API_BASE_URL, AI_API_KEY, AI_MODEL
- HF_EMOTION_API_URL, HF_EMOTION_API_TOKEN, HF_EMOTION_MODEL
- HF_INTENT_API_URL, HF_INTENT_MODEL
- HF_CHAT_API_URL, HF_CHAT_MODEL
- STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, STRIPE_CURRENCY
- JITSI_SERVER_URL
- GEOIP_API_URL

---

## 11) Frontend, templates et assets

- templates/: 100+ templates Twig (front, admin, emails, ai_tools, document, teleconsultation, etc.)
- assets/: app.js, bootstrap.js, controllers Stimulus, CSS
- public/: JS/CSS/images, models faciaux (face recognition), documents uploades

Points sensibles a la reimplementation:
- conserver separation front/admin
- reproduire interactions JS temps reel messagerie
- conserver templates emails transactionnels
- conserver flux face verification admin

---

## 12) Checklist de portage vers un autre langage (ordre recommande)

1. Cadrage architecture cible (framework web, ORM, auth, queue, websocket)
2. Modeles + schema DB (45 entites + enums + contraintes)
3. Auth/securite (login, OAuth, 2FA, reset, sessions, suspicious login)
4. APIs coeur metier (appointments, pharmacies, content, docs, messages)
5. Services metiers (reservation, recommendation, scoring, notification)
6. Teleconsultation/Jitsi + temps reel messaging
7. IA (gateway + 6 outils) avec fallbacks
8. Paiement Stripe + factures
9. Admin complet (validation, moderation, exports, securite)
10. Front templates + assets + emails
11. Tests unitaires/integration + non regression routes
12. Migration data + cutover production

---

## 13) Risques majeurs et anti-risques

Risques majeurs:
- parite des comportements IA/fallback
- parite securite (2FA + face verification + voters)
- parite temps reel (Mercure)
- parite des routes et payloads JSON existants
- migration de donnees sans casser les relations

Anti-risques:
- ecrire un contrat d API (OpenAPI) depuis l existant avant codage
- geler un snapshot schema SQL de reference
- ajouter tests de comparaison endpoint-par-endpoint
- migrer module par module derriere feature flags
- valider d abord tous les workflows critiques (auth, paiement, rdv, docs)

---

## 14) Annexes: autres inventaires utiles

### 14.1 Forms detectes
- AccompanimentPlanType
- AdminUserResetPasswordType
- AdminUserType
- ContenuType
- DocumentAccessType
- ForgotPasswordType
- MessageType
- PharmacyType
- ProfileType
- ResetPasswordType
- SanteQuotidienneType
- SharedDocumentType
- SymptomeListeType
- SymptomeQuotidienType
- TeleconsultationType
- UsersType

### 14.2 Repositories detectes
- 45 repositories dans src/Repository/
- inclut helpers metier (ex: RendezVousQueryHelper)

### 14.3 Fichiers infrastructure
- compose.yaml
- compose.override.yaml
- phpunit.xml.dist
- phpstan.neon
- importmap.php

---

Ce fichier est volontairement complet pour servir de reference de reimplementation dans une autre stack.
Si necessaire, la prochaine etape est de produire automatiquement une matrice "route -> controller -> service -> entites -> tests" pour pilotage sprint par sprint.

---

## 15) Comment utiliser chaque fonctionnalite et interaction avec la base de donnees

Cette section explique concretement comment utiliser chaque bloc fonctionnel, ce qui est lu/ecrit en base, et comment verifier.

### 15.0 Preparation technique (obligatoire)

1. Configurer l environnement:
- Renseigner `.env` (DB, Stripe, OAuth, IA, mail, Jitsi, GeoIP).

2. Creer et migrer la base:
```bash
cd symfony-app
php bin/console doctrine:database:create --if-not-exists
php bin/console doctrine:migrations:migrate -n
```

3. Charger des donnees de base (symptomes):
```bash
php bin/console app:import-symptoms
# ou
php import_csv_symptomes.php
```

4. Lancer l application:
```bash
symfony server:start
# ou php -S 127.0.0.1:8000 -t public
```

5. Inspecter les routes:
```bash
php bin/console debug:router
```

6. Inspecter SQL en debug:
- activer profiler/dev et observer les requetes Doctrine par page/API.

### 15.1 Authentification, comptes, profils

Comment utiliser:
1. Ouvrir `/register`, creer un compte.
2. Se connecter via `/login`.
3. Modifier profil via `/profile/*`.
4. Activer/desactiver 2FA dans profil.
5. Lancer reset mot de passe via `/forgot-password`.

Interaction BD:
- Lecture: `User`, `PasswordResetToken`, `UserSession`, `SuspiciousLogin`.
- Ecriture: insertion `User`, hash password update, tokens reset, sessions utilisateur, logs suspicious login.

Verification BD (logique):
1. Apres inscription: nouvelle ligne `User`.
2. Apres login: nouvelle ligne `UserSession`.
3. Apres reset: token cree/consomme dans `PasswordResetToken`.
4. Login suspect: ligne `SuspiciousLogin`.

### 15.2 OAuth Google/Facebook

Comment utiliser:
1. Ouvrir endpoints OAuth via boutons login social.
2. Autoriser consentement provider.
3. Retour callback et liaison compte.

Interaction BD:
- Lecture: `User` par email/provider id.
- Ecriture: creation compte si absent ou mise a jour compte existant.
- Google Fit: enregistrement token dans `GoogleFitAccount`.

Verification:
1. Compte OAuth relie au bon user.
2. Tokens Google Fit stockes et dates expiration coherentes.

### 15.3 Sante quotidienne

Comment utiliser:
1. Aller sur `/sante-quotidienne`.
2. Saisir poids, taille, tension, sommeil, activite, humeur, alimentation, eau, pas, calories.
3. Enregistrer.

Interaction BD:
- Lecture: dernieres valeurs utilisateur dans `SanteQuotidienne`.
- Ecriture: insertion/mise a jour `SanteQuotidienne` liee a `User`.
- Peut declencher recalcul risque via `RiskPredictionService` selon flux.

Verification:
1. Nouvelle mesure datee en base.
2. Donnees numeriques dans bornes attendues.
3. Source donnee (`SanteDataSource`) correcte (manuel/google_fit).

### 15.4 Symptomes quotidiens + statistiques

Comment utiliser:
1. Ouvrir `/symptomes` ou routes front equivalentes.
2. Ajouter symptome (categorie, intensite, date, note).
3. Editer/supprimer.
4. Consulter stats (`/symptomes/statistics` ou route associee).

Interaction BD:
- Lecture: `SymptomeListe`, `SymptomeQuotidien`.
- Ecriture: CRUD `SymptomeQuotidien`.
- Effet secondaire: recalcul `HealthRiskPrediction` via `RiskPredictionService`.

Verification:
1. CRUD symptome refl ete en base.
2. Prediction du jour mise a jour dans `HealthRiskPrediction`.

### 15.5 Prediction de risque sante

Comment utiliser:
1. Mettre a jour sante/symptomes.
2. Lancer recalcul batch si besoin:
```bash
php bin/console app:risk:recalculate
```

Interaction BD:
- Lecture: `SanteQuotidienne`, `SymptomeQuotidien`, `User`.
- Ecriture: `HealthRiskPrediction` (scores, niveaux, date prediction).

Verification:
1. Une prediction par user/date (selon logique du service).
2. Scores et niveaux non nuls et bornes valides.

### 15.6 Google Fit sync

Comment utiliser:
1. Connecter Google OAuth.
2. Lancer sync depuis UI/endpoint GoogleFit.

Interaction BD:
- Lecture: `GoogleFitAccount` (token/refresh).
- Ecriture: nouvelles lignes ou MAJ `SanteQuotidienne` avec source `google_fit`.

Verification:
1. Pas/calories/duree activite importes.
2. Expiration token geree (refresh effectif si necessaire).

### 15.7 Rendez-vous et disponibilites

Comment utiliser:
1. Lister medecins et creneaux disponibles (`/appointments`, `/rendez-vous`).
2. Reserver un creneau.
3. Cote medecin: accepter/refuser/reprogrammer.
4. Admin: visualiser et gerer depuis espace admin.

Interaction BD:
- Lecture: `Medecin`, `Disponibilite`, `RendezVous`, `Patient`.
- Ecriture: creation `RendezVous`, MAJ statut `RendezVous`, MAJ statut `Disponibilite`.

Verification:
1. Creneau reserve passe de disponible a reserve.
2. Statut RDV coherent avec action (pending/confirmed/cancelled/...).

### 15.8 Rappels rendez-vous

Comment utiliser:
```bash
php bin/console app:appointments:send-reminders
```

Interaction BD:
- Lecture: `RendezVous` futurs selon fenetre de rappel.
- Ecriture: pas forcement BD obligatoire, mais envoi mail + eventuels flags selon implementation.

Verification:
1. Emails reminder envoyes aux bons destinataires.
2. Aucun rappel pour RDV annules.

### 15.9 Teleconsultation

Comment utiliser:
1. Creer teleconsultation via UI/API.
2. Ouvrir lien de salle Jitsi genere.
3. Cloturer session.

Interaction BD:
- Lecture: `Teleconsultation`, `User`/`Patient`/`Medecin` selon modele.
- Ecriture: creation/MAJ `Teleconsultation` (status, start/end, duree).

Verification:
1. URL salle generee correctement.
2. Champs temps et statut mis a jour en fin de session.

### 15.10 Pharmacies, stocks, medicaments

Comment utiliser:
1. Rechercher pharmacies (`/pharmacies` + API front-office).
2. Rechercher medicaments (nom/code barre).
3. Consulter stocks d une pharmacie (`/api/pharmacies/{id}/stocks`).

Interaction BD:
- Lecture: `Pharmacy`, `StockPharmacy`, `Medicament`, `Pharmacien`.
- Ecriture: CRUD admin/front-office sur `Pharmacy`, `StockPharmacy`, `Medicament`.

Verification:
1. Quantites stock coherentes apres MAJ.
2. Recherche retourne resultats filtres correctement.

### 15.11 Reservations medicaments

Comment utiliser:
1. Creer reservation via endpoint reservation.
2. Pharmacien confirme/rejette.
3. Patient suit statut.

Interaction BD:
- Lecture: `StockPharmacy`, `Medicament`, `Pharmacy`, `User`.
- Ecriture: `ReservationMedicament` + MAJ quantite dans `StockPharmacy` a confirmation.

Verification:
1. A creation: statut `en_attente`.
2. A confirmation: decrement stock et statut `confirmee`.
3. A rejet/annulation: statut mis a jour sans effet stock incorrect.

### 15.12 Alternatives medicaments

Comment utiliser:
1. Appeler endpoint alternatives sur un medicament.
2. Afficher suggestions selon disponibilite/prix/categorie.

Interaction BD:
- Lecture: `Medicament`, `StockPharmacy`.
- Ecriture: aucune (service de recommendation lecture seule).

Verification:
1. Liste alternatives non vide quand equivalents existent.

### 15.13 Rapports d analyses / medicaux / partage

Comment utiliser:
1. CRUD rapport analyse (`/api/rapport-analyse/*`).
2. CRUD rapport medical (`/api/rapport-medical/*`).
3. Partager une analyse, puis reponse medecin.

Interaction BD:
- Lecture: `RapportAnalyse`, `RapportMedical`, `PartageAnalyse`, `ReponseMedecin`, `Patient`, `Medecin`.
- Ecriture: CRUD complet sur ces entites.

Verification:
1. Le partage reference bien rapport + destinataire.
2. Reponse medecin rattachee au bon partage.

### 15.14 Documents partages

Comment utiliser:
1. Upload document via `/documents/upload`.
2. Partager document avec utilisateur/role.
3. Telecharger document.
4. Revoquer acces.

Interaction BD:
- Lecture: `SharedDocument`, `DocumentAccess`, `User`.
- Ecriture: creation `SharedDocument`, creation/revocation `DocumentAccess`.
- Fichiers: ecriture/suppression dans `public/documents/*` via `DocumentStorageService`.

Verification:
1. Entree metadonnees creee + fichier present sur disque.
2. Telechargement refuse sans droit d acces.
3. Historique acces trace.

### 15.15 Messagerie et temps reel

Comment utiliser:
1. Ouvrir `/messages`.
2. Entrer dans conversation.
3. Envoyer message.
4. Verifier reception quasi instantanee (Mercure).

Interaction BD:
- Lecture: `Conversation`, `Message`.
- Ecriture: insertion `Message`, MAJ flags de lecture, eventuelle creation conversation.
- Temps reel: publish sur topic Mercure via `MessageRealtimePublisher`.

Verification:
1. Message persiste en base.
2. Compteur non-lus evolue correctement.
3. Event Mercure publie (si hub configure).

### 15.16 Contenu, commentaires, likes, scoring

Comment utiliser:
1. Creer contenu (`/front/content/create` ou API).
2. Commenter un contenu.
3. Liker/de-liker.
4. Moderer cote admin.

Interaction BD:
- Lecture: `Contenu`, `Commentaire`, `Like`, `ArticleScore`.
- Ecriture: CRUD `Contenu`, insert `Commentaire`, toggle `Like`, MAJ `ArticleScore` par subscribers.

Verification:
1. Commentaire cree avec resultat moderation/scoring.
2. Score article recalcule apres nouveau commentaire/contenu.

### 15.17 Recommandation contenu

Comment utiliser:
1. Consulter page contenu en etant connecte.
2. Le moteur propose des contenus pertinents.

Interaction BD:
- Lecture: profil `User` + `Contenu` via `ContentRecommendationService`.
- Ecriture: generalement aucune (sauf logs/telemetrie si activee).

Verification:
1. Recommandations differentes selon profil/roles.

### 15.18 Accompagnement (plan global, exercice, regime)

Comment utiliser:
1. Creer plan accompagnement pour patient.
2. Ajouter plan exercice et plan regime.
3. Suivre progression/statut.

Interaction BD:
- Lecture: `AccompanimentPlan`, `PlanExercice`, `PlanRegime`, `Patient`, `CoachSportif`, `Nutritionniste`.
- Ecriture: CRUD complet sur plan et sous-plans.

Verification:
1. Relations plan -> exercices/regimes valides.
2. Professionnels rattaches correctement.

### 15.19 Suggestions IA d accompagnement

Comment utiliser:
1. Depuis page plan, appeler endpoint `ai/suggestions`.
2. Fournir objectif, niveau, contraintes, etc.
3. Appliquer les suggestions au formulaire puis enregistrer.

Interaction BD:
- Lecture: contexte patient/profil.
- Ecriture: aucune pendant suggestion.
- Ecriture finale: creation/MAJ plan apres validation utilisateur.

Verification:
1. Suggestions generees meme si IA down (fallback attendu selon service).

### 15.20 Outils IA (document scanner, nutrition, workout, result explainer)

Comment utiliser:
1. Aller sur `/ai-tools`.
2. Ouvrir outil voulu.
3. Soumettre entree (fichier/texte/parametres).

Interaction BD:
- Lecture: abonnement utilisateur (`Abonnement`) pour gate premium.
- Ecriture: selon outil, souvent lecture seule; possible creation de traces/objets derives selon usage futur.
- Document scanner: lecture fichier upload (temp ou stockage) + IA parsing.

Verification:
1. Si abonnement manquant: acces refuse/redirection abonnement.
2. Si IA indisponible: fallback retourne resultat minimal exploitable.

### 15.21 Chatbot sante mentale

Comment utiliser:
1. Envoyer message utilisateur a l endpoint/chat UI associe.
2. Recevoir emotion, intent, et reponse adaptee.
3. Cas critique: message safety avec consignes d urgence.

Interaction BD:
- Lecture: possible contexte utilisateur.
- Ecriture: selon implementation active, logs de conversation possibles via `Message`/autre.

Verification:
1. Detection intent/emotion stable.
2. Trigger safety sur mots/intentions a risque.

### 15.22 Notifications

Comment utiliser:
1. Appeler `NotificationService` depuis actions metier (reservation, support, etc.).
2. Lire notifications dans UI associee.

Interaction BD:
- Ecriture: insertion `Notification` (type, priorite, cible, contenu).
- Lecture: affichage liste utilisateur/admin.

Verification:
1. Priorite conforme au contexte metier.
2. Date creation et destinataire corrects.

### 15.23 Abonnements, paiement Stripe, factures

Comment utiliser:
1. Aller `/subscription` et choisir une offre.
2. Lancer checkout Stripe.
3. Retour success + webhook.
4. Consulter facture (admin/utilisateur selon flux).

Interaction BD:
- Lecture: `Abonnement`, `User`.
- Ecriture: creation/MAJ `Abonnement`, stockage `paymentSessionId`, statut paiement.
- Ecriture facture: insertion `Facture` + generation PDF via `InvoiceService`.

Verification:
1. Webhook valide signature et met a jour abonnement.
2. Facture PDF generee et telechargeable.

### 15.24 Administration

Comment utiliser:
1. Ouvrir `/admin_dashboard`.
2. Gerer utilisateurs, validations, subscriptions, paiements, security sessions, moderation.
3. Export CSV selon ecran admin.

Interaction BD:
- Lecture: transversal sur presque toutes les entites.
- Ecriture: ban user, role/status updates, approbations, annulations, etc.
- Exports: lecture aggregatee puis streaming CSV.

Verification:
1. Toute action admin laisse un etat BD coherent.
2. Les restrictions role + face verification sont actives.

### 15.25 Face verification admin

Comment utiliser:
1. En tant qu admin, acceder route admin.
2. Si non verifie: redirection vers page verification faciale.
3. Realiser verification, puis acceder dashboard.

Interaction BD:
- Lecture/Ecriture: `FaceData` (templates faciaux), etat session (`face_verified`).

Verification:
1. Sans verification: acces admin bloque.
2. Avec verification valide: acces autorise.

### 15.26 Session management et connexions suspectes

Comment utiliser:
1. Se connecter depuis differents devices.
2. Consulter `/admin/security/sessions`.
3. Revoquer session.
4. Consulter `/admin/security/suspicious`.

Interaction BD:
- Ecriture: `UserSession` a chaque login/logout.
- Ecriture: `SuspiciousLogin` selon detection GeoIP.

Verification:
1. Revocation session effective immediate.
2. Entrees suspicious login creees avec raison explicite.

---

## 16) Guide pratique API + verification BD (pas-a-pas)

### 16.1 Exemple standard CRUD API

1. Creer une ressource:
```bash
curl -X POST http://127.0.0.1:8000/api/rapport-medical \
  -H "Content-Type: application/json" \
  -d '{"titre":"Test", "diagnostic":"RAS"}'
```

2. Lire la ressource:
```bash
curl http://127.0.0.1:8000/api/rapport-medical/1
```

3. Mettre a jour:
```bash
curl -X PATCH http://127.0.0.1:8000/api/rapport-medical/1 \
  -H "Content-Type: application/json" \
  -d '{"diagnostic":"Mise a jour"}'
```

4. Supprimer:
```bash
curl -X DELETE http://127.0.0.1:8000/api/rapport-medical/1
```

Verification BD:
- verifier insertion/update/delete sur l entite correspondante via outil SQL ou profiler Doctrine.

### 16.2 Requetes SQL de controle (adaptables)

```sql
-- utilisateurs et sessions
SELECT COUNT(*) FROM user;
SELECT COUNT(*) FROM user_session;

-- rendez-vous
SELECT COUNT(*) FROM rendez_vous;
SELECT statut, COUNT(*) FROM rendez_vous GROUP BY statut;

-- sante et symptomes
SELECT COUNT(*) FROM sante_quotidienne;
SELECT COUNT(*) FROM symptome_quotidien;

-- contenus et moderation
SELECT COUNT(*) FROM contenu;
SELECT COUNT(*) FROM commentaire;
SELECT COUNT(*) FROM article_score;

-- pharmacie
SELECT COUNT(*) FROM pharmacy;
SELECT COUNT(*) FROM stock_pharmacy;
SELECT COUNT(*) FROM reservation_medicament;

-- abonnement/paiement
SELECT COUNT(*) FROM abonnement;
SELECT COUNT(*) FROM facture;
```

Important:
- Les noms physiques de tables peuvent varier selon strategy Doctrine/migrations.
- En cas de doute, verifier les noms reels dans la base (SHOW TABLES).

### 16.3 Regle de validation pour la reimplementation

Pour chaque fonctionnalite, considerer qu elle est correctement portee seulement si:
1. le parcours UI/API fonctionne bout-en-bout
2. les ecritures BD sont exactes (bonnes tables, bonnes FK)
3. les lectures BD retournent le meme comportement metier
4. les side-effects sont conserves (events, mails, temps reel, scoring, IA)

---

## 17) Droits par type d abonnement et par type d utilisateur

Cette section precise exactement quels droits sont accordes selon:
- le role utilisateur
- le type d abonnement
- le statut d abonnement
- les regles de securite (access_control + voters + subscribers)

### 17.1 Champs base de donnees qui pilotent les droits

Table `users` (entite User):
- `role`: role principal (`ROLE_USER`, `ROLE_PATIENT`, `ROLE_MEDECIN`, `ROLE_PHARMACIEN`, `ROLE_COACH`, `ROLE_NUTRITIONNISTE`, `ROLE_ADMIN`)
- `subscription_status`: `PENDING`, `ACTIVE`, `SKIPPED`, `EXPIRED`
- `subscription_type`: type abonnement actif (`ROLE_PATIENT`, `ROLE_MEDECIN`, `ROLE_PHARMACIEN`, `ROLE_COACH`, `ROLE_NUTRITIONNISTE`, `AI_TOOLS`, ou null)
- `subscription_end_at`: date fin droit premium
- `is_banned`, `ban_until`: blocage utilisateur

Table `abonnements` (entite Abonnement):
- `type_abonnement`: meme logique metier que `subscription_type`
- `statut`, `date_debut`, `date_fin`
- `payment_session_id` (Stripe)

Regle fondamentale d autorisation metier:
- role effectif utilise dans plusieurs modules: `subscriptionType` si present, sinon `role`.

### 17.2 Statuts d abonnement et impact droits

1. `ACTIVE`
- Acces premium autorise selon role effectif.
- Acces contenu/pharmacie/messagerie metier selon voters et controleurs.

2. `PENDING`
- Acces restreint: redirection vers souscription pour routes protegees par `SubscriptionGateSubscriber`.

3. `SKIPPED` (mode decouverte)
- Autorise principalement front de base.
- Bloque: prefixes `front_content_`, `api_content_`, `front_pharmacy_`, `api_pharmacies_`, `front_pharmacien_`, `api_pharmacien_`, `admin_`.

4. `EXPIRED`
- Droits premium retires.
- remise automatique role utilisateur vers `ROLE_USER`.
- `subscription_type` null, fin abonnement retiree.

### 17.3 Types d abonnement pris en charge

Types valides dans la logique de paiement/souscription:
1. `ROLE_PATIENT`
2. `ROLE_MEDECIN`
3. `ROLE_PHARMACIEN`
4. `ROLE_COACH`
5. `ROLE_NUTRITIONNISTE`
6. `AI_TOOLS` (abonnement IA additionnel)

Prix actuel (service de souscription):
- abonnements role metier: 10.00
- abonnement IA (`AI_TOOLS`): 5.00

### 17.4 Droits par type d utilisateur (roles)

1. `ROLE_ADMIN`
- Bypass des restrictions d abonnement globales (subscriber).
- Acces complet admin (`/admin*`, moderation, securite, exports, gestion utilisateurs, dashboard).
- Acces outils IA meme sans abonnement IA actif.
- Face verification requise sur routes admin (subscriber dedie).

2. `ROLE_USER` (sans role metier actif)
- Acces routes publiques + base utilisateur connecte.
- Pas de droits metier premium tant que `subscription_status != ACTIVE` ou `subscription_type` null.

3. `ROLE_PATIENT` (actif)
- Acces patient: suivi symptomes/sante, rendez-vous cote patient, documents patient, messagerie avec professionnels.
- Pharmacie: peut reserver (voter pharmacie autorise reserve pour patient/medecin actifs).
- Contenu: peut voir/interagir si abonnement actif, mais creation contenu reservee aux professionnels.

4. `ROLE_MEDECIN` (actif)
- Acces medecin: disponibilites, gestion rendez-vous cote medecin, teleconsultation pro.
- Pharmacie: peut reserver.
- Contenu: peut creer du contenu (professionnel).
- Messagerie: peut contacter des patients.

5. `ROLE_PHARMACIEN` (actif)
- Acces pharmacien: gestion pharmacie, stocks, commandes/reservations.
- Pharmacie voter: `MANAGE`, `STOCK`, `ORDERS` autorises uniquement pharmacien actif.
- Contenu: peut creer du contenu (professionnel).
- Messagerie: peut contacter des patients.

6. `ROLE_COACH` (actif)
- Acces accompagnement/teleconsultation pro (selon controleurs).
- Contenu: peut creer du contenu (professionnel).
- Messagerie: peut contacter des patients.

7. `ROLE_NUTRITIONNISTE` (actif)
- Acces accompagnement/teleconsultation pro (selon controleurs).
- Contenu: peut creer du contenu (professionnel).
- Messagerie: peut contacter des patients.

### 17.5 Droits specifiques abonnement IA (`AI_TOOLS`)

Routes concernees:
- `/ai-tools`
- `/ai-tools/document-scanner`
- `/ai-tools/nutrition-planner`
- `/ai-tools/workout-planner`
- `/ai-tools/result-explainer`

Regles:
1. utilisateur non admin: abonnement `AI_TOOLS` actif obligatoire
2. sinon redirection vers `/ai-tools/subscription`
3. admin: acces direct sans abonnement IA

### 17.6 Regles access_control globales (config securite)

Exemples de protections definies:
- `/appointments`, `/rendez-vous`, `/api/appointments` -> `ROLE_USER`
- `/contenus`, `/api/content` -> `ROLE_USER`
- `/admin/content`, `/admin/moderation` -> `ROLE_ADMIN`
- `/pharmacies`, `/api/pharmacies` -> authentification forte (`IS_AUTHENTICATED_FULLY`)
- `/pharmacien`, `/api/pharmacien` -> `ROLE_PHARMACIEN`

### 17.7 Regles voters (droits fins)

1. ContentVoter
- `VIEW` / `INTERACT`: abonnement actif requis
- `CREATE`: abonnement actif + role effectif professionnel (`ROLE_MEDECIN`, `ROLE_PHARMACIEN`, `ROLE_COACH`, `ROLE_NUTRITIONNISTE`)
- `MODERATE`: reserve admin (via routes/controle)

2. PharmacyVoter
- `VIEW`: abonnement actif
- `RESERVE`: abonnement actif + role effectif patient ou medecin
- `MANAGE`, `STOCK`, `ORDERS`: abonnement actif + role effectif pharmacien

### 17.8 Messagerie: regles exactes de pair autorise

Regle de communication:
- patient -> professionnel uniquement
- professionnel -> patient uniquement
- non patient/non professionnel -> refuse

Roles consideres professionnels:
- `ROLE_MEDECIN`, `ROLE_PHARMACIEN`, `ROLE_COACH`, `ROLE_NUTRITIONNISTE`

### 17.9 Effets BD lors activation / expiration / annulation

1. Activation abonnement paye (non IA)
- cree `abonnements`
- cree `facture`
- met a jour `users.role`, `users.subscription_status=ACTIVE`, `users.subscription_type`, `users.subscription_end_at`
- cree entite role associee si absente (`Patient`, `Medecin`, `Pharmacien`, `CoachSportif`, `Nutritionniste`)

2. Activation abonnement IA
- cree `abonnements` + `facture`
- n ecrase pas le role metier principal
- sert de gate pour routes `ai-tools`

3. Expiration/annulation
- retrait abonnements utilisateur selon flux
- suppression entite role associee (si annulation)
- reset vers `ROLE_USER`
- `subscription_status` vers `EXPIRED`, `subscription_type` null, `subscription_end_at` null

### 17.10 Checklist de verification droits (base de donnees)

1. verifier `users.role`, `users.subscription_status`, `users.subscription_type`, `users.subscription_end_at`
2. verifier presence/absence entite role associee (`patient`, `medecin`, `pharmacien`, `coach_sportif`, `nutritionniste`)
3. verifier ligne `abonnements` active correspondante
4. verifier acces route selon matrice ci-dessus
5. verifier refus attendu en cas `SKIPPED`, `PENDING`, `EXPIRED`, ou utilisateur banni

