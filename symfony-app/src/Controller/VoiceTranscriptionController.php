<?php

namespace App\Controller;

use App\Service\VoiceTranscriptionService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/voice', name: 'api_voice_')]
class VoiceTranscriptionController extends AbstractController
{
    #[Route('/transcribe', name: 'transcribe', methods: ['POST'])]
    public function transcribe(Request $request, VoiceTranscriptionService $service): JsonResponse
    {
        $payload = json_decode($request->getContent(), true) ?: [];
        $audio = (string) ($payload['audio'] ?? '');
        if ($audio === '') {
            return new JsonResponse(['success' => false, 'message' => 'Audio manquant'], 422);
        }

        $decoded = base64_decode($audio, true);
        if ($decoded === false || $decoded === '') {
            return new JsonResponse(['success' => false, 'message' => 'Audio invalide'], 422);
        }

        $language = isset($payload['language']) ? trim((string) $payload['language']) : null;
        $tmp = tempnam(sys_get_temp_dir(), 'stt_');
        if (!$tmp) {
            return new JsonResponse(['success' => false, 'message' => 'Stockage temporaire indisponible'], 500);
        }

        try {
            file_put_contents($tmp, $decoded);
            $text = $service->transcribe($tmp, 'audio.wav', $language);
        } finally {
            @unlink($tmp);
        }

        if ($text === null || trim($text) === '') {
            return new JsonResponse(['success' => false, 'message' => 'Transcription indisponible'], 503);
        }

        return new JsonResponse([
            'success' => true,
            'text' => $text,
        ]);
    }
}
