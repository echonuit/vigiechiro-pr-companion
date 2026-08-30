---
name: aligner-le-brief-au-produit
description: Use at closure pass 5, once the user documentation is written, to bring the living design document back in line with what was actually delivered. Its reader is a contributor, not a student, and "sans objet" is rare for a chantier that touches product behaviour.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Aligner le brief au produit

## Loi d'airain

```
UN BRIEF QUI DÉCRIT UNE VERSION PÉRIMÉE ÉGARE SON LECTEUR
```

Le brief est le document de **conception vivant** du produit. Son lecteur est un **contributeur**,
pas un étudiant : ce n'est pas un sujet pédagogique, et ce qu'il y lit, il l'applique.

## Annoncer

« J'utilise la compétence aligner-le-brief-au-produit sur ce que <le chantier> a changé de la
conception. »

## Ce qui compte comme élément de conception

La question décide de la passe, et le brief y répond par sa propre structure :

| Famille | Ce qu'elle porte | Combien |
|---|---|---:|
| `P*` | les parcours utilisateurs, de P0 à P17 | 19 fiches |
| `M-*` | les maquettes d'écran | 21 fiches |
| `E*` | les exigences | 10 fiches |
| `O*` | les objectifs qualité | 8 fiches |
| `.mcd` et `.svg` | le modèle conceptuel, et celui de l'intégration plateforme | 2 modèles |

Un chantier qui change un parcours, une maquette, le modèle de données ou une contrainte produit
touche à l'un de ces objets. **« Sans objet » est donc rare** pour un chantier qui touche au
comportement ou à la conception, et se motive quand on le conclut.

## Fonction de garde

```
1. NOMMER    ce que le chantier a change de la CONCEPTION, pas du code.
2. OUVRIR    les fiches de la famille concernee : parcours, maquette, exigence,
             objectif, modele conceptuel.
3. REPERCUTER l evolution, dans les mots d un contributeur.
4. PREVISUALISER avant de livrer : mkdocs serve -f mkdocs-brief.yml
5. MOTIVER   un « sans objet », qui est rare a cette passe.
```

## Le piège du second dépôt

Les sources du brief vivent **dans ce dépôt**, sous `brief/`, à côté de `docs/` et `dev-docs/`. La
passe se fait donc dans la **pull request du chantier**, comme les deux passes de documentation qui
la précèdent.

Il n'y a plus de second dépôt ni de seconde demande de fusion. Le dépôt `echonuit/brief` ne porte
plus que le site construit, publié automatiquement : **le modifier n'a aucun effet**, et c'est le
genre de geste qui paraît avoir marché.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Le chantier n'a pas touché à la conception » | Un parcours, une maquette, le modèle, une contrainte ? « Sans objet » est rare ici |
| « J'écris pour l'évaluateur » | Le lecteur du brief est un contributeur. Ce n'est pas un sujet pédagogique |
| « Je vais corriger ça dans le dépôt du brief » | Il ne porte que le site construit. Le modifier n'a aucun effet |
| « Le rendu doit être bon » | `mkdocs serve -f mkdocs-brief.yml` le dit, la relecture le suppose |
