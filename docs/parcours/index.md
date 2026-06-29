# Parcours métier

Le traitement d'une nuit de capture suit toujours le même fil : de la **carte SD** jusqu'au
**dépôt** sur la plateforme Vigie-Chiro, puis, quelques jours plus tard, la **validation des
espèces** identifiées. Cette page déroule ce parcours étape par étape.

<div style="text-align:center; margin:1.2rem 0">
  <img src="../assets/illustrations/parcours.svg" style="max-width:100%; width:780px"
       alt="Parcours d'une nuit : Carte SD, puis Importer, Vérifier, Déposer ; dépôt sur Vigie-Chiro qui renvoie les résultats Tadarida 24-48 h après ; puis Valider.">
</div>

| Étape | Ce que vous faites | Écran |
|---|---|---|
| **Importer** | Copier la carte SD, renommer et transformer les enregistrements | Importation |
| **Vérifier** | Contrôler la qualité (pré-check + écoute) et poser un verdict | Qualification |
| **Déposer** | Préparer le lot, le téléverser sur Vigie-Chiro, le marquer déposé | Lot |
| **Valider** | Relire les espèces identifiées par Tadarida | Validation |

## L'écran Passage, votre pivot

Pour une nuit donnée, l'écran **Passage** est le point de pilotage : il affiche le **statut** de la
nuit, un résumé, et des cartes « avancer » vers l'étape suivante (une seule est mise en avant : la
prochaine action recommandée).

![Le pivot Passage au statut « Vérifié » : la carte « Préparer le dépôt » est recommandée, et « Validation Tadarida » reste grisée tant que la nuit n'est pas déposée.](../assets/captures/apercu-passage.png)

Le statut progresse ainsi :

<div style="text-align:center; margin:1.2rem 0">
  <img src="../assets/illustrations/workflow-statut.svg" style="max-width:100%; width:820px"
       alt="Statut d'un passage : Importé, Transformé, Vérifié, Prêt à déposer, Déposé ; une fois déposé, la Validation Tadarida se déverrouille.">
</div>

Une fois la nuit **déposée**, la carte « Validation Tadarida » se déverrouille :

![Le même pivot au statut « Déposé » : la carte « Validation Tadarida » est désormais accessible.](../assets/captures/apercu-passage-depose.png)

!!! warning "Le dépôt précède la validation"
    La **validation des espèces** (Tadarida) n'est accessible qu'une fois le **lot déposé** :
    Vigie-Chiro ne renvoie les résultats d'identification que **24 à 48 h après le dépôt**.
    L'application verrouille donc cette étape tant que le passage n'est pas au statut « Déposé ».
    L'ordre est bien **Vérifier, puis Déposer, puis Valider**.

## Importer la nuit

Branchez la carte SD, puis ouvrez **Importer une nuit**. L'assistant se remplit en trois temps :

![L'assistant d'import en trois temps : dossier source, inspection, rattachement.](../assets/captures/apercu-import-assistant.png)

1. **Dossier source** : désignez le dossier de la carte SD (ou une copie déjà sur disque).
2. **Inspection** : en lecture seule, l'application détecte le journal du capteur, le relevé
   climatique et les enregistrements WAV, et annonce ce qu'elle va renommer. **Aucun fichier de la
   carte n'est modifié.**
3. **Rattachement** : indiquez le site, le point d'écoute, l'année et le numéro de passage ;
   un aperçu montre le préfixe qui sera appliqué aux fichiers.

Un clic sur **Importer cette nuit** copie les fichiers (sans toucher aux originaux), les renomme
avec le préfixe `CarXXXXXX-AAAA-PassN-YY-`, puis les **transforme** en séquences d'écoute de 5 s
ralenties dix fois (les ultrasons deviennent audibles).

## Vérifier la qualité

Sur l'écran **Qualification**, vous contrôlez que la nuit est exploitable, à deux niveaux :

![L'écran de qualification : liste de séquences, vue audio et boutons de verdict.](../assets/captures/apercu-qualification.png)

- un **pré-check synthétique** (couverture horaire, nombre de fichiers, cohérence du renommage) ;
- un **contrôle par écoute** sur une sélection automatique de 10 à 30 séquences réparties sur la
  nuit, via la vue audio (sonogramme et spectrogramme).

Vous posez ensuite un **verdict global** : OK, Douteux ou À jeter. Cet écran se pilote
efficacement au clavier (voir [Raccourcis clavier](../raccourcis-clavier.md)).

## Déposer le lot

Sur l'écran **Lot**, le dépôt se fait en trois temps :

![L'écran de préparation du lot : récapitulatif et étapes du dépôt.](../assets/captures/apercu-lot-preparer.png)

1. **Préparer le lot** : l'application contrôle la cohérence du passage (préfixes, complétude) et
   ouvre le dossier prêt à déposer dans votre explorateur de fichiers.
2. **Téléverser** ce dossier **manuellement** sur la plateforme Vigie-Chiro, depuis votre navigateur.
3. **Marquer déposé** une fois le téléversement terminé : le passage passe au statut « Déposé ».

![À l'étape « Prêt à déposer », le bouton « Marquer déposé » devient actif.](../assets/captures/apercu-lot-deposer.png)

## Valider les espèces

**24 à 48 h après le dépôt**, Vigie-Chiro renvoie un fichier de résultats **Tadarida** (les espèces
détectées dans chaque séquence, avec leur probabilité). L'écran **Validation** vous permet alors de
relire ces résultats, séquence par séquence, et de **confirmer ou corriger** les identifications.

![L'écran « Sons & validation » : revue des espèces, écoute et actions.](../assets/captures/apercu-sons-validation.png)

Cette étape est accessible une fois le passage déposé (voir l'avertissement plus haut).

## Pour aller plus loin

- [Référence par écran](../ecrans/index.md) : le détail de chaque écran et de ses états.
- [Raccourcis clavier](../raccourcis-clavier.md) : piloter l'application sans la souris.
