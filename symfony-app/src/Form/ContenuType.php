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
            ->add('categorie', TextType::class, [
                'label' => 'Catégorie',
                'required' => false,
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

        $builder->addEventListener(FormEvents::PRE_SUBMIT, function (FormEvent $event): void {
            $data = $event->getData();
            if (!is_array($data)) {
                return;
            }
            $type = $data['type'] ?? null;
            $contenu = trim((string) ($data['contenu'] ?? ''));
            if ($type === 'article') {
                $data['contenu'] = trim((string) ($data['articleText'] ?? ''));
                $event->setData($data);
                return;
            }
            if ($type === 'lien') {
                $data['contenu'] = trim((string) ($data['linkUrl'] ?? ''));
                $event->setData($data);
                return;
            }
            if (in_array($type, ['pdf', 'video'], true) && $contenu === '') {
                $data['contenu'] = 'FILE_UPLOAD';
                $event->setData($data);
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
