## Purpose

Désigner un dossier, un fichier existant, ou l'endroit où enregistrer. Le geste précède presque toute
action qui touche aux fichiers de l'utilisateur, et il traverse les écrans sans appartenir à aucun.
Cette capacité couvre le geste et le choix du dispositif qui le porte, pas les écrans qui l'appellent.

## ADDED Requirements

### Requirement: L'utilisateur choisit le dispositif de désignation

Un réglage SHALL décider si la désignation passe par le dialogue **du système** ou par celui de
**l'application**. Le dialogue du système SHALL être le choix par défaut : une installation qui n'a
jamais touché ce réglage se comporte comme avant.

Le réglage SHALL survivre à la fermeture de l'application.

*Vérifié par* : un test de l'écran des réglages pour la persistance, et un test par dispositif pour
le défaut. Aucun n'existe aujourd'hui.

#### Scenario: le défaut ne change rien
- **WHEN** l'utilisateur n'a jamais touché ce réglage et demande un dossier
- **THEN** le dialogue du système s'ouvre

#### Scenario: le choix se garde
- **WHEN** l'utilisateur choisit le dialogue de l'application, ferme puis rouvre l'application
- **THEN** la désignation suivante passe par le dialogue de l'application

### Requirement: Le dispositif choisi ne change pas ce que le geste rend

Les deux dispositifs SHALL rendre la même chose : le chemin désigné, ou **rien** si l'utilisateur a
renoncé. Aucune action appelante ne SHALL se comporter différemment selon le dispositif.

C'est l'exigence qui rend le réglage sans danger : elle interdit qu'un geste marche d'un côté et pas
de l'autre.

*Vérifié par* : les tests des actions appelantes, rejoués contre les deux dispositifs. Ils existent
pour un seul aujourd'hui.

#### Scenario: renoncer n'écrit rien
- **WHEN** l'utilisateur ouvre le dialogue de l'application puis renonce
- **THEN** le geste s'arrête sans rien écrire, comme lorsqu'il renonce au dialogue du système

### Requirement: Le dialogue de l'application montre où l'on est et ce qu'il y a

Le dialogue de l'application SHALL montrer le dossier courant, son contenu, et le chemin de ce qui
est désigné. Pour un enregistrement, il SHALL porter un nom pré-rempli modifiable, et permettre de
créer un dossier là où rien n'existe encore.

Il SHALL rester dans le style de l'application. Il ne SHALL PAS imiter l'apparence d'un dialogue
système : la documentation montre un geste que le lecteur refera, et un dialogue qui ressemble au
sien sans l'être le tromperait.

*Vérifié par* : des tests du contenu joués sans fenêtre, sur le patron de `ContenuChoixSauvegardeTest`.
Aucun n'existe aujourd'hui.

#### Scenario: enregistrer propose un nom
- **WHEN** une action demande où enregistrer un fichier avec un nom proposé
- **THEN** le dialogue affiche ce nom, modifiable, et le type de fichier attendu

#### Scenario: créer un dossier
- **WHEN** l'utilisateur veut enregistrer là où le dossier n'existe pas encore
- **THEN** il peut le créer depuis le dialogue

### Requirement: Un chemin peut être saisi

Le dialogue de l'application SHALL permettre de **saisir** un chemin, et SHALL refuser explicitement
un chemin qu'il ne peut pas atteindre plutôt que de rendre un résultat vide.

C'est ce que le dialogue du système ne permet pas toujours, et cela vaut aussi là où l'application ne
voit qu'une partie du disque.

*Vérifié par* : un test du contenu pour la saisie acceptée, et un pour le refus explicite. Aucun
n'existe aujourd'hui.

#### Scenario: un chemin hors d'atteinte est refusé, pas ignoré
- **WHEN** l'utilisateur saisit un chemin que l'application ne peut pas lire
- **THEN** le dialogue le dit et laisse corriger, sans rendre un résultat vide

### Requirement: Le dialogue de l'application se filme

Le dialogue de l'application SHALL être une fenêtre de l'application, et non un dialogue du système.

C'est ce qui le rend visible au banc filmé, qui photographie les fenêtres de l'application et ne voit
rien de ce qui se passe hors d'elles.

*Vérifié par* : un clip du banc où le dialogue paraît. Le banc sait déjà filmer les fenêtres de
l'application et les décorer (#5285) ; le clip, lui, n'existe pas encore.

#### Scenario: le geste se voit de bout en bout
- **WHEN** un parcours filmé passe par une désignation, le réglage étant sur le dialogue de l'application
- **THEN** le film montre le dialogue s'ouvrir, le choix se faire, et le chemin arriver dans l'écran
