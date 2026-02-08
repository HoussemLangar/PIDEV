<?php

namespace App\Controller;

use App\Entity\SymptomeQuotidien;
use App\Entity\Patient;
use App\Form\SymptomeQuotidienType;
use App\Repository\PatientRepository;
use App\Repository\SymptomeQuotidienRepository;
use App\Repository\SymptomeListeRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Validator\Validator\ValidatorInterface;

class FrontSymptomeController extends AbstractController
{
    /**
     * Interface web pour ajouter un symptôme
     */
    #[Route('/legacy/symptomes', name: 'legacy_symptomes', methods: ['GET', 'POST'])]
    public function symptomes(Request $request, EntityManagerInterface $em, PatientRepository $patientRepository, SymptomeQuotidienRepository $symptomeRepository, SymptomeListeRepository $symptomeListeRepository): Response
    {
        // Créer ou récupérer un patient de test temporaire
        $patient = $patientRepository->findOneBy([]) ?? $this->createTestPatient($em);
        
        // Création d'un symptome quotidien
        $symptomeQuotidien = new SymptomeQuotidien();
        // Initialiser les propriétés requises avec des valeurs par défaut
        $symptomeQuotidien->setIntensite(5); // valeur par défaut pour l'intensité
        
        // S'assurer que la date par défaut utilise le bon timezone
        $dateActuelle = new \DateTime('now', new \DateTimeZone('Europe/Paris'));
        $symptomeQuotidien->setDateSymptome($dateActuelle);
        // Ne pas assigner le patient ici pour éviter les erreurs Doctrine
        
        // Récupérer la catégorie sélectionnée depuis la requête
        $categorieSelectionnee = $request->query->get('categorie', '');
        
        $form = $this->createForm(SymptomeQuotidienType::class, $symptomeQuotidien, [
            'categorie_selectionnee' => $categorieSelectionnee
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            // Assigner le patient seulement au moment de la persistance
            $symptomeQuotidien->setPatient($patient);
            
            // Correction timezone - récréer la date avec le bon timezone
            $dateSymptome = $symptomeQuotidien->getDateSymptome();
            if ($dateSymptome) {
                // Récupérer la date au format string et la recréer avec le bon timezone
                $dateString = $dateSymptome->format('Y-m-d');
                $nouvelleDateSymptome = \DateTime::createFromFormat('Y-m-d H:i:s', $dateString . ' 12:00:00', new \DateTimeZone('Europe/Paris'));
                $symptomeQuotidien->setDateSymptome($nouvelleDateSymptome);
            }
            
            $em->persist($symptomeQuotidien);
            $em->flush();

            $this->addFlash('success', '✅ Symptôme enregistré avec succès!');
            return $this->redirectToRoute('app_symptomes');
        }

        // Pour l'instant, liste vide des derniers symptômes
        // En attendant la configuration complète du système d'authentification
        $derniersSymptomes = [];
        
        // Récupérer les dates des symptômes enregistrés pour le calendrier
        $symptomesAvecDates = $symptomeRepository->createQueryBuilder('s')
            ->select('s.dateSymptome')
            ->getQuery()
            ->getResult();
        
        // Transformer en format de date simple pour le template
        $datesAvecSymptomes = [];
        foreach ($symptomesAvecDates as $symptome) {
            $dateStr = $symptome['dateSymptome']->format('Y-m-d');
            if (!in_array($dateStr, $datesAvecSymptomes)) {
                $datesAvecSymptomes[] = $dateStr;
            }
        }

        // Récupérer les statistiques des symptômes
        $symptomStats = [];
        try {
            $topSymptoms = $symptomeRepository->getTopSymptomStatistics($patient, 6);
            foreach ($topSymptoms as $stat) {
                $lastDate = null;
                if (isset($stat['lastDate'])) {
                    if ($stat['lastDate'] instanceof \DateTimeInterface) {
                        $lastDate = $stat['lastDate']->format('Y-m-d');
                    } elseif (is_string($stat['lastDate'])) {
                        $lastDate = $stat['lastDate'];
                    }
                }
                
                $symptomStats[] = [
                    'nom' => $stat['nom'],
                    'categorie' => 'Général',
                    'count' => (int)$stat['count'],
                    'averageIntensity' => number_format((float)$stat['averageIntensity'], 1),
                    'lastDate' => $lastDate
                ];
            }
        } catch (\Exception $e) {
            // En cas d'erreur, laisser vide
        }

        return $this->render('front/santequotidienne/symptomes.html.twig', [
            'form' => $form,
            'derniers_symptomes' => $derniersSymptomes,
            'categorie_selectionnee' => $categorieSelectionnee,
            'dates_symptomes' => $datesAvecSymptomes,
            'symptom_stats' => $symptomStats
        ]);
    }

    private function createTestPatient(EntityManagerInterface $em): Patient
    {
        // Créer un utilisateur de test d'abord (requis pour Patient)
        $user = new \App\Entity\User();
        $user->setEmail('test@example.com');
        $user->setRoles(['ROLE_USER']);
        $user->setPassword('test'); // mot de passe fictif
        $em->persist($user);
        $em->flush();

        // Créer un patient de test
        $patient = new Patient();
        $patient->setUser($user);
        $em->persist($patient);
        $em->flush();

        return $patient;
    }

    #[Route('/legacy/sante-quotidienne', name: 'app_sante_quotidienne')]
    public function santeQuotidienne(): Response
    {
        return $this->render('front/santequotidienne/index.html.twig');
    }

    #[Route('/legacy/symptomes/filter-by-category', name: 'legacy_symptomes_filter', methods: ['POST'])]
    public function filterSymptomesByCategory(Request $request, SymptomeListeRepository $symptomeListeRepository): JsonResponse
    {
        $categorie = $request->request->get('categorie');
        
        if (empty($categorie)) {
            // Si aucune catégorie, retourner tous les symptômes
            $symptomes = $symptomeListeRepository->findBy([], ['categorie' => 'ASC', 'nom' => 'ASC']);
        } else {
            // Filtrer par catégorie
            $symptomes = $symptomeListeRepository->findByCategorie($categorie);
        }

        $data = [];
        foreach ($symptomes as $symptome) {
            $data[] = [
                'id' => $symptome->getId(),
                'nom' => $symptome->getNom(),
                'categorie' => $symptome->getCategorie()
            ];
        }

        return new JsonResponse($data);
    }

    #[Route('/legacy/symptomes/by-date/{date}', name: 'legacy_symptomes_by_date', methods: ['GET'])]
    public function getSymptomesByDate(string $date, SymptomeQuotidienRepository $symptomeRepository): JsonResponse
    {
        $symptomes = $symptomeRepository->findByDate($date);
        
        $data = [];
        foreach ($symptomes as $symptome) {
            $data[] = [
                'id' => $symptome->getId(),
                'symptome' => $symptome->getSymptome()->getNom(),
                'categorie' => $symptome->getSymptome()->getCategorie(),
                'intensite' => $symptome->getIntensite(),
                'duree' => $symptome->getDuree(),
                'notes' => $symptome->getNotes(),
                'heureCreation' => $symptome->getCreatedAt()->format('H:i')
            ];
        }

        return new JsonResponse($data);
    }

    #[Route('/legacy/symptomes/delete/{id}', name: 'legacy_symptome_delete', methods: ['DELETE'])]
    public function deleteSymptome(int $id, SymptomeQuotidienRepository $symptomeRepository, EntityManagerInterface $em): JsonResponse
    {
        $symptome = $symptomeRepository->find($id);
        
        if (!$symptome) {
            return new JsonResponse(['error' => 'Symptôme non trouvé'], 404);
        }

        $em->remove($symptome);
        $em->flush();

        return new JsonResponse(['success' => true, 'message' => 'Symptôme supprimé avec succès']);
    }

    #[Route('/legacy/symptomes/edit/{id}', name: 'legacy_symptome_edit', methods: ['POST'])]
    public function editSymptome(int $id, Request $request, SymptomeQuotidienRepository $symptomeRepository, EntityManagerInterface $em, ValidatorInterface $validator): JsonResponse
    {
        $symptome = $symptomeRepository->find($id);
        
        if (!$symptome) {
            return new JsonResponse(['success' => false, 'error' => 'Symptôme non trouvé'], 404);
        }

        $data = json_decode($request->getContent(), true);
        
        // Mettre à jour les données
        if (isset($data['intensite'])) {
            $symptome->setIntensite((int)$data['intensite']);
        }
        if (isset($data['duree'])) {
            $symptome->setDuree(trim($data['duree']));
        }
        if (isset($data['notes'])) {
            $symptome->setNotes(trim($data['notes']));
        }

        // Validation avec le validator Symfony
        $errors = $validator->validate($symptome);
        
        if (count($errors) > 0) {
            // Collecter tous les messages d'erreur
            $errorMessages = [];
            foreach ($errors as $error) {
                $errorMessages[] = $error->getMessage();
            }
            
            return new JsonResponse([
                'success' => false, 
                'error' => implode(', ', $errorMessages)
            ], 400);
        }

        try {
            $em->flush();
            return new JsonResponse(['success' => true, 'message' => 'Symptôme modifié avec succès']);
        } catch (\Exception $e) {
            return new JsonResponse(['success' => false, 'error' => 'Erreur lors de la modification: ' . $e->getMessage()], 500);
        }
    }

    /**
     * Route pour récupérer les statistiques des symptômes
     */
    #[Route('/legacy/symptomes/statistics', name: 'legacy_symptomes_statistics', methods: ['GET'])]
    public function getSymptomStatistics(
        PatientRepository $patientRepository, 
        SymptomeQuotidienRepository $symptomeRepository
    ): JsonResponse {
        try {
            // Récupérer le patient (temporairement le premier)
            $patient = $patientRepository->findOneBy([]);
            
            if (!$patient) {
                // Retourner des statistiques vides si pas de patient
                return new JsonResponse([
                    'success' => true,
                    'statistics' => [],
                    'totalSymptoms' => 0,
                    'overallAverageIntensity' => 0.0,
                    'message' => 'Aucun patient trouvé'
                ]);
            }

            // Récupérer les statistiques depuis le repository
            $topSymptoms = $symptomeRepository->getTopSymptomStatistics($patient, 6);
            
            // Formater les données pour le frontend
            $formattedStats = [];
            foreach ($topSymptoms as $stat) {
                $lastDate = null;
                if (isset($stat['lastDate'])) {
                    if ($stat['lastDate'] instanceof \DateTimeInterface) {
                        $lastDate = $stat['lastDate']->format('Y-m-d');
                    } elseif (is_string($stat['lastDate'])) {
                        $lastDate = $stat['lastDate'];
                    }
                }
                
                $formattedStats[] = [
                    'nom' => $stat['nom'],
                    'categorie' => 'Général', // Catégorie par défaut
                    'count' => (int)$stat['count'],
                    'averageIntensity' => number_format((float)$stat['averageIntensity'], 1),
                    'lastDate' => $lastDate
                ];
            }

            return new JsonResponse([
                'success' => true,
                'statistics' => $formattedStats,
                'totalSymptoms' => $symptomeRepository->getTotalSymptomsCount($patient),
                'overallAverageIntensity' => $symptomeRepository->getAverageIntensity($patient)
            ]);

        } catch (\Exception $e) {
            // Retourner des statistiques vides en cas d'erreur
            return new JsonResponse([
                'success' => true,
                'statistics' => [],
                'totalSymptoms' => 0,
                'overallAverageIntensity' => 0.0,
                'message' => 'Aucun symptôme enregistré'
            ]);
        }
    }
}