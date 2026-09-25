---
type: adr
title: "Un engagement daté se lit par un dispositif, pas par la mémoire de celui qui l'a écrit"
status: stable
article: A11
chantier: "#5538 (le dépôt écrit des engagements datés, et rien ne les lit), lot #5539"
decided_at: 2026-09-25
verification: humaine
verification_note: "une échéance dépassée peut être un choix, et le savoir est un jugement. Un garde qui la refuserait obligerait à retirer la date pour livrer, donc supprimerait le signal au lieu de le lire. Une loupe le relève sans juger"
loupe:
  - "scripts/adr/loupe-5539-echeances-non-tenues.py"
verified:
  - by: human:nedseb
    at: 2026-09-25
relations:
  complete: ["4992-le-critere-de-fin-d-un-lot-se-rappelle-et-se-mesure-il-ne-se-refuse-pas"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-25
---

# Un engagement daté se lit par un dispositif, pas par la mémoire de celui qui l'a écrit

## Le contexte

Ce dépôt écrit des engagements datés dans ses issues. « À tenir vers le 2026-09-20 » pour une passe
de clôture reportée faute de données ; « À relever à partir du 2026-09-20 » pour une mesure qui
demande une fenêtre de quatorze jours.

Les deux étaient justes quand ils ont été écrits, et les deux ont échoué, chacun à sa façon :

| Issue | L'engagement | Ce qui s'est passé |
|---|---|---|
| #5384 | « À tenir vers le 2026-09-20 » | fermée le 2026-09-07, treize jours trop tôt, sur la population que la veille on jugeait impropre |
| #5327 | « À relever à partir du 2026-09-20 » | ouverte et échue, jamais relevée |

Les deux ont été trouvés le même jour, à la main, en reprenant après une pause. Aucun dispositif ne
les avait vus, et aucun n'aurait pu : rien dans ce dépôt ne lit ces dates.

## La décision

**Un engagement daté se relève par un dispositif.** Une loupe hebdomadaire lit les issues, ouvertes
et fermées, et nomme celles qui n'ont pas tenu leur date.

**Dans les deux sens, parce que les deux moitiés sont disjointes.** Une issue ouverte dont
l'échéance est passée, et une issue fermée **avant** la sienne. Un dispositif qui ne regarderait que
les issues ouvertes manquerait la seconde, qui est celle qui a coûté le plus.

**Une loupe, pas un garde.** Une échéance dépassée peut être un choix. Un garde qui refuserait
obligerait à retirer la date pour livrer, c'est-à-dire à supprimer le signal plutôt qu'à le tenir.

## Le signal est le verbe, pas la date

Mesure du 2026-09-25 sur 160 issues ouvertes : **45** portent une date, et **56** de ces dates sont
déjà passées. Presque toutes sont des dates de **mesure**, « mesuré le 2026-09-07 », qui ne
promettent rien.

Un dispositif indexé sur la date rendrait quarante-cinq lignes pour en désigner deux : il noierait sa
sortie, ce qui ne ment pas moins que de n'en rendre aucune. Le signal retenu est donc le **verbe qui
engage** collé à la date, et la liste des verbes vient des formes réellement écrites dans le dépôt,
sans ajout par anticipation.

## La règle d'exclusion vient d'un faux positif

Un troisième candidat, #5381, portait « la seconde clôture de #5294, prévue vers le 2026-09-20 ». Il
**mentionne** l'échéance d'un autre sans en porter aucune, et sa fermeture était juste.

D'où : **une ligne qui nomme une autre issue parle de l'échéance d'un autre**, et une ligne qui cite
un engagement entre guillemets n'en prend aucun. Les deux règles ont été dérivées de faux positifs
réels, le second étant l'EPIC de ce chantier, qui citait ses propres exemples.

## Ce que la décision assume

**La population connue est de deux.** Ce qui justifie le dispositif est le taux, deux engagements sur
deux non tenus, et non le nombre. Une loupe qui relève deux lignes par semaine coûte le temps de les
lire.

**Elle ne voit pas un engagement sans date**, « dans quinze jours », « après la prochaine release ».
Ces formes existent et ne se datent que relativement à autre chose ; les reconnaître demanderait
d'inférer un calendrier, ce que ce dépôt refuse ailleurs.

## Conséquences

- le rapport du lundi nomme les engagements non tenus, et leur nombre se lit dans le temps ;
- une échéance qu'on ne tiendra pas se **ferme par écrit**, en disant pourquoi, plutôt que de
  disparaître ;
- une issue qui porte une date et qu'on ferme avant elle reste visible après sa fermeture, ce qui est
  la seule façon de voir cette moitié-là.
