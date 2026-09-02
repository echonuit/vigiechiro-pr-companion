---
type: adr
title: "Un chemin suivi ne porte que de l'ASCII, et la prose garde ses accents"
status: stable
article: A3
chantier: "#5089 (chantier #5088)"
decided_at: 2026-09-02
verification: certaine
enforced_by:
  - ".github/scripts/verifie-chemins-ascii.sh"
verified:
  - by: machine:suspects
    at: 2026-09-02
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-02
---

# Un chemin suivi ne porte que de l'ASCII, et la prose garde ses accents

## Contexte

`git ls-files` et `git diff --name-only` **échappent** les chemins non-ASCII par défaut. Ils rendent
`brief/docs/Objectifs qualit\303\251s/index.md` là où le disque porte `Objectifs qualités`. Tout
outillage qui teste ensuite l'existence du fichier rejette la ligne, et le rejet ne dit rien.

Le 1er septembre 2026, une passe de mise à jour du graphe de connaissances a ainsi **écarté
50 fichiers** de `brief/` et présenté **6 renommages typographiques comme 56 suppressions**. Aucun
garde n'a rougi : le corpus était seulement incomplet, et il a fallu comparer le graphe avant et
après pour s'en apercevoir. C'est exactement ce que l'article A3 refuse : un dispositif qui ne dit pas
ce qu'il n'a pas pu lire.

La mesure : **101 chemins sur 3 203** portaient du non-ASCII, tous sous `brief/`. Rien que des
lettres accentuées - `é` 200 fois, `è` 59, `à` 2, `ô` et `ê` une chacune. Les 156 chemins de `brief/`
étaient déjà en **NFC** : ce n'était pas un défaut de normalisation Unicode, mais un défaut de jeu de
caractères.

## Décision

**Un chemin suivi par git ne porte que de l'ASCII.** Les lettres accentuées se translittèrent
(`é` vers `e`). Le garde refuse à tolérance zéro, sur tout le dépôt.

**Ce que la règle ne dit pas.** Elle ne dit rien de la casse, des espaces, des apostrophes ni des
tirets. `core.quotePath` ne les échappe pas : ils ne sont pas la cause, et une règle qui les
emporterait refuserait sans mesure. Une première rédaction normalisait tout en kebab-case ; elle a
été écartée parce que `C12`, `P17` et `M-CompteRendu` sont des **identifiants**, pas des phrases à
transformer en slugs, et que les minusculer détruit un contraste porteur de sens sans rien régler.

**La tension avec l'article A24 est assumée.** La langue du dépôt est le français, et elle le reste
partout où un humain lit : titres, prose, libellés d'interface, texte des liens, titres de la
navigation. Seul le **chemin** devient ASCII, parce que lui seul est manipulé par des outils dont on
ne contrôle pas les valeurs par défaut. `[Objectifs qualités](Objectifs%20qualites/index.md)` garde
son accent là où l'oeil le voit et le perd là où git le mange.

**Tolérance zéro plutôt que cliquet.** Les 101 chemins ont été renommés dans le même lot : la zone
est à zéro le jour de la décision. L'article A9 réserve le cliquet aux dettes qu'on ne peut pas vider
d'un coup, ce qui n'est pas le cas ici.

## Conséquences

**Ce qu'on gagne.** N'importe quelle commande git qui liste des chemins devient utilisable telle
quelle, sans que l'auteur ait à savoir que `core.quotePath` existe. Le mode de panne disparaît, il ne
se documente pas.

**Ce qu'on paie.** Les 101 URL publiées du site de brief changent. Elles ne sont référencées nulle
part hors du dépôt à notre connaissance, et c'est le seul effet observable de l'extérieur.

**Ce qui reste ouvert.** Les espaces dans les chemins restent autorisés. Ils obligent à citer les
variables dans les scripts shell, ce qui est une discipline ordinaire, et aucune mesure ne les
désigne aujourd'hui comme cause d'un défaut. Le jour où l'une le fera, elle fondera sa propre ADR.

**Comment on le voit rouge.** Sept cas tournent dans `lint.yml`, dont trois qui doivent rougir : un
nom de fichier accentué, un répertoire accentué au-dessus d'un fichier propre, et un caractère
non-latin. Un huitième exerce le **chemin réel** plutôt que le leurre, faute de quoi le
`-c core.quotePath=false` dont tout dépend ne serait éprouvé par rien - le garde serait vert
précisément sur ce qu'il doit refuser. Et il a été confronté au cas réel : un fichier accentué
ajouté et indexé exprès le fait sortir en code 1, son retrait le rend vert.
