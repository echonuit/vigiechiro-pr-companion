---
type: adr
title: "Un back local étalonne les sondes, il ne tourne aucun clip"
status: stable
article: A4
chantier: "#4444 (EPIC #4416)"
decided_at: 2026-08-25
verification: certaine
enforced_by:
  - "BancDeRecetteUrlTest#le_banc_ignore_l_url_ambiante"
verified:
  - by: humain
    at: 2026-08-25
  - by: machine:ci
    at: 2026-08-26
relations:
  prolonge: ["4142", "4291", "4406"]
generated:
  by: "process:assistance-par-agents"
---

# Un back local étalonne les sondes, il ne tourne aucun clip

## Contexte

Sur les 98 cas de recette déclarés hors de portée au palier 1 de l'EPIC #4416, 22 portent le même
motif : *un dépôt réel sur la plateforme, c'est-à-dire une écriture que le banc s'interdit*. C'est le
plus gros bloc de la liste, et le seul dont l'obstacle est une décision plutôt qu'un fait.

Le back réel est disponible dans l'atelier, et sa propre documentation décrit le montage tentant :
`DEV_FAKE_AUTH=true` active un fournisseur d'authentification simulé qui frappe un jeton sans OAuth,
`DEV_FAKE_S3_URL` détourne le stockage d'objets. Lecture faite du code : le compte ainsi fabriqué
naît avec `role: 'Observateur'`, exactement ce qu'exigent 53 des 64 routes authentifiées. Le montage
marcherait.

La tentation est donc réelle, et l'objection l'est aussi : ce back est figé à des dépendances de
2021, la plateforme nationale non.

## La mesure qui a déplacé la question

Le motif des 22 est posé au **bloc** et recopié sur chacun de ses cas. Ouverts un par un, ils se
séparent en sept obstacles distincts :

| Ce que le cas observe réellement | Cas | Combien |
|---|---|---|
| un observable local ; le serveur n'est que dans le préambule | S4-37, 38, 39, 45, 50, 85, 86, 87 | 8 |
| le transfert lui-même | S4-40, 41, 42, 44, 46 | 5 |
| une durée réelle mesurée contre la plateforme | S4-84, 88, 89 | 3 |
| la réponse du serveur | S4-47, 48 | 2 |
| une écriture publiée et relue | S4-90, 92 | 2 |
| le verdict d'un validateur humain du MNHN | S4-93 | 1 |
| rien : une prise de note | S4-51 | 1 |

S4-39 est un réglage local. S4-50 dit lui-même « sans réseau ». Le chapeau du bloc C annonce son
obstacle comme *des durées réelles*, et trois de ses cas sont des lectures. C'est le défaut que
#4325 avait déjà corrigé sur S8, où 4 cas sur 38 étaient mal classés.

Les huit premiers relèvent de l'[ADR 4406](4406-l-etat-de-depart-d-un-cas-se-declare-il-ne-s-enregistre-pas.md) :
leur état de départ se déclare et un générateur le matérialise. Ils ne demandent aucun serveur.

Il reste au plus neuf cas pour lesquels un back local changerait quelque chose.

## Décision

**Un back local ne sert jamais de préambule à un cas de recette filmé. Il ne produit aucun clip.**

Il sert de banc d'étalonnage : les sondes d'écriture qui manquent au contrat live s'écrivent et se
déboguent hors ligne contre lui, puis sont tirées contre la participation de rebut réelle. **L'écart
entre les deux tirs est la mesure de dérive**, et c'est lui qui donne à #4356 la citation qu'il exige
avant d'enrichir le bouchon.

Rien ne doit dépendre de son existence. Il ne vit ni en intégration continue, ni sur un poste
installé à demeure : il est jetable, dans les mains de qui écrit une sonde. En intégration continue
il deviendrait la seconde source de vérité que #4356 nomme déjà comme le risque, et il dériverait
en silence.

## Pourquoi aucun clip, et c'est le point dur

L'[ADR 4142](4142-un-cas-dit-ou-se-lit-son-verdict.md) condamne le clip tourné contre un bouchon :
convaincant et creux, muet sur son propre objet. On pourrait croire qu'un vrai serveur lève
l'objection. Il la déplace, et sur les cas visés il l'aggrave.

Les cinq cas de transfert sont ceux où un back local est le **plus** creux, pas le moins. Sur
`localhost`, la latence est nulle et la bande passante virtuellement infinie. S4-42, qui demande que
la barre « reflète les octets envoyés », filmerait la remise à un tampon local et non un transfert.
S4-41, qui demande cinq lignes en cours simultanément, filmerait cinq tâches planifiées. Le dépôt
énonce déjà exactement ce piège, en S4-61 :

> Les tests injectent un temporisateur factice qui n'attend pas : ils prouvent que l'attente est
> découpée et que le renoncement est lu entre deux tranches, jamais que la latence ressentie sur un
> vrai réseau reste acceptable.

Les trois cas de durée sont détruits par le même mécanisme : « paraît instantané » ne veut plus rien
dire quand tout l'est.

Un clip tourné contre un back local serait donc convaincant et creux au sens exact de l'ADR 4142,
avec un déguisement de plus : le spectateur verrait du vrai code serveur et en conclurait qu'il a vu
un dépôt.

## Ce que le témoin ne pourra jamais dire

`api-live.yml` était le témoin de dérive pressenti. Deux faits mesurés le bornent.

Premier fait : le dispositif d'écriture **existe déjà**. `ContratApiVigieChiroLiveTest` porte des
sondes d'écriture, derrière trois verrous successifs, dirigées vers une participation de rebut, et
chacune relit après avoir écrit. Ce n'est pas la suite qui s'interdit l'écriture, c'est le job
hebdomadaire qui ne passe aucun drapeau. En revanche `_etag` n'est aujourd'hui que lu : aucune sonde
n'exerce le refus `412` d'`If-Match`. Le mécanisme existe, la sonde manque, et c'est elle que le banc
d'étalonnage sert à écrire.

Second fait, et il borne pour de bon : l'[ADR 0020](0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md)
a mesuré trois défauts d'écriture réels, et les résume ainsi.

> Aucun de ces trois défauts ne produit d'erreur. Ils réussissent tous.

Une sentinelle locale publiée comme donnée, un dictionnaire distant écrasé, une clé écrite sous un
nom que le formulaire web ne lit pas : les trois renvoyaient `200`, et les trois se relisaient. Un
témoin qui écrit puis relit serait resté **vert** sur les trois. Ce qui les a attrapés est la fiche
web, c'est-à-dire le consommateur, pas le code de retour.

Un contrat d'écriture atteste donc la syntaxe et jamais que quiconque lit ce qu'on écrit. C'est
précisément pourquoi S4-70, 71, 72 et 91 portent le motif « fiche web » et resteront hors de portée
du banc quoi qu'il arrive.

## Le renversement, et c'est lui qui justifie le banc

La valeur d'un back local n'est pas la fidélité. Elle est que la protection devienne
**structurelle**.

Mesure faite dans le code du back : sur les 64 routes authentifiées, aucune n'accepte le rôle
`Lecteur`. Cinquante-trois exigent `Observateur`, dix `Administrateur`, une accepte tout compte
connecté ; `Lecteur` n'apparaît que dans la table `ROLE_RULES` et aucune route ne le nomme.
L'[ADR 4291](4291-un-clip-tourne-contre-la-plateforme-ne-se-range-pas-avec-les-autres.md) l'avait
déjà relevé : un jeton en lecture seule n'existe pas sur cette plateforme.

La lecture seule du tournage connecté ne tient donc que par le câblage et un garde de portée des
secrets. C'est une discipline, et une discipline se relâche. Sur un back local, il n'y a rien à
détruire : la propriété cesse d'être tenue par notre attention.

C'est un argument pour un banc d'essai. Ce n'en est pas un pour un décor de tournage, et la décision
sépare les deux.

## Ce qui a été écarté

**Le back local comme préambule des neuf cas restants.** C'est la position qu'ouvrait l'issue. Elle
échoue sur les cinq cas de transfert et les trois cas de durée, pour la raison développée plus haut.
Restent quatre cas, dont deux relèvent du tournage connecté ; le montage ne se justifie pas pour eux.

**Un tournage connecté en écriture, sur la ressource de rebut.** Il filmerait le vrai réseau, la
vraie authentification et le vrai stockage. Il retire aussi la seule chose qui protège les données de
terrain, puisque la lecture seule ne tient que par le câblage, et l'ADR 4291 rappelle qu'un clip
connecté ne se range ni ne se compare, son écran suivant des données vivantes. Le gain probatoire ne
paye pas ce que le geste ouvre.

**Un bouchon alimenté par enregistrement des échanges réels.** Séduisant, et déjà refusé un cran plus
loin dans la chaîne par l'ADR 4406 : un enregistrement figé s'éloigne en silence de ce qu'il imite, et
rien ne le dit. Ce que l'ADR 4406 refuse à une base de départ vaut pour un jeu de réponses.

**Écrire sur la vraie plateforme depuis l'intégration continue.** Refusé avant ce chantier, et pour
de bonnes raisons : ce sont des données réelles de terrain, et une réexécution destructive a déjà
détruit des archives.

## Conséquences

- **Aucun des 22 cas n'est débloqué par cette décision.** Les huit qui bougent vraiment bougent grâce
  à l'ADR 4406 et attendent le générateur de #4325. Les cinq du transfert et le cas du validateur
  humain resteront hors de portée du banc. Les quatre autres relèvent du tournage connecté.
- Le classement des 22 dans `dev-docs/recette/sessions/s4-deposer-suivre.md` reste **inchangé** :
  le corriger ferait passer le total hors de portée du palier 1 de 98 à 90, sur un palier clos dont
  le chiffre a circulé. La mesure est consignée ici ; sa reprise dans le corpus est un travail à
  part, à ouvrir séparément.
- Le premier usage du banc est la sonde manquante du refus `412` d'`If-Match`, puis celle du refus de
  `numero`. Les deux comblent ce que #4356 exige avant d'enrichir le bouchon.
- Rien dans le dépôt ne doit importer, invoquer ni supposer un back local. S'il disparaît du poste,
  aucune vérification ne change de couleur.

## Vérification

`certaine` depuis le 2026-08-26, et le garde n'a pas eu à être écrit : **il existait déjà**.

Cette section déclarait `humaine` avec un motif daté - le banc n'existait pas - et annonçait qu'un
garde devrait refuser qu'un scénario de recette filmé résolve son adresse ailleurs que vers le
bouchon ou la vraie plateforme. Le banc monté, la propriété s'est trouvée tenue par
`BancDeRecetteUrlTest#le_banc_ignore_l_url_ambiante`, posé par #4332 pour une autre raison : un
scénario qui n'a déclaré aucun serveur est épinglé sur `http://localhost:1`, l'idiome hors-ligne, et
ses réponses deviennent `Injoignable`.

Mesuré plutôt que déduit : la suite passe avec `VIGIECHIRO_URL=http://localhost:8080` - l'adresse du
banc - réellement posée dans l'environnement, et elle **rougit** dès que l'épinglage est retiré. Le
garde a donc été vu rouge sur la mutation qui le concerne, avec le banc en place.

Un scénario connecté échappe à cet épinglage, et c'est voulu : il garde l'URL ambiante parce qu'il
l'a précisément demandée. C'est le tournage connecté de l'ADR 4291, pas un clip qui s'égarerait.

Ce qu'aucun garde ne tiendra, et qui reste une affaire de relecture : la frontière entre étalonner
une sonde et s'appuyer sur elle. Une sonde écrite contre le banc et jamais tirée contre le rebut
passerait au vert en n'ayant rien établi, et seul un lecteur peut le refuser.

Ce qu'aucun garde ne tiendra, et qui restera une affaire de relecture : la frontière entre étalonner
une sonde et s'appuyer sur elle. Une sonde écrite contre le banc et jamais tirée contre le rebut
passerait au vert en n'ayant rien établi, et seul un lecteur peut le refuser.
