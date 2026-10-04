## Context

Voir `proposal.md` pour le pourquoi. L'état du code qui commande l'approche :

- la modale de site lit une paire collée par `PositionCollee.lire`, qui rend un `LecturePosition` scellé (`Lue`, `UrlDeCarte`, `Illisible`), chaque refus portant son motif ; `PropositionCarre` en tire le carré ;
- la modale de point porte deux propriétés texte, `latitude` et `longitude`, lues axe par axe par `AnalyseurCoordonnees`, qui accepte aussi les degrés-minutes décimales, la virgule décimale et le cardinal après un décimal ; ce lecteur n'a pas d'autre client que `PointEditViewModel` ;
- le contrôleur de la modale de point synchronise les deux champs et le marqueur, et lance le contrôle « hors du carré » à chaque changement ;
- la commande `ajouter-point` exige `--code` et prend `--lat` et `--lon` séparés ;
- trois tests lisent les deux champs : `PointEditViewModelTest`, `ModalePointViewTest`, `ScenarioFicheSiteTest`.

## Goals / Non-Goals

**Goals :**
- Une seule règle de lecture d'une position dans toute l'interface, et un seul champ pour la saisir.
- Un point créé en même temps que son site, sur accord explicite.
- Un code proposé qui suit la règle du portail pour un point libre.

**Non-Goals :**
- Les noms des points systématiques `A1` à `H2` (#5608, à instruire).
- La ligne de commande : `ajouter-point` garde `--code` obligatoire et `--lat`, `--lon` séparés. Un script nomme ses points, et une paire collée n'y a pas de sens. L'écart de parité (ADR 0014) se dit ici plutôt que de se découvrir.
- La réconciliation avec un point distant (#3750).

## Decisions

**D1. Une seule règle, celle du site, enrichie.** `PositionCollee` apprend les degrés-minutes décimales et le cardinal après un décimal, les deux formes que le point lisait et que le site refusait. La virgule décimale française reste refusée dans une paire, où elle est aussi le séparateur : un refus dédié (`LecturePosition` gagne un cas) dit d'écrire le point décimal. Décision du porteur, 4 octobre 2026. Écarté : garder `AnalyseurCoordonnees` pour le point, qui laisserait deux règles ; tout accepter avec un séparateur à deviner.

**D2. `AnalyseurCoordonnees` est retiré**, avec son test, une fois son dernier client parti : un lecteur sans appelant qui accepte d'autres formes que la règle commune serait le prochain à être recâblé par erreur.

**D3. Le champ unique est la seule source.** `PointEditViewModel` porte une propriété `position` (texte) ; la lecture en tire la latitude et la longitude, ou un refus avec motif. Le marqueur écrit la paire en degrés décimaux à six chiffres, point décimal, `Locale.ROOT`. La lecture se fait au fil de la saisie, choix du porteur : le bouton « Situer » du site n'existe que parce qu'il en déduit un carré.

**D4. Le code suivant est un calcul pur** de `sites/model` : le premier entier `n ≥ 1` tel que `Z<n>` n'est pas pris parmi les codes du site, en ignorant la casse. Il est imposé par la case du site (un site neuf donne `Z1`), proposé et modifiable dans la modale de point. *Hypothèse à valider* : le porteur a demandé « la même logique » pour la modale de point ; le code y reste modifiable tant que #5608 n'a pas dit comment se nomment les points systématiques.

**D5. Le voisinage est un calcul pur** : distance orthodromique (haversine) entre la position lue et chaque point positionné du site, seuil 40 m inclus. L'avertissement nomme le point le plus proche et n'empêche pas d'enregistrer. Il se recalcule à chaque lecture de la position.

**D6. La case du site** est portée par `SiteEditViewModel` : visible seulement quand la position collée est lue et que la déclaration n'est pas une récupération. À l'enregistrement réussi du site, elle crée le point par le même service que la modale de point, code `Z1`, sans description.

## Risks / Trade-offs

- [Le site est créé, la création du point échoue] : deux enregistrements successifs, non atomiques. → Le site reste, le retour le dit et renvoie à « Ajouter un point d'écoute ». Accepté : un site sans point est un état normal.
- [Une saisie axe par axe disparaît] : l'observateur qui corrigeait une seule coordonnée corrige maintenant la paire, ou glisse le marqueur. → Décision du porteur.
- [Des points existants portent une position que le champ n'affiche pas comme ils ont été saisis] : l'édition montre la paire normalisée à six décimales. → Acceptable : c'est la valeur enregistrée.
- [Trois tests à reprendre, dont `ScenarioFicheSiteTest`, stabilisé par une autre session] : → la reprise se limite aux sélecteurs et à la saisie ; la session concernée est prévenue.

## Open Questions

Aucune qui change les specs ou le découpage. L'hypothèse de D4 (code modifiable dans la modale de point) se valide à la relecture.
