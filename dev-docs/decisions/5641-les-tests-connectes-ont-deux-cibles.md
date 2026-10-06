---
type: adr
title: "Les tests connectés ont deux cibles, et la plateforme nationale confronte la plateforme de test"
status: stable
article: A5
chantier: "#5640 (deux cibles pour les tests connectés), lot #5641"
decided_at: 2026-09-30
verification: humaine
verification_note: "deux tests tiennent deja la plateforme de test et la cible d un scenario, mais rien ne tient encore la confrontation, qui est le coeur de la decision : elle recoit son garde avec #5647, et la decision passera alors a certaine"
enforced_by: []
verified:
  - by: humain
    at: 2026-09-30
relations:
  amende: ["4444-un-back-local-etalonne-les-sondes-il-ne-tourne-aucun-clip", "4291-un-clip-tourne-contre-la-plateforme-ne-se-range-pas-avec-les-autres"]
  prolonge: ["4406-l-etat-de-depart-d-un-cas-se-declare-il-ne-s-enregistre-pas", "0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer"]
  completee_par: ["5970-la-plateforme-de-test-joue-l-extraction-d-une-archive-par-le-code-du-serveur"]
generated:
  by: "process:assistance-par-agents"
---

# Les tests connectés ont deux cibles, et la plateforme nationale confronte la plateforme de test

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-06** : « la plateforme de test n'embarque pas le worker de traitement » est
    **complété** par
    [5970](5970-la-plateforme-de-test-joue-l-extraction-d-une-archive-par-le-code-du-serveur.md).
    Un banc peut lui faire jouer la seule extraction d'une archive, par le code du serveur. Le
    reste fait foi.

## Le contexte

Tout ce qui parle à Vigie-Chiro depuis les tests passe aujourd'hui par la plateforme nationale : le
contrat `api-live`, chaque semaine et sans aucune écriture, et les tournages connectés, lancés à la
main. Les deux dépendent d'un jeton de quatorze jours renouvelé à la main. Sans lui,
`ContratApiVigieChiroLiveTest` est sauté en entier.

Le dépôt faisait déjà tourner une copie de la plateforme, `banc-etalonnage/` (retiré en #5667), avec l'API épinglée et
Mongo. L'[ADR 4444](4444-un-back-local-etalonne-les-sondes-il-ne-tourne-aucun-clip.md) l'a tenue hors
de la CI parce qu'une copie figée **dériverait en silence**. L'argument est juste pour une copie
seule, et c'est la seule forme que 4444 a examinée.

## La décision

**Un test connecté déclare sa cible, parmi deux.**

- La **plateforme de test** est montée par les tests eux-mêmes : l'API épinglée, Mongo, un faux S3 en
  TLS, le front web et un navigateur. Elle sert au quotidien, à chaque demande de fusion, sans jeton,
  et les écritures y sont libres puisqu'il n'y a rien à abîmer.
- La **plateforme nationale** sert à une confrontation régulière, avec ses verrous d'écriture
  inchangés.

**Un test vert sur l'une et rouge sur l'autre est une dérive, et elle ouvre une issue.** C'est ce qui
répond à 4444 : la dérive qu'elle redoutait existe toujours, elle cesse d'être silencieuse. Son remède,
quand la plateforme nationale a évolué, est de remonter l'épinglage de l'API, qui devient un réglage
du dépôt et non un détail d'image.

La plateforme de test n'embarque pas le worker de traitement. Une participation « traitée » y vient
d'un état de départ déclaré, au sens de
l'[ADR 4406](4406-l-etat-de-depart-d-un-cas-se-declare-il-ne-s-enregistre-pas.md), et un calcul lancé
y reste planifié.

## Ce qui change dans les deux décisions amendées

**Dans 4444**, deux phrases cessent de valoir : « il ne vit ni en intégration continue » et « rien
dans le dépôt ne doit importer, invoquer ni supposer un back local ». Le banc d'étalonnage devient la plateforme de
test.

**Dans l'[ADR 4291](4291-un-clip-tourne-contre-la-plateforme-ne-se-range-pas-avec-les-autres.md)**, le
refus de comparer un clip connecté tient pour la plateforme nationale et tombe pour la plateforme de
test. Sa raison était que l'écran suit des données vivantes. Sur un état de départ déclaré, ce n'est
plus vrai, sous une condition : deux tournages du même commit doivent rester sous le plancher de bruit
de l'[ADR 4287](4287-un-ecart-se-lit-contre-le-plancher-de-son-cas.md). Cela se mesure au lot qui
tournera ces clips, et pas avant.

## Ce que cette décision garde de 4444

**Un transfert ou une durée ne se prouvent pas sur un lien local nu.** La latence y est nulle, et une
barre de progression y mesure un tampon. Ces cas se jouent au quotidien derrière un proxy qui dégrade
le réseau selon des profils déclarés, ce qui prouve que Companion encaisse un réseau lent, pas que le
vrai réseau ressemble au profil. La plateforme nationale reste leur vérification.

**Un contrat d'écriture n'atteste pas la lecture.** Les trois défauts de
l'[ADR 0020](0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md) rendaient `200` et se
relisaient. Seule la fiche web les voyait. La plateforme de test embarque donc le front, et c'est par
lui que ces écritures se relisent.

## Ce qui a été écarté

**Le back local sans confrontation**, c'est-à-dire la position que 4444 refusait, pour la raison
qu'elle donnait.

**La plateforme nationale seule, avec un compte de test.** #4466 l'avait instruite. Le compte garde
un rôle, celui de la confrontation, mais il ne protège que ce qu'il possède : recensé le 26 août, il
peut écrire les localités de n'importe quel site des trois protocoles.

**Le worker de traitement dans la plateforme de test.** Écarté pour ce chantier : une image lourde et
des binaires anciens, pour les seuls cas qui regardent un état de traitement bouger.

## Ce qui la tient, et ce qui manque encore

`BancDeRecettePlateformeDeTestTest#le_banc_vise_la_plateforme_de_test_declaree` tient que la
déclaration produit la bonne cible : un scénario qui déclare la plateforme de test la compose, et
jamais l'adresse ambiante. `DepotSurLaPlateformeDeTestTest` tient que la plateforme se monte et que
Companion y dépose une archive en parties ; le job `plateforme-de-test` le joue à chaque demande.

Il manque la confrontation : rien n'ouvre encore d'issue quand un test passe sur une cible et rougit
sur l'autre. Elle reçoit son garde avec #5647, et cette décision passera alors à `certaine`. Le garde
qui refuse de comparer les clips connectés s'élargit avec les tournages (#5644).
