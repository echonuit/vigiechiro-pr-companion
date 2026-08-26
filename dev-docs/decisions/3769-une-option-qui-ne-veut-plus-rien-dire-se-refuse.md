---
type: adr
title: "Une option qui ne veut plus rien dire se refuse, elle ne s'ignore pas"
status: stable
article: A13
chantier: "#3769, suites de #3458"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "ListerSitesVigieChiroTest#tout_avec_carre_est_refuse"
verified:
  - by: machine:ci
    at: 2026-08-16
---

# Une option qui ne veut plus rien dire se refuse, elle ne s'ignore pas

## Contexte

`lister-sites-vigiechiro --carre 130711` lisait **une page sur 208** puis filtrait côté client. Le carré
cherché n'étant presque jamais dans cette page, le tableau sortait **vide sur un carré qui existe**.

La ligne de bilan l'avouait - « 100 site(s) lu(s) sur 20 767 annoncés (1 page sur 208). Échantillon » -
mais qui lit le tableau y lit « ce carré n'existe pas ».

Depuis #3842, `--carre` passe par `GET /sites?q=`, mesuré filtrant côté serveur. La question qui restait
était : **que faire de `--pages` et `--tout`**, qui bornaient l'étendue d'une lecture qui n'a plus lieu ?

## Décision

**Une option que le nouveau mode rend vide de sens est refusée** (code de sortie 2), pas acceptée sans
effet.

`--pages` et `--tout` avec `--carre` : la recherche porte sur la collection **entière**, il n'y a donc
plus d'étendue à borner. Les accepter en silence serait exactement ce que ce dépôt reproche à `where=`
côté plateforme - un paramètre qu'on croit avoir posé, et qui ne fait rien.

Le refus tombe **avant** toute lecture de jeton : un test `bats` exige que le message ne parle pas de
jeton, donc qu'aucun réseau n'a été touché.

### Le dénominateur ne disparaît pas, il change de nature

« 1 site lu : collection complète » serait **exact et pourtant trompeur** - on n'a pas parcouru la
collection, on a posé une question. La sortie annonce donc « Recherche du carré 130711 sur toute la
collection : 1 site(s) trouvé(s) ».

La garde héritée de #1277 tient : un échantillon ne peut toujours pas passer pour un recensement, ni
l'inverse. Supprimer le dénominateur comme « inutile » rouvrirait précisément le défaut que cette ADR
existe pour fermer.

### `--point` reste filtré côté client

Aucun équivalent serveur. Inventer un filtre qui ne filtre pas serait pire que de l'appliquer après
coup - c'est la même règle que ci-dessus, appliquée à nous-mêmes.

## Conséquences

- **Les deux surfaces posent la même question de la même façon** : la fenêtre de déclaration (#3787) et
  la ligne de commande (#3769).
- **Un usage devient impossible** : `--carre` ne peut plus servir à échantillonner. C'est voulu ; il ne
  le servait qu'en donnant une réponse fausse.
- **`q` cherche des mots entiers, pas des préfixes** (`13071` ne ramène pas `130711`). Une éventuelle
  recherche partielle ne pourra pas s'appuyer dessus.

## Alternatives écartées

- **Accepter et ignorer** : le mode de panne de `where=`, celui qui ne rougit jamais.
- **Garder la pagination quand `--pages` est présent** : deux chemins pour une question, dont l'un rend
  un faux négatif. C'est le défaut d'origine, conservé sous condition.
- **Supprimer la ligne de dénominateur** : elle est ce qui distingue un échantillon d'un recensement, et
  sa disparition serait invisible jusqu'au jour où elle compte.
