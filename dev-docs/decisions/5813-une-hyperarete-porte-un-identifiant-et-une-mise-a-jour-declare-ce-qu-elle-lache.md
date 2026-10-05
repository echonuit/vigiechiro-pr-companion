---
type: adr
title: "Une hyperarête porte un identifiant, et une mise à jour déclare ce qu'elle lâche"
status: stable
article: A3
chantier: "#5813 (l'outillage de la couche sémantique n'existait que dans le brouillon d'une session), lot 2 de #5812"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "scripts/graphify/couche_semantique.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5790-le-graphe-lit-la-prose-d-un-perimetre-declare"]
generated:
  by: "process:assistance-par-agents"
---

# Une hyperarête porte un identifiant, et une mise à jour déclare ce qu'elle lâche

## Le contexte

L'ADR 5790 nomme trois défauts de la fusion des lots sémantiques qui ne font rougir aucun
dispositif. Elle a été écrite après la première extraction, et avant que la couche ne soit mise à
jour une seule fois.

Le même jour, 19 pages ont été réextraites, puis l'outillage a été versé au dépôt et rejoué sur ces
lots. Deux pertes de plus sont sorties, toutes deux invisibles dans un compte de nœuds.

## Ce qui a été mesuré

**Une hyperarête sans identifiant tombe à la fusion suivante, même à vide.** La première extraction
en avait produit 71, dont 25 sans `id` : les agents l'omettent, et le moteur n'en dit rien. La mise
à jour des 19 pages les a toutes effacées, dont 22 sur des pages qu'elle ne touchait pas.

| | Hyperarêtes |
|---|---:|
| après la première extraction | 71, dont 25 sans identifiant |
| après la mise à jour de 19 pages | 47 |
| le même graphe, un identifiant posé sur les 25, après une fusion à vide | 71 |

La perte n'a pas été vue en fusionnant. Les contrôles comparaient des comptes de nœuds, et les
hyperarêtes sont une liste à part dans `graph.json`. Elle est sortie trois heures plus tard, d'un
chiffre lu sans l'attendre.

**Une réextraction remplace toute la couche de sa page.** Un identifiant sémantique que le lot ne
réémet pas est effacé, et les arêtes venues d'autres pages se rompent. Sur les 19 pages, les agents
avaient reçu la liste des 272 identifiants existants : ils en ont réémis 272. Rien ne le garantissait
pour la passe suivante.

## La décision

**L'outil donne un identifiant à toute hyperarête qui n'en porte pas, et il refuse un lot de mise à
jour qui lâche un identifiant sémantique sans le déclarer.**

L'identifiant posé est dérivé de la page, du libellé et des membres de l'hyperarête. Deux fusions du
même lot lui donnent donc le même, et un rejeu ne la double pas.

Un lot qui lâche un identifiant le nomme dans son champ `laches`. Lâcher reste permis : une page qui
ne porte plus un concept n'a pas à garder son nœud. Ce qui est refusé est de le perdre sans l'avoir
dit.

## Ce que cela ne couvre pas

Le graphe de référence a été réparé à la main : 22 hyperarêtes rendues, 69 au total, toutes
identifiées. Celles des pages réextraites entre-temps n'ont pas été rendues, puisque leur page les
avait remplacées.

L'outil ne peut rien pour un graphe fusionné sans lui. `/graphify . --update`, que la compétence
générique propose, fusionne sans aucune des cinq parades.

## Les alternatives écartées

- **Demander l'identifiant aux agents, et s'en tenir là.** La consigne le demande désormais. Elle le
  demandait aussi pour le champ des membres, que les agents ont nommé de quatre façons.
- **Un compteur pour identifiant.** Un rejeu du même lot ferait naître l'hyperarête une seconde fois.
- **Interdire de lâcher un identifiant.** Une page réécrite garderait des nœuds pour des concepts
  qu'elle ne porte plus, et la couche mentirait dans l'autre sens.
- **Réécrire l'ADR 5790.** Elle est acceptée et dit ce qu'on savait le 4 octobre au matin. Elle porte
  l'encart qui renvoie ici.

## Comment on le sait

L'auto-test de `scripts/graphify/couche_semantique.py`, lancé par `lint.yml`, porte un cas rouge et
un cas témoin par parade. Les cinq ont été vues rouges en les retirant du code : 14 mutations jouées
au lot 2, toutes tuées par un cas nommé.

Deux de ces cas ont dû être refaits. Le repli exact survivait à sa mutation, parce que le repli
approché le couvrait. Et quatre cas du champ des membres bouclaient sur la constante qu'ils
éprouvaient.
