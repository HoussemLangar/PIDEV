<?php

namespace App\Controller;

use App\Entity\User;
use App\Form\UsersType;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Security\Core\Authentication\Token\UsernamePasswordToken;
use Symfony\Component\Security\Core\Authentication\Token\Storage\TokenStorageInterface;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Component\Mime\Address;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

class RegistrationController extends AbstractController
{
    // -------------------------------------------------------
    // Helper: generate & store a math captcha in the session
    // -------------------------------------------------------
    private function buildCaptcha(Request $request): string
    {
        $ops   = ['+', '-', '×'];
        $op    = $ops[array_rand($ops)];
        $a     = random_int(2, 15);
        $b     = random_int(1, 10);

        if ($op === '-') {
            // keep result positive
            if ($a < $b) { [$a, $b] = [$b, $a]; }
            $answer = $a - $b;
        } elseif ($op === '×') {
            $a      = random_int(2, 9);
            $b      = random_int(2, 9);
            $answer = $a * $b;
        } else {
            $answer = $a + $b;
        }

        $question = "{$a} {$op} {$b}";
        $request->getSession()->set('captcha_answer', (string) $answer);
        return $question;
    }

    // -------------------------------------------------------
    // AJAX: refresh captcha (returns question JSON)
    // -------------------------------------------------------
    #[Route('/captcha/refresh', name: 'captcha_refresh', methods: ['GET'])]
    public function refreshCaptcha(Request $request): JsonResponse
    {
        $question = $this->buildCaptcha($request);
        return $this->json(['question' => $question]);
    }

    #[Route('/register', name: 'app_register')]
    public function register(
        Request $request,
        UserPasswordHasherInterface $hasher,
        EntityManagerInterface $em,
        MailerInterface $mailer
    ): Response {
        $user = new User();
        $form = $this->createForm(UsersType::class, $user);
        $form->handleRequest($request);

        // Build the captcha question (GET) or re-use existing (POST will regenerate on error)
        if (!$form->isSubmitted()) {
            $captchaQuestion = $this->buildCaptcha($request);
        } else {
            // Keep whatever question was in session; regenerate only after success/failure
            $captchaQuestion = $request->getSession()->get('captcha_question_display', '? + ?');
        }

        if ($form->isSubmitted() && $form->isValid()) {

            // ---------------- Validate math captcha ----------------
            $submittedCaptcha = trim((string) $form->get('captcha')->getData());
            $expectedAnswer   = $request->getSession()->get('captcha_answer', '__none__');

            if ($submittedCaptcha !== $expectedAnswer) {
                $captchaQuestion = $this->buildCaptcha($request);
                $request->getSession()->set('captcha_question_display', $captchaQuestion);

                $this->addFlash('error', 'Le code de sécurité est incorrect. Veuillez réessayer.');

                return $this->render('front/registre.html.twig', [
                    'registration_form' => $form->createView(),
                    'captcha_question'  => $captchaQuestion,
                ]);
            }
            // -------------------------------------------------------

            $hashedPassword = $hasher->hashPassword(
                $user,
                $form->get('plainPassword')->getData()
            );
            $user->setPassword($hashedPassword);

            $em->persist($user);
            $em->flush();

            $user->setEmailVerified(false);
            $user->setAdminApproved(false);
            $verificationToken = bin2hex(random_bytes(32));
            $user->setEmailVerificationToken($verificationToken);
            $user->defineEmailVerificationExpiry((new \DateTimeImmutable())->modify('+2 days'));

            $em->flush();

            $verifyUrl = $this->generateUrl('app_verify_email', [
                'token' => $verificationToken,
            ], UrlGeneratorInterface::ABSOLUTE_URL);

            $email = (new TemplatedEmail())
                ->from(new Address('houssemlangar17@gmail.com', 'SANTÉA'))
                ->to($user->getEmail())
                ->subject('Confirmation de votre email')
                ->htmlTemplate('emails/verify_email.html.twig')
                ->context([
                    'user'      => $user,
                    'verifyUrl' => $verifyUrl,
                    'expiresAt' => $user->getEmailVerificationExpiresAt(),
                ]);

            $mailer->send($email);

            // Invalidate captcha so it can't be reused
            $request->getSession()->remove('captcha_answer');

            if ($request->isXmlHttpRequest()) {
                return $this->json([
                    'success'  => true,
                    'message'  => 'Compte créé avec succès. Vérifiez votre email.',
                    'redirect' => $this->generateUrl('login'),
                ]);
            }

            $this->addFlash('success', 'Compte créé avec succès. Vérifiez votre email.');
            return $this->redirectToRoute('login');
        }

        if ($form->isSubmitted() && !$form->isValid() && !$request->isXmlHttpRequest()) {
            $this->addFlash('error', 'Veuillez corriger les erreurs du formulaire.');
            // Regenerate captcha on validation error
            $captchaQuestion = $this->buildCaptcha($request);
        }

        if ($request->isXmlHttpRequest() && $form->isSubmitted()) {
            $errors = [];
            foreach ($form->getErrors(true) as $error) {
                $origin = $error->getOrigin();
                $name   = $origin ? $origin->getName() : 'form';
                $errors[$name][] = $error->getMessage();
            }

            return $this->json(['success' => false, 'errors' => $errors], 400);
        }

        $request->getSession()->set('captcha_question_display', $captchaQuestion);

        return $this->render('front/registre.html.twig', [
            'registration_form' => $form->createView(),
            'captcha_question'  => $captchaQuestion,
        ]);
    }
}