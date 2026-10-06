---
type: adr
title: "Les sujets de main n'ont pas de garde, et fusionner sans --subject en tient lieu"
status: stable
article: A11
chantier: "#4775, lot 4 du chantier #6009 (la prise et la clôture reposent sur des gestes qu'aucune compétence n'écrit)"
decided_at: 2026-10-06
verification: humaine
verification_note: "un sujet de squash se compose a la fusion : un garde ne le jugerait qu apres, sur main ou rien ne se repare ; la mesure qui fonde le renoncement se refait par la commande du corps, et sa section finale dit ce qui le rouvrirait"
enforced_by: []
verified:
  - by: humain
    at: 2026-10-06
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-06
---

# Les sujets de main n'ont pas de garde, et fusionner sans --subject en tient lieu

## Le contexte

Ce dépôt fusionne en squash. La forge compose le sujet du commit depuis le titre de la demande et
lui accole son numéro, `(#N)`. Le journal publié tire de ce numéro son lien vers la demande.

Le 29 août 2026, le commit `3a2a30662` est arrivé sur `main` sans son `(#4769)`. La fusion avait
reçu un `--subject`, qui remplace le sujet composé au lieu de le compléter. Rien n'a rougi :
`verifie_titre_pr.py` juge le titre de la demande, avant la fusion, et rien ne juge le sujet du
commit après.

L'issue #4775 demandait deux choses : écrire le geste, et décider s'il fallait un garde sur les
sujets de `main`.

## La décision

Le geste est écrit, et il n'y a pas de garde.

La compétence `clore-une-pr` et `dev-docs/ci-cd-release.md` prescrivent de fusionner nu, sans
`--body-file` ni `--subject`, et disent ce que chaque drapeau efface (#6026). Aucun dispositif ne
relit les sujets de `main`. Le porteur a confirmé ce renoncement le 6 octobre 2026.

## La mesure

Refaite le 6 octobre 2026 par l'API de la forge, sur les commits de `main` depuis le 29 août 2026,
la tête étant `47a8bedfea` :

| Population | Compte |
|---|---|
| commits | 671 |
| sujets sans `(#N)` | 66 |
| dont commits automatiques, qui portent la marque de saut de CI | 65 : captures 49, release 8, flatpak 8 |
| reste | 1, `3a2a30662` |

```bash
gh api --paginate "repos/echonuit/vigiechiro-pr-companion/commits?sha=main&since=2026-08-29T00:00:00Z&per_page=100" \
  -q '.[] | .commit.message | split("\n")[0]' | grep -v -E '\(#[0-9]+\)$' | grep -v -F '[skip ci]'
```

La mesure se fait par la forge, pas par `git log` : un clone superficiel la tronque sans le dire.
Celui du poste s'arrêtait au 29 août à 13 h 55 et rendait 646 commits pour 671.

Le lot avait compté 659 commits quelques heures plus tôt, avec les mêmes 66, 65 et 1. Une occurrence,
donc, et aucune récidive.

Le dégât de cette occurrence est nul. `3a2a30662` est un `docs`, et le journal ne porte que les
`feat`, `fix`, `perf` et `revert` : il n'a perdu aucune ligne. Le risque vaut pour un commit de ces
quatre types, dont la ligne garderait son lien de commit et perdrait celui de la demande.

## Ce qui est écarté, et pourquoi

**Un garde sur les sujets de `main`**, qui refuserait un commit sans `(#N)` en exemptant les commits
automatiques. Trois raisons l'écartent.

Il jugerait trop tard. Le sujet n'existe qu'à la fusion : le garde rougirait sur `main`, où le commit
ne se répare pas sans réécrire l'historique, ou chez la demande suivante, qui n'y est pour rien.

Il coûterait plus que ce qu'il protège. Les deux ateliers qui posent un sujet à dessein,
`capture-vues.yml` et `flatpak.yml`, et les commits de version demandent une exemption à tenir. Les
sujets d'avant le régime de squash, 342 comptés le même jour sur l'historique entier, demandent un
cliquet ou une fenêtre datée. En face : un lien de journal, perdu zéro fois.

Il garderait une règle qui a déjà son remède en amont. Fusionner sans `--subject` laisse la forge
composer le sujet, et elle ne l'oublie pas.

Ce renoncement n'est pas celui de l'[ADR 5414](5414-une-regle-que-rien-ne-peut-garder-se-declare.md),
qui couvre les règles qu'aucun garde ne peut tenir. Ici un garde est possible, et c'est son coût
rapporté à une récurrence nulle qui l'écarte. L'article A11 demande que cela soit écrit : sans cette
page, le prochain lecteur de #4775 y verrait un garde oublié et l'écrirait.

## Ce qui rouvrirait la question

- Une récidive : la commande ci-dessus rendant une seconde ligne, surtout sur un `feat` ou un `fix`.
- Un contrôle que la forge offrirait avant la fusion sur le sujet composé : le garde cesserait de
  juger trop tard.
- Un autre usage du `(#N)` que le lien du journal, qui ferait monter le prix d'un sujet sans numéro.
