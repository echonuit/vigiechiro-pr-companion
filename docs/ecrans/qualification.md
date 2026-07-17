# Qualification

L'écran **Qualification** (« Vérifier l'enregistrement par échantillonnage ») sert à juger la
**qualité** d'une nuit avant de la déposer. Il combine un contrôle automatique et une écoute par
sondage.

## Vérifier à deux niveaux

![L'écran de qualification : sélection de séquences, vue audio et verdict.](../assets/captures/apercu-qualification.png)

Le tableau de la **sélection d'écoute** se **trie**, se **réorganise** et laisse **choisir ses colonnes**
(clic droit ou menu ☰ « outils ») : voir [Personnaliser les tableaux](../personnaliser-les-tableaux.md).

- un **pré-check synthétique** (couverture horaire de la nuit, nombre de fichiers, cohérence du
  renommage) qui repère d'emblée un défaut grossier ;
- un **contrôle par écoute** sur une **sélection automatique de 10 à 30 séquences** réparties sur la
  nuit. Chaque séquence s'écoute dans la **vue audio** (sonogramme et spectrogramme, avec lecture),
  pour confirmer qu'aucun défaut global (saturation, parasite continu, micro défaillant) ne s'est
  produit. Chaque séquence écoutée reçoit un **verdict par fichier** (voir « Poser un verdict »). La
  sélection peut être **régénérée** ou **personnalisée**.

**Personnaliser la sélection** ouvre une fenêtre où vous choisissez la **méthode** de constitution
(**RéparTemporel**, réparties sur la nuit, ou **Aléatoire**) et la **taille** de la sélection, avant de la
**régénérer**. Régénérer efface la progression d'écoute (le verdict, lui, est conservé).

![La fenêtre « Personnaliser la sélection d'écoute » : méthode (RéparTemporel / Aléatoire) et taille, avec le bouton Régénérer.](../assets/captures/apercu-qualification-personnaliser.png)

## Poser un verdict

Le jugement se fait **à deux niveaux**, du fichier vers la nuit.

**Fichier par fichier.** Pour la séquence en cours d'écoute, la section **« Votre verdict sur ce
fichier »** propose trois boutons : **Bon**, **Mauvais**, **Inexploitable**. Chaque séquence jugée porte
son verdict dans la colonne **Verdict** de la liste, et la **barre de répartition** sous la liste se
colore en conséquence (vert / orange / rouge, avec une queue grise « non jugé » qui recule à mesure que
vous avancez) : un segment rouge qui domine signale d'un coup d'œil un enregistrement à problème.

**Décision d'ensemble.** Dès qu'au moins une séquence est jugée, l'application **propose** un **verdict
global du passage** - « Décision d'ensemble pour toute la nuit, pas pour un seul fichier » - dérivé de vos
verdicts par fichier : **OK** (tout est bon), **Utilisable** (des défauts, mais exploitable) ou
**Inexploitable** (majorité inexploitable). Ce verdict n'est qu'une **proposition** : il **pré-remplit**
votre choix, que vous pouvez **surcharger** (le changement est alors signalé « surchargé »). Vous
**enregistrez** ensuite le verdict global, éventuellement accompagné d'un commentaire.

!!! warning "Un passage Inexploitable ne peut pas être déposé"
    Le verdict **Inexploitable** est **bloquant** pour le dépôt : il faut d'abord **requalifier** la nuit
    (poser un autre verdict) pour pouvoir la déposer.

## État initial

À l'ouverture de l'écran, la sélection de séquences est déjà générée, mais rien n'a encore été
écouté et aucun verdict n'est posé.

![L'écran de qualification à l'ouverture : sélection générée, aucun verdict.](../assets/captures/apercu-qualification-initial.png)

## Raccourcis clavier

Cet écran est pensé pour un travail rapide **au clavier**, sans lâcher l'écoute : <kbd>Espace</kbd>
lance ou met en pause la lecture, <kbd>O</kbd> / <kbd>D</kbd> / <kbd>J</kbd> posent le **verdict global**
(OK / Utilisable / Inexploitable), <kbd>Entrée</kbd> l'enregistre, et <kbd>↑</kbd> / <kbd>↓</kbd> passent
d'une séquence à l'autre
(quand la liste a le focus). La liste complète est sur la page
[Raccourcis clavier](../raccourcis-clavier.md).
