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

- **S2-01** · *geste: designer-la-source-et-l-inspecter* · Le champ « Dossier source » est en lecture seule.
- **S2-02** · *geste: designer-la-source-et-l-inspecter* · *carton: le dossier est glissé-déposé sur l'écran* · Le glisser-déposer d'un dossier est accepté.
- **S2-03** · *geste: designer-la-source-et-l-inspecter* · L'inspection annonce le journal détecté (LogPR1925492).
- **S2-04** · *geste: designer-la-source-et-l-inspecter* · L'inspection annonce le relevé climatique détecté.
- **S2-05** · *geste: designer-la-source-et-l-inspecter* · L'inspection compte 6 originaux.
- **S2-06** · *geste: designer-la-source-et-l-inspecter* · Aucun bandeau d'avertissement (cas nominal).
- **S2-07** · *geste: designer-la-source-et-l-inspecter* · L'inspection annonce le renommage à venir (lecture seule, originaux intacts).
- **S2-08** · *geste: rattacher-la-nuit-a-son-point* · Le rattachement propose site, point, année, numéro.
- **S2-09** · *geste: rattacher-la-nuit-a-son-point* · La carte de confirmation montre le carré et le point choisi en indigo.
- **S2-10** · *geste: rattacher-la-nuit-a-son-point* · L'aperçu du préfixe `CarXXXXXX-AAAA-PassN-YY-` suit la saisie.
- **S2-11** · *geste: rattacher-la-nuit-a-son-point* · Le formulaire ne porte **plus** de case « Conserver les originaux » : le réglage a rejoint
  **Réglages ▸ Import** (#3471), où [S7](s7-reglages.md) le déroule. Ici on vérifie seulement
  qu'il n'en reste pas de trace, et que rien ne demande deux fois la même chose.
!!! warning "Le clip de S2-12 à S2-16 est FREINÉ, et ne dit rien de la vitesse du produit"

    Ces cinq cas portent sur ce qui se passe **pendant** l'import. Sur les cartes de recette, qui sont
    générées, l'opération dure des **millisecondes** : mesuré sur `sd-nominale` (6 wav) comme sur
    `sd-grosse` (60 wav), le compte rendu de fin est déjà là à l'instruction qui suit le clic. Grossir
    la carte ne change rien, c'est le travail lui-même qui est court.

    Le banc freine donc son exécuteur, d'environ une seconde par fichier
    (`ExecuteurTacheRalenti`). Le clip montre par conséquent une lenteur que le produit n'a pas.

    Ce qu'il démontre : ces cinq surfaces existent et s'enchaînent. Ce qu'il ne démontre **pas** :
    combien de temps un import prend. Sur une vraie carte SD, la durée se juge en séance, pas au clip.

- **S2-12** · *geste: importer-la-nuit* · « Importer cette nuit » affiche une barre de progression déterminée.
- **S2-13** · *geste: importer-la-nuit* · Une estimation de temps restant s'affiche.
- **S2-14** · *geste: importer-la-nuit* · Un bouton « Annuler » est disponible pendant l'import.
- **S2-15** · *geste: importer-la-nuit* · La table de suivi par fichier montre l'état de chaque wav (attente / copie / transformation /
  terminé).
- **S2-16** · *geste: importer-la-nuit* · Le formulaire est gelé pendant l'import.
- **S2-17** · *geste: importer-la-nuit* · Un message de succès conclut l'import.

**Étape 2 · Passage pivot (ouvert après l'import)**

- **S2-18** · *geste: lire-le-passage-pivot* · Le stepper marque « Importé » et « Transformé » comme franchis (un passage naît au statut
  Transformé).
- **S2-19** · *geste: lire-le-passage-pivot* · Une seule carte porte le liseré « recommandée » : Vérifier.
- **S2-20** · *geste: lire-le-passage-pivot* · La carte « Sons & validation » est grisée.
- **S2-21** · *geste: lire-le-passage-pivot* · Le grisé de « Sons & validation » est expliqué (tooltip ou « après dépôt »).
- **S2-22** · *geste: lire-le-passage-pivot* · Le bandeau d'identité affiche date, plage horaire, enregistreur, statut, verdict.
- **S2-23** · *geste: lire-le-passage-pivot* · Le résumé affiche volume bruts, volume transformé, durée, nombre de séquences.
- **S2-24** · *geste: lire-le-passage-pivot* · « Voir la participation » est grisé (passage non lié) avec explication.
- **S2-25** · *geste: lire-le-passage-pivot* · « 🗑 Supprimer » est actif (passage non déposé).
- **S2-26** · *geste: lire-le-passage-pivot* · « ♻ Réactiver ce passage » est absent ou grisé quand l'audio est complet, avec explication.

**Étape 3 · Modale « Modifier le passage »**

- **S2-27** · *geste: modifier-le-passage* · Le libellé du bouton et le titre de la modale sont « Modifier le passage » (la doc dit « Modifier le
  rattachement » : S2-C04).
- **S2-28** · *geste: modifier-le-passage* · Les spinners Année et N° de passage fonctionnent.
- **S2-29** · *geste: modifier-le-passage* · La météo se saisit (températures, vent, couverture).
- **S2-30** · *geste: modifier-le-passage* · « Récupérer la météo » remplit les champs.
- **S2-31** · *geste: modifier-le-passage* · Le matériel micro se saisit (position, hauteur, type en liste fermée).
- **S2-32** · *geste: modifier-le-passage* · Le récapitulatif se met à jour en direct.
- **S2-33** · *geste: renommer-le-passage-sur-le-disque* · Changer le numéro de passage déclenche la confirmation de renommage disque (garde).

**Étape 4 · Diagnostic (depuis le passage)**

*Écran refondu depuis la passe du 14/07 (#1673 ; #1497 GPS et #1498 soin de l'écran corrigés) : items
alignés sur l'écran actuel, à confirmer au re-jeu.*

- **S2-34** · *geste: lire-le-diagnostic-d-un-passage* · La courbe climatique T°/hygrométrie s'affiche, l'axe **gradué en heures**.
- **S2-35** · *geste: lire-le-diagnostic-d-un-passage* · Les anomalies détectées s'affichent (ou leur placeholder).
- **S2-36** · *geste: lire-le-diagnostic-d-un-passage* · Les évènements du journal s'affichent (ou leur placeholder).
- **S2-37** · *geste: lire-le-diagnostic-d-un-passage* · La cohérence horaire indique la fenêtre nocturne (coucher/lever du soleil), et **sous elle
  les deux plages** : « Protocole … » (coucher moins 30 minutes → lever plus 30) et « Enregistré … ».
- **S2-66** · *geste: lire-le-diagnostic-d-un-passage* · Sur cette nuit, qui **ne couvre pas** la fenêtre exigée, un **avertissement** paraît et **dit ce
  qui manque**. Il ne se contente pas de signaler.
- **S2-67** · *geste: lire-le-diagnostic-d-un-passage* · *(carte `nuit-longue`)* Sur une nuit qui **couvre** la fenêtre exigée et la dépasse, l'écran rend
  une **information**, jamais un avertissement : le protocole est un **plancher**, et le dépasser
  n'est pas un défaut. C'est le cas qui ferait rougir la règle inversée, et c'est celui qu'aucune
  case ne portait : l'ancienne rédaction de S2-37 demandait au contraire de vérifier « l'alerte "hors
  nuit" si l'enregistrement déborde », c'est-à-dire de confirmer le défaut (#4984). **À jouer à la
  main** : le comportement est éprouvé par un banc, mais aucun clip ne le montre, la carte de recette
  n'ayant pas de nuit qui couvre la fenêtre (#5061).
- **S2-74** · *geste: lire-le-diagnostic-d-un-passage* · Sous la cohérence horaire, un **second encart** dit ce que le journal établit de la **fin**
  de la nuit. Deux axes distincts : le premier dit si l'enregistrement couvre la fenêtre exigée,
  celui-ci s'il s'est interrompu. Une nuit peut porter les deux (#5093).
- **S2-75** · *geste: un-journal-absent-ou-corrompu* · `sd-sans-journal` : ce second encart **ne dit rien**. Le journal ne couvre pas la nuit, donc
  il n'y a ni interruption à signaler ni fin normale à attester - et son silence n'est pas une preuve
  que la nuit fut entière, le journal étant circulaire (R19).
- **S2-38** · *geste: lire-le-diagnostic-d-un-passage* · L'état GPS du **point d'écoute** est **toujours visible** : « disponible » ou « non renseigné
  (compléter la fiche site) ».
- **S2-39** · *geste: lire-le-diagnostic-d-un-passage* · La **barre de statut** (bas de fenêtre) affiche l'enregistreur diagnostiqué et le **nombre de
  mesures climatiques**.

**Étape 5 · Cas dégradés (un import ou une inspection par fixture)**

- **S2-40** · *geste: les-bandeaux-d-inspection-non-bloquants* · `sd-melange` : bandeau « mélange » (2 enregistreurs), non bloquant.
- **S2-41** · *geste: les-bandeaux-d-inspection-non-bloquants* · `sd-incoherente` : bandeau « incohérence » journal↔wav (série et date), plus ferme.
- **S2-71** · *geste: les-bandeaux-d-inspection-non-bloquants* · *(carte réellement protégée en écriture, ou volume monté en lecture seule pour
  l'occasion)* · Un quatrième bandeau annonce que **le support est monté en lecture seule**. Il dit,
  dans cet ordre : que l'import de cette nuit fonctionne, le geste à faire sur la carte, et que c'est
  la **prochaine** nuit qui est en jeu. Il n'affirme aucune cause : un verrou mécanique poussé sans y
  penser donne le même symptôme qu'une carte en fin de vie (#4991).
- **S2-72** · *geste: les-bandeaux-d-inspection-non-bloquants* · *(même carte)* · **L'import aboutit**, avec ses fichiers et son passage. C'est le point
  qui compte : le bandeau informe, il ne bloque pas. Companion lit la source et n'y écrit jamais (R9),
  y compris pour poser cette question - elle est posée au **volume**, sans aucune écriture.
- **S2-73** · *geste: les-bandeaux-d-inspection-non-bloquants* · Sur `sd-nominale`, ce quatrième bandeau **n'apparaît pas**. Un message qui paraîtrait
  sur toutes les cartes est un message qu'on apprend à ignorer.
- **S2-42** · *geste: une-carte-qui-porte-plusieurs-nuits* · `sd-multi-nuits` : la table des nuits apparaît (3 lignes, n° automatiques, cases Inclure).
- **S2-43** · *geste: une-carte-qui-porte-plusieurs-nuits* · `sd-multi-configs` : deux nuits, et le capteur a été **reconfiguré entre les deux** (384 kHz puis
  256 kHz). Importer les deux, puis ouvrir chaque nuit : chacune annonce la fréquence
  d'acquisition de **sa** session, et non celle de la première (#3460).
- **S2-44** · *geste: un-journal-absent-ou-corrompu* · `sd-sans-journal` : l'absence de journal est signalée, l'import reste possible (mode dégradé).
- **S2-68** · *geste: un-journal-absent-ou-corrompu* · `sd-sans-journal` : la nuit porte le badge **« complétude inconnue »**, et **non** « complète ». Sa
  pastille n'est ni verte ni ambre : rien ne permet de rassurer, rien ne permet d'inquiéter. Avant
  #4990, cette nuit recevait le badge vert le plus rassurant, l'absence de preuve étant lue comme une
  preuve.
- **S2-69** · *geste: un-journal-absent-ou-corrompu* · `sd-sans-journal` · *survoler la pastille* · L'infobulle dit **pourquoi** on ne sait pas (« ses entrées
  ont pu être effacées, une carte pleine effaçant les plus anciennes »), que la nuit est peut-être
  entière, et que ses enregistrements s'importent normalement. Elle n'affirme aucune cause.
- **S2-70** · *geste: une-carte-qui-porte-plusieurs-nuits* · `sd-multi-nuits` : le journal ne couvre que la **première** nuit. Seule celle-ci porte un badge de
  complétude établi ; les deux suivantes disent « complétude inconnue ». C'est le cas ordinaire de R19
  sur le terrain - la carte tourne plusieurs nuits, le journal circulaire perd les plus anciennes - et
  c'est celui que la table présentait comme trois nuits complètes.
- **S2-45** · *geste: un-journal-absent-ou-corrompu* · `sd-journal-corrompu` : l'inspection échoue avec un message compréhensible.
- **S2-46** · *geste: les-bandeaux-d-inspection-non-bloquants* · `sd-prefixee` : bandeau « discordance de préfixe » si le rattachement ne correspond pas.
- **S2-47** · *geste: importer-malgre-des-rejets* · `sd-rejets` : l'import aboutit malgré le faux wav, la zone des rejets liste « nom - raison ».
- **S2-48** · *geste: importer-depuis-une-archive-zip* · `sd-nominale.zip` : la décompression affiche sa barre et son bouton Annuler avant l'inspection.
- **S2-49** · *geste: reimporter-une-nuit-deja-connue* · Ré-inspection de `sd-nominale` : bandeau « nuit déjà importée », informatif.
- **S2-50** · *geste: reimporter-une-nuit-deja-connue* · Rattachement au même point + année + n° : bandeau « n° déjà pris » avec « Utiliser ce n° » et

**Bloc · Gestes de ligne (EPIC #1792)** : non automatisable (rendu du popup).

- **S2-51** · *geste: le-menu-contextuel-du-suivi-des-fichiers* · Pendant un import, clic droit sur une ligne du **suivi des fichiers** : le menu s'ouvre,
  entièrement lisible.
- **S2-52** · *geste: le-menu-contextuel-du-suivi-des-fichiers* · « Copier ▸ Nom du fichier » place le nom de l'enregistrement dans le presse-papier.
- **S2-53** · *geste: le-menu-contextuel-du-suivi-des-fichiers* · « Colonnes… » y figure **en dernier** ; la disposition choisie n'est **pas** mémorisée
  d'un import à l'autre (écran transitoire, assumé).
  « 🗑 Écraser et réimporter » ; « Écraser » enchaîne deux confirmations (principe, puis liste de ce
  qui sera supprimé).

**Bloc · Décompression d'une grosse archive (#2733)** : non automatisable (perception du temps réel).
Nécessite une archive dont **une seule entrée** dépasse le gigaoctet - une nuit non découpée, ou un
`.zip` fabriqué pour l'occasion. Les tests couvrent le mécanisme sur quelques mégaoctets ; ce qui se
vérifie ici est ce qu'un humain **ressent**.

- **S2-54** · *hors-portée: un fichier de plusieurs gigaoctets, et la durée qui va avec : le banc décompresse une fixture en une seconde, et un volume qui défile pendant une seconde ne prouve rien* · Pendant la décompression d'un fichier de plusieurs Go, le **volume écrit défile** à côté du nom du
  fichier, alors que le compteur « X / N fichiers » reste immobile.
- **S2-55** · *hors-portée: un fichier de plusieurs gigaoctets, et la durée qui va avec : le banc décompresse une fixture en une seconde, et un volume qui défile pendant une seconde ne prouve rien* · « Annuler » pendant ce fichier arrête la décompression **sans attendre la fin du fichier** : le
  retour à l'état neutre est perçu comme immédiat, pas au bout de plusieurs minutes.
- **S2-56** · *hors-portée: un fait de DISQUE : ce que le cas observe est ce qui reste - ou ne reste pas - dans le dossier de travail, et l'écran ne le montre pas* · Après cette annulation, aucun dossier `import-zip-*` ne subsiste dans le dossier de travail.

**Bloc · Archive refusée (#2732)** : automatisé au niveau unitaire, à confirmer **à l'écran** - c'est
la lisibilité du bandeau qui se juge ici, pas la règle.

- **S2-57** · *hors-portée: un support dont la place disponible est mesurable et insuffisante : le banc ne sait pas fabriquer un disque presque plein* · Une archive dont le contenu décompressé dépasse la place disponible est refusée **avant** que quoi
  que ce soit ne soit écrit ; le bandeau donne les deux volumes (nécessaire, disponible).
- **S2-58** · *hors-portée: un support dont la place disponible est mesurable et insuffisante : le banc ne sait pas fabriquer un disque presque plein* · Le bandeau du refus est lisible **en entier** : la phrase qui dit quoi faire n'est pas tronquée.

**Étape 6 · Ce que l'import, le rattachement et la suppression **annoncent** (stabilisation #3424)**

> Ces six faits portent sur le **compte rendu**, pas sur l'action : dans les quatre cas, l'action était
> juste et le message mentait. Ils se jugent donc sur ce que l'écran **dit**, confronté à ce qui s'est
> réellement produit - et deux d'entre eux exigent de regarder **ailleurs que dans l'application**.

- **S2-59** · *geste: ce-que-l-import-annonce-connecte* · 🔌 Connecté, importer une nuit : le compte rendu annonce une participation créée, **et** elle existe
  réellement sur la plateforme (« Voir la participation » l'ouvre). #3448
- **S2-60** · *geste: ce-que-l-import-annonce-connecte* · La même annonce dit **ce qu'il reste à faire** : « pensez à la compléter sur le portail (météo,
  matériel, commentaires) ». Sans cette suite, une création se lit comme une fiche terminée (#3473).
  Numéro hors suite : les points 60 à 64 sont écrits, les renuméroter décalerait la section.
- **S2-61** · *geste: ce-que-l-import-annonce-deconnecte* · 🔒 Déconnecté, importer : le compte rendu **ne prétend pas** avoir créé de participation.
- **S2-62** · *geste: ce-que-le-rattachement-annonce-des-renommages* · Rattacher une nuit dont des séquences doivent être renommées : le compte rendu **chiffre** les
  séquences renommées. #3449
- **S2-63** · *geste: ce-que-l-import-annonce-deconnecte* · 🔒 Faire échouer l'envoi (se déconnecter avant de valider) : le compte rendu dit **à la fois** le
  renommage réussi **et** l'échec de l'envoi, et non l'échec seul.
- **S2-64** · *geste: ce-que-la-suppression-annonce* · « 🗑 Supprimer » : la confirmation dit que les **fichiers audio restent sur le disque**, et affiche
  **où**. #3482
- **S2-65** · *hors-portée: un fait de DISQUE : ce que le cas observe est ce qui reste - ou ne reste pas - dans le dossier de travail, et l'écran ne le montre pas* · Après confirmation, regarder le disque : le dossier de la nuit est **toujours là**, conformément à
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
