# S3 · Vérifier

> **Écran propriétaire** : qualification (+ raccourcis clavier au complet).
> **Features** : qualification, audio (volet écoute).
> **Statut : à rejouer au delta.** Jouée le 2026-07-14 sur l'écran **pré-refonte** ; l'écran a depuis
> changé de **modèle de verdict** (chantier #1524 : verdict par fichier + verdict global dérivé, barre
> tricolore), et tous les constats de cette passe ont été corrigés. Ce script cible l'**écran refondu** ;
> la passe du 14/07 est conservée plus bas comme relevé historique. Retour à la [méthode](../index.md).

## Objectif

Pré-check consultatif, échantillon d'écoute et modale de sélection (#1462), écoute réelle, **verdict à
deux niveaux** (par fichier son puis verdict global du passage, proposé et surchargeable), et **tous** les
raccourcis clavier.

## Environnement

- Même lancement que S2 (workspace de recette) ; base issue de S2 (carré 640380, passages Transformés
  dont des dégradés : mélange n°4, sans-journal, zip).
- Session **avec son** (écoute réelle).

## Raccourcis à exercer (tous)

| Touche | Action |
|---|---|
| `Espace` | Lecture / pause de l'extrait |
| `O` / `D` / `J` | Verdict **global** : OK / Utilisable / Inexploitable |
| `Entrée` | Enregistrer le verdict global |
| `↑` / `↓` | Séquence précédente / suivante (liste focalisée) |
| `Échap` | Fermer la modale de sélection |

## Le script (une case = un fait observable)

**Bloc 1 · Pré-check (3 feux, consultatif et explicatif)**

- **S3-01** · Sur un passage Transformé, « Pré-check : » affiche 3 feux : Couverture horaire, Nombre de fichiers,
  Cohérence du renommage.
- **S3-02** · Chaque feu porte son pictogramme ✓ / ⚠ / ✖.
- **S3-03** · Chaque feu **explique la mesure et l'écart** (l'infobulle dit le seuil et la valeur, pas juste
  « à surveiller »).
- **S3-04** · Sur la nuit réelle (peu de fichiers), « Nombre de fichiers » est orange.
- **S3-05** · Sur le passage mélange (n°4), « Cohérence du renommage » est rouge.
- **S3-06** · Avec un feu rouge, « Anomalie au pré-check (consultatif, non bloquant). » s'affiche.
- **S3-07** · Un verdict reste enregistrable malgré un feu rouge (consultatif).

**Bloc 2 · Échantillon + modale de sélection (#1462)**

- **S3-08** · À l'ouverture, la sélection est déjà générée (RéparTemporel), sans écoute ni verdict.
- **S3-09** · Le titre de liste est « Sélection d'écoute » et donne le compte.
- **S3-10** · La table liste N° / Fichier / Durée / Écouté (○ / ✓) / **Verdict**.
- **S3-11** · Les **séquences tronquées** sont **écartées** de l'échantillon (#1507).
- **S3-12** · « Régénérer » direct ne demande confirmation que si au moins une séquence a été écoutée.
- **S3-13** · « Personnaliser… » ouvre la modale « Personnaliser la sélection d'écoute » (2 méthodes RéparTemporel
  / Aléatoire, curseur borné 10→30 par pas de 5, pré-réglée sur l'état courant).
- **S3-14** · L'avertissement « Régénérer efface la progression d'écoute (le verdict est conservé) » est visible en
  permanence.
- **S3-15** · « Régénérer » applique, reconstruit et ferme ; la progression repart à 0.
- **S3-16** · « Annuler » ne touche rien (méthode, taille, progression) : le test clé de #1462.
- **S3-17** · `Échap` **ferme** la modale (#1505).

**Bloc 3 · Écoute et verdict par fichier**

- **S3-18** · Sélectionner une séquence affiche son détail sous « Séquence sélectionnée ».
- **S3-19** · « Vue audio (sonogramme + spectrogramme) » affiche la séquence.
- **S3-20** · Les commandes de la vue audio (« Lecture », « Temps ± », « Fréq. ± ») se **voient** comme des
  boutons : chacune porte un contour net qui la détache du fond de la barre. C'est ce qui manquait
  quand un utilisateur n'a lancé aucune écoute de toute une séance (#3462), le libellé étant
  pourtant lisible.
- **S3-21** · Dès le début de lecture, la séquence passe à « ✓ écoutée » (pas de latence au premier clic, #1509).
- **S3-22** · La section « Votre verdict sur ce fichier » propose **Bon / Mauvais / Inexploitable**.
- **S3-23** · Juger la séquence courante inscrit son verdict dans la colonne **Verdict** de la liste.
- **S3-24** · La **barre de répartition** tricolore reflète les verdicts par fichier (segments Bon / Mauvais /
  Inexploitable + queue grise « non jugé » qui recule), avec un **résumé chiffré** (pas la couleur
  seule).
- **S3-25** · L'axe des fréquences du spectrogramme **s'ajuste au contenu** (#1508).
- **S3-26** · Sur un passage sans séquences : « Aucune séquence à écouter pour l'instant. ».

**Bloc 4 · Verdict global du passage (proposé, dérivé, surchargeable)**

- **S3-27** · La section « Verdict global du passage » porte le sous-titre « Décision d'ensemble pour toute la nuit,
  pas pour un seul fichier ».
- **S3-28** · Tant qu'aucune séquence n'est jugée, la puce « Verdict proposé » est **masquée**.
- **S3-29** · Dès qu'une séquence est jugée, la puce affiche « Proposé : X » (OK / Utilisable / Inexploitable),
  colorée, dérivée des verdicts par fichier.
- **S3-30** · Le verdict proposé **pré-remplit** le verdict global.
- **S3-31** · Surcharger le verdict (choisir autre chose que le proposé) affiche la mention « (surchargé) ».
- **S3-32** · Les 3 boutons du verdict global sont **OK / Utilisable / Inexploitable** (plus « À jeter »).
- **S3-33** · « Enregistrer le verdict » est grisé tant qu'aucun verdict global n'est posé, avec infobulle.
- **S3-34** · Enregistrer met à jour VERDICT ACTUEL et STATUT (→ Vérifié) ; badge « Verdict enregistré » + regrisage.
- **S3-35** · Choisir « Inexploitable » dit son **effet réel** (le passage ne pourra pas être déposé sans
  requalification), sans le mot « lot » (#1510).
- **S3-36** · Un passage au verdict **Inexploitable** est **bloqué au dépôt** (garde + affordance).
- **S3-37** · Quitter avec un verdict non enregistré déclenche la garde de sortie.
- **S3-38** · Rouvrir un passage Vérifié : le bandeau montre le verdict persisté, la répartition par fichier est
  rechargée.

**Bloc 5 · Clavier**

- **S3-39** · `↑`/`↓` dans la liste changent de séquence.
- **S3-40** · `Espace` lance/pause la lecture (#1504 : Espace opérant, ne déclenche pas un bouton focalisé).
- **S3-41** · `O`/`D`/`J` posent le verdict **global** (OK / Utilisable / Inexploitable).
- **S3-42** · `Entrée` enregistre (si verdict global choisi).
- **S3-43** · Curseur dans le commentaire : O/D/J tapent la lettre (raccourcis de verdict suspendus).
- **S3-44** · La légende clavier est visible (footer) et cohérente avec les libellés à l'écran (#1513).

## Corrections à confirmer (constats du 14/07, tous mergés)

Ces constats de la passe pré-refonte sont **corrigés** ; le re-jeu les **confirme** au lieu de les
trouver : #1504 (Espace opérant), #1505 (Échap ferme les modales), #1506 (pré-check explicatif), #1507
(séquences tronquées écartées), #1508 (échelle de fréquences auto), #1509 (préchargement audio + retour
de « Régénérer »), #1510 (« Inexploitable » dit son effet réel), #1511 (emojis → FontIcon), #1514
(passage déposé figé, pas de régression). Restent ouverts : #1512 (parité CLI sélection/pré-check), #1513
(fiche/raccourcis, réalignée sur le verdict à deux niveaux).

## Dernière passe (14/07, écran pré-refonte) : relevé historique

Verdict par axe **avant** la refonte #1524 (conservé pour mémoire ; à refaire au delta) :

| Axe | Verdict | Constats |
|---|---|---|
| C · Conformité | remarque | `Espace` documenté mais inopérant (#1504) ; `Échap` promis, absent (#1505) ; libellés périmés (#1513) |
| E · États | remarque | pré-check qui signale sans expliquer (#1506) ; séquences tronquées (#1507) ; échelle de fréquences (#1508) |
| F · Fluidité | remarque | latence au 1er clic + « Régénérer » muet (#1509) ; « À jeter » ambigu (#1510) ; emojis non rendus (#1511) |
| R · Clavier | **bloquant** | `Espace` sans effet (#1504) ; `O`/`D`/`J`/`Entrée`/`↑`/`↓` conformes |
| P · Parité CLI | remarque | sélection d'écoute et pré-check absents de la CLI + divergence de garde (#1512) |
| D · Doc & captures | remarque | fiche à jour de #1462 ✓ ; raccourcis et doc-comment à réaligner (#1513) |

Constats **infirmés** en séance : S3-C02 (pas de spectrogramme noir en fenêtre réduite) ; S3-C03 (la
réouverture d'un passage vérifié n'a pas dérouté). Constat S3-01 (tri lexicographique de « N° passage »)
versé à #1379.

## Notes de méthode

!!! danger "Faux positif écarté : « QualificationController$1 » = bruit d'environnement"
    En séance, « Une erreur inattendue est survenue - QualificationController$1 » sur O/D/J : **pas un
    défaut produit**. Un **build concurrent** avait régénéré les classes sous la JVM en vol (la classe
    synthétique du `switch` sur `KeyCode` avait disparu de `target/classes`). Après `clean compile` et
    relance, tout fonctionnait sauf le vrai bug `Espace` (#1504).

    **Règle rejouable** : ne pas lancer de build pendant qu'une instance de recette tourne ; relancer sur
    un `clean compile` frais.
