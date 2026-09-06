---
type: adr
title: "Un garde qui ne déclare pas ses chemins est lancé"
status: stable
article: A5
chantier: "#5294 (le coût des ateliers), lot #5340"
decided_at: 2026-09-06
verification: probable
enforced_by:
  - "scripts/adr/5340-chemins-non-declares.py"
ratchet: 47
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Un garde qui ne déclare pas ses chemins est lancé

## Le contexte

Un agent qui a fini ne sait pas quoi lancer. Il a deux options : tout lancer, ce qui coûte une
cinquantaine de minutes, ou deviner. Il devine, et le banc qu'il se compose est tantôt
disproportionné, tantôt insuffisant.

Trois fois en une journée sur le chantier #5294 : une batterie **dérivée puis filtrée à la main**
qui a laissé passer un rouge jusqu'en CI ; un harnais qui lançait `python3` **sans argument** cent
trente et une fois, zsh ne découpant pas `$ligne` en mots, et annonçait cent trente et une commandes
vertes ; et un butoir plus court qu'un banc de mutation, qui a fabriqué un faux rouge.

Le dépôt avait déjà mesuré le manque : l'EPIC #5006 relève « porte d'entrée unique : **aucune**, ni
Makefile ni justfile, et le `pre-commit` ne fait que Spotless ».

## Ce qui rendait la porte impossible jusqu'ici

Chaque garde déclare une `population` depuis #5006, mais **elle est de la prose** : « PRODUCTION +
TESTS », « les ADR de dev-docs/decisions ». Elle se lit, elle ne se joint pas à un `git diff`.

Et l'inférer est un cul-de-sac **déjà mesuré** : `loupe-5175-population-non-nommee.py` ne résout le
parcours que de **treize gardes sur quarante et un**, et écrit qu'« aucune loupe ne les couvrira ».

## La décision

**Un champ `chemins` facultatif est ajouté au contrat, et un garde qui ne le déclare pas est LANCÉ.**

Le défaut penche du côté coûteux, jamais du côté muet. C'est le même parti que la portée CI du même
chantier, qui vérifie tout quand la base de comparaison manque.

## Pourquoi facultatif, et pourquoi c'est ce qui rend la décision applicable

`CHAMPS_DU_CONTRAT` est un tuple fermé dont `imprime_contrat` exige tous les membres : y ajouter
`chemins` ferait échouer les soixante-douze porteurs d'un coup.

Mais la raison de fond est meilleure que la contrainte technique. **Le repli rend la porte juste dès
le premier jour**, avec dix déclarants sur soixante-douze : elle lance trop, jamais trop peu. Le
cliquet la rend précise par tranches, sans qu'elle passe par un état où elle en oublie un.

Une porte qui aurait exigé les soixante-douze déclarations avant de servir n'aurait jamais servi.

## Le cliquet est passé de 62 à 48, et ce n'est PAS un progrès

Il faut le dire, sinon le nombre ment sur ce qu'il compte. La baisse ne vient pas de gardes qui se
seraient mis à déclarer leurs `chemins` : elle vient de #5363, qui a **retiré de la population** les
quatorze dispositifs qui ne jugent pas - loupes, rapports, générateurs. Ils ne peuvent pas faire
rougir la CI, donc la porte n'a aucune raison de les lancer, donc leur absence de déclaration ne
coûte rien.

**Un cliquet qui descend parce que sa population rétrécit n'a rien résorbé.** Le confondre avec un
gain rendrait la marge regagnée invisible le jour où elle se reperdrait. Les 48 restants sont la
vraie dette, et c'est elle qui doit descendre.

## Ce que le cliquet borne, et ce qu'il ne voit pas

Il compte les gardes muets, et ce compte descend. Il **ne voit pas** qu'un `chemins` déclaré soit
faux. Trop étroit, il ferait taire un garde qui devait juger ; trop large, il le ferait lancer
toujours et le cliquet le compterait quand même comme déclarant.

C'est pourquoi la vérification est **probable** et non certaine : le compte est exact, la décision
qu'il sert ne l'est qu'en partie. Une relecture trie, comme `loupe-5175` le fait pour la
`population`.

## Un garde illisible est engagé, il n'est pas sauté

La porte lit les contrats par `ast`. Sa première écriture sautait un fichier qu'elle ne savait pas
analyser, et cela a coûté le jour même : une insertion fautive a cassé
`verifie-dependances-declarees.py`, et la porte l'a fait **disparaître du corpus sans un mot**. Le
compte restait plausible, le garde n'était plus lancé, et rien ne le disait.

Un garde illisible est donc signalé **et engagé**, avec sa raison.

## Ce que la porte ne remplace pas

La CI. `AGENTS.md` le pose : la mesure fait foi en CI, pas sur le poste. La porte est le **premier
lecteur**, celui qui évite l'aller-retour, pas l'autorité.
