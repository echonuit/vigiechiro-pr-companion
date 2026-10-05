---
type: adr
title: "Un registre réécrit à chaque relecture sort du périmètre, et une page sortie quitte la couche"
status: stable
article: A3
chantier: "#5857 (un registre sortait « modifiée » à presque chaque passe), lot 4 de #5812"
decided_at: 2026-10-05
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

# Un registre réécrit à chaque relecture sort du périmètre, et une page sortie quitte la couche

## Le contexte

L'ADR 5790 met dans le périmètre de la couche sémantique « les documents de `scripts/` ». Parmi
eux, `scripts/methode/relus.txt` : une ligne par fichier Java relu, avec l'empreinte de sa javadoc.
Chaque relecture le réécrit.

Depuis #5814, une commande rend les pages modifiées depuis leur extraction. Sa première tournée
réelle a montré ce que cette phrase du périmètre coûtait.

## Ce qui a été mesuré

Le 5 octobre, à `5b584815f`, sur les 33 fusions arrivées sur `main` depuis la première extraction :

| Page | Fusions qui la touchent | Sa couche |
|---|---:|---|
| `scripts/methode/relus.txt` | 15 | un nœud, aucune arête |
| `scripts/adr/non-declarees.txt` | 0 | un nœud |
| `scripts/methode/versions-verifiees.txt` | 0 | quatre nœuds |
| `dev-docs/decisions/index.md` | 17 | dix-huit nœuds, sur son introduction |

Le registre sortait donc « modifiée » à presque chaque passe. Il fallait le noter relu à la main,
ou le donner à un agent qui relirait 2 200 lignes d'empreintes pour ne rien y trouver.

Le sortir du périmètre ne suffisait pas. Une page qui quitte le périmètre reste au registre des
empreintes, et la commande la rend alors « disparue », à chaque passe aussi. Ses nœuds, eux,
restent dans le graphe et répondent pour une page que plus personne ne relit.

## La décision

**`scripts/methode/relus.txt` sort du périmètre, nommément, et une page sortie du périmètre ou du
dépôt se retire de la couche par une commande, `oublie`.**

L'exclusion est nommée, comme celle du journal des versions : aucune règle de forme ne distingue ce
registre de ses deux voisins, qui n'ont pas bougé une fois.

`oublie` retire les nœuds sémantiques de la page, toute arête qui touchait l'un d'eux, ses
hyperarêtes, et son empreinte. Une hyperarête d'une autre page perd le membre retiré et reste. La
couche de structure n'est pas touchée : sa mise à jour sait déjà retirer une page disparue.

La commande refuse une page encore dans le périmètre. Celle-là se réextrait, elle ne s'oublie pas.

## Ce que cela ne couvre pas

`dev-docs/decisions/index.md` bouge autant que le registre, et reste dans le périmètre : il porte de
la prose. Quand seul son tableau a changé, il se note relu à la main, par `note`.

Rien ne lance `oublie` tout seul. Une page disparue reste nommée par `a-reextraire` jusqu'à ce que
quelqu'un la retire, et c'est voulu : effacer une couche sans qu'on l'ait demandé serait une perte
de plus que personne n'aurait vue.

## Les alternatives écartées

- **Sortir tous les `.txt` de `scripts/`.** Deux sur trois n'ont pas bougé, et l'un porte quatre
  nœuds utiles. Une règle de forme aurait jeté ce que la mesure ne condamne pas.
- **Garder le registre et le noter relu à chaque passe.** C'est ce que la tournée du 5 octobre a
  fait, à la main. Un geste répété à chaque passe pour une page d'un nœud n'est pas une méthode.
- **Laisser `fusionne` retirer les pages disparues.** La fusion effacerait alors des nœuds sur la
  foi d'une liste qu'on ne lui a pas montrée.
- **Réécrire l'ADR 5790.** Elle dit le périmètre du 4 octobre et porte l'encart qui renvoie ici.

## Comment on le sait

L'auto-test de `scripts/graphify/couche_semantique.py`, lancé par `lint.yml`, tient les deux
moitiés. La règle du périmètre refuse le registre et garde ses deux voisins ; rejouée sur le commit
de la première extraction, elle rend 576 de ses 577 pages. Et sur un dépôt témoin, `oublie` retire
la couche de la page disparue sans toucher celle d'une autre, après quoi la commande ne la rend
plus, ni modifiée ni disparue.
