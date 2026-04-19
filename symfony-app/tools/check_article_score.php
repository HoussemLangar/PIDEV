<?php

require __DIR__ . '/../vendor/autoload.php';

use App\Service\CommentSentimentScoringService;
use App\Service\ForbiddenWordsFilterService;
use Symfony\Component\HttpClient\HttpClient;

$articleId = isset($argv[1]) ? (int) $argv[1] : 2;

$pdo = new PDO('mysql:host=db;port=3306;dbname=pidev;charset=utf8mb4', 'symfony', 'symfony');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$forbiddenWords = [
    'spam', 'arnaque', 'insultant', 'obscenite', 'merde', 'putain', 'con', 'connard', 'salope', 'batard',
    'fuck', 'shit', 'menace', 'haine', 'violence', 'harcelement', 'discriminant', 'faux',
];

$filter = new ForbiddenWordsFilterService($forbiddenWords);
$sentiment = new CommentSentimentScoringService(HttpClient::create());

$stmt = $pdo->prepare("SELECT id, commentaire FROM commentaires WHERE contenu_id = :id AND statut = 'publie' ORDER BY id ASC");
$stmt->execute(['id' => $articleId]);
$rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

$sum = 0.0;
$count = 0;

echo "Article {$articleId}\n";
foreach ($rows as $r) {
    $id = (int) $r['id'];
    $text = trim((string) $r['commentaire']);
    if ($text === '') {
        echo "- #{$id} ignored (vide)\n";
        continue;
    }

    $cleaned = $filter->cleanText($text);
    if ($cleaned === '' || $filter->hasBadWord($cleaned)) {
        echo "- #{$id} ignored (filtre gros mots): {$text}\n";
        continue;
    }

    $analysis = $sentiment->analyze($text);
    $signed = $sentiment->toSignedScore($analysis);
    $sum += $signed;
    $count++;

    printf(
        "- #%d label=%s confidence=%.2f signed=%.2f text=%s\n",
        $id,
        $analysis['label'],
        (float) $analysis['confidence'],
        $signed,
        $text
    );
}

$avg = $count > 0 ? ($sum / $count) : 0.0;
printf("\nSUM=%.6f, COUNT=%d, SCORE_ARTICLE=%.6f\n", $sum, $count, $avg);

$check = $pdo->prepare('SELECT score_article, nb_commentaires FROM article_scores WHERE contenu_id = :id');
$check->execute(['id' => $articleId]);
$stored = $check->fetch(PDO::FETCH_ASSOC);
if ($stored) {
    printf("DB score_article=%.6f, nb_commentaires=%d\n", (float) $stored['score_article'], (int) $stored['nb_commentaires']);
}
