---
type: adr
title: "Un cas dit où se lit son verdict, et ce que son clip ne prouve pas"
status: stable
article: A3
chantier: "#4142, EPIC #4133"
decided_at: 2026-08-22
verification: certaine
enforced_by:
  - "CorrespondanceRecetteTest#la_reserve_accompagne_la_portee"
verified:
  - by: machine:ci
    at: 2026-08-22
relations:
  prolonge: ["3764"]
---

# Un cas dit où se lit son verdict, et ce que son clip ne prouve pas

## Contexte

L'EPIC #4133 pose qu'un cas de recette est un E2E qui montre le produit **et qui se regarde**. La règle
a une limite, et elle est large : sur les 360 cas non couverts, environ **220** vivent dans des sessions
dont l'objet est **hors de l'application** - le dépôt reçu par Vigie-Chiro, la carte SD réelle, la nuit
rapatriée du serveur, l'installeur sur un vrai poste, la commande dans un vrai terminal.

Filmer ceux-là avec une frontière bouchonnée donne un clip **convaincant et creux**. Il ne devient pas
faux : il devient **muet sur son propre objet**, ce qui est pire, parce qu'on le regarde en croyant
savoir.

Le scénario de `S4-33` l'écrivait déjà de lui-même :

> Ce raccourci a une limite, et il faut la connaître : il ne prouve pas que l'écran **atteint** cet
> état. `S4-30` à `S4-32` s'en chargent, et ils demandent le stub.

Cette phrase existait parce que quelqu'un a pensé à l'écrire. **Rien ne l'exigeait**, et rien ne la
ferait réapparaître si elle disparaissait.

## Décision

**1. Tout cas cité déclare sa portée, et l'attribut n'a pas de valeur par défaut.**

`A_L_ECRAN` - le verdict se lit à l'écran - ou `HORS_APPLICATION` - il se lit ailleurs. C'est le
**compilateur** qui pose la question, à l'écriture du test, quand celui qui écrit sait encore ce que son
scénario truque.

⚠️ C'est un écart assumé avec le `Jugement` voisin, qui porte un défaut. Le `Jugement` le peut parce
qu'une **seconde source** vient le contredire quand il est faux : la marque `*perceptif*` du script, que
le garde confronte au code. La portée n'a pas cette seconde source. Un défaut la rendrait invisible, et
la question ne se poserait **plus jamais** - c'est ainsi qu'une catégorie déclarative meurt.

**2. Une portée hors application exige une réserve, et elle seule.**

La réserve dit, en une phrase, ce que le clip **ne prouve pas**. Exigée si et seulement si la portée est
`HORS_APPLICATION` : ailleurs elle serait du bruit, et une page qui met une réserve partout n'en fait
lire aucune.

**3. La réserve figure sur la page du clip, pas seulement dans le code.**

Une réserve qui ne vit que dans une annotation ne borne rien : **personne ne lit une annotation en
regardant une vidéo**. Le garde la cherche dans la section du cas, sur la page qui porte le lecteur
(`CorrespondanceRecetteTest#la_reserve_atteint_celui_qui_regarde`).

## Ce que le classement a révélé

Les 57 annotations existantes sont **toutes** `A_L_ECRAN`. Ce n'est pas un hasard, et c'est le résultat
le plus utile de cette décision : ce qui a été couvert jusqu'ici est précisément ce qui se filme **sans
réserve**. La difficulté est entière et elle est devant nous - dite maintenant par le dispositif, et non
plus seulement par un EPIC.

⚠️ Le compte n'est recopié nulle part : le garde l'imprime à chaque lancement. Un nombre écrit à la main
dérive sans que rien ne le signale, ce qui a produit #3885.

## Portée de cette décision

Elle catégorise les cas **couverts**, pas les 403 déclarés. Marquer les 360 non couverts demande un
jugement par cas, dans les fichiers de session, et c'est le travail de chaque session de l'EPIC quand
elle sera prise. Le mécanisme est posé ; son application au reste suit le même chemin que les clips.

## Ce qui a été écarté

**Un défaut à `A_L_ECRAN`.** Il aurait évité 57 éditions. Il aurait aussi rendu la question invisible :
tout cas non déclaré serait réputé montrable, et les 220 cas qui ne le sont pas seraient entrés dans le
dispositif sous une étiquette fausse, sans que rien ne rougisse.

**Une marque dans les fichiers de session, comme `*perceptif*`.** C'est la forme qu'aura la
catégorisation des 403 cas, et elle donnera la seconde source qui manque aujourd'hui. Elle demande un
jugement par cas que ce chantier n'était pas en position de rendre - et poser une marque au jugé sur 360
cas aurait fabriqué exactement la dérive que le garde existe pour empêcher.
