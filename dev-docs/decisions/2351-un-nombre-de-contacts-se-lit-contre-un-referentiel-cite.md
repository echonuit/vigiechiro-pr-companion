---
type: adr
title: "Un nombre de contacts se lit contre un référentiel cité, et on retient le plus fiable avant le plus fin"
status: stable
article: A5
chantier: "#2351 (lot 1 de l'EPIC #2348)"
decided_at: 2026-07-29
verification: certaine
enforced_by:
  - "ReferentielActiviteTest#precise_mais_peu_fiable_ecartee"
verified:
  - by: machine:ci
    at: 2026-07-29
---

# Un nombre de contacts se lit contre un référentiel cité, et on retient le plus fiable avant le plus fin

## Contexte

« 718 contacts de Pipistrelle de Kuhl » ne veut rien dire pour qui n'a pas déjà en tête l'ordre de grandeur attendu. C'est la première question posée devant l'écran d'analyse, et le produit n'y répondait pas.

Y répondre demande un **référentiel** : des seuils par espèce, déclinés par saison, région et milieu. Trois choses s'y décident, et chacune peut se prendre à l'envers.

## Décision

### 1. La source est ACTICHIRO / Vigie-Chiro, citée partout où elle sert

> Bas Y., Kerbiriou C., Roemer C. & Julien J.-F. (2020), *Bat reference scale of activity levels* (v. 2020-04-10), Team-Chiro / CESCO, Muséum national d'Histoire naturelle. Méthode : Naturae 2022, [DOI 10.5852/naturae2022a14](https://doi.org/10.5852/naturae2022a14).

C'est la **seule** source qui corresponde exactement au besoin : mêmes quatre classes, même pipeline TADARIDA, quantiles Q25/Q75/Q98 par espèce déclinés saison / région / habitat, avec un indicateur de fiabilité par déclinaison.

Données ouvertes, **libres d'usage avec citation obligatoire**. La citation est donc exposée comme une constante du modèle et se recopie **à l'écran** et **dans chaque export**, avec l'avertissement qui l'accompagne. Un référentiel scientifique qui voyage sans sa source est une donnée orpheline : le lecteur n'a plus aucun moyen de contester ce qu'on lui affirme, ni même de savoir à quoi son nombre a été comparé.

### 2. Le repli retient la première déclinaison **fiable**, pas la plus fine

On cherche du plus précis au plus général (milieu, puis région, puis national) et l'on **s'arrête à la première déclinaison fiable**. La colonne `confiance` de la source (`Très bonne`, `Bonne`, `Modérée`, `Faible`) n'est pas décorative : c'est elle qui arrête la descente.

Douze nuits d'un habitat donné sont plus **spécifiques** que neuf mille nuits nationales, et beaucoup moins **solides**. Descendre vers le seuil peu fiable parce qu'il est plus précis produit une classe plus fausse, pas plus juste : et rien à l'écran ne distinguerait les deux.

Quand aucune déclinaison fiable n'existe, la plus précise des non fiables est rendue, **marquée indicative**. Ne rien dire ferait croire à une absence de données là où il n'y a qu'une incertitude assumée.

### 3. Ce qui se déduit, ce qui se choisit, ce qui s'abstient

| Dimension | D'où elle vient | Pourquoi |
|---|---|---|
| **Saison** | de la date de la nuit | elle est dans la donnée |
| **Région** | des **deux premiers chiffres du numéro de carré**, qui sont le département | propriété du numérotage Vigie-Chiro, confirmée par le porteur du produit |
| **Milieu** | d'un **choix explicite** de l'utilisateur, « national » par défaut | aucune donnée du produit ne dit si un point est en forêt ou en ville |

Le milieu ne se devine pas, et on ne le suppose donc pas. Une déclinaison devinée de travers change le verdict en silence.

## Conséquences

- **Les bornes de saison viennent de la source, pas du produit.** 1er avril → 15 juin (gestation), 16 juin → 31 août (mise bas et élevage), 1er septembre → 15 novembre (émancipation, migration, accouplements). Elles tombent **au milieu des mois** : un découpage calendaire placerait août en automne, alors que c'est un mois de suivi dense, et comparerait une part importante des nuits réelles aux mauvais seuils. Recoupées sur deux sources, dont le [rapport GMB 2022](https://gmb.bzh/wp-content/uploads/2022/05/BarbosaDubos_2022-ReferentielActiviteChiroBretagne.pdf).
- **L'hiver n'a pas de saison.** Du 16 novembre au 31 mars, aucune fenêtre ne s'applique : la comparaison se fait « toutes saisons ». Une nuit à trois contacts en janvier n'est pas une nuit faible, c'est une nuit d'hibernation, et un seuil estival la ferait passer pour un désert.
- **Les libellés de région sont des clés de jointure, pas du texte.** Le référentiel écrit `Grand-Est` (avec trait d'union) et `Nouvelle Aquitaine` (sans) : les corriger orthographiquement les rendrait introuvables, et la comparaison retomberait sur `national` **sans le dire**. Une garde confronte chaque région produite à celles que la ressource porte réellement.
- **Les quantiles s'affichent à côté de la classe**, jamais seuls derrière elle. Une classe seule est un verdict ; « Forte · Q75 = 480 · Q98 = 1 240 » est une lecture que l'utilisateur peut contester, ce qu'on attend d'un outil scientifique.
- **Les taxons hors chiroptères ne sont pas couverts** : la lecture rend vide, et l'écran doit écrire « non couvert par le référentiel » plutôt que de laisser une cellule blanche, qui se lirait comme une donnée manquante.
- Le référentiel vit dans `commun/model`, **sans dépendance JavaFX** : il se teste seul, et la CLI le consomme comme l'écran.

## Alternatives écartées

- **Repartir des publications d'origine** pour recalculer les seuils. Traçable jusqu'au bout, mais c'est refaire un travail scientifique publié, avec le risque d'en diverger sans le savoir. Embarquer le dérivé publié **en le citant** garde le lien avec la source.
- **Retenir toujours la déclinaison la plus fine.** Le réflexe naturel, et l'erreur que la colonne `confiance` existe pour éviter.
- **Deviner le milieu** depuis les coordonnées du point (occupation du sol). Séduisant, mais un milieu deviné faux produit une classe fausse sans le signaler ; et le produit n'embarque aucune donnée d'occupation du sol.
- **Faire de la région un sélecteur explicite**, comme le milieu. Envisagé tant que l'encodage du numéro de carré n'était pas confirmé : une déduction tirée de quelques exemples n'aurait pas suffi. La confirmation obtenue, la déduction est légitime, et elle épargne un choix à l'utilisateur à chaque nuit.
