<?php

namespace App\Controller;

use App\Entity\Conversation;
use App\Entity\Message;
use App\Entity\User;
use App\Form\MessageType;
use App\Repository\ConversationRepository;
use App\Repository\MessageRepository;
use App\Repository\UserRepository;
use App\Service\MessageRealtimePublisher;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/messages', name: 'app_message_')]
#[IsGranted('ROLE_USER')]
class MessageController extends AbstractController
{
    private const PROFESSIONAL_ROLES = ['ROLE_MEDECIN', 'ROLE_PHARMACIEN', 'ROLE_COACH', 'ROLE_NUTRITIONNISTE'];

    public function __construct(
        private ConversationRepository $conversationRepository,
        private MessageRepository $messageRepository,
        private UserRepository $userRepository,
        private MessageRealtimePublisher $messageRealtimePublisher,
        private EntityManagerInterface $em,
    ) {
    }

    /**
     * List all conversations for the current user
     */
    #[Route('', name: 'index', methods: ['GET'])]
    public function index(): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $conversations = $this->conversationRepository->findUserConversations($user);
        $unreadCount = $this->conversationRepository->getUnreadCount($user);

        $availableRecipients = [];
        if ($this->isPatientRole($user)) {
            $availableRecipients = [];
            foreach (self::PROFESSIONAL_ROLES as $role) {
                foreach ($this->userRepository->findByRole($role) as $candidate) {
                    if ($candidate->getId() === $user->getId()) {
                        continue;
                    }
                    $availableRecipients[$candidate->getId()] = $candidate;
                }
            }
            $availableRecipients = array_values($availableRecipients);
        } else {
            $availableRecipients = $this->userRepository->findByRole('ROLE_PATIENT');
        }

        return $this->render('message/index.html.twig', [
            'conversations' => $conversations,
            'unreadCount' => $unreadCount,
            'availableRecipients' => $availableRecipients,
        ]);
    }

    /**
     * Show a specific conversation
     */
    #[Route('/conversation/{id}', name: 'show', methods: ['GET', 'POST'])]
    public function show(Request $request, Conversation $conversation): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        // Security: verify user is part of this conversation
        if (!$conversation->hasUser($user)) {
            throw $this->createAccessDeniedException('Vous n\'avez pas accès à cette conversation.');
        }

        // Handle message form submission
        $message = new Message($conversation, $user, $conversation->getOtherUser($user), '');
        $form = $this->createForm(MessageType::class, $message);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $this->em->persist($message);
            $conversation->touchLastMessageAt(new \DateTimeImmutable());
            $this->em->flush();
            $this->messageRealtimePublisher->publishNewMessage($message);

            return $this->redirectToRoute('app_message_show', ['id' => $conversation->getId()]);
        }

        // Mark all messages as read
        $this->messageRepository->markAllAsRead($user, $conversation);
        $this->em->flush();

        $page = max(1, $request->query->getInt('page', 1));
        $limit = min(100, max(20, $request->query->getInt('limit', 50)));
        $offset = ($page - 1) * $limit;

        // Get conversation messages
        $messages = $this->messageRepository->findConversationMessages($conversation, $offset, $limit);
        $messages = array_reverse($messages); // Show oldest first

        $otherUser = $conversation->getOtherUser($user);

        return $this->render('message/show.html.twig', [
            'conversation' => $conversation,
            'messages' => $messages,
            'form' => $form,
            'otherUser' => $otherUser,
            'page' => $page,
            'limit' => $limit,
            'hasMore' => count($messages) === $limit,
            'liveComponentEnabled' => class_exists(\Symfony\UX\LiveComponent\LiveComponentBundle::class),
        ]);
    }

    /**
     * Start a new conversation with a user
     */
    #[Route('/start/{recipient}', name: 'start', methods: ['GET', 'POST'])]
    public function start(Request $request, User $recipient): Response
    {
        $sender = $this->getUser();
        assert($sender instanceof User);

        if ($sender === $recipient) {
            throw $this->createAccessDeniedException('Vous ne pouvez pas vous envoyer des messages.');
        }

        $this->denyIfInvalidMessagingPair($sender, $recipient);

        $conversation = $this->conversationRepository->findOrCreateConversation($sender, $recipient);

        return $this->redirectToRoute('app_message_show', ['id' => $conversation->getId()]);
    }

    /**
     * API: Send a message (JSON response)
     */
    #[Route('/send', name: 'send', methods: ['POST'])]
    public function send(Request $request): JsonResponse
    {
        $sender = $this->getUser();
        assert($sender instanceof User);

        $data = json_decode($request->getContent(), true);

        if (!isset($data['recipientId']) || !isset($data['content'])) {
            return $this->json(['error' => 'Données invalides'], 400);
        }

        $recipient = $this->userRepository->find($data['recipientId']);
        if (!$recipient) {
            return $this->json(['error' => 'Destinataire introuvable'], 404);
        }

        $this->denyIfInvalidMessagingPair($sender, $recipient);

        $conversation = $this->conversationRepository->findOrCreateConversation($sender, $recipient);

        try {
            $message = new Message($conversation, $sender, $recipient, $data['content']);
            $this->em->persist($message);
            $conversation->touchLastMessageAt(new \DateTimeImmutable());
            $this->em->flush();
            $this->messageRealtimePublisher->publishNewMessage($message);

            return $this->json([
                'success' => true,
                'message' => [
                    'id' => $message->getId(),
                    'content' => $message->getContent(),
                    'createdAt' => $message->getCreatedAt()->format('Y-m-d H:i:s'),
                    'senderName' => $sender->getUsername(),
                ],
            ]);
        } catch (\Exception $e) {
            return $this->json(['error' => 'Erreur lors de l\'envoi du message'], 500);
        }
    }

    /**
     * API: Get conversations summary (for navbar dropdown)
     */
    #[Route('/api/conversations', name: 'api_conversations', methods: ['GET'])]
    public function apiConversations(): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $conversations = $this->conversationRepository->findUserConversationSummaries($user);
        $unreadCount = $this->messageRepository->countUnreadForUser($user);

        $data = array_map(function (array $conv) {
            $avatarName = urlencode(($conv['otherNom'] ?? '') . '+' . ($conv['otherPrenom'] ?? ''));
            return [
                'id' => (int) $conv['id'],
                'otherUserId' => (int) $conv['otherUserId'],
                'otherUserName' => trim(($conv['otherPrenom'] ?? '') . ' ' . ($conv['otherNom'] ?? '')),
                'otherUserAvatar' => 'https://ui-avatars.com/api/?name=' . $avatarName . '&background=0D8ABC&color=fff&size=40',
                'lastMessageAt' => isset($conv['lastMessageAt']) && $conv['lastMessageAt'] instanceof \DateTimeInterface
                    ? $conv['lastMessageAt']->format('Y-m-d H:i:s')
                    : null,
            ];
        }, $conversations);

        return $this->json(['conversations' => $data, 'unread' => $unreadCount]);
    }

    /**
     * API: Get unread messages for a conversation
     */
    #[Route('/unread/{id}', name: 'unread', methods: ['GET'])]
    public function getUnreadMessages(Conversation $conversation): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if (!$conversation->hasUser($user)) {
            return $this->json(['error' => 'Non autorisé'], 403);
        }

        $messages = $this->messageRepository->findUnreadMessages($user, $conversation);

        $messageDtos = array_map(function (Message $message) {
            return [
                'id' => $message->getId(),
                'content' => $message->getContent(),
                'sender' => $message->getSender()->getUsername(),
                'createdAt' => $message->getCreatedAt()->format('Y-m-d H:i:s'),
            ];
        }, $messages);

        return $this->json(['messages' => $messageDtos]);
    }

    /**
     * API: Mark message as read
     */
    #[Route('/{id}/read', name: 'mark_read', methods: ['POST'])]
    public function markAsRead(Message $message): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if ($message->getRecipient() !== $user) {
            return $this->json(['error' => 'Non autorisé'], 403);
        }

        $message->markAsRead();
        $this->em->flush();

        return $this->json(['success' => true]);
    }

    /**
     * API: Get message history between two users
     */
    #[Route('/history/{recipientId}', name: 'history', methods: ['GET'])]
    public function getHistory(int $recipientId, Request $request): JsonResponse
    {
        $user = $this->getUser();
        assert($user instanceof User);

        $recipient = $this->userRepository->find($recipientId);
        if (!$recipient) {
            return $this->json(['error' => 'Utilisateur introuvable'], 404);
        }

        $this->denyIfInvalidMessagingPair($user, $recipient);

        $page = max(1, $request->query->getInt('page', 1));
        $limit = min(100, max(20, $request->query->getInt('limit', 50)));
        $offset = ($page - 1) * $limit;

        $messages = $this->messageRepository->findHistoryBetweenUsers($user, $recipient, $offset, $limit);

        $messageDtos = array_map(function (Message $message) use ($user) {
            return [
                'id' => $message->getId(),
                'content' => $message->getContent(),
                'sender' => $message->getSender()->getId(),
                'senderName' => $message->getSender()->getUsername(),
                'isOwn' => $message->getSender() === $user,
                'createdAt' => $message->getCreatedAt()->format('Y-m-d H:i:s'),
                'isRead' => $message->isRead(),
            ];
        }, $messages);

        return $this->json([
            'messages' => $messageDtos,
            'page' => $page,
            'limit' => $limit,
            'hasMore' => count($messageDtos) === $limit,
        ]);
    }

    /**
     * Delete a conversation
     */
    #[Route('/{id}/delete', name: 'delete', methods: ['POST'])]
    public function deleteConversation(Conversation $conversation): Response
    {
        $user = $this->getUser();
        assert($user instanceof User);

        if (!$conversation->hasUser($user)) {
            throw $this->createAccessDeniedException();
        }

        $this->em->remove($conversation);
        $this->em->flush();

        return $this->redirectToRoute('app_message_index');
    }

    private function denyIfInvalidMessagingPair(User $sender, User $recipient): void
    {
        $senderIsPatient = $this->isPatientRole($sender);
        $recipientIsPatient = $this->isPatientRole($recipient);
        $senderIsProfessional = $this->isProfessionalRole($sender);
        $recipientIsProfessional = $this->isProfessionalRole($recipient);

        if ($senderIsPatient && !$recipientIsProfessional) {
            throw $this->createAccessDeniedException('Vous pouvez contacter uniquement des professionnels de santé.');
        }

        if ($senderIsProfessional && !$recipientIsPatient) {
            throw $this->createAccessDeniedException('Vous pouvez contacter uniquement des patients.');
        }

        if (!$senderIsPatient && !$senderIsProfessional) {
            throw $this->createAccessDeniedException('Rôle non autorisé pour la messagerie d\'accompagnement.');
        }
    }

    private function isPatientRole(User $user): bool
    {
        return $this->getEffectiveRole($user) === 'ROLE_PATIENT';
    }

    private function isProfessionalRole(User $user): bool
    {
        return in_array($this->getEffectiveRole($user), self::PROFESSIONAL_ROLES, true);
    }

    private function getEffectiveRole(User $user): string
    {
        return $user->getSubscriptionType() ?: $user->getRole();
    }
}
