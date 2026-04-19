<?php

namespace App\Form;

use App\Entity\Contenu;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\FileType;
use Symfony\Component\Form\Extension\Core\Type\HiddenType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\FormEvent;
use Symfony\Component\Form\FormEvents;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints\File;

class ContenuType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('titre', TextType::class, [
                'label' => 'Titre',
            ])
            ->add('description', TextareaType::class, [
                'label' => 'Description',
                'required' => false,
                'attr' => ['rows' => 3],
            ])
            ->add('type', ChoiceType::class, [
                'label' => 'Type de contenu',
                'choices' => [
                    'Article texte' => 'article',
                    'Vidéo (lien)' => 'video',
                    'PDF (upload)' => 'pdf',
                    'Lien externe' => 'lien',
                ],
            ])
            ->add('contenu', HiddenType::class, [
                'label' => 'Contenu / Lien',
                'required' => false,
            ])
            ->add('articleText', TextareaType::class, [
                'label' => 'Article',
                'mapped' => false,
                'required' => false,
                'attr' => ['rows' => 6],
            ])
            ->add('linkUrl', TextType::class, [
                'label' => 'Lien externe',
                'mapped' => false,
                'required' => false,
            ])
            ->add('categorie', ChoiceType::class, [
                'label' => 'Catégorie',
                'required' => false,
                'placeholder' => 'Sélectionner une catégorie',
                'choices' => [
                    'Santé mentale' => 'Santé mentale',
                    'Général' => 'Général',
                    'Médicaments' => 'Médicaments',
                    'Sport' => 'Sport',
                    'Nutrition' => 'Nutrition',
                    'Bien-être' => 'Bien-être',
                    'Prévention' => 'Prévention',
                    'Maladies chroniques' => 'Maladies chroniques',
                    'Grossesse' => 'Grossesse',
                    'Pédiatrie' => 'Pédiatrie',
                    'Gériatrie' => 'Gériatrie',
                    'Urgences' => 'Urgences',
                    'Dermatologie' => 'Dermatologie',
                    'Cardiologie' => 'Cardiologie',
                    'Dentaire' => 'Dentaire',
                    'Ophtalmologie' => 'Ophtalmologie',
                    'Sexualité' => 'Sexualité',
                    'Addictions' => 'Addictions',
                    'Sommeil' => 'Sommeil',
                    'Stress' => 'Stress',
                    'Alimentation' => 'Alimentation',
                    'Vaccination' => 'Vaccination',
                    'Thérapies' => 'Thérapies',
                    'Médicaments génériques' => 'Médicaments génériques',
                    'Médecine douce' => 'Médecine douce',
                ],
            ])
            ->add('tags', TextType::class, [
                'label' => 'Tags (séparés par des virgules)',
                'required' => false,
            ])
            ->add('pdfFile', FileType::class, [
                'label' => 'Fichier PDF',
                'mapped' => false,
                'required' => false,
                'constraints' => [
                    new File([
                        'maxSize' => '8M',
                        'mimeTypes' => ['application/pdf'],
                        'mimeTypesMessage' => 'Veuillez fournir un PDF valide.',
                    ]),
                ],
            ]);

        $builder->add('videoFile', FileType::class, [
            'label' => 'Fichier vidéo',
            'mapped' => false,
            'required' => false,
            'constraints' => [
                new File([
                    'maxSize' => '80M',
                    'mimeTypes' => ['video/mp4', 'video/webm', 'video/ogg'],
                    'mimeTypesMessage' => 'Veuillez fournir une vidéo valide (MP4, WebM, OGG).',
                ]),
            ],
        ]);

        // Pré-remplir les champs non mappés lors de la modification
        $builder->addEventListener(FormEvents::POST_SET_DATA, function (FormEvent $event): void {
            $contenu = $event->getData();
            $form = $event->getForm();
            
            if ($contenu && $contenu->getId()) {
                // Si c'est une modification, pré-remplir les champs selon le type
                $type = $contenu->getType();
                $contenuText = $contenu->getContenu();
                
                if ($type === 'article' && $contenuText) {
                    // Pré-remplir le champ articleText
                    $form->get('articleText')->setData($contenuText);
                } elseif ($type === 'lien' && $contenuText) {
                    // Pré-remplir le champ linkUrl
                    $form->get('linkUrl')->setData($contenuText);
                }
            }
        });

        $builder->addEventListener(FormEvents::PRE_SUBMIT, function (FormEvent $event): void {
            $data = $event->getData();
            if (!is_array($data)) {
                return;
            }
            $type = $data['type'] ?? null;
            $contenu = trim((string) ($data['contenu'] ?? ''));
            
            if ($type === 'article') {
                $articleText = trim((string) ($data['articleText'] ?? ''));
                // Toujours définir le contenu, même si vide (le contrôleur gérera la validation)
                $data['contenu'] = $articleText;
                $event->setData($data);
                return;
            }
            
            if ($type === 'lien') {
                $linkUrl = trim((string) ($data['linkUrl'] ?? ''));
                // Toujours définir le contenu, même si vide (le contrôleur gérera la validation)
                $data['contenu'] = $linkUrl;
                $event->setData($data);
                return;
            }
            
            if (in_array($type, ['pdf', 'video'], true)) {
                // Pour les fichiers, ne rien faire ici, le contrôleur gère l'upload
            }
        });
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Contenu::class,
        ]);
    }
}
