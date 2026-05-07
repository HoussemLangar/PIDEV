<?php

namespace App\Form;

use App\Entity\User;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\Extension\Core\Type\PasswordType;
use Symfony\Component\Form\Extension\Core\Type\RepeatedType;
use Symfony\Component\Form\Extension\Core\Type\CheckboxType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\Extension\Core\Type\EmailType;
use Symfony\Component\Form\Extension\Core\Type\TelType;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints\NotBlank;
use Symfony\Component\Validator\Constraints\Length;
use Symfony\Component\Validator\Constraints\IsTrue;

class UsersType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options)
    {
        $builder
            ->add('username', TextType::class, [
                'label' => 'Nom d\'utilisateur',
                'attr' => [
                    'placeholder' => 'Choisissez un nom d\'utilisateur',
                    'class' => 'form-control'
                ]
            ])
            ->add('email', EmailType::class, [
                'label' => 'Adresse email',
                'attr' => [
                    'placeholder' => 'exemple@email.com',
                    'class' => 'form-control'
                ]
            ])
            ->add('plainPassword', RepeatedType::class, [
                'type' => PasswordType::class,
                'mapped' => false,
                'invalid_message' => 'Les mots de passe ne correspondent pas',
                'required' => true,
                'constraints' => [
                    new NotBlank(['message' => 'Le mot de passe est obligatoire.']),
                    new Length(['min' => 8, 'minMessage' => 'Le mot de passe doit contenir au moins {{ limit }} caractères.']),
                ],
                'first_options' => [
                    'label' => 'Mot de passe',
                    'attr' => [
                        'placeholder' => '••••••••',
                        'class' => 'form-control',
                        'required' => 'required'
                    ]
                ],
                'second_options' => [
                    'label' => 'Confirmer le mot de passe',
                    'attr' => [
                        'placeholder' => '••••••••',
                        'class' => 'form-control',
                        'required' => 'required'
                    ]
                ],
            ])
            ->add('nom', TextType::class, [
                'label' => 'Nom',
                'attr' => [
                    'placeholder' => 'Votre nom de famille',
                    'class' => 'form-control'
                ]
            ])
            ->add('prenom', TextType::class, [
                'label' => 'Prénom',
                'attr' => [
                    'placeholder' => 'Votre prénom',
                    'class' => 'form-control'
                ]
            ])
            ->add('dateNaissance', DateType::class, [
                'label' => 'Date de naissance',
                'widget' => 'single_text',
                'html5' => true,
                'attr' => [
                    'class' => 'form-control date-picker',
                    'max' => (new \DateTime())->format('Y-m-d'),
                    'min' => (new \DateTime('-120 years'))->format('Y-m-d'),
                ],
                'input' => 'datetime',
                'help' => 'Vous devez avoir au moins 13 ans pour vous inscrire'
            ])
            ->add('adresse', TextType::class, [
                'label' => 'Adresse',
                'required' => false,
                'attr' => [
                    'placeholder' => 'Rue, ville, code postal',
                    'class' => 'form-control'
                ]
            ])
            ->add('telephone', TelType::class, [
                'label' => 'Téléphone',
                'required' => false,
                'attr' => [
                    'placeholder' => '+216 XX XXX XXX',
                    'class' => 'form-control'
                ]
            ])
            ->add('captcha', TextType::class, [
                'label' => 'Captcha',
                'mapped' => false,
                'required' => true,
                'constraints' => [
                    new NotBlank(['message' => 'Merci de compléter le captcha.']),
                ],
                'attr' => [
                    'placeholder' => 'Tapez le résultat',
                    'autocomplete' => 'off',
                    'class' => 'form-control'
                ]
            ])
            ->add('termsAccepted', CheckboxType::class, [
                'label' => 'J\'accepte les conditions d\'utilisation',
                'mapped' => false,
                'required' => true,
                'constraints' => [
                    new IsTrue(['message' => 'Vous devez accepter les conditions pour continuer.'])
                ],
                'label_attr' => [
                    'class' => 'required'
                ]
            ]);
    }

    public function configureOptions(OptionsResolver $resolver)
    {
        $resolver->setDefaults([
            'data_class' => User::class,
            'attr' => ['novalidate' => 'novalidate'], 
        ]);
    }
}