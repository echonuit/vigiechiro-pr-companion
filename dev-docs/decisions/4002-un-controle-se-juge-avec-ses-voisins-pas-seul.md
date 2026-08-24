---
type: adr
title: "Un contrôle se juge avec ses voisins, et aucun garde ne sait le faire"
status: stable
article: A2
chantier: "#4002, passes 7 à 10 de la clôture des suites des finitions de recette (#3424)"
decided_at: 2026-08-18
verification: humaine
loupe:
  - "ContrasteAATest"
  - "LisibiliteCapture"
  - "ScenesHabilleesTest"
verification_note: "la cohérence entre deux contrôles voisins n'est pas assertable ; ce que le dépôt peut tenir, il le tient déjà (`ContrasteAATest`, `LisibiliteCapture`, `ScenesHabilleesTest`), et cette ADR existe pour dire ce qu'ils **ne** couvrent pas"
verified:
  - by: human:nedseb
    at: 2026-08-18
---

# Un contrôle se juge avec ses voisins, et aucun garde ne sait le faire

## Le fait

Une remarque sur **deux** boutons d'un écran a fini par en corriger **29**, sur douze écrans. Entre les
deux, quatre correctifs successifs, et chacun a **révélé le suivant** :

| Correctif | Ce qu'il a rendu visible |
|---|---|
| styler « Copier » pour qu'il ressemble à son jumeau du Lot | « Choisir… », resté nu **à côté de lui** |
| styler les 29 boutons | le **☰** de réglage des tables, qu'aucune classe n'habillait |
| habiller le ☰ | sa **hauteur** : 42 px quand ses voisins tenaient en 34 |
| aligner les hauteurs | sa **largeur** : +20 px, qui faisaient envelopper les puces de filtre |

⚠️ **Les quatre fois, la CI était verte.** Et les quatre fois, c'est un œil extérieur qui a vu le
défaut - ni moi, ni les gardes.

## Ce que les gardes du dépôt savent dire, et ce qu'ils ne savent pas

Le dépôt est riche en gardes de rendu, et ils sont bons. Ils partagent une propriété : ils jugent un
contrôle **isolément**.

| Garde | Ce qu'il tient | Ce qu'il ne voit pas |
|---|---|---|
| `LisibiliteCapture` | un texte tient dans son cadre, sans ellipse ni compression | deux contrôles qui ne se ressemblent pas |
| `ContrasteAATest` | une surface se détache de son fond | deux surfaces qui se détachent chacune, mais pas l'une de l'autre |
| `ScenesHabilleesTest` | toute fenêtre porte le trio du chrome | ce que les feuilles font une fois posées |
| `PoliceCouvreLIhmTest` | chaque glyphe existe dans la police embarquée | l'alignement de deux libellés voisins |
| garde anti-troncature | un libellé n'est pas rogné | un libellé rogné **parce que son voisin a grandi** |

La propriété manquante n'est pas la lisibilité d'un contrôle : c'est la **cohérence d'une rangée**.
Elle porte sur des rapports - même hauteur, même famille visuelle, même poids - et non sur des seuils.

## La décision

**1. Cette lacune s'écrit, plutôt que de se croire couverte.** Un dépôt qui a huit gardes de rendu
donne l'impression que le rendu est gardé. Il ne l'est pas sur cet axe, et la conséquence pratique est
qu'un vert complet ne dispense **jamais** de regarder une rangée.

**2. On ne fabrique pas de garde pour autant, et c'est délibéré.** Un test « tous les contrôles d'une
`HBox` ont la même hauteur » rougirait sur du travail correct - une barre porte légitimement des
entrées plus basses que ses actions, et « + Filtre » garde délibérément une apparence distincte parce
que c'est une affordance d'ajout. Un garde qui crie sur du bon travail est un garde qu'on apprend à
ignorer ([ADR 3774](3774-le-clip-se-taille-sur-le-test.md), point 3).

**3. Ce qui est mécanisable l'a été.** Ce qu'on pouvait rendre déterministe l'est :

- un **jeton** `-hauteur-controle-barre` dans `palette.css`, plutôt que cinq valeurs recopiées dans
  cinq classes qui doivent rester d'accord ;
- une valeur choisie sur ce que les boutons **rendaient déjà** - 34 px - pour que l'alignement parte de
  l'existant et non d'une valeur inventée.

**4. ⚠️ Une classe qui nomme une intention sans rien faire est pire qu'une absence de classe.** Cinq
boutons portaient `action-primaire` / `action-secondaire`, que **nulle feuille ne définit**. Un lecteur
de la source les croyait stylés ; un bouton nu, lui, ne ment pas. Ces deux classes ont été remplacées
par les réelles.

## Ce que la séquence a coûté, et qui est instructif

⚠️ **Trois causes supposées ont été écartées par la mesure avant la bonne**, sur un seul aperçu refusé
par le garde anti-troncature :

| Cause supposée | Ce que la mesure a dit |
|---|---|
| l'espacement de la rangée | rogné à 6 par #3952, puis à 4 : **6 px gagnés sur 12 manquants** |
| la largeur de la scène de capture | portée à 1080, **l'image est restée à 760** |
| le plafond de colonne du formulaire | **620 px**, documenté, et jamais regardé |

Les deux premières auraient « marché », au sens où le garde serait passé. C'est le mode de panne à
retenir : **rogner jusqu'à ce que le garde passe est indiscernable, dans le journal, d'avoir corrigé la
cause.** Le plafond porté à 640, l'espacement a retrouvé sa valeur d'origine - le rognage de #3952
traitait déjà le symptôme.

Et une quatrième, après coup : la largeur du ☰. J'aurais parié sur la hauteur ; c'est une **bissection**
sur les commits, et non un raisonnement, qui a désigné le bon.

## Conséquences

- Toute revue visuelle regarde une **rangée**, pas un contrôle. La passe 8 du cycle dit déjà « ouvrir
  les captures une par une » ; il faut y lire « et regarder ce qui est à côté ».
- Un changement dans `design.css` touche **tous** les écrans. La vérification locale de ce chantier
  avait couvert cinq features sur six et manqué `audio.view` : c'est la CI qui a trouvé le test cassé.
  Une feuille partagée se vérifie sur la suite entière, pas sur une sélection.
- Les 46 aperçus modifiés par ce chantier sont la trace de ce qu'une feuille partagée déplace.

## Alternatives écartées

- **Un garde d'égalité des hauteurs par conteneur.** Voir le point 2 : il rougirait sur des
  différences voulues, et la première exception légitime le ferait désactiver.
- **Ne rien écrire, puisque le correctif est livré.** C'est ce qui garantit que la prochaine fois se
  découvrira de la même façon, c'est-à-dire par hasard et par quelqu'un d'autre.
