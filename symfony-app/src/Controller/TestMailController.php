<?php

namespace App\Controller;

use Symfony\Component\Mime\Email;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;

class TestMailController extends AbstractController
{
    #[Route('/test-mail', name: 'test_mail')]
    public function sendMail(MailerInterface $mailer): Response
    {
        $email = (new Email())
            ->from('houssemlangar17@gmail.com')
            ->to('houssemlangar3@gmail.com')
            ->subject('Test Symfony Mailer')
            ->text('Si tu reçois ce mail, tout fonctionne 🎉');

        $mailer->send($email);

        return new Response('Email envoyé !');
    }
}