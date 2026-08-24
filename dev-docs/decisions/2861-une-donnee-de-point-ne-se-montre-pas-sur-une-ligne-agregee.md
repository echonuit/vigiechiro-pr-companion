---
type: adr
title: "Une donnée de **point** ne se montre pas sur une ligne **agrégée**, et chaque table garde sa marque d'absence"
status: stable
article: A3
chantier: "#2861, lot 3 du chantier #3151"
decided_at: 2026-08-04
verification: certaine
enforced_by:
  - "AnalyseViewTest#colonne_commune_sur_la_table_des_observations"
verified:
  - by: machine:ci
    at: 2026-08-04
---

# Une donnée de **point** ne se montre pas sur une ligne **agrégée**, et chaque table garde sa marque d'absence

## Contexte

Depuis [ADR 2791](2791-la-commune-se-derive-du-gps-et-s-attache-au-point.md), chaque point d'écoute
porte sa commune. Elle est devenue **cherchable** et **cochable** sur quatre écrans, et sortait au CSV -
mais **aucune table ne l'affichait**. Des lignes apparaissaient au filtrage sans que l'utilisateur voie
pourquoi elles correspondaient : une recherche qui trouve sans montrer demande de croire le résultat
sur parole.

Le lot 3 a donc posé la colonne sur les tables concernées. Deux questions se sont posées à chaque fois,
et leurs réponses ne se devinent pas.

## Décision 1 : sur les lignes à point unique, et sur elles seules

Une donnée du **point** ne s'affiche que sur une table dont **une ligne porte un point**. Les tables
qui **agrègent** plusieurs points ne la reçoivent pas.

Le cas qui tranche : un carré fait 2 km de côté (R26) et peut chevaucher **deux communes** - une
commune française fait 15 km² en moyenne, si bien qu'une maille de 4 km² tombe très souvent à cheval. Sur la table des
carrés d'Espèces & observations, une cellule « Commune » unique afficherait l'une des deux, ou la
première venue - elle **mentirait**, et d'autant plus discrètement qu'elle aurait l'air juste.

La colonne existe donc sur les observations, les passages et la revue audio ; pas sur les tables
Espèces et Carrés, qui comptent des points plutôt que d'en désigner un.

**Le critère exact, trouvé à l'audit d'harmonisation** : ce n'est pas « donnée du point » contre
« ligne agrégée », c'est **« la donnée est-elle constante sur tout le groupe ? »**. `AgregationAnalyse`
pose le **nom du site** sur une ligne de la table des carrés, en prenant celui de la première
observation - et c'est **licite**, puisque le groupe *est* un carré et que toutes ses observations
partagent ce nom. La commune, elle, varie à l'intérieur d'un carré ; c'est ce qui la disqualifie, pas
son appartenance au point.

Formulé ainsi, le critère se vérifie mécaniquement : **prendre la valeur de la première ligne du groupe
donne-t-il la même chose que prendre celle de n'importe quelle autre ?** Si oui, la colonne a un sens.
Sinon, elle affiche un artefact d'agrégation.

## Décision 2 : chaque table garde sa marque d'absence

Une commune non résolue est un **état normal** (un point sans GPS, une résolution jamais faite) et non
une anomalie à signaler. Ce qui s'affiche alors suit la convention **de la table**, et non une règle
imposée aux trois :

| Table | Absence | Pourquoi celle-là |
|---|---|---|
| Carte & passages | cellule **vide** | c'est ce que fait déjà la campagne d'une nuit non rattachée |
| Sons & validation | **tiret** (`Formats.VALEUR_ABSENTE`) | toutes les colonnes de contexte de cette table marquent l'absence ainsi |
| Espèces & observations | cellule **vide** | convention de ses trois tables |

Uniformiser aurait demandé de changer des colonnes que le lot ne touchait pas, pour une cohérence que
personne ne perçoit : ces tables ne se lisent pas côte à côte.

Le dépôt dit « tiret » par habitude, mais le caractère est un **cadratin** (`Formats.VALEUR_ABSENTE`,
U+2014), appliqué par `FormatLigneAudio.ouTiret`. Qui écrira le test qui manque (#3236) comparera donc
à cette constante, jamais à un tiret d'imprimerie tapé à la main : les deux se ressemblent à l'écran et
ne sont pas le même caractère.

## Décision 3 : sur la revue audio, c'est du contexte

Sur Sons & validation, la commune rejoint le groupe **passage / carré / point / date**, masqué quand la
source cible un **unique passage** : toutes les lignes y partagent la commune de son point, et la
colonne ne dirait rien que l'en-tête de l'écran ne dise déjà. C'est la règle de cet écran depuis #1194,
et l'y soumettre valait mieux que d'ajouter une colonne constante à la table la plus dense du produit.

## Conséquences

- La question « cette donnée a-t-elle un sens sur cette ligne ? » se pose **avant** d'ajouter une
  colonne, et sa réponse tient à la **granularité de la ligne**, pas à la disponibilité de la donnée.
- L'export CSV suit l'écran : la colonne ajoutée à la table des passages a rejoint le fichier au même
  endroit (passe 2 de la clôture), sans quoi l'écran et le fichier auraient divergé pour rien.
- Le prochain qui voudra montrer une donnée du point - ses coordonnées, son enregistreur, son
  département - trouvera la règle écrite plutôt que trois précédents à interpréter.

## Alternatives écartées

- **La colonne partout, y compris sur les agrégats**, avec la première commune trouvée ou une liste
  « Aix, Venelles ». Une cellule qui énumère ne se trie pas, ne se filtre pas, et sur un carré à
  plusieurs communes elle ferait passer un artefact d'agrégation pour une information.
- **Masquée par défaut**, à afficher via le sélecteur de colonnes. Elle ne montrerait toujours rien à
  qui ne pense pas à l'afficher - c'est-à-dire à ceux que le défaut concerne.
- **Une marque d'absence unique** pour les trois tables. Aurait imposé de retoucher des colonnes hors
  périmètre pour une cohérence invisible à l'usage.
