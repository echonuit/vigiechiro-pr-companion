---
type: adr
title: "Une hyperarête se déclare quand on la lâche, et les ponts parcourent les pages que le graphe porte"
status: stable
article: A3
chantier: "#5904 (une page réextraite perdait son hyperarête en silence), lot 7 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/couche_semantique.py"
  - "scripts/graphify/pont_doc_code.py"
  - "scripts/graphify/pont_ci.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5813-une-hyperarete-porte-un-identifiant-et-une-mise-a-jour-declare-ce-qu-elle-lache"]
generated:
  by: "process:assistance-par-agents"
---

# Une hyperarête se déclare quand on la lâche, et les ponts parcourent les pages que le graphe porte

## Le contexte

Le chantier #5812 se donnait pour critère qu'une session neuve, sans le brouillon des précédentes,
refasse la couche d'une page avec les scripts du dépôt. Jouée à sa clôture, l'épreuve a réussi, et
elle a trouvé ce que son auteur ne pouvait pas voir.

L'ADR 5813 dit qu'une mise à jour déclare l'identifiant sémantique qu'elle lâche. Elle ne parlait que
des nœuds. Le rendu d'une page remplace pourtant toute sa couche, hyperarêtes comprises, et rien ne
disait à l'agent qu'elle en portait une.

La passe d'harmonisation a trouvé le second défaut. Les ponts qui relient une page à ce qu'elle
cite parcouraient une liste de dossiers écrite en dur.

## Ce qui a été mesuré

Le 5 octobre, sur le graphe de référence :

| | |
|---|---:|
| pages couvertes qui portent une hyperarête | 63 sur 589 |
| hyperarêtes dont un membre vit sur une autre page que la leur | 34 sur 71 |
| hyperarêtes après fusion d'un rendu qui omet celle de sa page | 70 au lieu de 71 |
| pages qui ont un nœud de page, hors du parcours des ponts | 173 sur 763 |
| citations de classe ajoutées par le parcours du graphe | 436 |
| pages de `.claude/skills` identiques à leur source | 32 sur 32 |

L'audit acceptait le rendu sans hyperarête, et la fusion rendait « 0 perdu ». Lâcher un nœud que
l'hyperarête d'une autre page tenait y laissait un membre qui ne désignait plus rien.

Et `fusionne`, en désignant un graphe qui n'est pas celui de son arbre, sortait en 0 : le graphe
désigné n'avait pas bougé, son registre disait la page extraite.

## La décision

**La fiche d'une page nomme ses hyperarêtes, et l'audit refuse un rendu qui en lâche une sans la
déclarer.** La règle de l'ADR 5813 vaut pour elles comme pour les nœuds. La fiche range sous
`ailleurs` les membres qui vivent sur une autre page : l'audit ne voit pas le graphe, et refuserait
sinon une hyperarête réémise fidèlement. Elle porte aussi les arêtes d'avant, pour comparer. Elles
n'ont pas d'identifiant, donc rien ne les exige : c'est la page qui dit si elles tiennent encore.

**La fusion retire le membre qui ne désigne plus aucun nœud, et le dit.** L'hyperarête reste.

**`fusionne` écrit le graphe de l'arbre d'où il est lancé, et refuse un autre graphe.** Son verdict
porte ce qu'une fusion à vide perd, à côté de ce que les lots font perdre.

**Les ponts parcourent les pages qui ont un nœud de page.** Le parcours se dérive du graphe. Deux
exclusions restent, nommées : `CHANGELOG.md`, et `.claude/skills`, copie de `.agents/skills` dont
les citations entreraient en double.

## Ce que cela ne couvre pas

Les pages d'`openspec/` et les compétences sont reliées à ce qu'elles citent, mais leur prose n'est
toujours pas lue : la couche sémantique garde le périmètre de l'ADR 5790.

Une page que la structure ne porte pas n'est pas parcourue, et ne reçoit aucune citation tant
qu'elle n'a pas son nœud de page.

Le plafond de trois hyperarêtes par lot ne compte que les neuves. Rien ne borne celles qu'un lot
réémet.

## Les alternatives écartées

- **Faire réémettre les hyperarêtes par la fusion.** Elle recollerait à la page une hyperarête dont
  le flux n'y est peut-être plus. Seul le lecteur de la page le sait.
- **Faire marcher `fusionne` sur un graphe désigné.** Les ponts lisent les pages de l'arbre courant :
  le graphe d'un autre arbre recevrait les citations d'un disque qui n'est pas le sien.
- **Ajouter `openspec/` à la liste des dossiers.** Le défaut reviendrait au prochain dossier. Une
  liste et une règle de reconnaissance écrites à deux endroits finissent par diverger, et c'est ce
  qui a fait perdre leur nœud de page à cinq pages de la racine.
- **Relier aussi `.claude/skills`.** Chaque citation d'une compétence existerait deux fois.

## Comment on le sait

L'auto-test de `scripts/graphify/couche_semantique.py`, lancé par `lint.yml`, refuse un rendu qui
omet l'hyperarête de sa page et l'accepte quand il la déclare ; il joue `fusionne` avec un faux
moteur, qui refuse un graphe étranger et retire un membre pendant avant d'écrire.

Ceux de `pont_doc_code.py` et de `pont_ci.py` jouent les passes sur un dépôt fabriqué : une page
d'`openspec/` qui cite une classe y est reliée, la copie des compétences et le journal des versions
ne le sont pas.

Avec le moteur, absent du runner, la mesure se rejoue à la main sur une copie du graphe.
