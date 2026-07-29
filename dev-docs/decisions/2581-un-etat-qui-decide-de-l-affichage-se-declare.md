# ADR 2581 — Un état qu'on observe pour décider se déclare quand il faut l'afficher

- **Statut** : Accepté — 2026-07-29
- **Chantier** : #2581 (lot 1, #2772) — suite de l'EPIC #2554
- **Vérification** : certaine — `MigrationV37StatutRecupereTest#v37_dit_la_meme_chose_que_le_code`

## Contexte

Depuis #2557, la synchronisation rapatrie les nuits que Vigie-Chiro connaît : elles arrivent avec leurs
observations et leur rattachement, mais **sans leur audio**, que la plateforme ne renvoie pas. Elles
naissaient avec le statut « Déposé », ce qui est exact — la participation existe bien là-bas.

Ce statut recouvrait donc deux situations : « **nous** avons déposé cette nuit » et « cette nuit vient
du serveur, nous n'en avons rien fait ici ». Leurs gardes, leurs gestes et leur affichage diffèrent.

Une première tranche (#2760, #2771) a traité les **gardes** sans rien déclarer, en observant deux faits
que la base portait déjà : la nuit est rattachée à une participation, et aucun de ses originaux ne porte
de fréquence d'échantillonnage. Une nuit importée puis déposée réunit le premier fait, **jamais** le
second. C'était suffisant, et conforme à l'[ADR 0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md).

Ça ne l'est plus dès qu'il faut **afficher**. Le stepper, les pastilles, la carte, les filtres, la ligne
de commande et l'audit présentent toujours ces nuits comme déposées par nous. Un prédicat répond à une
question ; il ne nomme pas un état, et une vue a besoin d'un nom — pas d'une requête sur trois tables
par ligne de tableau.

## Décision

**Un état reste observé tant qu'il ne sert qu'à décider ; il se déclare quand il doit être montré.**

`StatutWorkflow.RECUPERE` existe donc, et il est **posé** par la synchronisation, **quitté** par la
réactivation, et **rattrapé** par la migration V37 sur les nuits déjà rapatriées.

Trois conséquences en découlent.

**L'état n'entre pas dans la file linéaire.** `MoteurWorkflowPassage` définit la progression par une
liste ordonnée. Une nuit récupérée n'a franchi aucune de ces étapes : elle est arrivée par un autre
chemin. L'y glisser aurait obligé à répondre à des questions sans objet — quel statut la précède ?
laquelle la suit ? — et lui aurait donné un successeur inconditionnel. Elle vit **hors de `ORDRE`**,
avec une seule transition autorisée, `RECUPERE → DEPOSE`, et elle n'a **pas** de successeur au sens de
`suivant()` : sa suite dépend d'un événement, le retour de l'audio.

**La migration rejoue le critère observé, elle n'en invente pas un second.** V37 reconnaît les nuits
déjà rapatriées avec exactement la requête de `NuitRecupereeDao`. Deux critères qui divergeraient
rendraient la base incohérente avec le code qui la lit — le genre d'écart qui ne se voit qu'au moment où
il fait mal. C'est le risque propre à cette décision, et il est tenu par un test :
`MigrationV37StatutRecupereTest` **exécute la migration livrée** (relue depuis les ressources, jamais
recopiée) sur une base semée, puis compare son verdict **nuit par nuit** à celui de `NuitRecupereeDao`.
Il vérifie aussi le sens dans les deux directions, pour qu'un critère devenu toujours-faux des deux
côtés ne passe pas pour un accord.

**La valeur est déclarée en dernier dans l'énumération.** Plusieurs endroits comparent les statuts par
`ordinal()` (« au moins vérifié », « au moins transformé »). Insérer la valeur au milieu aurait décalé
ces comparaisons **en silence** : elles n'auraient rien levé, elles auraient simplement répondu autre
chose. Les `switch` exhaustifs, eux, ont échoué à la compilation et ont été traités un par un — c'est la
différence entre une rupture qui se voit et une qui se paye plus tard.

## Ce qui n'est pas décidé ici

Cet ADR ne dit pas **comment** l'état s'affiche. Le stepper le traite pour l'instant comme « Déposé »,
ce qui fait apparaître les étapes précédentes comme franchies — une approximation assumée et signalée
dans le code, dont la levée est l'objet de #2774. La représentation juste est un choix d'affichage, pas
de modèle.

## Alternatives écartées

**Rester au prédicat.** Il tient pour les gardes, mais une vue de tableau paierait une requête sur trois
tables par ligne, et surtout aucune vue ne peut *nommer* ce qu'elle affiche à partir d'une question. Le
symptôme serait revenu à chaque écran.

**Une table latérale**, sur le patron de `passage_opportuniste` (V34) ou `site_tiers` (V35). Ces tables
portent un fait **qu'aucune autre donnée ne trahit** — être opportuniste ne se déduit de rien. Ici,
l'état est parfaitement dérivable : une table le dupliquerait, avec le risque classique de la voir
diverger de ce qu'elle décrit.

**Insérer la valeur dans la file linéaire**, entre `IMPORTE` et `TRANSFORME` par exemple. Cela aurait
fait entrer une nuit récupérée dans une progression qu'elle n'a pas parcourue, et déplacé tous les
`ordinal()` du dépôt.

## Rapport avec l'ADR 0048

L'[ADR 0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md) pose qu'« archivé » et « purgé » sont des
états **observés**, pas des marqueurs déclarés — c'est elle qui a fait retirer la colonne `archived_at`
en V31. Elle n'est ni amendée ni contredite : le critère de démarcation est **l'usage**.

- « Archivé » sert à **décider** (peut-on écouter ? faut-il proposer la réactivation ?). Rien ne
  l'affiche comme un état du workflow. Il reste observé.
- « Récupéré » doit être **montré** — dans un stepper, une pastille, une couleur de carte, un filtre,
  une colonne de tableau, une sortie de commande. Il se déclare.

La règle générale, donc : **observer tant qu'on décide, déclarer quand on montre.** Un marqueur déclaré
qui ne sert qu'à décider est une redondance qui finit par mentir ; un état montré qui se recalcule à
chaque affichage est un coût et une source de divergence entre les vues.
