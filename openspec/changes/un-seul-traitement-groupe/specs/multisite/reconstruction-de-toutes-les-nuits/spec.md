## Purpose

Compléter en une passe toutes les nuits déposées sur Vigie-Chiro dont le contenu manque sur le poste, par
« Tout compléter » à l'écran et par `reconstruire-passage --tout` en ligne de commande, en sachant avant de
partir ce qui sera fait, et après coup ce qui est arrivé à chaque nuit.

## ADDED Requirements

### Requirement: L'annonce avant de partir dit ce qui sera fait et ce qui ne le sera pas

Avant de compléter quoi que ce soit, « Tout compléter » SHALL annoncer combien de nuits vont être complétées
sur combien, et nommer chaque nuit écartée avec son motif. L'utilisateur SHALL pouvoir renoncer à ce moment,
et rien n'est alors écrit. Une nuit dont le point d'écoute n'existe pas sur le poste SHALL être écartée dès
cette annonce.

*Vérifié par* : aucun dispositif n'existe encore. `ReconstructionModaleViewTest` le tiendra, par un cas qui
lit l'annonce puis renonce.

#### Scenario: Une nuit sur trois ne peut pas être complétée

- **WHEN** la liste porte trois nuits, dont une sur un point d'écoute inconnu du poste, et que l'utilisateur
  demande « Tout compléter »
- **THEN** l'annonce dit que deux nuits sur trois vont être complétées, et nomme la troisième avec son motif

#### Scenario: L'utilisateur renonce

- **WHEN** l'annonce est affichée et que l'utilisateur renonce
- **THEN** aucune nuit n'est complétée, et la liste est inchangée

#### Scenario: Aucune nuit ne peut être complétée

- **WHEN** toutes les nuits de la liste sont écartées
- **THEN** l'annonce dit que rien ne sera fait, et n'offre pas de lancer

### Requirement: Le compte rendu dit le sort de chaque nuit

Le compte rendu d'un lot SHALL dire, pour chaque nuit soumise, lequel de quatre sorts elle a connu :
complétée, écartée, en échec ou non traitée. Une nuit écartée, en échec ou non traitée SHALL porter son
motif. Le compte rendu SHALL garder les totaux de séquences recréées et d'observations rapatriées des nuits
complétées.

*Vérifié par* : aucun dispositif n'existe encore pour le détail par nuit. `ReconstructionViewModelTest` tient
aujourd'hui les totaux, et tiendra le sort de chaque nuit.

#### Scenario: Un lot de trois nuits aux trois sorts différents

- **WHEN** une nuit est complétée, une est écartée et une échoue
- **THEN** le compte rendu nomme les trois nuits, chacune avec son sort, et les deux dernières avec leur motif
- **AND** il donne le nombre de séquences et d'observations de la nuit complétée

### Requirement: Une nuit dont l'analyse n'est pas terminée est écartée, pas en échec

Une nuit pour laquelle la plateforme ne rend encore aucune donnée SHALL être écartée, avec un motif qui dit
que son analyse n'est pas terminée. Elle MUST NOT compter parmi les nuits en échec.

*Vérifié par* : aucun dispositif n'existe encore. `ServiceReconstructionPassagesTest` le tiendra, par un cas
où la plateforme ne rend aucune donnée pour une nuit du lot.

#### Scenario: La plateforme n'a pas fini d'analyser une nuit

- **WHEN** le lot atteint une nuit dont la plateforme ne rend aucune observation
- **THEN** cette nuit est écartée avec le motif d'une analyse non terminée, et le lot continue
- **AND** le lot ne compte aucune nuit en échec de ce fait

### Requirement: Une nuit en échec n'arrête pas le lot

Une nuit dont la complétion échoue pour une raison qui n'est pas un motif d'écartement SHALL être en échec,
avec sa cause. Le lot SHALL continuer avec les nuits suivantes, et le compte rendu SHALL toujours être rendu.

*Vérifié par* : aucun dispositif n'existe encore. `ServiceReconstructionPassagesTest` le tiendra, par un cas
où la complétion d'une nuit lève une exception inattendue au milieu du lot.

#### Scenario: La deuxième nuit de trois échoue de façon inattendue

- **WHEN** la complétion de la deuxième nuit lève une erreur que rien n'annonçait
- **THEN** la deuxième nuit est en échec avec sa cause, et la troisième est tout de même complétée
- **AND** le compte rendu porte les trois nuits

### Requirement: Annuler défait la nuit en cours et laisse intactes les suivantes

Quand l'utilisateur annule un lot, la nuit en cours de complétion SHALL être défaite : aucun passage partiel
ne subsiste, et elle reste dans la liste. Elle SHALL compter « non traitée », comme les nuits que le lot
n'a pas atteintes. Les nuits déjà complétées SHALL le rester, et le compte rendu SHALL dire que le lot a été
interrompu.

*Vérifié par* : aucun dispositif n'existe encore pour l'annulation au milieu d'une nuit.
`ServiceReconstructionPassagesTest` tient l'annulation entre deux nuits, et tiendra celle-ci.

#### Scenario: Annulation pendant la deuxième nuit de trois

- **WHEN** l'utilisateur annule pendant que la deuxième nuit se complète
- **THEN** la première nuit reste complétée, et la deuxième et la troisième sont non traitées
- **AND** aucun passage n'existe pour la deuxième nuit, qui figure toujours dans la liste
- **AND** le compte rendu dit que le lot a été interrompu

### Requirement: L'écran montre l'avancement du lot et celui de la nuit en cours

Pendant un lot, l'écran SHALL montrer deux avancements : celui du lot, qui dit la nuit en cours sur le
total, et celui de la nuit en cours.

*Vérifié par* : `ServiceReconstructionPassagesTest` pour l'avancement du lot, et l'aperçu
`apercu-multisite-reconstruction-groupe.png` pour les deux barres.

#### Scenario: La deuxième nuit de trois est en cours

- **WHEN** le lot complète la deuxième nuit de trois
- **THEN** l'avancement du lot dit « Nuit 2 / 3 », et celui de la nuit avance de son côté

### Requirement: La commande rend le même compte, et son code de sortie dit s'il y a eu un échec

`reconstruire-passage --tout` SHALL écrire une ligne par nuit avec son sort et son motif, puis le résumé du
lot. Elle SHALL sortir en 0 tant qu'aucune nuit n'est en échec, nuits écartées comprises, et en 1 dès qu'une
nuit est en échec. Avec `--json`, elle SHALL rendre les totaux du lot et la liste des nuits, chacune avec son
statut, son motif, et ce qui a été rapatrié quand elle est complétée.

*Vérifié par* : `ReconstruirePassageTest` tient aujourd'hui le rendu du lot et un code 0 dans tous les cas.
Il tiendra les deux codes et le JSON. Aucun cas bats ne joue `--tout` : `cli.bats` en recevra un.

#### Scenario: Une nuit écartée, aucune en échec

- **WHEN** le lot complète une nuit et en écarte une
- **THEN** la commande nomme les deux nuits avec leur sort, et sort en 0

#### Scenario: Une nuit en échec

- **WHEN** une nuit du lot est en échec
- **THEN** la commande écrit sa cause sur la ligne de cette nuit, et sort en 1

#### Scenario: Le rendu en JSON

- **WHEN** la commande est lancée avec `--json` sur un lot de deux nuits, l'une complétée, l'autre écartée
- **THEN** la sortie porte les totaux du lot et deux entrées, chacune avec son statut
- **AND** l'entrée de la nuit écartée porte son motif, et celle de la nuit complétée ce qui a été rapatrié
