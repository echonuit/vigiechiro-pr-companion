---
type: adr
title: "Un portage se prouve par confrontation, pas par relecture"
status: stable
article: A1
chantier: "#5218 (sous-chantier #5215)"
decided_at: 2026-09-05
verification: humaine
verification_note: "aucun motif ne distingue un portage fidèle d'un portage plausible ; la confrontation est un geste, et c'est la revue qui l'exige"
verified:
  - by: human:nedseb
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-05
---

# Un portage se prouve par confrontation, pas par relecture

## Contexte

L'[ADR 5188](5188-bash-disparait-une-tolerance-est-un-delai.md) fait disparaître le shell et pose un
cliquet pour que le corpus ne remonte pas. Elle nomme elle-même ce qu'elle ne tranche pas :

> Le niveau est `probable` et non `certaine` parce qu'aucun compte ne prouve qu'un script a été
> **converti** plutôt que supprimé. Le cliquet borne la dette et rend la cible opposable ; il ne juge
> aucune conversion, et **c'est une relecture qui trie**.

Cinquante scripts sont passés en Python entre le 2026-09-03 et le 2026-09-05. La relecture n'a pas
suffi, et le chantier en porte la mesure.

## La mesure

**Quatre fois, un auto-test était vert pendant que la sortie différait.** Aucun de ces quatre défauts
n'a été trouvé par un cas ; les quatre l'ont été en lançant les deux versions côte à côte.

| Ce qui différait | Ce que l'auto-test en disait | Comment il a été trouvé |
|---|---|---|
| trois espaces parasites sur un chemin de refus, dans trois gardes | vert : ces chemins se jugent au code de sortie | confrontation manuelle |
| l'ordre d'écriture des lignes de `capture_screenshots` | vert : les lignes y sont toutes | confrontation manuelle |
| l'ordre à clé égale d'un tri, `sort -rn` comparant la ligne entière en dernier ressort | vert : aucun cas n'avait deux ex æquo | un jeu réel de trois clips |
| une zone de cliquet tombée à zéro fichier | vert en local, ROUGE en CI | la CI, après fusion |

À l'inverse, **la relecture du diff n'en a trouvé aucun**. Elle a servi à autre chose : voir ce que le
portage décidait de ne pas porter.

## Décision

**Un portage se prouve en lançant les deux versions, mode par mode, et en comparant leurs sorties par
`diff`.** Pas en relisant le diff du code.

Trois choses en découlent, et chacune a coûté d'être apprise.

**Tous les modes, y compris ceux que l'auto-test n'atteint pas.** Les cinq modes de la porte APT
s'arrêtaient tous avant d'atteindre `apt-get` ; il a fallu un `apt-get` de comptoir pour éprouver la
séquence d'appels, les bornes et la reprise. C'est là qu'un défaut d'ordre a été trouvé, invisible
aux cinq autres.

**Un leurre par outil absent**, selon l'[ADR 4331](4331-un-garde-execute-la-regle-qu-il-juge.md) : un
script qui s'arrête avant sa ligne intéressante ne prouve rien. `cairosvg`, `icotool`,
`appimagetool`, `desktop-file-validate`, `gh`, `apt-get`, `sudo` et `dpkg` ont tous eu le leur.

**L'écart systématique se déclare une fois, et le reste doit être vide.** `printf '%-Ns'` remplit
jusqu'à N **octets**, `{:<N}` compte des **caractères** : tout libellé accentué s'aligne donc
différemment. C'est la seule différence attendue des cinquante conversions, elle est écrite dans
chaque demande, et `diff -b` la neutralise. Un second écart non expliqué arrête la conversion.

## Conséquences

**Ce qu'on gagne.** Un portage cesse d'être un acte de foi. Le geste rend un verdict opposable -
« `diff -b` est vide sur les cinq modes » - là où « j'ai relu, cela me paraît équivalent » n'en rend
aucun.

**Ce qu'on paie.** Le décor. Un leurre par outil externe, et pour les scripts qui pilotent un serveur
d'affichage il en faut plusieurs. C'est le coût qui a fait garder ce lot pour la fin.

**Ce que cela ne remplace pas.** L'auto-test reste, et il garde un rôle : il juge le comportement
d'aujourd'hui, quand la confrontation ne juge que l'équivalence à hier. Un portage confronté et sans
auto-test serait fidèle à un original que plus personne ne peut relancer.

**Ce que cela n'atteint pas.** Ce qui demande un vrai réseau, un vrai miroir APT, un vrai serveur
d'affichage. Le comptoir les feint et le dit ; feindre sans le dire ferait un vert qui ne prouve rien.

## Ce que cette ADR ne tranche pas

**Aucun garde ne la tient.** Le corpus shell tombe à zéro : un dispositif qui vérifierait qu'une
conversion a été confrontée arriverait après la dernière. Elle vaut pour les portages à venir, quelle
que soit la langue de départ, et c'est la revue qui l'exige.

**La sévérité des auto-tests convertis reste hors de portée**, et #5254 la porte : les gardes de CI
sont en Python et rien ne les mute, alors que la conversion était réputée les faire entrer
gratuitement dans un banc qui existe.
