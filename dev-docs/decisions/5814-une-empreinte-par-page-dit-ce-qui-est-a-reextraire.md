---
type: adr
title: "Une empreinte par page dit ce qui est à réextraire, et les libellés des communautés suivent leurs membres"
status: stable
article: A3
chantier: "#5814 (une page modifiée gardait ses nœuds sémantiques d'avant), lot 3 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/couche_semantique.py"
  - "scripts/graphify/rebuild.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5790-le-graphe-lit-la-prose-d-un-perimetre-declare"]
  completee_par: ["5940-la-partition-du-graphe-se-garde-et-ne-se-refait-que-sur-demande"]
generated:
  by: "process:assistance-par-agents"
---

# Une empreinte par page dit ce qui est à réextraire, et les libellés des communautés suivent leurs membres

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-05** : « les libellés des communautés suivent leurs membres » est **complété** par
    [5940](5940-la-partition-du-graphe-se-garde-et-ne-se-refait-que-sur-demande.md).
    La partition se garde d'une reconstruction à la suivante, et ses libellés se reportent par
    identifiant. Le recouvrement ne sert plus qu'après une repartition demandée. Le reste fait foi.

## Le contexte

L'ADR 5790 nommait ce que la couche sémantique ne garantit pas : une page modifiée garde ses nœuds
d'avant, et rien ne le signale. Le graphe répond alors l'ancienne prose.

Savoir quelles pages avaient changé se faisait à la main, par un `git diff` contre un commit tenu de
mémoire. La compétence d'extraction a bien un détecteur, mais il signale le corpus entier.

Et mettre à jour la structure coûtait autre chose : `graphify update .` renomme toutes les
communautés d'après leur nœud le plus connecté.

## Ce qui a été mesuré

Le 5 octobre :

| | |
|---|---:|
| pages que la règle du périmètre rend sur le commit de la première extraction | 577 sur 577 |
| pages rendues par l'outil sur le graphe de référence | 14 |
| pages rendues par un `git diff` indépendant, mêmes chemins et mêmes raisons | 14 |
| communautés renommées par `graphify update .` | 1 287 |
| libellés que le report retrouve | 1 072, soit 83 % |

## La décision

**`fusionne` note l'empreinte git de chaque page qu'il fusionne, et `a-reextraire` rend les pages
dont l'empreinte a changé, les pages neuves et les pages disparues, chacune avec sa raison.**

Le registre vit à côté du graphe, dans `graphify-out/couche-semantique.json`. L'empreinte est relevée
par `decoupe`, au moment où les agents commencent à lire : une page modifiée pendant l'extraction
ressort donc comme modifiée.

**L'outil refuse, au lieu de rendre une liste vide, quand il n'a ni graphe ni empreinte.** Le graphe
est ignoré par git et ne vit que dans la copie principale. Depuis un worktree, une liste vide est le
résultat le plus facile à obtenir, et celui qui ne prouve rien.

**`rebuild.py --mets-a-jour` reporte les libellés des communautés par recouvrement de leurs
membres**, depuis une copie prise avant `graphify update .`. Un libellé va à la communauté nouvelle
qui recouvre le mieux l'ancienne, au sens de Jaccard, si ce recouvrement atteint 0,3. Il ne se donne
qu'une fois.

## Ce que cela ne couvre pas

`a-reextraire` ne voit que les pages suivies par git. Une page neuve non indexée n'y figure pas.

Une page disparue est nommée, pas nettoyée. La retirer est le geste de l'ADR 5857.

L'outil dit quelles pages relire, il ne les relit pas : la réextraction demande une session.

Le report ne retrouve pas tout. Une communauté que la mise à jour a scindée ou refondue reçoit un
libellé calculé. Mesuré le 5 octobre entre deux mises à jour du graphe de référence : 720 libellés
distincts retrouvés sur 919.

## Les alternatives écartées

- **Noter un seul commit d'extraction.** C'était le critère de fin d'origine. La couche a été faite
  en deux passes le 4 octobre, et six pages ont été écartées de la seconde exprès : un commit ne
  décrit pas cela.
- **S'en remettre au détecteur de la compétence d'extraction.** Il signale le corpus entier, et la
  fusion qu'il lance n'a aucune des parades de l'ADR 5790.
- **Rendre une liste vide sans graphe.** C'est ce qu'un outil fait par défaut, et c'est une réponse
  que rien ne distingue de « tout est à jour ».
- **Reporter les libellés par identifiant de communauté.** Les identifiants changent à chaque
  partition : le libellé irait à une communauté sans rapport.

## Comment on le sait

L'auto-test de `scripts/graphify/couche_semantique.py`, lancé par `lint.yml`, fabrique un dépôt
témoin avec git. Une page du périmètre modifiée y est rendue, une page hors périmètre ne l'est pas,
une page posée à la racine d'un dossier l'est, la liste est vide quand rien n'a changé, et l'outil
sort en 2 sans graphe comme sans empreinte.

Celui de `scripts/graphify/rebuild.py` tient le report : un libellé suit sa communauté quand son
identifiant change, il n'est pas reporté sous le seuil, et deux communautés fusionnées n'en nomment
pas deux fois une seule.
