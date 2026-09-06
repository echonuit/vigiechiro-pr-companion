## Purpose

Désigner un dossier, un fichier existant, ou l'endroit où enregistrer. Le geste précède presque toute
action qui touche aux fichiers de l'utilisateur, et il traverse les écrans sans appartenir à aucun.
Cette capacité couvre le geste et le choix du dispositif qui le porte, pas les écrans qui l'appellent.

## Requirements

### Requirement: L'utilisateur choisit le dispositif de désignation

Un réglage SHALL décider si la désignation passe par le dialogue **du système** ou par celui de
**l'application**. Le dialogue du système SHALL être le choix par défaut : une installation qui n'a
jamais touché ce réglage se comporte comme avant.

Le réglage SHALL survivre à la fermeture de l'application, et SHALL être relu **à chaque
désignation** plutôt qu'au démarrage : le basculer prend effet sans redémarrer.

*Vérifié par* : `SelecteursTest` pour le défaut et pour le choix dans les deux sens ;
`PreferenceDesignationPersisteeTest` pour la persistance, y compris la relecture par une seconde
instance montée sur le même dossier ; `OngletReglagesEmplacementsTest` pour la présence du réglage
dans l'écran. La relecture à chaque geste a son témoin propre,
`SelecteursTest#le_reglage_est_relu_a_chaque_designation`, qui rougit si le dispositif est figé à la
construction. Cases de recette `S7-39` à `S7-41`.

#### Scenario: le défaut ne change rien
- **WHEN** l'utilisateur n'a jamais touché ce réglage et demande un dossier
- **THEN** le dialogue du système s'ouvre

#### Scenario: le choix se garde
- **WHEN** l'utilisateur choisit le dialogue de l'application, ferme puis rouvre l'application
- **THEN** la désignation suivante passe par le dialogue de l'application

#### Scenario: le choix prend effet tout de suite
- **WHEN** l'utilisateur bascule le réglage sans redémarrer, puis demande un dossier
- **THEN** le dispositif qui s'ouvre est celui qu'il vient de choisir

### Requirement: Le dispositif choisi ne change pas ce que le geste rend

Les deux dispositifs SHALL rendre la même chose : le chemin désigné, ou **rien** si l'utilisateur a
renoncé. Aucune action appelante ne SHALL se comporter différemment selon le dispositif.

C'est l'exigence qui rend le réglage sans danger : elle interdit qu'un geste marche d'un côté et pas
de l'autre.

*Vérifié par* : trois maillons, et **aucun ne peut piloter le dispositif du système** - celui-ci
ouvre un `DirectoryChooser` natif, invisible au processus et impossible à conduire sans écran. La
surface commune est tenue par le compilateur, les deux implémentant `SelecteurFichier` ; le
comportement du dialogue de l'application est tenu par `SelecteurFichierEnFenetreTest`, dont un cas
porte précisément le renoncement qui doit rendre vide ; et `SelecteursTest` tient que la fabrique
rend le dispositif que le réglage désigne. **Ce qui reste non vérifié par une machine** est que le
dispositif du système se comporte comme annoncé : cela se constate en recette, cases `S7-39` et
`S7-40`.

#### Scenario: renoncer n'écrit rien
- **WHEN** l'utilisateur ouvre le dialogue de l'application puis renonce
- **THEN** le geste s'arrête sans rien écrire, comme lorsqu'il renonce au dialogue du système

### Requirement: Le dialogue de l'application montre où l'on est et ce qu'il y a

Le dialogue de l'application SHALL montrer le dossier courant, son contenu, et le chemin de ce qui
est désigné. Il SHALL permettre de remonter au dossier parent. Pour un enregistrement, il SHALL
porter un nom pré-rempli modifiable, et permettre de créer un dossier là où rien n'existe encore.

Il SHALL rester dans le style de l'application. Il ne SHALL PAS imiter l'apparence d'un dialogue
système : la documentation montre un geste que le lecteur refera, et un dialogue qui ressemble au
sien sans l'être le tromperait.

*Vérifié par* : `ContenuDesignationTest`, joué **sans fenêtre** sur le patron de
`ContenuChoixSauvegardeTest` - l'ordre et le masquage des entrées, le nom proposé, le filtre de type,
la création d'un dossier et son refus quand le nom est vide, la remontée au parent et son cas limite à
la racine.

#### Scenario: enregistrer propose un nom
- **WHEN** une action demande où enregistrer un fichier avec un nom proposé
- **THEN** le dialogue affiche ce nom, modifiable, et le type de fichier attendu

#### Scenario: créer un dossier
- **WHEN** l'utilisateur veut enregistrer là où le dossier n'existe pas encore
- **THEN** il peut le créer depuis le dialogue

#### Scenario: remonter d'un cran
- **WHEN** l'utilisateur demande le dossier parent
- **THEN** la liste montre ce dossier-là, et rien ne se passe s'il est déjà à la racine

### Requirement: Un chemin peut être saisi

Le dialogue de l'application SHALL permettre de **saisir** un chemin, et SHALL refuser explicitement
un chemin qu'il ne peut pas atteindre plutôt que de rendre un résultat vide.

C'est ce que le dialogue du système ne permet pas toujours, et cela vaut aussi là où l'application ne
voit qu'une partie du disque.

*Vérifié par* : `ContenuDesignationTest`, un cas pour le chemin lisible qui mène là où il pointe, un
pour le chemin hors d'atteinte refusé **avec son message** et sans rien rendre.

#### Scenario: un chemin hors d'atteinte est refusé, pas ignoré
- **WHEN** l'utilisateur saisit un chemin que l'application ne peut pas lire
- **THEN** le dialogue le dit et laisse corriger, sans rendre un résultat vide

### Requirement: Le dialogue de l'application se filme

Le dialogue de l'application SHALL être une fenêtre de l'application, et non un dialogue du système.

C'est ce qui le rend visible au banc filmé, qui photographie les fenêtres de l'application et ne voit
rien de ce qui se passe hors d'elles.

*Vérifié par* : `ParcoursImporterUneNuitTest`, qui joue le geste **sans substituer son porteur** et
pilote le vrai dialogue au robot. Le clip qu'il rend - une prise unique, décoration comprise - est
publié sur la page du spike de convergence, et c'est lui qui a tranché la question de l'[ADR 5282].

#### Scenario: le geste se voit de bout en bout
- **WHEN** un parcours filmé passe par une désignation, le réglage étant sur le dialogue de l'application
- **THEN** le film montre le dialogue s'ouvrir, le choix se faire, et le chemin arriver dans l'écran
