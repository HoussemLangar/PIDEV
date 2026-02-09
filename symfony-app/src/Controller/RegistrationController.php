<?php

namespace App\Controller;

use App\Entity\User;
use App\Form\UsersType;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
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

        if ($form->isSubmitted() && $form->isValid()) {

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
            $user->setEmailVerificationExpiresAt((new \DateTimeImmutable())->modify('+2 days'));

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
                    'user' => $user,
                    'verifyUrl' => $verifyUrl,
                    'expiresAt' => $user->getEmailVerificationExpiresAt(),
                ]);

            $mailer->send($email);

            if ($request->isXmlHttpRequest()) {
                return $this->json([
                    'success' => true,
                    'message' => 'Compte créé avec succès. Vérifiez votre email.',
                    'redirect' => $this->generateUrl('login'),
                ]);
            }

            $this->addFlash('success', 'Compte créé avec succès. Vérifiez votre email.');
            return $this->redirectToRoute('login');
        }

        if ($form->isSubmitted() && !$form->isValid() && !$request->isXmlHttpRequest()) {
            $this->addFlash('error', 'Veuillez corriger les erreurs du formulaire.');
        }

        if ($request->isXmlHttpRequest() && $form->isSubmitted()) {
            $errors = [];
            foreach ($form->getErrors(true) as $error) {
                $origin = $error->getOrigin();
                $name = $origin ? $origin->getName() : 'form';
                $errors[$name][] = $error->getMessage();
            }

            return $this->json(['success' => false, 'errors' => $errors], 400);
        }

        return $this->render('front/registre.html.twig', [
            'registration_form' => $form->createView(),
        ]);
    }
}