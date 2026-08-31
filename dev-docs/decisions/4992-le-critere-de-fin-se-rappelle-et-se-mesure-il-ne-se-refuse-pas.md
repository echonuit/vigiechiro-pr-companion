---
type: adr
title: "Le critère de fin d'un lot se rappelle et se mesure, il ne se refuse pas"
status: stable
article: A11
chantier: "#4992, chantier du « fini quand » (#4961)"
decided_at: 2026-08-31
verification: humaine
verification_note: "un critère de fin est de la prose dans le corps d'une issue, et savoir s'il est vérifiable est un jugement. Deux dispositifs le tiennent sans juger : un rappel au moment de l'ouverture, une loupe hebdomadaire sur le stock. Aucun des deux ne refuse"
loupe:
  - "scripts/adr/loupe-4992-lots-sans-critere.py"
verified:
  - by: human:nedseb
    at: 2026-08-31
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-31
---

# Le critère de fin d'un lot se rappelle et se mesure, il ne se refuse pas

## Contexte

`ouvrir-un-chantier` exigeait depuis le 2026-08-29 que chaque lot dise comment on saura qu'il est
fini. La règle vivait dans un paragraphe de fin, et ne disait pas **où** le critère s'écrit. #4951 et
#4975 l'ont portée sur les quatre surfaces de méthode, et ont tranché le lieu : le corps de la
sous-issue, parce qu'un lot est une sous-issue native depuis #4829 et qu'un commentaire descend sous
le fil.

Restait à décider **comment** cette règle se tient. Trois options ont été confrontées hors du dépôt,
puis mesurées.

## Décision

**Deux dispositifs, et aucun ne bloque.**

Un **rappel à l'ouverture** : `rappelle-le-critere-de-fin.sh`, sur l'évènement `issues`, poste un
commentaire une seule fois sur un lot qui ne dit pas son critère.

Une **loupe hebdomadaire** : `loupe-4992-lots-sans-critere.py`, au rapport du lundi, qui nomme les
lots ouverts muets des chantiers ouverts depuis la naissance de la règle.

Les deux couvrent deux trous différents. La forge n'émet aucun évènement au rattachement d'une
sous-issue, et aucun workflow ne peut s'y abonner : un lot rattaché après coup et jamais réédité
échappe au premier, et c'est la loupe qui le voit.

## Pourquoi rien ne bloque

**Le rouge tomberait sur qui n'a pas la main.** Un garde attaché à une demande de fusion jugerait un
chantier ouvert des jours plus tôt par quelqu'un d'autre. Il bloquerait au hasard la première
personne qui arrive, et apprendrait à contourner les rouges, ce qui coûte plus cher que le manque
qu'il signale. Les trois auditeurs consultés l'ont écarté d'une seule voix, chacun par un chemin
différent.

**Et un libellé opposable serait un jeton à poser.** Normaliser une formulation unique rendrait le
motif exact, au prix de récompenser « Fini quand : c'est bon » et de refuser un critère écrit
autrement. Le critère s'écrit de **cinq** façons dans ce dépôt, dont deux viennent de la compétence
elle-même et une du bloc que `CLAUDE.md` prescrit, et le comptage d'origine s'est trompé trois fois
pour cette raison. Elles vivent dans un seul fichier, `scripts/adr/critere-de-fin.motif`, lu par les
deux dispositifs : chacun portait sa copie, et la cinquième a manqué aux deux sans que rien ne le
dise (#4995).

## Ce que la mesure a refusé

**Le gabarit d'issue.** L'arbitrage l'avait d'abord retenu, sur l'idée qu'un champ obligatoire agit à
la saisie. Sur les 1 563 issues du dépôt, **aucune** n'a de corps engendré par un formulaire : tout
naît de `gh issue create`, qui n'en traverse aucun. Ce qu'un agent lit avant de taper la commande est
`AGENTS.md`, que #4975 a corrigé : la prévention à la saisie est portée par la prose, pas par un
formulaire.

**Le comptage d'origine.** #4951 annonçait 3 EPIC sur 70. Il mêlait 118 chantiers antérieurs à la
règle avec les 22 qui pouvaient lui obéir, et ne cherchait qu'une des formulations en usage. Refait :
11 chantiers sur 22 portaient un critère, et au grain du lot, 9 lots ouverts sur 11 n'en portaient
aucun.

## Conséquences

**Le stock ne descend que si quelqu'un lit le rapport.** Une loupe rend 0 en signalant. C'est le prix
assumé de ne rien bloquer, et ce qui empêche le stock de grossir est le rappel à l'ouverture, pas la
loupe.

**Une quatrième formulation échappera au motif.** Signaler à tort coûte une ligne de rapport qu'un
lecteur écarte ; se taire à tort laisse un lot muet, que le rappel aura déjà signalé s'il est neuf.
Le motif peut donc rester généreux là où un cliquet aurait dû être exact.

**Deux loupes de forge parlent enfin au rapport hebdomadaire.** Elles refusaient sans jeton, et
`adr-rapport.yml` n'en posait que sur l'étape qui ouvre une PR. Le jeton et le droit `issues: read`
sont posés sur l'étape qui publie le rapport. La loupe 4712 en profite : muette depuis son écriture,
elle affiche désormais ses candidats.

**Le corpus s'arrête au 2026-08-29.** Un chantier ouvert avant la règle ne pouvait pas y répondre.
Cette borne est un fait historique et ne se met pas à jour.

**La loupe prend l'union des deux définitions d'un EPIC.** Le label `epic` en désigne 92, le titre
`[epic]` ou `[chantier]` en désigne 130, et aucune population ne contient l'autre. Rater un chantier
reviendrait à ne pas poser la question. La divergence elle-même est consignée en #4948.
