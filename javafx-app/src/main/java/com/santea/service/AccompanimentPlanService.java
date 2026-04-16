package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.AccompanimentPlan;
import com.santea.model.CoachSportif;
import com.santea.model.Nutritionniste;
import com.santea.model.Patient;
import com.santea.model.PlanExercice;
import com.santea.model.PlanRegime;
import com.santea.model.User;
import com.santea.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AccompanimentPlanService {
    private static final List<String> PROFESSIONAL_ROLES = Arrays.asList("ROLE_COACH", "ROLE_NUTRITIONNISTE", "ROLE_MEDECIN");
    private static final Map<Integer, AccompanimentPlan> plans = new HashMap<>();
    private static boolean seeded;

    private final UserRepository userRepository = new UserRepository(new DatabaseService(DatabaseConfig.fromEnvironment()));

    public AccompanimentPlanService() {
        seedIfNeeded();
    }

    public List<AccompanimentPlan> findPlansForUser(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        if (isPatient(user)) {
            return plans.values().stream()
                .filter(plan -> plan.getPatient() != null && plan.getPatient().getUser() != null)
                .filter(plan -> user.getId().equals(plan.getPatient().getUser().getId()))
                .sorted(Comparator.comparing(AccompanimentPlan::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        }
        if (isMedecin(user)) {
            return sortPlans(plans.values());
        }
        return plans.values().stream()
            .filter(plan -> isAssignedProfessional(plan, user))
            .sorted(Comparator.comparing(AccompanimentPlan::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    public Map<String, Integer> getStatistics(User user) {
        List<AccompanimentPlan> visiblePlans = findPlansForUser(user);
        Map<String, Integer> stats = new HashMap<>();
        stats.put("active", (int) visiblePlans.stream().filter(AccompanimentPlan::isActive).count());
        stats.put("completed", (int) visiblePlans.stream().filter(AccompanimentPlan::isCompleted).count());
        stats.put("cancelled", (int) visiblePlans.stream().filter(AccompanimentPlan::isCancelled).count());
        stats.put("uniquePatients", (int) visiblePlans.stream()
            .filter(plan -> plan.getPatient() != null)
            .map(plan -> plan.getPatient().getId())
            .distinct()
            .count());
        return stats;
    }

    public List<Patient> findAvailablePatients() {
        List<User> patientUsers = userRepository.findByRole("ROLE_PATIENT");
        if (patientUsers.isEmpty()) {
            User currentUser = AuthSession.getCurrentUser();
            if (currentUser != null && isPatient(currentUser)) {
                return List.of(buildPatient(currentUser));
            }
            return List.of();
        }
        return patientUsers.stream().map(this::buildPatient).collect(Collectors.toList());
    }

    public AccompanimentPlan getPlan(Integer id) {
        return id == null ? null : plans.get(id);
    }

    public Patient getPatientByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        return findAvailablePatients().stream()
            .filter(patient -> patient.getUser() != null && userId.equals(patient.getUser().getId()))
            .findFirst()
            .orElse(null);
    }

    public AccompanimentPlan createPlan(User creator, Patient patient, String title, String objectives, String description,
                                        String status, LocalDateTime startDate, Integer durationWeeks,
                                        List<PlanExercice> exercisePlans, List<PlanRegime> dietPlans) {
        if (creator == null || creator.getId() == null || patient == null || patient.getUser() == null) {
            return null;
        }
        if (!canCreatePlan(creator)) {
            return null;
        }
        AccompanimentPlan plan = new AccompanimentPlan();
        plan.setId(Math.abs(UUID.randomUUID().hashCode()));
        plan.setPatient(patient);
        assignProfessional(plan, creator);
        plan.setTitle(safe(title).isBlank() ? "Plan d'accompagnement personnalisé" : safe(title));
        plan.setObjectives(safe(objectives));
        plan.setDescription(safe(description));
        plan.setStatus(normalizeStatus(status));
        LocalDateTime effectiveStart = startDate == null ? LocalDateTime.now() : startDate;
        plan.setStartDate(effectiveStart);
        int effectiveDuration = durationWeeks == null || durationWeeks <= 0 ? 8 : durationWeeks;
        plan.setDurationWeeks(effectiveDuration);
        plan.setEndDate(effectiveStart.plusWeeks(effectiveDuration));
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setExercisePlans(linkExercisePlans(plan, patient, exercisePlans));
        plan.setDietPlans(linkDietPlans(plan, patient, dietPlans));
        plans.put(plan.getId(), plan);
        return plan;
    }

    public boolean deletePlan(Integer id, User actor) {
        AccompanimentPlan plan = getPlan(id);
        if (plan == null || actor == null || actor.getId() == null) {
            return false;
        }
        if (isMedecin(actor)) {
            return false;
        }
        if (!isAssignedProfessional(plan, actor)) {
            return false;
        }
        plans.remove(id);
        return true;
    }

    public boolean canCreatePlan(User user) {
        return isCoach(user) || isNutritionist(user);
    }

    public boolean canDeletePlan(AccompanimentPlan plan, User user) {
        return plan != null && user != null && canCreatePlan(user) && isAssignedProfessional(plan, user);
    }

    public boolean canAccessPlan(AccompanimentPlan plan, User user) {
        if (plan == null || user == null || user.getId() == null) {
            return false;
        }
        if (isMedecin(user)) {
            return true;
        }
        if (isPatient(user)) {
            return plan.getPatient() != null && plan.getPatient().getUser() != null
                && user.getId().equals(plan.getPatient().getUser().getId());
        }
        return isAssignedProfessional(plan, user);
    }

    public AiSuggestions buildAiSuggestions(String goal, String dietStyle, String allergies, int nutritionDays,
                                            String level, int daysPerWeek, int minutes, String constraints) {
        String cleanGoal = safe(goal);
        String cleanDiet = safe(dietStyle).isBlank() ? "equilibre" : safe(dietStyle);
        String cleanLevel = safe(level).isBlank() ? "intermediaire" : safe(level);
        String cleanConstraints = safe(constraints);
        String summary = "Programme mixte axé sur " + (cleanGoal.isBlank() ? "le bien-être général" : cleanGoal.toLowerCase())
            + ", avec " + Math.max(1, nutritionDays) + " jours de nutrition suivie et "
            + Math.max(1, daysPerWeek) + " séances d'activité par semaine.";

        String suggestedTitle = cleanGoal.isBlank()
            ? "Plan d'accompagnement personnalisé"
            : "Plan " + trimTo(cleanGoal, 48);
        String suggestedObjectives = "Objectif principal: " + (cleanGoal.isBlank() ? "améliorer l'équilibre de vie." : cleanGoal + ".")
            + "\nRythme d'activité: " + Math.max(1, daysPerWeek) + " séance(s) de " + Math.max(10, minutes) + " minutes."
            + "\nNutrition: approche " + cleanDiet + (safe(allergies).isBlank() ? "." : ", en tenant compte de: " + allergies + ".");
        String suggestedDescription = summary
            + (cleanConstraints.isBlank() ? "" : "\nContraintes patient: " + cleanConstraints)
            + "\nNiveau conseillé: " + cleanLevel + ".";

        List<PlanExercice> exercisePlans = new ArrayList<>();
        for (int i = 1; i <= Math.max(1, daysPerWeek); i++) {
            PlanExercice exercise = new PlanExercice();
            exercise.setId(Math.abs(UUID.randomUUID().hashCode()));
            exercise.setTitre("Séance " + i + " - " + (cleanGoal.isBlank() ? "Condition physique" : trimTo(cleanGoal, 26)));
            exercise.setFrequence("Jour " + i + " / semaine");
            exercise.setDureMinutes(Math.max(10, minutes));
            exercise.setNiveau(cleanLevel);
            exercise.setObjectifs(cleanGoal);
            exercise.setDescription("Échauffement, bloc principal adapté au niveau " + cleanLevel + ", puis retour au calme.");
            exercisePlans.add(exercise);
        }

        List<PlanRegime> dietPlans = new ArrayList<>();
        String[] meals = {"Petit-déjeuner", "Déjeuner", "Dîner"};
        for (int i = 0; i < Math.min(3, Math.max(1, nutritionDays)); i++) {
            PlanRegime diet = new PlanRegime();
            diet.setId(Math.abs(UUID.randomUUID().hashCode()));
            diet.setTitre(meals[i] + " - " + (cleanGoal.isBlank() ? "Routine équilibrée" : trimTo(cleanGoal, 24)));
            diet.setTypeRegime(cleanDiet);
            diet.setCaloriesJour(1800 + (i * 120));
            diet.setObjectif(cleanGoal);
            diet.setRestrictions(safe(allergies));
            diet.setDescription("Repas structuré, riche en protéines maigres, légumes et hydratation régulière.");
            dietPlans.add(diet);
        }

        return new AiSuggestions(summary, suggestedTitle, suggestedObjectives, suggestedDescription, exercisePlans, dietPlans);
    }

    private List<AccompanimentPlan> sortPlans(java.util.Collection<AccompanimentPlan> collection) {
        return collection.stream()
            .sorted(Comparator.comparing(AccompanimentPlan::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    private void assignProfessional(AccompanimentPlan plan, User creator) {
        if (isCoach(creator)) {
            CoachSportif coach = new CoachSportif();
            coach.setId(creator.getId());
            coach.setUser(creator);
            coach.setSpecialite("Accompagnement sportif");
            plan.setCoach(coach);
        }
        if (isNutritionist(creator)) {
            Nutritionniste nutritionniste = new Nutritionniste();
            nutritionniste.setId(creator.getId());
            nutritionniste.setUser(creator);
            nutritionniste.setSpecialite("Suivi nutritionnel");
            plan.setNutritionist(nutritionniste);
        }
    }

    private List<PlanExercice> linkExercisePlans(AccompanimentPlan plan, Patient patient, List<PlanExercice> source) {
        List<PlanExercice> exercises = source == null ? new ArrayList<>() : source;
        for (PlanExercice exercise : exercises) {
            exercise.setPlan(plan);
            exercise.setPatient(patient);
            exercise.setCreatedAt(LocalDateTime.now());
            exercise.setUpdatedAt(LocalDateTime.now());
        }
        return exercises;
    }

    private List<PlanRegime> linkDietPlans(AccompanimentPlan plan, Patient patient, List<PlanRegime> source) {
        List<PlanRegime> diets = source == null ? new ArrayList<>() : source;
        for (PlanRegime diet : diets) {
            diet.setPlan(plan);
            diet.setPatient(patient);
            diet.setCreatedAt(LocalDateTime.now());
            diet.setUpdatedAt(LocalDateTime.now());
        }
        return diets;
    }

    private void seedIfNeeded() {
        if (seeded) {
            return;
        }
        seeded = true;

        List<Patient> patients = findAvailablePatients();
        List<User> coaches = userRepository.findByRole("ROLE_COACH");
        List<User> nutritionists = userRepository.findByRole("ROLE_NUTRITIONNISTE");
        User current = AuthSession.getCurrentUser();

        if (patients.isEmpty() && current != null && isPatient(current)) {
            patients = List.of(buildPatient(current));
        }
        if (patients.isEmpty()) {
            return;
        }

        User coachUser = !coaches.isEmpty() ? coaches.get(0) : current;
        User nutritionistUser = !nutritionists.isEmpty() ? nutritionists.get(0) : current;
        Patient firstPatient = patients.get(0);

        if (coachUser != null && canCreatePlanForSeed(coachUser)) {
            AiSuggestions suggestions = buildAiSuggestions("Reprendre une activité régulière", "equilibre", "", 7, "debutant", 3, 35, "Prévoir une montée en charge progressive.");
            createPlan(coachUser, firstPatient, suggestions.suggestedTitle(), suggestions.suggestedObjectives(), suggestions.suggestedDescription(),
                "active", LocalDateTime.now().minusWeeks(1), 8, suggestions.exercisePlans(), new ArrayList<>());
        }

        if (nutritionistUser != null && canCreatePlanForSeed(nutritionistUser) && patients.size() > 1) {
            Patient secondPatient = patients.get(1);
            AiSuggestions suggestions = buildAiSuggestions("Rééquilibrage alimentaire", "mediterraneen", "Arachides", 7, "intermediaire", 2, 25, "Limiter les produits ultra-transformés.");
            createPlan(nutritionistUser, secondPatient, suggestions.suggestedTitle(), suggestions.suggestedObjectives(), suggestions.suggestedDescription(),
                "completed", LocalDateTime.now().minusWeeks(10), 6, new ArrayList<>(), suggestions.dietPlans());
        }
    }

    private boolean canCreatePlanForSeed(User user) {
        return user != null && user.getId() != null && (isCoach(user) || isNutritionist(user));
    }

    private Patient buildPatient(User user) {
        Patient patient = new Patient();
        patient.setId(user.getId());
        patient.setUser(user);
        patient.setCreatedAt(LocalDateTime.now().minusMonths(3));
        patient.setAllergies("");
        patient.setAntecedentsMedicaux("");
        return patient;
    }

    private boolean isAssignedProfessional(AccompanimentPlan plan, User user) {
        return (plan.getCoach() != null && plan.getCoach().getUser() != null && user.getId().equals(plan.getCoach().getUser().getId()))
            || (plan.getNutritionist() != null && plan.getNutritionist().getUser() != null && user.getId().equals(plan.getNutritionist().getUser().getId()));
    }

    private String normalizeStatus(String value) {
        String normalized = safe(value).toLowerCase();
        return switch (normalized) {
            case "completed", "cancelled", "paused", "draft" -> normalized;
            default -> "active";
        };
    }

    private boolean isPatient(User user) {
        return "ROLE_PATIENT".equals(getEffectiveRole(user));
    }

    private boolean isCoach(User user) {
        return "ROLE_COACH".equals(getEffectiveRole(user));
    }

    private boolean isNutritionist(User user) {
        return "ROLE_NUTRITIONNISTE".equals(getEffectiveRole(user));
    }

    private boolean isMedecin(User user) {
        return "ROLE_MEDECIN".equals(getEffectiveRole(user));
    }

    private String getEffectiveRole(User user) {
        if (user == null) {
            return "";
        }
        return safe(user.getSubscriptionType()).isBlank() ? safe(user.getRole()) : safe(user.getSubscriptionType());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimTo(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
    }

    public record AiSuggestions(
        String summary,
        String suggestedTitle,
        String suggestedObjectives,
        String suggestedDescription,
        List<PlanExercice> exercisePlans,
        List<PlanRegime> dietPlans
    ) {}
}
