# S3 · Vérifier

> **Écran propriétaire** : qualification (+ raccourcis clavier au complet).
> **Features** : qualification, audio (volet écoute). · **Jouée** le 2026-07-14, **avec son**.
> Checklist rejouable + relevé de la dernière passe. Retour à la [méthode](../index.md).

## Objectif

Pré-check consultatif, échantillon d'écoute et modale de sélection (#1462), écoute réelle, pose et
enregistrement du verdict, et **tous** les raccourcis clavier.

## Environnement

- Même lancement que S2 (workspace de recette) ; base issue de S2 (carré 640380, ~10 passages
  Transformés dont des dégradés : mélange n°4, sans-journal, zip).
- Session **avec son** (écoute réelle).

## Raccourcis à exercer (tous)

| Touche | Action |
|---|---|
| `Espace` | Lecture / pause de l'extrait |
| `O` / `D` / `J` | Verdict OK / Douteux / À jeter |
| `Entrée` | Enregistrer le verdict |
| `↑` / `↓` | Séquence précédente / suivante (liste focalisée) |
| `Échap` | Fermer la modale (promesse des raccourcis globaux, à vérifier) |

## Le script (une case = un fait observable)

**Bloc 1 · Pré-check (3 feux, consultatif)**

1. Sur un passage Transformé, le bandeau « Pré-check » affiche 3 feux : Couverture horaire, Nombre de
   fichiers, Cohérence du renommage.
2. Chaque feu porte son pictogramme ✓ / ⚠ / ✖.
3. Chaque feu porte une infobulle (conforme / à surveiller / anomalie).
4. Sur la nuit réelle (peu de fichiers), « Nombre de fichiers » est orange.
5. Sur le passage mélange (n°4), « Cohérence du renommage » est rouge.
6. Avec un feu rouge, la ligne « ⚠ Une anomalie est signalée (consultatif, non bloquant) » s'affiche.
7. Un verdict reste enregistrable malgré un feu rouge (consultatif).
8. La barre de statut affiche « ⚠ Anomalie au pré-check » (prioritaire sur la progression).

**Bloc 2 · Échantillon + modale de sélection (#1462)**

9. À l'ouverture, la sélection est déjà générée (RéparTemporel, ~20), sans écoute ni verdict.
10. Le titre de liste donne le compte : « Sélection d'écoute (N séquences) ».
11. La table liste N° / Fichier / Durée / Écouté (○ / ✓).
12. « ↺ Régénérer » direct ne demande confirmation que si au moins une séquence a été écoutée.
13. « ⚙ Personnaliser… » ouvre la modale « Personnaliser la sélection d'écoute ».
14. La modale propose 2 méthodes : RéparTemporel et Aléatoire.
15. Le curseur de taille est borné 10→30 par pas de 5.
16. La modale s'ouvre pré-réglée sur la méthode et la taille courantes.
17. L'avertissement « Régénérer efface la progression d'écoute (le verdict est conservé) » est visible en
    permanence.
18. « ↺ Régénérer » applique, reconstruit et ferme ; la progression repart à 0.
19. « Annuler » ne touche rien (méthode, taille, progression) : le test clé de #1462.
20. `Échap` sur la modale : la ferme, ou pas ? (S3-C06)

**Bloc 3 · Écoute**

21. Sélectionner une séquence affiche son détail (n°, fichier, durée, écoutée).
22. L'AudioView affiche sonogramme + spectrogramme de la séquence.
23. Dès le début de lecture, la séquence passe à « ✓ écoutée » et la progression avance.
24. La progression affiche « n / N écoutées (x %) ».
25. L'axe des fréquences est en valeurs réelles ×10 (jusqu'à ~192 kHz, pas ~19).
26. Fenêtre raccourcie en hauteur : le spectrogramme reste rendu malgré le ScrollPane (S3-C02).
27. Sur un passage sans séquences : message « Aucune séquence d'écoute à échantillonner… ».
28. Placeholder « Aucune séquence à écouter pour l'instant » quand la liste est vide.

**Bloc 4 · Verdicts**

29. Les 3 boutons ✓ OK / ⚠ Douteux / ❌ À jeter sont présents.
30. Cliquer un verdict le met en surbrillance.
31. Sans verdict choisi, « Enregistrer le verdict » est grisé.
32. Le bouton grisé a une infobulle explicative (« Choisissez d'abord un verdict… »).
33. Choisir « À jeter » affiche l'aperçu « exclura ce passage du prochain lot prêt à déposer ».
34. Enregistrer met à jour VERDICT ACTUEL et STATUT (→ Vérifié).
35. Le badge « ✓ Verdict enregistré » apparaît et Enregistrer se grise.
36. Après « À jeter » enregistré, l'avertissement d'exclusion du lot s'affiche.
37. « À jeter » ne supprime pas le passage (statut Vérifié, verdict À jeter).
38. Quitter avec un verdict choisi non enregistré déclenche la garde de sortie.
39. Rouvrir un passage Vérifié : le bandeau montre le verdict persisté, aucun bouton pré-sélectionné
    (S3-C03).

**Bloc 5 · Clavier**

40. `↑`/`↓` dans la liste changent de séquence.
41. `Espace` lance/pause la lecture.
42. `O`/`D`/`J` posent le verdict.
43. `Entrée` enregistre (si verdict choisi).
44. Curseur dans le commentaire : O/D/J tapent la lettre (raccourcis suspendus), noter si
    `Espace`/`Entrée` sont aussi suspendus (S3-C05).
45. La légende clavier est visible (footer), noter « O vérifié » vs bouton « OK » (S3-C05).

## Verdict par axe (dernière passe)

| Axe | Verdict | Constats |
|---|---|---|
| C · Conformité | remarque | `Espace` documenté mais inopérant (#1504) ; `Échap` promis, absent partout (#1505) ; libellés et note de saisie périmés (#1513) |
| E · États | remarque | pré-check qui signale sans expliquer (#1506) ; séquences tronquées dans l'échantillon (#1507) ; échelle de fréquences incohérente (#1508) |
| F · Fluidité | remarque | latence au premier clic + « Régénérer » muet (#1509) ; « À jeter » ambigu (#1510) ; emojis non rendus (#1511) |
| R · Clavier | **bloquant** | `Espace` sans effet (#1504) ; `O`/`D`/`J`/`Entrée`/`↑`/`↓` conformes |
| P · Parité CLI | remarque | sélection d'écoute et pré-check absents de la CLI + divergence de garde sur passage déposé (#1512) |
| D · Doc & captures | remarque | fiche à jour de #1462 ✓ ; raccourcis et doc-comment à réaligner (#1513) |

## Issues produites (10)

#1504 (Espace + trou de test), #1505 (Échap transverse), #1506 (pré-check explicatif), #1507 (séquences
tronquées écartées de l'échantillon), #1508 (échelle de fréquences auto), #1509 (préchargement audio +
retour de « Régénérer »), #1510 (« À jeter » ambigu), #1511 (emojis non rendus), #1512 (parité CLI +
divergence de garde à confirmer en S4), #1513 (docs). Constat S3-01 (tri lexicographique de « N°
passage ») versé à #1379.

## Renvois et décisions

- Constats **infirmés** par la séance : S3-C02 (pas de spectrogramme noir en fenêtre réduite) ; S3-C03
  (la réouverture d'un passage vérifié, verdict affiché mais boutons non pré-sélectionnés, n'a pas
  dérouté).
- **À reprendre en S4** : confirmer la sémantique de « lot » (formulation de #1510) ; vérifier
  l'atteignabilité de la qualification sur un passage **déposé** (divergence de garde IHM/CLI, #1512 /
  #1514).

## Notes de méthode

!!! danger "Faux positif écarté : « QualificationController$1 » = bruit d'environnement"
    Un premier passage a fait apparaître « Une erreur inattendue est survenue - QualificationController$1 »
    sur O/D/J, et aucune réaction sur Espace/Entrée. Diagnostic : **pas un défaut produit**. `main` a bougé
    pendant la séance et un **build concurrent** a régénéré les classes sous la JVM en vol : la classe
    synthétique du `switch` sur `KeyCode`, chargée à la première frappe, avait disparu de `target/classes`.
    Après `clean compile` et relance : O/D/J/Entrée fonctionnent (`QualificationViewTest` 15/15). Seul
    `Espace` restait cassé : le **vrai** bug (#1504), invisible en test parce que `KeyCode.SPACE` n'y était
    jamais exercé.

    **Règle rejouable** : ne pas lancer de build pendant qu'une instance de recette tourne ; relancer sur
    un `clean compile` frais.
