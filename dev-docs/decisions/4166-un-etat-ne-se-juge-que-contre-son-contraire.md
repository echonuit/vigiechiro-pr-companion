---
type: adr
title: "Un état ne se juge que contre son contraire, et dans le même clip"
status: stable
article: A4
chantier: "#4166, EPIC #4133"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "MesSitesViewTest#hors_connexion_la_recuperation_est_fermee"
verified:
  - by: machine:ci
    at: 2026-08-23
relations:
  prolonge: ["4142"]
---

# Un état ne se juge que contre son contraire, et dans le même clip

## Contexte

La revue des clips a formulé la même remarque **trois fois**, sous trois formes, avant qu'on entende
qu'il s'agissait d'un seul principe :

| Cas | Ce que la revue en a dit |
|---|---|
| `S1-16` | « on devrait voir la connexion **avant** de voir que le bouton existe » |
| `S1-25` | « il faudrait la même vidéo avec la création d'un carré qui montre que la validation **ajoute** un carré » |
| `S1-19` | « ne montre pas ce qu'il doit » - deux boutons ternes parmi cinq, sans point de comparaison |
| `S1-17` | « je ne comprends pas ce que je dois voir » - un voile déjà disparu |

Un bouton grisé, un bandeau masqué, un voile qui a cédé, une liste qui n'a pas changé : ce sont des
**absences**, et une absence n'a pas d'image. Elle ne devient lisible que si le clip montre aussi le
moment où la chose est là.

`S1-02` l'avait eu - la donnée arrive, le bandeau paraît, la donnée s'en va, le bandeau se retire - et
c'est le seul cas de la première vague que la revue n'a pas rappelé.

## Décision

Un cas qui fait juger un **état** montre cet état **et son contraire**, dans le même clip.

Trois formes, selon ce que le cas éprouve :

- **le contraire dans le temps** : le geste offert, puis empêché (`S1-19` ouvre sur un carré où les deux
  commandes sont actives, avant celui où elles ne le sont pas) ;
- **le contraire par le voisinage** : le geste fermé à côté d'un geste ouvert (`S1-16` montre
  « Récupérer » grisé contre « + Nouveau site » en couleur) ;
- **le jumeau, dans un autre cas** : `S1-25` montre qu'annuler n'ajoute rien, et `S1-13` montre que
  valider ajoute. Deux cas, un principe - la comparaison se fait alors entre deux clips, et le script
  les nomme l'un à côté de l'autre.

## Corollaire

**Un clip qui remplace le geste par son effet ne montre pas le produit.** Il montre une conséquence dont
la cause est hors champ, ce qu'un spectateur attentif prend pour un défaut. C'est ce qui a produit
« on ne voit pas le clic sur la restauration » (#4169) et « pas clair que l'on a cliqué sur le bouton de
récupération » (#4181) - et, à la fin, la règle que le pointeur **s'arrête** sur sa cible avant
d'appuyer.

## Conséquences

Les clips s'allongent : montrer un état et son contraire, c'est jouer deux fois. C'est le prix de ce
qu'un cas prétend faire juger, et il ne se paie qu'en séance filmée.

⚠️ **Ce que la règle ne dit pas** : elle n'oblige pas à fabriquer le contraire. `S1-17` a été déclaré
infilmable pendant un temps au motif que le voile ne durait pas - et c'était vrai d'une fixture de deux
sites, faux du produit : sur cent cinquante carrés, le chargement prend un demi-seconde. Quand le
contraire semble introuvable, la question à se poser est **d'abord** « sur quelles données ai-je
mesuré ? », avant « comment le fabriquer ? ».

## Ce qui a été vérifié à la clôture

Un balayage des cas filmés qui jugent une absence sans jamais montrer la présence : **deux candidats,
tous deux faux positifs** à la lecture. `S6-29` ajoute la puce de filtre avant de l'effacer, et
`S1-25` a son jumeau dans `S1-13`. Le principe est donc appliqué partout où il s'applique - mais
l'heuristique ne sait pas voir un jumeau qui vit dans un autre cas, et c'est à la lecture que cela se
tranche.
