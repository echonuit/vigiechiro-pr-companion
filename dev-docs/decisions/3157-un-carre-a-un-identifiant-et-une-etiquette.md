# ADR 3157 - Un carré a un **identifiant** et une **étiquette**, pas deux dimensions

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #3157, lot 1 du chantier #3151
- **Vérification** : certaine - `CritereLieuTest#un_carre_une_entree_deux_etiquettes`

> Le corollaire - deux carrés homonymes restent distincts - est tenu par
> `CritereLieuTest#deux_carres_homonymes_restent_distincts` : le garde des ADR n'accepte qu'une
> référence, la décision en a deux.

## Contexte

Le critère « Lieu » offrait quatre groupes sur la revue audio : **Communes**, **Carrés**, **Points**,
**Sites**. On les lisait comme quatre dimensions géographiques, et l'écart entre écrans - trois ici,
quatre là, et pas les mêmes trois - passait pour une question d'ergonomie à trancher (#3145).

Ce n'en était pas une. `monitoring_site` porte `square_number` **et** `friendly_name` sur la **même
ligne** : les projections les lisent côte à côte (`ms.square_number AS carre, ms.friendly_name AS
nom_site`). Cocher « 640380 » et cocher « Vallon » retenaient donc exactement le même ensemble, sans
que rien à l'écran ne dise que ce sont deux noms d'un seul objet.

Deux conséquences, l'une visible et l'autre latente :

- le menu s'allongeait d'une entrée par carré, pour un pouvoir de filtrage nul ;
- `friendly_name` n'a **aucune contrainte d'unicité** (V01) ni garde à la saisie. Deux carrés nommés
  « Vallon » donnaient une entrée qui en désignait deux, sans moyen de choisir - le défaut que
  [ADR 2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992) avait corrigé pour les
  points, jamais traité à ce niveau.

## Décision

**Le domaine a trois niveaux géographiques** : la **commune** (dérivée du GPS du point,
[ADR 2791](2791-la-commune-se-derive-du-gps-et-s-attache-au-point.md)), le **carré** et le **point
d'écoute**.

Le carré s'offre dans **une seule entrée**, portant ses deux étiquettes quand la seconde existe :

```
Carrés
  640380 · Vallon      <- le carré nommé
  840962               <- le carré que personne n'a nommé
```

Le **préfixe identifie**, le suffixe ne fait que nommer. D'où l'asymétrie de `LieuQualifie.qualifier` :
sans suffixe on garde le préfixe, sans préfixe on ne rend rien. Un nom convivial orphelin ne désigne
aucun lieu.

## Conséquences

- **#3145 devient sans objet.** La question « faut-il ajouter le site à Carte & passages ? » n'a pas
  reçu un « non » : il n'y a plus de dimension « site », et le niveau qu'elle désignait était déjà
  offert partout. L'écart entre écrans se réduit au seul point d'Espèces & observations, dont la cause
  est une colonne non remontée (#3161).
- **L'homonymie tombe** sans règle supplémentaire : qualifiés par leur numéro, deux « Vallon » se
  distinguent.
- **Les vues mémorisées changent de contenu** : ce qui se persiste est l'étiquette affichée. D'où
  [ADR 3158](3158-une-valeur-memorisee-se-rattrape-par-dimension.md), sans laquelle toute vue portant
  un carré se rejouerait en filtrant moins qu'annoncé.
- **La ligne de commande ne change pas de comportement**, sa correspondance étant partielle :
  `--lieu 640380` et `--lieu vallon` retenaient déjà le même carré. Ce qu'elle **dit** s'aligne en
  revanche (#3159), pour que la valeur nommée par un refus se recopie telle quelle.
- L'écriture qualifiée a dû remonter en `commun.model.LieuQualifie` : née dans la vue, elle est lue par
  la CLI, qui est un modèle.

## Alternatives écartées

- **Ajouter « Sites » partout** (la réponse « oui » à #3145). Uniformise l'apparence en gardant le
  doublon : chaque carré aurait deux entrées sur les quatre écrans, et l'homonymie resterait entière.
- **Retirer « Sites » partout**, numéro seul. Le plus court des menus, mais l'utilisateur qui pense ses
  carrés par le nom qu'il leur a donné perd ce point d'entrée - et ce nom existe précisément pour cela.
- **Laisser l'écart et l'écrire.** C'était l'état de départ ; il rendait la géométrie du critère
  impossible à justifier autrement que par l'histoire de chaque écran.
