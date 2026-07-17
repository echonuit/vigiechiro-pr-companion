# S2 · Importer une nuit

> **Écrans propriétaires** : importation (+ cas dégradés), modale Rattachement, passage, diagnostic.
> **Features** : importation, passage, diagnostic. · **Jouée** le 2026-07-14.
> Checklist rejouable + relevé de la dernière passe. Retour à la [méthode](../index.md).

## Objectif

Amener une nuit de la carte SD jusqu'au passage pivot : inspection en lecture seule, rattachement,
import (copie protégée + renommage + transformation), puis les cas dégradés d'une carte SD réelle.

## Environnement

- Lancement sur la base issue de S1 (2 sites, aucun passage), workspace de recette.
- Jeux « carte SD » sous `recette-sd/` : `sd-nominale` (6 wav, série 1925492, nuit du 22/04),
  `sd-melange`, `sd-incoherente`, `sd-multi-nuits`, `sd-sans-journal`, `sd-journal-corrompu`,
  `sd-prefixee`, `sd-rejets`, `sd-nominale.zip`.

!!! warning "Garde-fou plateforme (règle de séance)"
    L'import crée la participation VigieChiro **au plus tôt** dès que l'observateur est connecté et le
    site relié (`ServiceImport.creerParticipationSiPossible`). Tous les imports de test se font donc sur
    le **carré 640380** (local, non relié) : **aucune écriture serveur**. Interdiction de rattacher les
    fixtures au carré 130711 (verrouillé). Les écritures plateforme n'arrivent qu'en S4.

## Le script (une case = un fait observable)

**Étape 1 · Import nominal (`sd-nominale`, depuis le détail du carré 640380 → « 📥 Importer une nuit »)**

1. Le champ « Dossier source » est en lecture seule.
2. Le glisser-déposer d'un dossier est accepté.
3. L'inspection annonce le journal détecté (LogPR1925492).
4. L'inspection annonce le relevé climatique détecté.
5. L'inspection compte 6 originaux.
6. Aucun bandeau d'avertissement (cas nominal).
7. L'inspection annonce le renommage à venir (lecture seule, originaux intacts).
8. Le rattachement propose site, point, année, numéro.
9. La carte de confirmation montre le carré et le point choisi en indigo.
10. L'aperçu du préfixe `CarXXXXXX-AAAA-PassN-YY-` suit la saisie.
11. La case « Conserver les originaux sur le disque » est présente.
12. « Importer cette nuit » affiche une barre de progression déterminée.
13. Une estimation de temps restant s'affiche.
14. Un bouton « Annuler » est disponible pendant l'import.
15. La table de suivi par fichier montre l'état de chaque wav (attente / copie / transformation /
    terminé).
16. Le formulaire est gelé pendant l'import.
17. Un message de succès conclut l'import.

**Étape 2 · Passage pivot (ouvert après l'import)**

18. Le stepper marque « Importé » et « Transformé » comme franchis (un passage naît au statut
    Transformé).
19. Une seule carte porte le liseré « recommandée » : Vérifier.
20. La carte « Sons & validation » est grisée.
21. Le grisé de « Sons & validation » est expliqué (tooltip ou « après dépôt »).
22. Le bandeau d'identité affiche date, plage horaire, enregistreur, statut, verdict.
23. Le résumé affiche volume bruts, volume transformé, durée, nombre de séquences.
24. « Voir la participation » est grisé (passage non lié) avec explication.
25. « 📦 Archiver ce passage » est grisé (non déposé) avec explication.
26. « 🗑 Supprimer » est actif (passage non déposé).
27. « 🧹 Purger les originaux » est visible (des bruts existent).
28. « ♻ Réactiver ce passage » est absent ou grisé sur un passage non archivé, avec explication.

**Étape 3 · Modale « Modifier le passage »**

29. Le libellé du bouton et le titre de la modale sont « Modifier le passage » (la doc dit « Modifier le
    rattachement » : S2-C04).
30. Les spinners Année et N° de passage fonctionnent.
31. La météo se saisit (températures, vent, couverture).
32. « Récupérer la météo » remplit les champs.
33. Le matériel micro se saisit (position, hauteur, type en liste fermée).
34. Le récapitulatif se met à jour en direct.
35. Changer le numéro de passage déclenche la confirmation de renommage disque (garde).

**Étape 4 · Diagnostic (depuis le passage)**

36. La courbe climatique T°/hygrométrie s'affiche.
37. La liste des anomalies s'affiche (ou son placeholder).
38. La liste des évènements du journal s'affiche (ou son placeholder).
39. La fenêtre nocturne est indiquée, avec l'alerte « hors nuit » le cas échéant.
40. La disponibilité GPS est indiquée.

**Étape 5 · Cas dégradés (un import ou une inspection par fixture)**

41. `sd-melange` : bandeau « mélange » (2 enregistreurs), non bloquant.
42. `sd-incoherente` : bandeau « incohérence » journal↔wav (série et date), plus ferme.
43. `sd-multi-nuits` : la table des nuits apparaît (3 lignes, n° automatiques, cases Inclure).
44. `sd-sans-journal` : l'absence de journal est signalée, l'import reste possible (mode dégradé).
45. `sd-journal-corrompu` : l'inspection échoue avec un message compréhensible.
46. `sd-prefixee` : bandeau « discordance de préfixe » si le rattachement ne correspond pas.
47. `sd-rejets` : l'import aboutit malgré le faux wav, la zone des rejets liste « nom - raison ».
48. `sd-nominale.zip` : la décompression affiche sa barre et son bouton Annuler avant l'inspection.
49. Ré-inspection de `sd-nominale` : bandeau « nuit déjà importée », informatif.
50. Rattachement au même point + année + n° : bandeau « n° déjà pris » avec « Utiliser ce n° » et
    « 🗑 Écraser et réimporter » ; « Écraser » enchaîne deux confirmations (principe, puis liste de ce
    qui sera supprimé).

## Verdict par axe (dernière passe)

| Écran | C | E | F | R | P | D |
|---|---|---|---|---|---|---|
| Importation | remarque (#1488, #1492, #1493) | remarque (#1486, #1487) | remarque (#1486, #1487, #1489, #1490, #1491) | non exercé | remarque (#1500) | remarque (#1501) |
| Modale « Modifier le passage » | remarque (#1501) | OK / cause ambiguë (#1494) | remarque (#1494, #1495) | OK | OK | remarque (#1501) |
| Passage (pivot) | OK | remarque (#1496) | remarque (#1496) | OK | OK (#1304) | remarque (#1501) |
| Diagnostic | remarque (#1497) | OK | remarque (#1498) | OK | s.o. | OK |

## Issues produites (16)

#1486, #1487, #1488, #1489, #1490, #1491 (fix importation), #1492 (filtrer sur la série du journal),
#1493 (bloquer le préfixe discordant), #1494, #1495 (point éditable), #1496, #1497 (GPS invisible au
diagnostic), #1498 (soigner Diagnostic), #1499 (Alert générique), #1500 (parité CLI import), #1501
(docs).

## Renvois et décisions

- « Importer les observations » inaccessible sur passage déposé : déjà tracé → #1350.
- Captures Passage périmées (S2-C01) et doc archivage (S2-C03) : résolus par les chantiers absorbés
  (#1402 + régénération), constatés à jour.
- Décisions de séance : mélange → filtrage sur la série du journal ; préfixe discordant → blocage ;
  point d'écoute éditable → finalisation ; écran Diagnostic → soigné avant livraison.

## Notes de méthode

Trois vérifications (progression / ETA / annulation) closes comme **non observables en volumétrie
locale** : les vraies nuits font des dizaines de Go. Couvertes par la capture d'état « import en cours »
et les tests d'intégration ; leur part observable réelle est renvoyée à S4 (`sd-grosse` et la nuit de
terrain).
