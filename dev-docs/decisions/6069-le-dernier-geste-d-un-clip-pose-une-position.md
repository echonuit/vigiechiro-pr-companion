---
type: adr
title: "Le dernier geste d'un clip pose une position, et l'assertion de fin la relit"
status: stable
article: A5
chantier: "#6069, lot 11 du chantier #5933"
decided_at: 2026-10-06
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/recette/GesteVisiblePositionPoseeTest.java"
verification_note: "sept cas : le défaut d origine reproduit dans l ordre fautif, le geste qui pose dans ce même ordre, le contrôle hors de cet ordre, la page qui bouge après le geste, le prédicat qui met en page avant de juger, la section qui n a pas paru, le refus d une cible sans panneau. Six mutations du geste et du prédicat, toutes tuées, dont une par un cas de plus que ceux annoncés d avance."
relations:
  amende: ["5870-un-verdict-au-bas-de-sa-page-s-y-cale"]
  prolonge: ["5911-un-clip-a-deux-fins-n-a-pas-de-plancher"]
verified:
  - by: machine:ci
    at: 2026-10-06
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-06
---

# Le dernier geste d'un clip pose une position, et l'assertion de fin la relit

## Contexte

L'[ADR 5870](5870-un-verdict-au-bas-de-sa-page-s-y-cale.md) a réglé le cas d'un verdict qui est le
dernier élément de sa page. Pour les autres, elle laissait `GesteVisible.amenerDansLeCadre`, « le bon
geste pour une cible au milieu d'une page ».

Le clip de `S2-48` finissait ainsi (#6027). Il avait reçu un plancher de 0,274 %, pris sur six
tournages qui finissaient tous de la même façon. Les six tournages du lot suivant ont montré une
seconde fin deux fois, la page en haut, à 21 % des autres : son plancher n'avait mesuré qu'un mode, et
le clip est retourné dans la table de l'[ADR 5911](5911-un-clip-a-deux-fins-n-a-pas-de-plancher.md).

Deux choses laissaient passer cette seconde fin.

**Le geste s'arrête dès que sa cible est dans le cadre**, et cette condition est vraie à plusieurs
positions de la page. Appelé juste après que l'inspection rend sa section visible, il règle la page
sur les bornes d'avant la mise en page qui place cette section. La page reste où elle était, la
section est dans le cadre quand même, et le geste conclut.

**L'assertion de fin lisait la même condition** : « la section est dans le cadre » était vrai aux deux
fins, et ne tenait donc rien.

Le défaut du geste se reproduit à coup sûr dans un banc. La course du cas réel, dont la fenêtre est
d'une pulsation, n'est pas sortie sur un poste en dix passes filmées, dont deux sous une charge
étrangère. Douze passes plus anciennes ne comptent pas : leur témoin lisait la page avant le geste,
et laissait passer la mise en page. Que cet ordre soit celui des deux tournages n'est donc pas
observé : c'est ce que leurs images et le code laissent conclure.

## Décision

**Le dernier geste d'un clip pose une position, et l'assertion de fin relit cette position.**

Un verdict au bas de sa page se cale toujours par `allerAuBasDeLaPage` : l'ADR 5870 n'est pas touchée
sur ce point.

Un verdict ailleurs dans sa page se pose par `GesteVisible.poserDansLeCadre`. Le geste règle les
panneaux de défilement, puis ne s'arrête que sur `estPoseDansLeCadre` : la page est à la place que le
réglage lui donne, au demi-pixel, et la cible est dans le cadre. Le prédicat met en page avant de
lire, pour ne pas juger des bornes périmées. Tant qu'il répond faux, le geste règle de nouveau.

L'assertion de fin du cas appelle ce même prédicat **après** la tenue de l'image. Une page qui bouge
encore, parce qu'un contenu grandit, n'est plus posée, et le cas rougit au lieu de filmer une autre
fin.

`amenerDansLeCadre` n'est pas modifié. Il reste le geste qui rend une cible cliquable en cours de
cas, où « dans le cadre » est exactement ce qu'un clic demande. Ce que cette décision retire à l'ADR
5870 est son emploi pour **finir** un clip.

## Ce qui a été écarté

**Changer la condition d'arrêt d'`amenerDansLeCadre`.** Quinze appels l'emploient encore hors de ses
propres bancs, tous suivis d'un autre geste dans leur cas, et aucun n'a besoin d'une position.

**Un contrôle préalable dans le geste**, qui vérifiait d'abord que la cible descend d'un panneau.
C'était le premier dessin. Une mutation l'a démenti : privé de sa condition d'arrêt, le geste restait
vert, parce que ce contrôle laissait passer une mise en page avant le premier réglage. Le geste
réussissait pour une raison qui n'était pas la sienne. Le réglage est maintenant son premier acte, et
le refus se lit dessus.

**Mettre en page dans le réglage aussi.** Le prédicat le fait déjà, et c'est lui qui décide : la
ligne ne changeait le verdict d'aucun cas.

## Conséquences

Deux cas finissent par ce geste : celui de `S2-48`, et celui de la modale du passage, seul autre cas
dont `amenerDansLeCadre` était le dernier geste.

Douze tournages du même commit finissent sur la même image pour les deux clips, regardée une à une.
Sur six d'entre eux, l'atelier mesure quinze paires de 0,000 à 0,238 % pour le premier et de 0,016 à
0,268 % pour le second. La table des clips sans plancher est vide.

Trois limites, dites telles quelles :

- douze tournages laissent passer un mode qui sort une fois sur six environ une fois sur neuf ;
- pour la modale, retirer le geste ne fait pas rougir le cas hors séance filmée : la modale y est
  déjà où le geste la mettrait, et le témoin reste intermittent comme le défaut (#6054) ;
- un plancher pris sur six tournages a été faux sans que rien ne le dise. Rien ne fixe encore
  combien de tournages il faut pour sortir un clip de la table.
