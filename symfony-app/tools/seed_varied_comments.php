<?php

$pdo = new PDO('mysql:host=db;port=3306;dbname=pidev;charset=utf8mb4', 'symfony', 'symfony');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$rows = [
    ['Excellent article, tres clair et utile', 1, 2],
    ['Pas mal, mais il manque des exemples concrets', 1, 1],
    ['Je ne suis pas convaincu par ce conseil', 1, 3],
    ['Super explication, merci beaucoup', 2, 2],
    ['Contenu correct sans plus', 2, 1],
    ['Plutot decevant, trop general', 2, 3],
    ['Tres bien structure, bravo', 3, 2],
    ['Moyen, je reste mitige', 3, 1],
    ['Je recommande cet article', 3, 3],
    ['Bonne synthese, facile a lire', 4, 2],
    ['Pas tres utile pour mon cas', 4, 1],
    ['Excellent travail editorial', 4, 3],
    ['Article pertinent et pratique', 5, 2],
    ['Bof, je m attendais a mieux', 5, 1],
    ['Tres utile au quotidien', 5, 3],
    ['Merci, ca m a aide', 6, 2],
    ['Assez neutre, rien de nouveau', 6, 1],
    ['Bon article mais un peu long', 6, 3],
    ['Top, simple et efficace', 7, 2],
    ['Pas convaincu du tout', 7, 1],
    ['Correct, peut mieux faire', 7, 3],
    ['Excellent contenu, tres utile', 8, 2],
    ['Moyen, trop repetitif', 8, 1],
    ['J ai bien aime cette approche', 8, 3],
    ['Tres bon article', 9, 2],
    ['Mitige, certaines parties sont floues', 9, 1],
    ['Merci pour les conseils', 9, 3],
    ['Super contenu, continuez', 10, 2],
    ['Pas terrible a mon avis', 10, 1],
    ['Plutot utile globalement', 10, 3],
];

$stmt = $pdo->prepare(
    "INSERT INTO commentaires (commentaire, statut, contenu_id, user_id, created_at, updated_at)
     VALUES (:commentaire, 'publie', :contenu_id, :user_id, NOW(), NOW())"
);

$inserted = 0;
foreach ($rows as [$commentaire, $contenuId, $userId]) {
    $stmt->execute([
        'commentaire' => $commentaire,
        'contenu_id' => $contenuId,
        'user_id' => $userId,
    ]);
    $inserted++;
}

echo "Inserted: {$inserted}\n";
