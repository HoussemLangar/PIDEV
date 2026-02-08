<?php

namespace App\Form;

use App\Entity\Teleconsultation;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateTimeType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class TeleconsultationType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('recipient', EntityType::class, [
                'class' => User::class,
                'choice_label' => function (User $user) {
                    return $user->getUsername() . ' (' . $user->getEmail() . ')';
                },
                'label' => 'Destinataire',
                'attr' => [
                    'class' => 'form-control',
                ],
                'query_builder' => function ($repo) use ($options) {
                    $qb = $repo->createQueryBuilder('u')
                        ->orderBy('u.username', 'ASC');
                    if (!empty($options['recipient_role'])) {
                        $qb->where('u.role = :role')
                            ->setParameter('role', $options['recipient_role']);
                    }
                    return $qb;
                },
            ])
            ->add('scheduledAt', DateTimeType::class, [
                'label' => 'Date et heure prévues',
                'widget' => 'single_text',
                'attr' => [
                    'class' => 'form-control',
                    'min' => (new \DateTimeImmutable())->format('Y-m-d\TH:i'),
                ],
                'constraints' => [
                    new Assert\GreaterThan([
                        'value' => new \DateTimeImmutable(),
                        'message' => 'La date doit être dans le futur',
                    ]),
                ],
            ])
            ->add('type', ChoiceType::class, [
                'label' => 'Type de consultation',
                'choices' => [
                    'Consultation générale' => 'general',
                    'Suivi' => 'follow_up',
                    'Urgence' => 'emergency',
                    'Diagnostic' => 'diagnostic',
                ],
                'attr' => [
                    'class' => 'form-control',
                ],
            ])
            ->add('description', TextareaType::class, [
                'label' => 'Description / Raison de la consultation',
                'required' => false,
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Décrivez la raison de cette consultation...',
                    'rows' => 4,
                ],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Teleconsultation::class,
            'recipient_role' => null,
        ]);
    }
}
