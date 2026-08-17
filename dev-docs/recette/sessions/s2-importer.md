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
    L'import crée la participation Vigie-Chiro **au plus tôt** dès que l'observateur est connecté et le
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
11. Le formulaire ne porte **plus** de case « Conserver les originaux » : le réglage a rejoint
    **Réglages ▸ Import** (#3471), où [S7](s7-reglages.md) le déroule. Ici on vérifie seulement
    qu'il n'en reste pas de trace, et que rien ne demande deux fois la même chose.
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
25. « 🗑 Supprimer » est actif (passage non déposé).
26. « ♻ Réactiver ce passage » est absent ou grisé quand l'audio est complet, avec explication.

**Étape 3 · Modale « Modifier le passage »**

27. Le libellé du bouton et le titre de la modale sont « Modifier le passage » (la doc dit « Modifier le
    rattachement » : S2-C04).
28. Les spinners Année et N° de passage fonctionnent.
29. La météo se saisit (températures, vent, couverture).
30. « Récupérer la météo » remplit les champs.
31. Le matériel micro se saisit (position, hauteur, type en liste fermée).
32. Le récapitulatif se met à jour en direct.
33. Changer le numéro de passage déclenche la confirmation de renommage disque (garde).

**Étape 4 · Diagnostic (depuis le passage)**

*Écran refondu depuis la passe du 14/07 (#1673 ; #1497 GPS et #1498 soin de l'écran corrigés) : items
alignés sur l'écran actuel, à confirmer au re-jeu.*

34. La courbe climatique T°/hygrométrie s'affiche, l'axe **gradué en heures**.
35. Les anomalies détectées s'affichent (ou leur placeholder).
36. Les évènements du journal s'affichent (ou leur placeholder).
37. La cohérence horaire indique la fenêtre nocturne (coucher/lever du soleil), avec l'alerte
    « hors nuit » si l'enregistrement déborde.
38. L'état GPS du **point d'écoute** est **toujours visible** : « disponible » ou « non renseigné
    (compléter la fiche site) ».
39. La **barre de statut** (bas de fenêtre) affiche l'enregistreur diagnostiqué et le **nombre de
    mesures climatiques**.

**Étape 5 · Cas dégradés (un import ou une inspection par fixture)**

40. `sd-melange` : bandeau « mélange » (2 enregistreurs), non bloquant.
41. `sd-incoherente` : bandeau « incohérence » journal↔wav (série et date), plus ferme.
42. `sd-multi-nuits` : la table des nuits apparaît (3 lignes, n° automatiques, cases Inclure).
43. `sd-multi-configs` : deux nuits, et le capteur a été **reconfiguré entre les deux** (384 kHz puis
    256 kHz). Importer les deux, puis ouvrir chaque nuit : chacune annonce la fréquence
    d'acquisition de **sa** session, et non celle de la première (#3460).
44. `sd-sans-journal` : l'absence de journal est signalée, l'import reste possible (mode dégradé).
45. `sd-journal-corrompu` : l'inspection échoue avec un message compréhensible.
46. `sd-prefixee` : bandeau « discordance de préfixe » si le rattachement ne correspond pas.
47. `sd-rejets` : l'import aboutit malgré le faux wav, la zone des rejets liste « nom - raison ».
48. `sd-nominale.zip` : la décompression affiche sa barre et son bouton Annuler avant l'inspection.
49. Ré-inspection de `sd-nominale` : bandeau « nuit déjà importée », informatif.
50. Rattachement au même point + année + n° : bandeau « n° déjà pris » avec « Utiliser ce n° » et

**Bloc · Gestes de ligne (EPIC #1792)** : non automatisable (rendu du popup).

51. Pendant un import, clic droit sur une ligne du **suivi des fichiers** : le menu s'ouvre,
    entièrement lisible.
52. « Copier ▸ Nom du fichier » place le nom de l'enregistrement dans le presse-papier.
53. « Colonnes… » y figure **en dernier** ; la disposition choisie n'est **pas** mémorisée
    d'un import à l'autre (écran transitoire, assumé).
    « 🗑 Écraser et réimporter » ; « Écraser » enchaîne deux confirmations (principe, puis liste de ce
    qui sera supprimé).

**Bloc · Décompression d'une grosse archive (#2733)** : non automatisable (perception du temps réel).
Nécessite une archive dont **une seule entrée** dépasse le gigaoctet - une nuit non découpée, ou un
`.zip` fabriqué pour l'occasion. Les tests couvrent le mécanisme sur quelques mégaoctets ; ce qui se
vérifie ici est ce qu'un humain **ressent**.

54. Pendant la décompression d'un fichier de plusieurs Go, le **volume écrit défile** à côté du nom du
    fichier, alors que le compteur « X / N fichiers » reste immobile.
55. « Annuler » pendant ce fichier arrête la décompression **sans attendre la fin du fichier** : le
    retour à l'état neutre est perçu comme immédiat, pas au bout de plusieurs minutes.
56. Après cette annulation, aucun dossier `import-zip-*` ne subsiste dans le dossier de travail.

**Bloc · Archive refusée (#2732)** : automatisé au niveau unitaire, à confirmer **à l'écran** - c'est
la lisibilité du bandeau qui se juge ici, pas la règle.

57. Une archive dont le contenu décompressé dépasse la place disponible est refusée **avant** que quoi
    que ce soit ne soit écrit ; le bandeau donne les deux volumes (nécessaire, disponible).
58. Le bandeau du refus est lisible **en entier** : la phrase qui dit quoi faire n'est pas tronquée.

**Étape 6 · Ce que l'import, le rattachement et la suppression **annoncent** (stabilisation #3424)**

> Ces six faits portent sur le **compte rendu**, pas sur l'action : dans les quatre cas, l'action était
> juste et le message mentait. Ils se jugent donc sur ce que l'écran **dit**, confronté à ce qui s'est
> réellement produit - et deux d'entre eux exigent de regarder **ailleurs que dans l'application**.

59. 🔌 Connecté, importer une nuit : le compte rendu annonce une participation créée, **et** elle existe
    réellement sur la plateforme (« Voir la participation » l'ouvre). #3448
60. 🔒 Déconnecté, importer : le compte rendu **ne prétend pas** avoir créé de participation.
61. Rattacher une nuit dont des séquences doivent être renommées : le compte rendu **chiffre** les
    séquences renommées. #3449
62. 🔒 Faire échouer l'envoi (se déconnecter avant de valider) : le compte rendu dit **à la fois** le
    renommage réussi **et** l'échec de l'envoi, et non l'échec seul.
63. « 🗑 Supprimer » : la confirmation dit que les **fichiers audio restent sur le disque**, et affiche
    **où**. #3482
64. Après confirmation, regarder le disque : le dossier de la nuit est **toujours là**, conformément à
    ce qu'annonçait la confirmation.

> Le point 58 ne se coche pas sur le message : c'est exactement ce que #3448 a corrigé, l'écran
> annonçant une création qui n'avait pas eu lieu. Le fait observable est **sur la plateforme**.
>
> Le point 61 est la moitié qu'on perdait : une opération en deux temps dont la seconde échoue
> annonçait un échec sec, et l'utilisateur ne savait pas si la première avait abouti - donc s'il pouvait
> relancer sans risque.
>
> Ce que devient le dossier du point 63 se joue en [S6](s6-exploiter-piloter.md), à l'audit : c'est lui
> qui le ramasse.

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
