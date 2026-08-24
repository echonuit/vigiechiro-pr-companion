---
type: adr
title: "Une nuit porte le fuseau de son site, pas celui du poste"
status: stable
article: A21
chantier: "#3406, trouvé à la clôture des suites du chantier #3151"
decided_at: 2026-08-07
verification: certaine
enforced_by:
  - "CorrespondanceParticipationTest#le_depot_ne_depend_pas_du_poste"
verified:
  - by: machine:ci
    at: 2026-08-07
---

# Une nuit porte le fuseau de son site, pas celui du poste

## Contexte

`CorrespondanceParticipation` convertissait la date et l'heure d'un passage vers l'UTC attendu par la
plateforme en passant par `ZoneId.systemDefault()`, c'est-à-dire le fuseau du **poste qui dépouille**.

Ces heures ne viennent pourtant pas du poste. Elles viennent de l'**enregistreur planté sur le site**.
« 21:00 » veut dire « 21 h là où le micro était », et cette phrase n'a de sens qu'associée à un fuseau.

**Mesuré**, même nuit `2026-07-03 21:00 → 05:00`, trois postes :

| Fuseau du poste | date déposée sur la plateforme |
| --- | --- |
| `Europe/Paris` | `Fri, 3 Jul 2026 19:00:00 GMT` |
| `UTC` | `Fri, 3 Jul 2026 21:00:00 GMT` |
| `America/Cayenne` | `Sat, 4 Jul 2026 00:00:00 GMT` - **la date change** |

Ce n'est pas un défaut d'affichage. Le chemin est `versParticipation` →
`SynchronisationParticipation` → `ClientVigieChiro.creerParticipation` : c'est la donnée **déposée sur
la plateforme nationale**, et le jour de la nuit peut en changer.

Le fuseau n'a jamais été choisi. Il était le défaut de la machine, et le produit s'en accommodait
parce qu'un observateur qui dépouille ses propres nuits, sur place, ne voit pas l'écart.

## Décision

Le fuseau d'une nuit est celui de **son site d'écoute**, et il vaut `Europe/Paris`, écrit en dur dans
`FuseauDuSite.ZONE`.

VigieChiro est un programme national français : le fuseau du site est donc connu, et le fixer évite
d'aller le chercher. `ZoneId.of` gère l'heure d'été, ce qu'un décalage fixe ne ferait pas.

C'est la généralisation d'une décision **déjà prise ailleurs** : `AnalyseCoherenceHoraire` déclarait
`FUSEAU_SITE = ZoneId.of("Europe/Paris")`, motivé de la même façon. Elle ne régissait qu'un écran ;
elle régit maintenant le dépôt.

Les `ZoneId.systemDefault()` restants sont conservés **délibérément** : ce sont des horodatages
techniques - sauvegardes, messages, avis - où le fuseau de l'observateur est le bon.

!!! warning "Amendement (2026-08-07, clôture des suites) - un chiffre était faux"
    Cette ADR annonçait « **six** `systemDefault()` restants ». Il y en a **quatre** :
    `FormatAvisValidateur`, `Discussion`, `ListerSauvegardes`, `ContenuChoixSauvegarde`. Les deux
    autres occurrences étaient des **commentaires** citant le terme, dans `FuseauDuSite` et
    `CorrespondanceParticipation` - dont celui que cette ADR a fait écrire.

    L'inventaire était aussi **trop étroit**. Le fuseau de la machine s'emprunte par d'autres
    constructions, et le relevé complet en donne **dix** : les quatre ci-dessus, trois `LocalDate.now()`
    (deux datent un export, une est le port `HorlogeSysteme`) et trois `LocalDateTime.now()`
    (`HorlogeSysteme`, `RegistreMigrations`, `VerrouWorkspace`). Toutes légitimes - aucune ne convertit
    une heure de site - mais aucune n'avait été regardée avant d'écrire « six ».

    C'est la **troisième fois** dans ce chantier qu'un décompte gonfle en comptant des commentaires
    pour du code, après les « vingt-six caractères d'IHM » qui étaient huit
    ([ADR 3389](3389-ce-que-l-application-affiche-tient-dans-la-police-embarquee.md)). Un `grep -c`
    compte des **lignes**, pas des appels.

## Ce que cette décision laisse faux, et qui est assumé

Les carrés d'**outre-mer**, où l'écart réel atteint plusieurs heures. C'était déjà le cas avant, et
pour tout le monde : la décision ne le crée pas, elle le rend visible et le nomme.

Dériver le fuseau des coordonnées du point - comme la commune l'est déjà
([ADR 2791](2791-la-commune-se-derive-du-gps-et-s-attache-au-point.md)) - reste la réponse juste. Elle
est **différée, pas ignorée**, et #3442 la porte : le référentiel des carrés couvre l'outre-mer, la
dérivation a donc de quoi s'appuyer le jour où on la fera.

C'est aussi la raison d'être de la classe `FuseauDuSite`, plutôt qu'un `ZoneId.of` recopié aux deux
endroits qui en avaient besoin : le jour où le fuseau se dérive, il n'y a **qu'un** endroit à changer.

## Conséquences

- **le garde ne peut plus s'annuler lui-même.** `CorrespondanceParticipationTest` vérifiait par un
  aller-retour qui reconvertissait avec le même `systemDefault()` : l'aller et le retour s'annulaient,
  et le test sortait vert sous **tout** fuseau, y compris ceux où la donnée déposée était fausse. Son
  commentaire revendiquait même « déterministe quel que soit le fuseau », sans voir qu'il l'obtenait en
  neutralisant précisément ce qui pouvait être faux. L'attendu est désormais écrit **en dur**
  (`"Fri, 3 Jul 2026 19:00:00 GMT"`), et le cas boucle sur trois fuseaux dont un d'outre-mer ;
- **l'écriture et la lecture parlent le même fuseau, et il a fallu la CI pour le voir.** La première
  version ne corrigeait que `CorrespondanceParticipation`. `ParticipationOrpheline` relisait encore en
  `systemDefault()`, ce qui casse le point fixe posé par #1860 - le cliquet écrit après qu'une nuit de
  21:00 fut descendue à 15:00 en quatre cycles. Aucune reproduction locale n'était possible : sur le
  poste de développement, `systemDefault()` **est** `Europe/Paris`, donc les deux moitiés parlaient le
  même fuseau par accident.

## Ce que cette ADR apprend au-delà de son cas

Un test sur une conversion de fuseau qui **reconvertit avec la même zone** ne teste rien : il vérifie
qu'une fonction est l'inverse de son inverse. Il faut comparer à une chaîne écrite en dur, et lancer
sous au moins deux fuseaux.

Plus généralement : la machine de développement, ici, était **accordée à la référence**. Tout ce
qu'elle ne pouvait pas distinguer restait invisible - le fuseau comme, quelques jours plus tôt, la
police du système ([ADR 3417](3417-la-galerie-rend-comme-une-machine-accordee-au-produit.md)). Un
dispositif de vérification doit être exercé là où il peut échouer, pas là où il ne peut que réussir.
