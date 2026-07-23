# ADR 0033 — Une fenêtre bornée, pas un pipeline unitaire, et deux seuils disque au lieu d'un

- **Statut** : Accepté — 2026-07-19
- **Chantier** : #1991 (lots #1993 à #1999)
- **Vérification** : humaine — la fenêtre bornée et les deux seuils disque sont un choix d'architecture du dépôt, non réductible à un motif observable

## Contexte

L'analyse d'ouverture (#1930) proposait de replier le dépôt sur « pour chaque archive : la produire, la
téléverser, la supprimer », en concluant que « l'occupation disque retombe à **une** archive ».

Le code disait autre chose. Les deux phases sont **déjà parallèles en interne** : `CompacteurDepot`
compresse sur un thread par cœur, et `DepotVigieChiro` téléverse par cinq (`NB_UPLOADS_PARALLELES`,
calqué sur le front web). Ce qui était sérialisé, ce sont les deux **phases entre elles**, pas leur
contenu.

Un pipeline strictement unitaire aurait donc échangé un gain de recouvrement contre une perte de
parallélisme, en sérialisant une compression aujourd'hui multi-cœurs. Sur les gros passages — ceux qui
motivaient #769, de l'ordre de 4800 séquences — le remède aurait pu coûter plus cher que le mal.

## Décision

**1. Un producteur / consommateur borné, de fenêtre 2.** Le pool de compression remplit une fenêtre de
deux archives, le pool de téléversement la vide, et chaque archive est libérée dès qu'elle est *prouvée*
en ligne. Le pic disque passe de la somme des archives à environ 1,4 Go, sans renoncer au parallélisme
des deux phases.

**2. La fenêtre est une constante interne, pas un réglage.** Elle n'ajoute donc ni surface de
configuration, ni axe de variation à couvrir en test. Si le besoin d'ajuster apparaît, il deviendra une
issue — pas une option offerte par précaution.

**3. La libération suit la preuve, jamais l'envoi.** Elle est déclenchée par `uniteDeposee`, émis après
le commit qui marque l'unité `depose`. Une archive en échec reste sur le disque.

**4. Le parallélisme du moteur est plafonné par la source.** Cinq téléversements de front rendraient la
fenêtre inopérante : la source déclare son `parallelismeMax`, et le moteur prend le minimum. Sans cela,
la borne serait décorative.

**5. Deux seuils disque, parce que deux opérations différentes.** Le garde-fou de #769 n'est pas rendu
caduc, il est **dédoublé** :

| Opération | Ce qu'elle écrit | Seuil |
|---|---|---|
| Générer les archives (étape ②, dépôt manuel) | tout le lot, d'un coup | volume total estimé |
| Déposer (pipeline) | jamais plus que la fenêtre | fenêtre au plafond, ou le lot s'il est plus petit |

`AnticipationEspaceDisque` continue donc de dimensionner l'étape ② sur le volume total. Lui appliquer le
seuil du pipeline la laisserait démarrer puis échouer à mi-parcours en laissant des archives partielles
— exactement ce que #769 avait créé ce garde-fou pour empêcher.

## Conséquences

Un disque trop petit pour l'ensemble du lot mais suffisant pour la fenêtre permet désormais un dépôt
ZIP, là où il déclenchait auparavant un repli. Cette conséquence est la raison d'être de
l'[ADR 0034](0034-la-forme-du-depot-se-choisit.md) : elle supprimait en silence la seule route par
laquelle l'IHM produisait un dépôt WAV.

Le grisage du bouton de téléversement pendant la génération **reste**, alors que le chantier semblait
devoir le retirer. Il n'exprimait pas une attente mais une **exclusion mutuelle** : les deux opérations
écrivent le même `<préfixe>-N.zip` (`CompacteurDepot.ecrireArchive` et
`SourceArchivesRegenerables.resoudre`), et les laisser se recouvrir corromprait des archives. Ce qui a
disparu, c'est l'obligation de lancer l'étape ② d'abord — le stepper le dit désormais.

L'espace disque du choix de source passe par une couture injectable, sur le modèle de
`CompacteurDepot.EspaceDisque` : sans elle, la bascule de seuil n'était vérifiable qu'au niveau
arithmétique, jamais sur la décision elle-même.

## Ce qui a été écarté

**La fenêtre à une archive**, telle que #1930 la décrivait. Elle minimise le pic disque mais sérialise
la compression, au risque d'une régression de durée sur les gros passages — ceux-là mêmes que le
chantier vise.

**Une fenêtre réglable dans Réglages ▸ Dépôt.** Plus honnête sur un poste contraint, mais elle ajoute un
réglage, sa persistance, sa documentation et un axe de variation en test, pour un arbitrage que
l'utilisateur n'a aucun moyen d'évaluer.

**Rebaser aussi le garde-fou de la génération**, comme l'issue le demandait. Ce serait faux : l'étape ②
écrit réellement tout le lot.
