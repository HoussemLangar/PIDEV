<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class MentalHealthChatbotService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $hfEmotionApiUrl = '',
        private readonly string $hfEmotionApiToken = '',
        private readonly string $hfEmotionModel = 'astrosbd/french_emotion_camembert',
        private readonly string $hfIntentApiUrl = '',
        private readonly string $hfIntentModel = 'MoritzLaurer/mDeBERTa-v3-base-mnli-xnli',
        private readonly string $hfChatApiUrl = '',
        private readonly string $hfChatModel = 'google/flan-t5-base',
        private readonly float $hfEmotionTimeout = 8.0,
    ) {}

    /**
     * @return array{emotion:string,intent:string,response:string,safetyAlert:bool}
     */
    public function reply(string $rawMessage): array
    {
        $message = trim($rawMessage);
        if ($message === '') {
            return [
                'emotion' => 'neutre',
                'intent' => 'inconnu',
                'response' => "Je suis la pour vous ecouter. Vous pouvez me dire ce que vous ressentez en ce moment ?",
                'safetyAlert' => false,
            ];
        }

        $normalized = $this->normalize($message);
        $safetyAlert = $this->containsSafetyRisk($normalized);
        if ($safetyAlert) {
            return [
                'emotion' => 'detresse',
                'intent' => 'crise',
                'response' => "Je suis vraiment desole que vous traversiez cela. Votre securite est prioritaire. Si vous etes en danger immediat, contactez les urgences locales maintenant et parlez a une personne de confiance tout de suite.",
                'safetyAlert' => true,
            ];
        }

        $emotion = $this->detectEmotion($message, $normalized);
        $intent = $this->detectIntent($message, $normalized);
        $response = $this->buildEmpathicResponse($message, $normalized, $emotion, $intent);

        return [
            'emotion' => $emotion,
            'intent' => $intent,
            'response' => $response,
            'safetyAlert' => false,
        ];
    }

    private function normalize(string $text): string
    {
        $lower = mb_strtolower($text);
        $clean = preg_replace('/[^\p{L}\p{N}\s]/u', ' ', $lower);
        $clean = preg_replace('/\s+/', ' ', (string) $clean);

        return trim((string) $clean);
    }

    private function containsSafetyRisk(string $text): bool
    {
        $riskPatterns = [
            'suicide', 'me suicider', 'me tuer', 'je vais me tuer', 'je veux mourir',
            'mourir', 'auto mutilation', 'automutilation', 'se faire du mal',
            'tuer quelqu', 'je vais te tuer',
        ];
        foreach ($riskPatterns as $pattern) {
            if (str_contains($text, $pattern)) {
                return true;
            }
        }

        return false;
    }

    private function detectIntent(string $rawText, string $normalizedText): string
    {
        $remoteIntent = $this->detectIntentWithHuggingFace($rawText);
        if ($remoteIntent !== null) {
            return $remoteIntent;
        }

        return $this->detectIntentFallback($normalizedText);
    }

    private function detectIntentFallback(string $text): string
    {
        $intents = [
            'rupture_amoureuse' => ['rupture', 'rupture amoureuse', 'mon ex', 'ma copine m a quitte', 'mon copain m a quitte', 'separation', 'coeur brise'],
            'anxiete' => ['anxiete', 'angoisse', 'panique', 'stresse', 'stress', 'peur'],
            'deprime' => ['deprime', 'depression', 'triste', 'vide', 'sans energie'],
            'colere' => ['colere', 'enerve', 'rage', 'frustre'],
        ];

        foreach ($intents as $intent => $keywords) {
            foreach ($keywords as $keyword) {
                if (str_contains($text, $keyword)) {
                    return $intent;
                }
            }
        }

        return 'general';
    }

    private function detectEmotion(string $rawText, string $normalizedText): string
    {
        $remoteEmotion = $this->detectEmotionWithHuggingFace($rawText);
        if ($remoteEmotion !== null) {
            return $remoteEmotion;
        }

        return $this->detectEmotionFallback($normalizedText);
    }

    private function detectEmotionFallback(string $text): string
    {
        $emotionKeywords = [
            'tristesse' => ['triste', 'vide', 'mal', 'coeur brise', 'deprime', 'seul', 'solitude', 'pleure'],
            'anxiete' => ['anxieux', 'angoisse', 'stresse', 'panic', 'peur', 'inquiet'],
            'colere' => ['colere', 'enerve', 'rage', 'frustre', 'haine'],
            'joie' => ['heureux', 'joie', 'content', 'soulage', 'mieux'],
        ];

        $scores = [
            'tristesse' => 0,
            'anxiete' => 0,
            'colere' => 0,
            'joie' => 0,
        ];

        foreach ($emotionKeywords as $emotion => $keywords) {
            foreach ($keywords as $keyword) {
                if (str_contains($text, $keyword)) {
                    $scores[$emotion]++;
                }
            }
        }

        arsort($scores);
        $topEmotion = array_key_first($scores);
        if ($topEmotion === null || $scores[$topEmotion] === 0) {
            return 'neutre';
        }

        return $topEmotion;
    }

    private function detectEmotionWithHuggingFace(string $text): ?string
    {
        $model = trim($this->hfEmotionModel);
        $endpoint = trim($this->hfEmotionApiUrl);
        if ($endpoint === '') {
            if ($model === '') {
                return null;
            }
            $endpoint = 'https://router.huggingface.co/hf-inference/models/' . rawurlencode($model);
        }

        $headers = ['Content-Type' => 'application/json'];
        $token = trim($this->hfEmotionApiToken);
        if ($token !== '') {
            $headers['Authorization'] = 'Bearer ' . $token;
        }

        try {
            $response = $this->httpClient->request('POST', $endpoint, [
                'headers' => $headers,
                'json' => [
                    'inputs' => $text,
                    'options' => ['wait_for_model' => true],
                ],
                'timeout' => max(1.0, $this->hfEmotionTimeout),
            ]);
            $data = $response->toArray(false);
        } catch (TransportExceptionInterface|\Throwable) {
            return null;
        }

        $label = $this->extractTopLabel($data);
        if ($label === null) {
            return null;
        }

        return $this->mapModelLabelToEmotion($label);
    }

    /**
     * @param mixed $data
     */
    private function extractTopLabel(mixed $data): ?string
    {
        if (!is_array($data)) {
            return null;
        }

        if (isset($data['error']) && is_string($data['error'])) {
            return null;
        }

        $candidates = [];
        $this->collectLabelCandidates($data, $candidates);
        if ($candidates === []) {
            return null;
        }

        usort($candidates, static fn(array $a, array $b): int => $b['score'] <=> $a['score']);
        return $candidates[0]['label'] ?? null;
    }

    /**
     * @param mixed $node
     * @param array<int, array{label:string,score:float}> $out
     */
    private function collectLabelCandidates(mixed $node, array &$out): void
    {
        if (!is_array($node)) {
            return;
        }

        if (isset($node['label']) && is_string($node['label'])) {
            $scoreRaw = $node['score'] ?? $node['confidence'] ?? 0.0;
            $score = is_numeric($scoreRaw) ? (float) $scoreRaw : 0.0;
            if ($score > 1.0) {
                $score = $score / 100.0;
            }
            $out[] = [
                'label' => mb_strtolower(trim($node['label'])),
                'score' => max(0.0, min(1.0, $score)),
            ];
        }

        foreach ($node as $child) {
            if (is_array($child)) {
                $this->collectLabelCandidates($child, $out);
            }
        }
    }

    private function mapModelLabelToEmotion(string $label): string
    {
        $l = mb_strtolower($label);

        if (str_contains($l, 'sad') || str_contains($l, 'trist') || str_contains($l, 'depress')) {
            return 'tristesse';
        }
        if (str_contains($l, 'fear') || str_contains($l, 'anx') || str_contains($l, 'stress') || str_contains($l, 'angoiss')) {
            return 'anxiete';
        }
        if (str_contains($l, 'anger') || str_contains($l, 'coler') || str_contains($l, 'rage')) {
            return 'colere';
        }
        if (str_contains($l, 'joy') || str_contains($l, 'happy') || str_contains($l, 'joie') || str_contains($l, 'amour')) {
            return 'joie';
        }
        if (str_contains($l, 'neutral') || str_contains($l, 'neutre')) {
            return 'neutre';
        }

        return 'neutre';
    }

    private function buildEmpathicResponse(string $rawMessage, string $normalizedMessage, string $emotion, string $intent): string
    {
        $remoteResponse = $this->generateEmpathicResponseWithHuggingFace($rawMessage, $emotion, $intent);
        if ($remoteResponse !== null) {
            return $remoteResponse;
        }

        $focus = $this->extractFocus($rawMessage);

        if ($intent === 'rupture_amoureuse') {
            $empathy = $this->pickVariant($normalizedMessage, [
                "Je suis desole que vous traversiez cette rupture, c'est une douleur reelle.",
                "Ce que vous vivez apres une rupture peut etre tres destabilisant, je vous entends.",
                "Une rupture peut faire tres mal, surtout quand on se sent vide ensuite.",
            ]);
            $question = $this->pickVariant($normalizedMessage . 'q1', [
                "Qu'est-ce qui vous fait le plus souffrir en ce moment: le manque, la trahison, ou la peur de l'avenir ?",
                "Si vous deviez nommer une emotion dominante maintenant, ce serait laquelle ?",
                "Depuis quand cette sensation est la plus intense dans vos journees ?",
            ]);
            $advice = $this->pickVariant($normalizedMessage . 'a1', [
                "Petit pas concret pour aujourd'hui: mangez, hydratez-vous, puis faites 10 minutes de marche sans telephone.",
                "Pour ce soir: notez 3 phrases \"ce que je ressens / ce dont j'ai besoin / la prochaine petite action\".",
                "Essayez un ancrage simple: respiration lente 2 minutes, puis envoyez un message a une personne de confiance.",
            ]);

            if ($focus !== null) {
                return $empathy . " J'entends que \"" . $focus . "\" pese beaucoup. " . $question . " " . $advice;
            }

            return $empathy . " " . $question . " " . $advice;
        }

        if ($emotion === 'tristesse') {
            $empathy = $this->pickVariant($normalizedMessage, [
                "Ce que vous ressentez a du poids, et vous avez bien fait d'en parler.",
                "Je vous lis, et votre tristesse est vraiment prise au serieux ici.",
                "Quand tout semble lourd, le fait de mettre des mots est deja important.",
            ]);
            $question = $this->pickVariant($normalizedMessage . 'q2', [
                "Quel moment de la journee est le plus difficile pour vous en ce moment ?",
                "Y a-t-il un declencheur precis qui fait monter cette tristesse ?",
                "Sur une echelle de 0 a 10, vous vous sentez a combien la maintenant ?",
            ]);
            $advice = $this->pickVariant($normalizedMessage . 'a2', [
                "Petit conseil: ecrivez 3 phrases courtes sur vos emotions, sans vous juger.",
                "Essayez 5 minutes de routine apaisante: respiration lente + eau + lumiere du jour.",
                "Faites une mini-action faisable dans l'heure: douche, repas simple, ou appel court a quelqu'un.",
            ]);

            return $focus !== null
                ? $empathy . " Vous mentionnez \"" . $focus . "\". " . $question . " " . $advice
                : $empathy . " " . $question . " " . $advice;
        }

        if ($emotion === 'anxiete') {
            $empathy = $this->pickVariant($normalizedMessage, [
                "L'anxiete peut epuiser tres vite, je comprends que ce soit dur.",
                "Merci de le dire clairement, c'est deja une etape utile quand l'angoisse monte.",
                "Ce sentiment d'alerte permanente est difficile a porter seul.",
            ]);
            $question = $this->pickVariant($normalizedMessage . 'q3', [
                "Qu'est-ce qui vous inquiete le plus: sante, relations, travail, ou autre ?",
                "Votre anxiete est plutot dans les pensees, le corps, ou les deux ?",
                "Y a-t-il une situation precise qui a declenche cela aujourd'hui ?",
            ]);
            $advice = $this->pickVariant($normalizedMessage . 'a3', [
                "Testez maintenant: inspirer 4s, expirer 6s, pendant 2 minutes.",
                "Posez vos pieds au sol, regardez 5 objets autour de vous, puis nommez-les lentement.",
                "Coupez les ecrans 10 minutes et faites une respiration guidee courte.",
            ]);

            return $empathy . " " . $question . " " . $advice;
        }

        if ($emotion === 'colere') {
            $empathy = $this->pickVariant($normalizedMessage, [
                "La colere signale souvent qu'une limite importante a ete touchee.",
                "Je vois que la tension est forte, et c'est utile de la nommer.",
                "Votre reaction a du sens dans un contexte de frustration.",
            ]);
            $question = $this->pickVariant($normalizedMessage . 'q4', [
                "Qu'est-ce qui a ete l'element declencheur exact ?",
                "Qu'est-ce que cette colere essaie de proteger chez vous ?",
                "A quel moment la colere est montee d'un coup aujourd'hui ?",
            ]);
            $advice = $this->pickVariant($normalizedMessage . 'a4', [
                "Faites une pause de 90 secondes avant toute reponse importante.",
                "Notez en 2 colonnes: ce que je controle / ce que je ne controle pas.",
                "Changez de contexte 5 minutes (marche courte, eau, respiration), puis revenez.",
            ]);

            return $empathy . " " . $question . " " . $advice;
        }

        return "Je suis la pour vous ecouter. Pour mieux vous aider, dites-moi: quelle emotion est la plus forte maintenant, et ce qui la declenche. Petit conseil: formule simple \"je ressens ... parce que ...\".";
    }

    private function detectIntentWithHuggingFace(string $text): ?string
    {
        [$endpoint, $token] = $this->resolveEndpointAndToken($this->hfIntentApiUrl, $this->hfIntentModel);
        if ($endpoint === null) {
            return null;
        }

        $headers = ['Content-Type' => 'application/json'];
        if ($token !== '') {
            $headers['Authorization'] = 'Bearer ' . $token;
        }

        try {
            $response = $this->httpClient->request('POST', $endpoint, [
                'headers' => $headers,
                'json' => [
                    'inputs' => $text,
                    'parameters' => [
                        'candidate_labels' => ['rupture_amoureuse', 'anxiete', 'deprime', 'colere', 'general'],
                        'hypothesis_template' => 'Ce texte parle de {}.',
                    ],
                    'options' => ['wait_for_model' => true],
                ],
                'timeout' => max(1.0, $this->hfEmotionTimeout),
            ]);
            $data = $response->toArray(false);
        } catch (TransportExceptionInterface|\Throwable) {
            return null;
        }

        if (!is_array($data) || isset($data['error'])) {
            return null;
        }

        $labels = $data['labels'] ?? null;
        $scores = $data['scores'] ?? null;
        if (!is_array($labels) || !is_array($scores) || !isset($labels[0], $scores[0])) {
            return null;
        }

        $topLabel = (string) $labels[0];
        $topScore = is_numeric($scores[0]) ? (float) $scores[0] : 0.0;
        $topScore = $topScore > 1.0 ? ($topScore / 100.0) : $topScore;
        if ($topScore < 0.35) {
            return 'general';
        }

        $normalized = mb_strtolower(trim(str_replace(' ', '_', $topLabel)));
        if (!in_array($normalized, ['rupture_amoureuse', 'anxiete', 'deprime', 'colere', 'general'], true)) {
            return 'general';
        }

        return $normalized;
    }

    private function generateEmpathicResponseWithHuggingFace(string $text, string $emotion, string $intent): ?string
    {
        [$endpoint, $token] = $this->resolveEndpointAndToken($this->hfChatApiUrl, $this->hfChatModel);
        if ($endpoint === null) {
            return null;
        }

        $headers = ['Content-Type' => 'application/json'];
        if ($token !== '') {
            $headers['Authorization'] = 'Bearer ' . $token;
        }

        $prompt = "Tu es un psychologue empathique francophone. "
            . "Contexte emotion={$emotion}, intent={$intent}. "
            . "Reponds en 3 phrases max: 1) empathie, 2) une question ouverte, 3) un petit conseil concret. "
            . "Ne pas poser de diagnostic medical. Message utilisateur: " . $text;

        try {
            $response = $this->httpClient->request('POST', $endpoint, [
                'headers' => $headers,
                'json' => [
                    'inputs' => $prompt,
                    'parameters' => [
                        'max_new_tokens' => 140,
                        'temperature' => 0.7,
                        'return_full_text' => false,
                    ],
                    'options' => ['wait_for_model' => true],
                ],
                'timeout' => max(2.0, $this->hfEmotionTimeout + 2.0),
            ]);
            $data = $response->toArray(false);
        } catch (TransportExceptionInterface|\Throwable) {
            return null;
        }

        $generated = $this->extractGeneratedText($data);
        if ($generated === null) {
            return null;
        }

        $generated = trim(preg_replace('/\s+/', ' ', $generated) ?? '');
        if ($generated === '') {
            return null;
        }

        if (mb_strlen($generated) > 700) {
            $generated = mb_substr($generated, 0, 700);
        }

        return $generated;
    }

    /**
     * @param mixed $data
     */
    private function extractGeneratedText(mixed $data): ?string
    {
        if (is_array($data)) {
            if (isset($data['error'])) {
                return null;
            }
            if (isset($data['generated_text']) && is_string($data['generated_text'])) {
                return $data['generated_text'];
            }
            if (array_is_list($data) && isset($data[0]) && is_array($data[0])) {
                if (isset($data[0]['generated_text']) && is_string($data[0]['generated_text'])) {
                    return $data[0]['generated_text'];
                }
                if (isset($data[0]['summary_text']) && is_string($data[0]['summary_text'])) {
                    return $data[0]['summary_text'];
                }
            }
        }

        return null;
    }

    /**
     * @return array{0:?string,1:string}
     */
    private function resolveEndpointAndToken(string $apiUrl, string $model): array
    {
        $endpoint = trim($apiUrl);
        $token = trim($this->hfEmotionApiToken);

        if ($endpoint === '') {
            $modelName = trim($model);
            if ($modelName === '') {
                return [null, $token];
            }
            $endpoint = 'https://router.huggingface.co/hf-inference/models/' . rawurlencode($modelName);
        }

        return [$endpoint, $token];
    }

    /**
     * @param array<int, string> $variants
     */
    private function pickVariant(string $seed, array $variants): string
    {
        if ($variants === []) {
            return '';
        }

        $hash = abs(crc32($seed));
        $index = $hash % count($variants);

        return $variants[$index];
    }

    private function extractFocus(string $rawMessage): ?string
    {
        $clean = trim(preg_replace('/\s+/', ' ', $rawMessage) ?? '');
        if ($clean === '') {
            return null;
        }

        $lower = mb_strtolower($clean);
        $markers = ['parce que', 'car', 'depuis', 'quand', 'a cause de'];
        foreach ($markers as $marker) {
            $pos = mb_stripos($lower, $marker);
            if ($pos !== false) {
                $snippet = trim(mb_substr($clean, $pos));
                if ($snippet !== '') {
                    return mb_substr($snippet, 0, 120);
                }
            }
        }

        return mb_strlen($clean) > 18 ? mb_substr($clean, 0, 120) : null;
    }
}
