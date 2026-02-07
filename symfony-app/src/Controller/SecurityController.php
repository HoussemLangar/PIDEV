<?php
namespace App\Controller;

use App\Entity\FaceData;
use App\Entity\PasswordResetToken;
use App\Entity\User;
use App\Form\ForgotPasswordType;
use App\Form\ResetPasswordType;
use App\Repository\FaceDataRepository;
use App\Repository\PasswordResetTokenRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Mime\Address;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Http\Authentication\AuthenticationUtils;

class SecurityController extends AbstractController
{
    #[Route('/login', name: 'login')]
    public function login(AuthenticationUtils $authenticationUtils): Response
    {
        if ($this->getUser()) {
            if ($this->isGranted('ROLE_ADMIN')) {
                // Vérifier si la reconnaissance faciale est configurée
                return $this->redirectToRoute('admin_face_verification');
            }
            return $this->redirectToRoute('app_home'); 
        }

        $error = $authenticationUtils->getLastAuthenticationError();
        $lastUsername = $authenticationUtils->getLastUsername();

        return $this->render('front/login.html.twig', [
            'last_username' => $lastUsername,
            'error' => $error,
        ]);
    }

    /**
     * Page de vérification faciale pour les admins
     */
    #[Route('/admin/face-verification', name: 'admin_face_verification')]
    public function faceVerification(FaceDataRepository $faceDataRepository): Response
    {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');
        
        $user = $this->getUser();
        $faceData = $faceDataRepository->findOneBy(['user' => $user]);

        return $this->render('security/face_verification.html.twig', [
            'hasRegisteredFace' => $faceData !== null,
        ]);
    }

    /**
     * Enregistrer les données faciales
     */
    #[Route('/admin/register-face', name: 'admin_register_face', methods: ['POST'])]
    public function registerFace(
        Request $request,
        EntityManagerInterface $em,
        FaceDataRepository $faceDataRepository
    ): JsonResponse {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');

        $data = json_decode($request->getContent(), true);
        
        if (!isset($data['faceDescriptor'])) {
            return new JsonResponse(['success' => false, 'message' => 'Données faciales manquantes'], 400);
        }

        $user = $this->getUser();
        
        // Vérifier si l'utilisateur a déjà des données faciales
        $faceData = $faceDataRepository->findOneBy(['user' => $user]);
        
        if ($faceData) {
            return new JsonResponse([
                'success' => false,
                'message' => 'Visage déjà enregistré'
            ], 409);
        }

        $faceData = new FaceData();
        $faceData->setUser($user);

        $faceData->setFaceDescriptor(json_encode($data['faceDescriptor']));
        
        $em->persist($faceData);
        $em->flush();

        // Marquer la session comme vérifiée après le premier enregistrement
        $request->getSession()->set('face_verified', true);

        return new JsonResponse(['success' => true, 'message' => 'Visage enregistré avec succès']);
    }

    /**
     * Vérifier la reconnaissance faciale
     */
    #[Route('/admin/verify-face', name: 'admin_verify_face', methods: ['POST'])]
    public function verifyFace(
        Request $request,
        FaceDataRepository $faceDataRepository
    ): JsonResponse {
        $this->denyAccessUnlessGranted('ROLE_ADMIN');

        $data = json_decode($request->getContent(), true);
        
        if (!isset($data['faceDescriptor'])) {
            return new JsonResponse([
                'success' => false, 
                'message' => 'Données faciales manquantes'
            ], 400);
        }

        $user = $this->getUser();
        $faceData = $faceDataRepository->findOneBy(['user' => $user]);

        if (!$faceData) {
            return new JsonResponse([
                'success' => false, 
                'message' => 'Aucune donnée faciale enregistrée'
            ], 404);
        }

        $storedDescriptor = json_decode($faceData->getFaceDescriptor(), true);
        $currentDescriptor = $data['faceDescriptor'];

        // Calculer une distance moyenne (différence absolue moyenne par feature)
        $distance = $this->calculateAverageAbsoluteDistance($storedDescriptor, $currentDescriptor);
        
        // Seuil pour la comparaison d'histogrammes RGB (0-255)
        $threshold = 25.0;

        if ($distance < $threshold) {
            // Marquer la session comme vérifiée
            $request->getSession()->set('face_verified', true);
            return new JsonResponse([
                'success' => true, 
                'message' => 'Reconnaissance faciale réussie ✅',
                'distance' => round($distance, 2)
            ]);
        }

        return new JsonResponse([
            'success' => false, 
            'message' => 'Reconnaissance faciale échouée. Similarité: ' . round(max(0, 100 - ($distance / $threshold * 100)), 1) . '%',
            'distance' => round($distance, 2)
        ], 401);
    }

    /**
     * Calculer la distance moyenne absolue entre deux descripteurs
     */
    private function calculateAverageAbsoluteDistance(array $desc1, array $desc2): float
    {
        $count1 = count($desc1);
        $count2 = count($desc2);
        if ($count1 === 0 || $count1 !== $count2) {
            return INF;
        }

        $sum = 0.0;
        for ($i = 0; $i < $count1; $i++) {
            $sum += abs($desc1[$i] - $desc2[$i]);
        }
        return $sum / $count1;
    }

    #[Route('/logout', name: 'app_logout')]
    public function logout(): void
    {
        throw new \LogicException('This method can be blank - it will be intercepted by the logout key on your firewall.');
    }

    #[Route('/verify-email/{token}', name: 'app_verify_email')]
    public function verifyEmail(
        string $token,
        UserRepository $userRepository,
        EntityManagerInterface $em
    ): Response {
        $user = $userRepository->findByEmailVerificationToken($token);
        if (!$user) {
            $this->addFlash('error', 'Lien de vérification invalide.');
            return $this->redirectToRoute('login');
        }

        $expiresAt = $user->getEmailVerificationExpiresAt();
        if ($expiresAt && $expiresAt < new \DateTimeImmutable()) {
            $this->addFlash('error', 'Lien de vérification expiré.');
            return $this->redirectToRoute('login');
        }

        $user->setEmailVerified(true);
        $user->setEmailVerificationToken(null);
        $user->setEmailVerificationExpiresAt(null);
        $user->setUpdatedAt(new \DateTimeImmutable());
        $em->flush();

        $this->addFlash('success', 'Email confirmé. En attente de validation admin.');
        return $this->redirectToRoute('login');
    }

    #[Route('/pending-approval', name: 'app_pending_approval')]
    public function pendingApproval(): Response
    {
        return $this->render('security/pending_approval.html.twig');
    }

    #[Route('/banned', name: 'app_banned')]
    public function banned(): Response
    {
        return $this->render('security/banned.html.twig', [
            'user' => $this->getUser(),
        ]);
    }

    /**
     * Page de demande de réinitialisation de mot de passe
     */
    #[Route('/forgot-password', name: 'app_forgot_password')]
    public function forgotPassword(
        Request $request,
        UserRepository $userRepository,
        EntityManagerInterface $em,
        MailerInterface $mailer,
        PasswordResetTokenRepository $tokenRepository
    ): Response {
        $form = $this->createForm(ForgotPasswordType::class);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
        $userEmail = $form->get('email')->getData();
        $user = $userRepository->findOneBy(['email' => $userEmail]);

        $this->addFlash('success', 'Si cette adresse email existe, vous recevrez un lien de réinitialisation.');

        if ($user) {
            $tokenRepository->deleteExpiredTokensForUser($user);

            $resetToken = new PasswordResetToken();
            $resetToken->setUser($user);

            $em->persist($resetToken);
            $em->flush();

            $resetUrl = $this->generateUrl(
                'app_reset_password',
                ['token' => $resetToken->getToken()],
                UrlGeneratorInterface::ABSOLUTE_URL
            );

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Réinitialisation de votre mot de passe')
                ->htmlTemplate('emails/reset_password.html.twig')
                ->context([
                    'user' => $user,
                    'resetUrl' => $resetUrl,
                    'expiresAt' => $resetToken->getExpiresAt()
                ]);

            $mailer->send($email);
        }

        return $this->redirectToRoute('login');
        }

        return $this->render('security/forgot_password.html.twig', [
            'forgotPasswordForm' => $form->createView()
        ]);
    }

    /**
     * Page de réinitialisation du mot de passe
     */
    #[Route('/reset-password/{token}', name: 'app_reset_password')]
    public function resetPassword(
        string $token,
        Request $request,
        PasswordResetTokenRepository $tokenRepository,
        UserPasswordHasherInterface $passwordHasher,
        EntityManagerInterface $em
    ): Response {
        // Vérifier si le token est valide
        $resetToken = $tokenRepository->findValidToken($token);

        if (!$resetToken) {
            $this->addFlash('error', 'Ce lien de réinitialisation est invalide ou a expiré.');
            return $this->redirectToRoute('app_forgot_password');
        }

        $form = $this->createForm(ResetPasswordType::class);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            // Récupérer l'utilisateur
            $user = $resetToken->getUser();

            // Hasher le nouveau mot de passe
            $hashedPassword = $passwordHasher->hashPassword(
                $user,
                $form->get('plainPassword')->getData()
            );
            $user->setPassword($hashedPassword);

            // Marquer le token comme utilisé
            $resetToken->setIsUsed(true);

            $em->flush();

            $this->addFlash('success', 'Votre mot de passe a été réinitialisé avec succès.');
            return $this->redirectToRoute('login');
        }

        return $this->render('security/reset_password.html.twig', [
            'resetPasswordForm' => $form->createView(),
            'token' => $token
        ]);
    }
}
