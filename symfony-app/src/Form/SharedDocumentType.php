<?php

namespace App\Form;

use App\Entity\SharedDocument;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\CheckboxType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\FileType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class SharedDocumentType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $documentChoices = [
            'Analyse médicale' => 'analysis',
            'Rapport médical' => 'report',
            'Résultats laboratoire' => 'lab_results',
            'Imagerie médicale' => 'imaging',
        ];

        if (!$options['is_patient']) {
            $documentChoices += [
                'Ordonnance' => 'prescription',
                'Facture' => 'invoice',
                'Autre' => 'other',
            ];
        }

        $builder
            ->add('file', FileType::class, [
                'label' => 'Fichier',
                'mapped' => false,
                'required' => true,
                'attr' => [
                    'class' => 'form-control',
                    'accept' => '.pdf,.doc,.docx,.jpg,.jpeg,.png,.xls,.xlsx',
                ],
                'constraints' => [
                    new Assert\NotNull(message: 'Veuillez sélectionner un fichier'),
                    new Assert\File([
                        'maxSize' => '50M',
                        'mimeTypes' => [
                            'application/pdf',
                            'application/msword',
                            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                            'image/jpeg',
                            'image/png',
                            'application/vnd.ms-excel',
                            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                        ],
                        'mimeTypesMessage' => 'Le fichier doit être l\'un des formats suivants: PDF, Word, Excel, Images',
                    ]),
                ],
                'help' => 'Formats acceptés: PDF, Word, Excel, Images (max 50 MB)',
            ])
            ->add('description', TextareaType::class, [
                'label' => 'Description',
                'required' => false,
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Décrivez le contenu du document...',
                    'rows' => 3,
                ],
            ])
            ->add('documentType', ChoiceType::class, [
                'label' => 'Type de document',
                'placeholder' => 'Sélectionnez un type de document',
                'choices' => $documentChoices,
                'attr' => [
                    'class' => 'form-control',
                ],
                'constraints' => [
                    new Assert\NotBlank(message: 'Le type de document est obligatoire'),
                ],
            ])
            ->add('public', CheckboxType::class, [
                'label' => 'Rendre ce document accessible au public',
                'required' => false,
                'attr' => [
                    'class' => 'form-check-input',
                ],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => SharedDocument::class,
            'is_patient' => false,
        ]);
    }
}
