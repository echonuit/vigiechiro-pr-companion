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
  produit. La sélection peut être **régénérée** ou **personnalisée**.

**Personnaliser la sélection** ouvre une fenêtre où vous choisissez la **méthode** de constitution
(**RéparTemporel**, réparties sur la nuit, ou **Aléatoire**) et la **taille** de la sélection, avant de la
**régénérer**. Régénérer efface la progression d'écoute (le verdict, lui, est conservé).

![La fenêtre « Personnaliser la sélection d'écoute » : méthode (RéparTemporel / Aléatoire) et taille, avec le bouton Régénérer.](../assets/captures/apercu-qualification-personnaliser.png)

## Poser un verdict

Au terme du contrôle, vous posez un **verdict global** sur la nuit : **OK**, **Douteux** ou
**À jeter**, éventuellement accompagné d'un commentaire, puis vous **enregistrez**.

## État initial

À l'ouverture de l'écran, la sélection de séquences est déjà générée, mais rien n'a encore été
écouté et aucun verdict n'est posé.

![L'écran de qualification à l'ouverture : sélection générée, aucun verdict.](../assets/captures/apercu-qualification-initial.png)

## Raccourcis clavier

Cet écran est pensé pour un travail rapide **au clavier**, sans lâcher l'écoute : <kbd>Espace</kbd>
lance ou met en pause la lecture, <kbd>O</kbd> / <kbd>D</kbd> / <kbd>J</kbd> posent le verdict,
<kbd>Entrée</kbd> l'enregistre, et <kbd>↑</kbd> / <kbd>↓</kbd> passent d'une séquence à l'autre
(quand la liste a le focus). La liste complète est sur la page
[Raccourcis clavier](../raccourcis-clavier.md).
