<?php

namespace App\Form;

use App\Entity\DocumentAccess;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateTimeType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class DocumentAccessType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('sharedWith', EntityType::class, [
                'class' => User::class,
                'choice_label' => function (User $user) {
                    return $user->getUsername() . ' (' . $user->getEmail() . ')';
                },
                'label' => 'Partager avec',
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Sélectionnez un utilisateur...',
                ],
                'query_builder' => function ($repo) {
                    return $repo->createQueryBuilder('u')
                        ->orderBy('u.username', 'ASC');
                },
            ])
            ->add('permission', ChoiceType::class, [
                'label' => 'Permission',
                'choices' => [
                    'Affichage seulement' => 'view',
                    'Télécharger' => 'download',
                ],
                'attr' => [
                    'class' => 'form-control',
                ],
            ])
            ->add('expiresAt', DateTimeType::class, [
                'label' => 'Date d\'expiration (optionnel)',
                'required' => false,
                'widget' => 'single_text',
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'La permission n\'expirera pas si vide',
                ],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => DocumentAccess::class,
        ]);
    }
}
