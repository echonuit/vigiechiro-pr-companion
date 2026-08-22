# ADR 4188 - Une modale se filme avec l'écran d'où elle part et celui où elle rend

- **Statut** : Accepté - 2026-08-22
- **Chantier** : #4188, EPIC #4133
- **Prolonge** : [ADR 4142](4142-un-cas-dit-ou-se-lit-son-verdict.md)
- **Vérification** : certaine - `ClipDeModaleTest#aucun_cas_ne_monte_une_modale_seule`

## Contexte

La revue des clips de la session S1 a produit quinze constats. Cinq disaient la même chose sous cinq
formes, et il a fallu les cinq pour que la cause apparaisse :

| Cas | Ce que la revue en a dit |
|---|---|
| `S1-25` | « il faudrait qu'on voie l'écran principal et que quand on annule, aucun carré ne soit ajouté » |
| `S1-34` | « il faudrait voir la fenêtre principale avec la modale pour comprendre ce qui se passe » |
| `S1-24` | « montrer la fenêtre avant d'ouvrir la modale pour bien montrer que B2 a été créé par la modale » |
| `S1-22` | « ne montre pas ce qu'il dit » |
| `S1-11` | « la confirmation ne s'affiche pas » |

Ces tests montent `Modale*.fxml` **seule**. Le clip montre alors une modale flottant sur du **noir** :

> `annuler_ne_cree_rien` : une modale sur fond noir. « Aucun carré ajouté » n'a pas seulement du mal à
> se voir, il n'a **rien où** se voir.

Une modale sans son écran ne montre **ni sa cause ni son effet** - c'est-à-dire rien de ce qu'un cas de
recette existe pour faire juger.

## Décision

**Un cas de recette qui passe par une modale se filme avec les deux écrans qui l'encadrent.**

1. L'**écran de départ**, où le geste qui ouvre la modale est fait - et il est fait, par un clic sur son
   bouton, pas par un appel de navigation.
2. La modale elle-même, remplie par des gestes.
3. L'**écran d'arrivée**, une fois la modale refermée, tenu assez longtemps pour qu'on voie ce qui a
   changé.

**Les classes qui montent une modale seule gardent leurs assertions, pas leur cas.** Elles éprouvent le
câblage de la modale, ce qui est un travail légitime et différent. Le `@CasDeRecette` déménage vers le
scénario qui montre le geste. C'est le même mouvement que celui qui a sorti dix cas des tests de
ViewModel (EPIC #4133).

## Ce qui rendait la règle applicable, et qu'on ne savait pas

⚠️ **Il n'y avait pas d'obstacle technique.** Les modales du produit s'ouvrent en `show()`, jamais en
`showAndWait` : `NavigationSites#afficherModale` le fait depuis toujours. Elles sont donc **pilotables
en headless**, et la raison qu'on se donnait - « un dialogue fige TestFX » - ne valait que pour les
`Alert` du socle, pas pour les modales de la feature.

La confusion a coûté cinq clips illisibles et deux classes de test bâties sur un double inutile.

## Conséquences

Trois classes ont perdu leurs cas au profit de scénarios : `ModaleSiteVerifierCarreViewTest` (six),
`SiteDetailRenommageViewTest`, `ModalePointViewTest`. Les scénarios correspondants montent le vrai
chrome et l'exécuteur **asynchrone** - en synchrone, le travail occupe le fil JavaFX et aucune image
n'est rendue pendant l'attente que ces cas font juger.

⚠️ Un clip qui montre les deux écrans est plus **long**. C'est le prix, et il est juste : la durée
n'était jamais le problème, l'absence de contexte l'était.

## Ce qui a été écarté

**Poser un fond derrière la modale pour qu'elle ne flotte pas sur du noir.** Cela aurait réglé
l'apparence et rien d'autre : l'écran de départ n'aurait toujours pas montré le geste, et l'écran
d'arrivée toujours pas ce que la modale a changé. Un décor n'est pas un contexte.
