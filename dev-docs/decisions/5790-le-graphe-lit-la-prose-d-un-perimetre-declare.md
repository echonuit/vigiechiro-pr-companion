---
type: adr
title: "Le graphe lit la prose d'un périmètre déclaré, et dit ce qu'il n'a pas lu"
status: stable
article: A3
chantier: "#5790 (le graphe partagé n'avait aucune couche sémantique), lot 1 de #5812"
decided_at: 2026-10-04
verification: humaine
verification_note: "le graphe est ignore par git et ne vit que sur un poste : aucune demande de fusion ne peut le lire, donc aucun dispositif ne peut refuser"
enforced_by: []
verified:
  - by: humain
    at: 2026-10-04
relations:
  prolonge: ["5553-trois-index-repondent-aucun-ne-devient-un-garde"]
  completee_par: ["5813-une-hyperarete-porte-un-identifiant-et-une-mise-a-jour-declare-ce-qu-elle-lache", "5857-un-registre-sort-du-perimetre-et-une-page-sortie-quitte-la-couche", "5877-la-mise-a-jour-du-graphe-relit-la-structure-de-toutes-les-pages", "5814-une-empreinte-par-page-dit-ce-qui-est-a-reextraire", "5868-une-citation-part-du-noeud-de-page-et-un-pont-ne-fabrique-pas-de-noeud-de-document"]
generated:
  by: "process:assistance-par-agents"
---

# Le graphe lit la prose d'un périmètre déclaré, et dit ce qu'il n'a pas lu

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-05** : les trois parades sont **complétées** par
    [5813](5813-une-hyperarete-porte-un-identifiant-et-une-mise-a-jour-declare-ce-qu-elle-lache.md).
    L'outil en porte cinq : une hyperarête sans identifiant en reçoit un, et une mise à jour déclare
    l'identifiant sémantique qu'elle lâche. Le reste fait foi.

    **2026-10-05** : le périmètre est **complété** par
    [5857](5857-un-registre-sort-du-perimetre-et-une-page-sortie-quitte-la-couche.md).
    `scripts/methode/relus.txt` en sort, et une page sortie du périmètre quitte la couche par la
    commande `oublie`.

    **2026-10-05** : ce que la couche ne garantit pas est **complété** par
    [5877](5877-la-mise-a-jour-du-graphe-relit-la-structure-de-toutes-les-pages.md).
    `graphify update .` conserve la couche, mais ne relit plus la structure d'une page qui la
    porte. `rebuild.py --mets-a-jour` la relit, et dit ce qu'il retire.

    **2026-10-05** : « une page modifiée garde ses nœuds d'avant, et rien ne le signale » est
    **complété** par [5814](5814-une-empreinte-par-page-dit-ce-qui-est-a-reextraire.md).
    Une empreinte par page le signale, et `a-reextraire` rend ces pages.

    **2026-10-05** : la reconstruction des ponts est **complétée** par
    [5868](5868-une-citation-part-du-noeud-de-page-et-un-pont-ne-fabrique-pas-de-noeud-de-document.md).
    Une citation part du nœud de page, et un pont ne fabrique pas de nœud de document.

## Le contexte

`AGENTS.md` prescrit d'interroger le graphe du dépôt avant tout autre outil. Jusqu'au 4 octobre 2026,
ce graphe était entièrement de structure : 31 276 nœuds, dont 7 220 issus de pages `.md`, qui n'y
entraient que par leur fichier et leurs titres de section.

Une question conceptuelle, « quelles pages décrivent le dépôt en archive ? », recevait donc des
titres. Le défaut n'était pas l'absence de réponse : un nœud de page existe, porte un libellé et
répond. Rien ne distinguait « je n'ai rien trouvé dans la prose » de « je n'ai pas lu la prose ».

## Ce qui a été mesuré

Le 4 octobre, une couche sémantique a été extraite de la prose par des agents, puis fusionnée dans
le graphe de la copie principale.

| | Avant | Après |
|---|---:|---:|
| Nœuds | 31 276, tous de structure | 34 748, dont 3 090 sémantiques |
| Arêtes | 109 641 | 119 059, dont 6 533 sémantiques |
| Pages lues | 0 | 577 |

Le coût : 32 lots d'environ 22 000 mots, un agent par lot, environ 245 000 jetons chacun, soit près
de 7,9 millions. Le coût fixe domine : un lot pilote de 4 300 mots en demandait déjà 127 000.

## La décision

**Le graphe porte une couche sémantique sur un périmètre écrit ici, et la page qui le prescrit dit
ce qu'il lit et ce qu'il ne lit pas.**

Le périmètre : `brief/`, `dev-docs/`, `docs/`, les `.md` de la racine hors `CHANGELOG.md`, et les
documents de `scripts/` et de `src/` hors fichiers `*.approved.txt`.

Hors couche, donc lus par leurs seuls titres : `.github/`, `openspec/`, `.agents/skills/`, `.claude/`,
`recette/`, `flatpak/`, les images et les vidéos.

## Les parades, et ce que chacune a coûté de ne pas exister

Trois défauts ne font rougir aucun dispositif, et se mesurent **avant** la fusion.

- **Un nœud sémantique ne réémet jamais un identifiant de structure.** Chaque lot reçoit la liste des
  identifiants de ses fichiers et s'y ancre par une arête. Les 32 lots ont passé l'audit sans reprise.
- **Un nœud sémantique homonyme d'un titre se replie sur ce titre.** Un agent nomme volontiers sa
  décision comme la section qui la porte. Le dédoublonnage fusionne alors les deux sous l'identifiant
  sémantique, et celui du titre disparaît : 62 perdus au premier essai, dont 10 par ressemblance
  approchée. Le repli en traite 101 et n'en perd aucun.
- **Le champ des membres d'une hyperarête se normalise.** Les agents l'ont nommé de quatre façons, le
  moteur n'en lit qu'une, et les autres hyperarêtes tombaient sans message.

La perte se juge contre une **fusion à vide**, qui fait déjà tomber 550 identifiants, presque tous des
titres répétés du journal des versions. Le premier garde en comptait 612 et refusait à tort : seul
l'écart de 62 était imputable au lot.

## Ce que la couche ne garantit pas

**Une page modifiée garde ses nœuds sémantiques d'avant, et rien ne le signale.** Sept pages ont
changé pendant l'extraction elle-même.

**Personne n'écrit ce graphe à la main.** Quand `VIGIECHIRO_GRAPHIFY=1` est posé, le crochet
`post-commit` le refait après chaque commit de la copie principale, par `scripts/graphify/rebuild.py`.
La reconstruction de ce script conserve la couche : jouée le 4 octobre, elle a rendu les 3 090 nœuds.
`graphify update .` la conserve aussi, mais renomme toutes les communautés d'après leur nœud le plus
connecté.

Les lots #5813 et #5814 du chantier #5812 versent l'outillage au dépôt et traitent le vieillissement.

## Les alternatives écartées

- **Renoncer, et faire dire au crochet que le graphe ne lit pas la prose.** C'était l'autre branche du
  critère de #5790. Elle laissait la passe 3 de la clôture demander au graphe des concepts qu'il ne
  pouvait pas rendre.
- **Extraire quelques pages.** Écarté à la clôture de #5643 : un îlot de concepts sans voisin
  sémantique répond moins bien qu'aucun.
- **Suivre le détecteur de changements de l'outil.** Il signale le corpus entier, 3 427 fichiers,
  faute d'empreinte sémantique au manifeste, et encore 2 857 après estampillage. Ce compte ne désigne
  aucune page à relire.
- **Faire de la couche un garde.** L'ADR 5553 l'a tranché pour trois index : ils répondent, ils ne
  refusent pas. Un graphe que git ignore peut encore moins refuser.

## Comment on le sait

Les chiffres de cette page se relisent dans `graphify-out/graph.json` de la copie principale, en
comptant les nœuds par leur champ `_origin`. Aucun dispositif ne le fait à la place du lecteur, et
l'en-tête le déclare : la vérification est `humaine`, parce que le fichier jugé n'atteint jamais une
demande de fusion.
