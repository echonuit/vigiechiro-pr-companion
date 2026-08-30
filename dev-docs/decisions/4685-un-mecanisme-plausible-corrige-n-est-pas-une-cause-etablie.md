---
type: adr
title: "Un mécanisme plausible corrigé n'est pas une cause établie"
status: stable
article: A1
chantier: "#4685 (clôture, passe 11)"
decided_at: 2026-08-29
verification: humaine
verification_note: "la règle porte sur ce qu'une correction PRÉTEND, et une prétention ne se mesure pas : aucun garde ne peut distinguer un correctif qui explique son incident d'un correctif qui lui ressemble"
verified:
  - by: human:nedseb
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
relations:
  prolonge: ["3627"]
---

# Un mécanisme plausible corrigé n'est pas une cause établie

## Contexte

Le garde des captures a fait rougir la CI en accusant `apercu-diagnostic-sans-gps.png` d'être « non
déclarée » alors qu'elle l'était, ligne 22 du manifeste (#4642). Une relance sur le même commit est
passée au vert sans qu'une ligne bouge.

En lisant le garde, un mécanisme est apparu, et il était réel :

```bash
printf '%s\n' "$declarees" | grep -qx "$png"
```

`grep -q` sort au premier match et referme le tuyau ; un `printf` encore en train d'écrire reçoit
`SIGPIPE`, que `set -o pipefail` propage **alors même que `grep` a trouvé**. Le garde accuse alors
une capture parfaitement déclarée. Le mécanisme explique l'intermittence, il explique l'accusation,
il explique le retour au vert. Tout concordait.

**Il n'explique pas cet échec-là.** Le tampon d'un tube fait 65 536 octets ; en deçà, `printf` écrit
tout d'un coup et ne reçoit jamais `EPIPE`.

| Source | Taille | Accusations à tort |
|---|---|---|
| le manifeste réel, 142 captures | 4 492 octets | 0 sur 200 |
| le garde entier, sous `bash -e` | idem | 0 sur 30 |
| une liste fabriquée de 50 000 entrées | 888 893 octets | 40 sur 40 |

Le manifeste est quinze fois trop petit. Le défaut est **latent** : il se réveillerait vers deux
mille captures, contre cent quarante aujourd'hui.

La tentation, à ce moment-là, était de corriger et de fermer. Le correctif est bon, le mécanisme est
démontré, l'incident ne s'est pas reproduit. Ce raisonnement a déjà été poussé plus loin dans ce
chantier : une passe avait commencé à balayer 51 emplacements du même motif dans 21 gardes, au nom
d'une cause qui n'était pas la cause.

## Décision

**Une correction qui n'explique pas l'incident qui l'a fait écrire se déclare préventive, dans le
code, et l'incident reste ouvert.**

Trois gestes, indissociables :

1. le correctif porte en commentaire ce qu'il prévient **et ce qu'il n'explique pas**, avec les
   chiffres qui séparent les deux ;
2. le témoin éprouve le **motif**, sur une source assez grosse pour le déclencher, et non le cas
   observé, qu'il rendrait vert avant comme après ;
3. l'issue de l'incident n'est pas fermée par le correctif. Elle attend une seconde occurrence,
   seule chose qui la rendra traitable.

## Conséquences

Un lecteur trouve dans `check-doc-images.sh` un correctif accompagné d'une phrase qui dit qu'il ne
corrige pas l'échec observé. **Cette phrase a l'air d'un aveu de faiblesse, et c'est pourquoi elle
est ici** : sans elle, le prochain à lire ce fichier conclura que l'affaire est réglée, et fermera
#4642 sur la foi d'un correctif qui n'a rien expliqué.

Le coût du geste inverse est asymétrique. Déclarer préventif un correctif qui se trouvait être la
cause laisse une issue ouverte de trop, qu'une seconde absence d'occurrence finira par fermer.
Déclarer résolu un incident dont la cause court toujours le rend invisible : il reviendra sous un
autre visage, et personne ne le reliera au premier.

L'[ADR 3627](3627-une-mesure-dit-ce-qu-elle-n-a-pas-pu-lire.md) demandait déjà qu'une mesure dise ce
qu'elle n'a pas pu lire. Celle-ci l'étend d'un cran : **une correction dit ce qu'elle n'a pas pu
expliquer.**

## Ce que cette décision ne dit pas

Elle n'interdit pas de corriger un mécanisme non coupable : le motif de tube était un vrai défaut,
et sa suppression est un gain. Elle n'oblige pas non plus à prouver une cause avant d'agir, ce qui
paralyserait. Elle n'exige qu'une chose : que la **prétention** du correctif soit à la hauteur de ce
qui a été mesuré.
