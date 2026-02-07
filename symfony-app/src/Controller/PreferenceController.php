<?php

namespace App\Controller;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

class PreferenceController extends AbstractController
{
    #[Route('/theme/{theme}', name: 'app_theme')]
    public function setTheme(string $theme, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        $theme = in_array($theme, ['light', 'dark'], true) ? $theme : 'light';
        $session = $request->getSession();
        if ($session) {
            $session->set('theme', $theme);
        }

        $user = $this->getUser();
        if ($user instanceof User) {
            $user->setThemePreference($theme);
            $em->flush();
        }

        return $this->redirect($request->headers->get('referer') ?: $this->generateUrl('app_home'));
    }

    #[Route('/locale/{_locale}', name: 'app_locale')]
    public function setLocale(string $_locale, Request $request, EntityManagerInterface $em): RedirectResponse
    {
        $allowed = ['fr', 'en', 'ar'];
        if (!in_array($_locale, $allowed, true)) {
            $_locale = 'fr';
        }

        $session = $request->getSession();
        if ($session) {
            $session->set('_locale', $_locale);
        }
        $request->setLocale($_locale);

        $user = $this->getUser();
        if ($user instanceof User) {
            $user->setLocale($_locale);
            $em->flush();
        }

        return $this->redirect($request->headers->get('referer') ?: $this->generateUrl('app_home'));
    }
}
