---
type: adr
title: "La dernière image d'un clip ne dépend plus de la place de la dernière image clé"
status: stable
article: A11
chantier: "#6135, lot 17 du chantier #5933"
decided_at: 2026-10-12
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/recette/film/EncodeurTest.java"
verification_note: "deux cas : l arithmétique des copies de la fin, qui tombe sur un multiple du pas après un pas entier, et un faux ffmpeg qui relève les réglages reçus et la fin recopiée. Ni l un ni l autre ne regarde une image : que la dernière image ne varie plus se lit dans la mesure des planchers, sur huit tournages du même commit"
relations:
  amende: ["5911-un-clip-a-deux-fins-n-a-pas-de-plancher"]
  complete: ["4309-le-pire-observe-reste-la-regle-et-un-plancher-a-un-sol", "5885-un-plancher-appartient-a-l-instrument-qui-l-a-pris"]
verified:
  - by: machine:ci
    at: 2026-10-12
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-12
---

# La dernière image d'un clip ne dépend plus de la place de la dernière image clé

## Contexte

L'ADR 5911 nommait les causes qui donnent deux fins à un clip, et toutes étaient dans le scénario :
un import encore en vol, un fondu, une page que rien n'a calée. Une cinquième cause était dans
l'encodeur, et c'est elle qui faisait le plancher de la plupart des clips stables.

`Encodeur.VersFfmpeg` laissait x264 placer ses images clés tout seul : une toutes les 250 images, plus
une à chaque coupure de scène. Une image immobile qui suit une image P se code comme un simple renvoi
à elle, sans rien corriger. La dernière image d'un clip garde donc la qualité de la dernière clé, et
celle-ci tombe plus ou moins près de la fin selon le rythme du tournage.

Mesuré à source constante (#6135) sur un clip sans perte, avec l'instrument de la CI : ne déplacer
que la clé, de 1 à 32 images avant la fin, fait varier la dernière image de **0,318 à 0,904 %**. C'est
l'intervalle que montraient de vrais tournages du même commit, sur des clips dont la fin regardée
était pourtant la même. L'ADR 4309 gardait le pire observé comme plancher : ce pire était, pour
l'essentiel, ce bruit-là.

## Décision

**Les clés tombent à pas fixe, et la séance finit sur un pas entier de copies de sa dernière image.**

- l'encodeur pose `-g 25 -keyint_min 25 -sc_threshold 0` : une clé toutes les 25 images, jamais sur
  une coupure de scène ;
- à sa fermeture, il recopie la dernière image jusqu'au multiple de 25, puis 25 fois encore. La
  dernière clé tombe alors sur une copie, toujours au même rang avant la fin, et la dernière image
  ne dépend plus que de son contenu.

Les deux moitiés sont nécessaires, et la mesure l'a montré. Avec 25 copies seules, la clé tombe à un
rang variable parmi elles, et x264 quantifie une clé selon le nombre d'images qui la suivent : il
restait 0,354 %. Bourrer d'abord jusqu'au multiple fixe ce rang, et l'écart tombe à 0,000 %.

Le pas a été choisi par le porteur entre trois mesurés, qui tiennent tous à 0,000 % :

| Pas | Taille d'un clip | Fin immobile ajoutée |
|---|---|---|
| 10 | ×6,5 | 1 à 2 s |
| **25** | **×3,2** | **2,5 à 5 s** |
| 50 | ×2 | 5 à 10 s |

## Conséquences

- les planchers se sont remesurés depuis zéro (`repartir_de_zero`), le 12 octobre 2026, sur six
  tournages du même commit et deux témoins. Ceux d'avant mesuraient surtout ce bruit, et la règle du
  pire observé ne les aurait jamais fait redescendre seuls. Sur les 95 clips ordinaires, la médiane
  est passée de 0,167 % à 0,000 %, les clips à plancher nul de 3 à 73, et le pire de 1,033 % à
  0,297 % ;
- un plancher ne vaut que pour la chaîne qui l'a mesuré. Le banc de `lance-test-filme.sh` encode
  autrement et garde ce bruit ; qu'il soit jugé contre ces planchers est l'objet de #6150 ;
- un clip est plus long et plus lourd. L'artefact de clips d'un tournage est passé de 21 à 65 Mo.

## Vérification

`EncodeurTest` tient l'arithmétique des copies et ce que ffmpeg reçoit, par un faux ffmpeg qui relève
ses arguments et ses octets. Aucun test ne regarde une image : que la dernière ne varie plus se lit
dans la mesure des planchers, et c'est elle qui dira si un réglage de x264 ou de son instrument a
défait ce que cette décision pose.
