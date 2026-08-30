---
type: adr
title: "Un garde qui interroge la forge juge l'objet de sa demande, pas le dépôt entier"
status: stable
article: A3
chantier: "#4649 (chantier #4643)"
decided_at: 2026-08-30
verification: humaine
loupe: "aucun motif ne distingue une portée juste d'une portée large : la question se pose à l'écriture de chaque garde qui interroge la forge, et il n'y en a que deux"
verified:
  - by: humain
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un garde qui interroge la forge juge l'objet de sa demande, pas le dépôt entier

## Contexte

Le dépôt porte deux gardes qui interrogent la forge plutôt que des fichiers. `verifie-cloture-consignee.sh`
compte les EPIC clos sans trace de clôture ; `verifie-chantier-de-l-issue.sh` refuse une demande de
fusion dont l'issue n'appartient à aucun chantier.

Le second devait d'abord balayer **toutes** les issues ouvertes et assignées. C'était le dessin écrit
dans #4649, et il paraissait le plus fort : un garde qui regarde tout attrape tout.

## Le défaut d'un balayage global

**Il accuse des gens qui ne peuvent rien y faire.** Une demande de fusion rougit alors pour l'état
d'une issue qui ne la concerne pas, et dont l'auteur n'est peut-être pas là.

La fenêtre n'est pas théorique, elle a été mesurée pendant la clôture de #4643. La population
d'issues ouvertes, assignées et sans chantier est passée de zéro à un, puis de nouveau à zéro, en une
demi-heure : #4860 a été créée et assignée à 12h31, et rattachée peu après. **Cette fenêtre est
normale** : on s'assigne une issue avant d'avoir tranché à quel chantier elle appartient.

Le dépôt connaissait déjà le défaut sous son autre forme. L'en-tête de `verifie-cloture-consignee.sh`
l'écrit : « refuser tout net rendrait le dépôt rouge sans qu'aucune PR soit fautive, et le garde se
ferait désactiver la première semaine ». Il s'en était sorti par un cliquet, parce que 43 clôtures
manquaient déjà. Ce remède ne s'appliquait pas ici, la population étant nulle.

## Décision

**La portée se resserre plutôt que la sévérité ne s'affaiblisse.** Le garde lit l'objet que sa propre
demande désigne, ici l'issue du `Closes #N`, et refuse tout net sur elle.

Trois conséquences :

- il reste un **butoir**, sans cliquet ni ADR pour en porter le nombre ;
- il n'accuse que qui peut corriger, et la correction est une commande ;
- la fenêtre d'une issue en attente de rattachement cesse d'être un problème, sauf pour qui ouvre une
  demande de fusion pendant qu'elle dure, et c'est alors à juste titre.

Un cliquet reste le bon remède quand la population fautive **préexiste** et qu'on ne peut pas la
rattraper. Ce n'est pas la même situation, et cette ADR ne renverse pas #4659.

## Conséquences

**Une demande qui ne ferme aucune issue n'est pas jugée.** C'est le cas des demandes de méthode qui
renvoient par `Refs`, et c'est voulu : le garde n'a alors aucun objet.

**Rattacher l'issue ne modifie pas la demande**, donc l'évènement `edited` ne se déclenche pas et le
contrôle ne se rejoue pas seul. La correction se relit en relançant le job, ou à la poussée suivante.
C'est écrit dans l'étape plutôt que laissé à découvrir.

**La vérification de cette décision est humaine et le restera.** Aucun motif ne distingue un garde à
portée juste d'un garde à portée large : il faut lire ce que le garde interroge et le comparer à ce
que sa demande désigne. La question se pose à l'écriture, et il n'y a que deux gardes concernés.

## Alternatives écartées

- **Le balayage global avec un cliquet.** Il aurait fallu poser le cliquet à zéro, la population
  l'étant, et le premier à s'assigner une issue avant de la rattacher aurait fait rougir tout le
  monde. Un cliquet ne sert que ce qu'on ne peut pas rattraper.
- **Balayer, mais n'avertir que.** Un dispositif qui avertit sans refuser cesse d'être lu, et le
  dépôt le sait : c'est la raison pour laquelle ses loupes sont nommées comme telles et séparées des
  gardes.
- **Attendre que la fenêtre disparaisse.** Elle ne disparaîtra pas : elle est le temps entre voir un
  défaut et savoir de quel chantier il relève, et ce temps est du travail, pas de la négligence.
