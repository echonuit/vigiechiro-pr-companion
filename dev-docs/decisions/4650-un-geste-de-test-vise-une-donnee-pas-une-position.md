---
type: adr
title: "Un geste de test vise une donnée, et l'exemption qui vise une position se déclare"
status: stable
article: A2
chantier: "#4650 (un dispositif peut être vert sans avoir jugé), passe 11"
decided_at: 2026-09-29
verification: probable
enforced_by:
  - "scripts/adr/4650-double-clic-vise-la-donnee.py"
ratchet: 0
relations:
  applique: ["5398-une-exemption-se-declare-elle-ne-s-infere-pas"]
verified:
  - by: machine:ci
    at: 2026-09-29
generated:
  by: "process:assistance-par-agents"
---

# Un geste de test vise une donnée, et l'exemption qui vise une position se déclare

## Le contexte

Le lot #4554 a livré `DoubleClicDeterministe` : le double-clic envoie l'événement à la ligne trouvée
par son **contenu**, après l'avoir amenée dans le cadre, au lieu de cliquer une position que la mise
en page décide. Le défaut d'origine était discret et coûteux - une ligne hors cadre n'est pas
« introuvable », elle n'est pas encore construite, et le message qui remontait se lisait comme une
absence de donnée.

Douze classes l'emploient aujourd'hui, sur seize sites.

## Ce que la clôture a trouvé

**Quatre appels positionnels subsistent, et les quatre sont légitimes.** Trois éprouvent le chemin
que l'utilisateur emprunte, robot et placement compris ; le quatrième double-clique un champ de
texte, ce que le helper ne couvre pas.

Mais leur raison vivait dans **trois commentaires au point d'appel qui ne se connaissaient pas**, et
deux d'entre eux disaient la même chose - l'un renvoyant à « la même raison qu'au parcours jumeau ».
Une seule des trois porte la raison distincte : le cas est **filmé**, et un événement envoyé
directement à la ligne ne déplace pas le curseur, si bien que le clip montrerait une table qui change
toute seule.

Trois écritures d'une même règle, dont rien ne garantissait qu'elles resteraient d'accord : c'est la
forme que ce chantier traque, rencontrée dans son propre code.

## La décision

**Un geste de test vise la donnée, pas la position. Et l'exemption qui vise la position se déclare,
au point d'appel.**

Le garde ne refuse pas l'appel positionnel : il refuse l'appel positionnel **muet**. La frontière
reste un jugement - la position est-elle ce que ce cas sert à attraper ? - et c'est exactement pour
cela qu'elle se déclare au lieu de s'inférer, comme l'exige l'ADR 5398.

## Ce que cela ne dit pas

Ce n'est pas « le helper partout ». Un parcours E2E qui contournerait le clic **deviendrait stable en
perdant ce qu'il sert à attraper**, et un cas filmé qui l'emploierait montrerait un écran qui bouge
sans geste. Les deux raisons sont bonnes, et elles ne sont pas la même : c'est pourquoi chacune
s'écrit là où elle s'applique plutôt que de se déduire d'une liste centrale.

## Les alternatives écartées

- **Interdire l'appel positionnel.** Il aurait fallu réécrire les trois parcours pour qu'ils cessent
  d'éprouver ce qu'ils éprouvent.
- **Une table centrale des sites exemptés.** Elle vieillit sans que rien ne le dise, et elle éloigne
  la raison de l'endroit où on la cherche, c'est-à-dire le code qu'on est en train de lire.
- **Ne rien garder et laisser les commentaires suffire.** C'est l'état d'avant : la règle était vraie,
  écrite trois fois, et un cinquième appel muet n'aurait rien fait rougir.

## Comment on le sait

`scripts/adr/4650-double-clic-vise-la-donnee.py` compte les appels **sans exemption déclarée**, avec
un cliquet à zéro. Il lit l'arbre syntaxique et non un motif, parce que la javadoc du helper cite
`doubleClickOn` pour dire ce qu'il imite : un `grep` la compte comme un appel, ce qui est arrivé
pendant la passe 7 de cette clôture.

Son témoin porte les deux moitiés - un appel nu est vu, un appel déclaré ne l'est pas - et la seconde
n'est pas une formalité : la première écriture du garde lisait le **récepteur** au lieu du nom de la
méthode, comptait zéro appel partout, et ce cas-là passait donc **à vide**. Le contraste l'a attrapé
avant la livraison.
