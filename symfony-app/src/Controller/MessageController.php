<?php

namespace App\Controller;

use App\Entity\Conversation;
use App\Entity\Message;
use App\Entity\User;
use App\Form\MessageType;
use App\Repository\ConversationRepository;
use App\Repository\MessageRepository;
use App\Repository\UserRepository;
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
    public function __construct(
        private ConversationRepository $conversationRepository,
        private MessageRepository $messageRepository,
        private UserRepository $userRepository,
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
        if ($this->isGranted('ROLE_PATIENT')) {
            $availableRecipients = array_filter(
                $this->userRepository->findAll(),
                fn (User $u) => $u->getId() !== $user->getId()
            );
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
            $conversation->setLastMessageAt(new \DateTimeImmutable());
            $this->em->flush();

            return $this->redirectToRoute('app_message_show', ['id' => $conversation->getId()]);
        }

        // Mark all messages as read
        $this->messageRepository->markAllAsRead($user, $conversation);
        $this->em->flush();

        // Get conversation messages
        $messages = $this->messageRepository->findConversationMessages($conversation, 0, 100);
        $messages = array_reverse($messages); // Show oldest first

        $otherUser = $conversation->getOtherUser($user);

        return $this->render('message/show.html.twig', [
            'conversation' => $conversation,
            'messages' => $messages,
            'form' => $form,
            'otherUser' => $otherUser,
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
            $conversation->setLastMessageAt(new \DateTimeImmutable());
            $this->em->flush();

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

        $page = $request->query->getInt('page', 1);
        $limit = 50;
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

        return $this->json(['messages' => $messageDtos]);
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
        $senderIsPatient = $this->isGranted('ROLE_PATIENT');
        $recipientIsPatient = in_array('ROLE_PATIENT', $recipient->getRoles(), true);

        if (!$senderIsPatient && !$recipientIsPatient) {
            throw $this->createAccessDeniedException('Vous ne pouvez contacter que des patients.');
        }
    }
}
