---
type: adr
title: "Une citation part du nœud de page, et un pont ne fabrique pas de nœud de document"
status: stable
article: A3
chantier: "#5868 (cinq pages de la racine avaient perdu leur nœud de page), lot 5 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/pont_doc_code.py"
  - "scripts/graphify/pont_ci.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5790-le-graphe-lit-la-prose-d-un-perimetre-declare"]
generated:
  by: "process:assistance-par-agents"
---

# Une citation part du nœud de page, et un pont ne fabrique pas de nœud de document

## Le contexte

Deux passes de pont relient une page à ce qu'elle cite dans un span de code : une classe Java, un
workflow, le `pom.xml`. Elles partaient du « nœud de fichier » de la page.

Cinq pages de la racine, dont `AGENTS.md`, avaient perdu leur nœud de page dans le graphe. L'index
donné aux agents se bâtit sur ces nœuds : aucun ne pouvait plus citer ces pages.

La cause tenait en trois temps. `pont_doc_code.py` parcourait les `.md` de la racine, mais ne
reconnaissait un nœud de fichier qu'à `brief/`, `dev-docs/` et `docs/`. Pour `AGENTS.md` il n'en
trouvait pas, en fabriquait un, et le nommait `agents_doc`, puisque `agents` était pris par le vrai
nœud de page. Or ce suffixe est celui que le moteur de graphify réserve au jumeau d'un document : il
supprimait le nœud `agents` au profit du doublon.

## Ce qui a été mesuré

Le 5 octobre, sur le graphe du 4 octobre rejoué sans aucun lot sémantique, puis sur une copie du
graphe de référence :

| | Avant | Après |
|---|---|---|
| pages de la racine avec leur nœud de page, sur huit | 3 | 8 |
| citations d'un workflow ou du `pom.xml` reliées | 172 | 190 |
| pages à l'index donné aux agents | 742 | 747 |
| nœuds sémantiques | 3 750 | 3 750 |

`pont_ci.py` portait une copie du même filtre. Lui ne fabriquait rien : il sautait la page, et dix-huit
citations de cinq pages n'étaient jamais reliées.

## La décision

**Une citation part du nœud de page de la page qui la porte, où qu'elle vive. Ce nœud se reconnaît à
sa nature, pas à son dossier ni à la longueur de son identifiant.**

Les deux passes partagent ce choix : `pont_ci.py` l'importe de `pont_doc_code.py` au lieu d'en porter
une copie.

**Un pont ne fabrique pas de nœud de document.** Une page que la structure ne porte pas attend la
sienne, et le journal de la passe la compte. La raison n'est pas l'économie. Un nœud de document qui
n'est pas de structure fait passer sa page pour couverte, et le moteur ne relit plus la structure
d'une page couverte : lui en fabriquer un, c'est lui interdire d'en recevoir un.

**La passe résorbe ce qu'elle avait laissé.** Un doublon se replie sur le nœud de page quand il est
là, avec ses arêtes. Sinon il quitte seulement le suffixe réservé. Une citation partie d'un autre
nœud de la page repart de la page.

## Ce que cela ne couvre pas

Ce lot laissait le parcours tel qu'il était : `brief/`, `dev-docs/`, `docs/` et la racine. Le
5 octobre, 173 pages qui ont un nœud de page vivaient ailleurs, dans `openspec/` et les compétences
surtout, et leurs citations n'étaient pas reliées. L'ADR
[5904](5904-une-hyperarete-se-declare-et-les-ponts-parcourent-les-pages-du-graphe.md) l'a tranché le
même jour : le parcours se dérive des nœuds de page.

La première passe de `pont_doc_code.py`, qui replie les nœuds de code logés dans une page, garde le
filtre à trois dossiers. Elle n'a rien à replier : le graphe ne porte aucun nœud de cette sorte.

## Les alternatives écartées

- **Fabriquer le nœud sous un autre suffixe.** C'était le premier remède envisagé. Il laisse la page
  sans structure à jamais, pour la raison dite plus haut.
- **Élargir le filtre aux fichiers de la racine.** Le défaut reviendrait au prochain dossier que le
  parcours gagnerait. La nature du nœud ne dépend pas du parcours.
- **Corriger le moteur.** Il fait ce qu'il annonce, et il vit hors du dépôt.

## Comment on le sait

Les auto-tests des deux passes, lancés par `lint.yml`, les jouent sur un dépôt fabriqué, comme
`rebuild.py` les joue : par `runpy`, dans la boucle d'un appelant qui doit reprendre la main. Une
page de la racine qui cite une classe y garde son nœud de page, une page sans nœud de page ne reçoit
ni nœud ni citation, et un doublon laissé par l'ancienne passe se replie quand la page revient.

La suppression elle-même est le fait du moteur, absent du runner. Elle se rejoue à la main, avec lui.
