<?php
$modelsDir = __DIR__ . '/../public/models';

// Créer le dossier s'il n'existe pas
if (!is_dir($modelsDir)) {
    mkdir($modelsDir, 0755, true);
}

$baseUrl = 'https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/';

$files = [
    // Tiny Face Detector
    'tiny_face_detector_model-weights_manifest.json',
    'tiny_face_detector_model-shard1',
    
    // Face Landmark 68
    'face_landmark_68_model-weights_manifest.json',
    'face_landmark_68_model-shard1',
    
    // Face Recognition
    'face_recognition_model-weights_manifest.json',
    'face_recognition_model-shard1',
];

echo "Téléchargement des modèles face-api.js...\n\n";

foreach ($files as $file) {
    $url = $baseUrl . $file;
    $destination = $modelsDir . '/' . $file;
    
    echo "Téléchargement de $file...\n";
    
    $content = file_get_contents($url);
    
    if ($content === false) {
        echo "❌ Erreur lors du téléchargement de $file\n";
        continue;
    }
    
    file_put_contents($destination, $content);
    echo "✅ $file téléchargé avec succès\n";
}

echo "\n✅ Tous les modèles ont été téléchargés dans public/models/\n";