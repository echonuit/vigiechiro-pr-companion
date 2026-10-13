Cinq lots du chantier #2755, une demande de fusion chacun. Les issues s'ouvrent une fois cette proposition
fusionnée, et chaque groupe porte alors le numéro de la sienne.

## 1. Le moteur sait traiter une cible qui n'est pas un passage

- [ ] 1.1 Écrire les cas rouges du moteur dans `MoteurTraitementGroupeTest` : une cible d'un autre type que `CiblePassage` est déroulée et nommée par sa désignation ; un geste qui écarte sa cible en cours de route la rend écartée avec son motif ; un geste qui s'annule en cours de route rend sa cible non traitée, le lot interrompu, et les suivantes non traitées sans les toucher. Fait quand les trois sont rouges sur le moteur d'aujourd'hui, chacun pour sa raison.
- [ ] 1.2 Étendre le contrat selon les décisions 1, 3 et 4 de la conception. Fait quand 1.1 passe, et que les tests des quatre porteurs et des deux surfaces du lot passent sans qu'aucune de leurs assertions ait changé.
- [ ] 1.3 Écrire le cas rouge de l'avancement : le moteur publie la cible en cours sur le total, et remet au geste un avancement pour sa cible. Puis l'ajouter selon la décision 5. Fait quand le cas passe et que les quatre porteurs n'ont pas changé de signature.
- [ ] 1.4 Écrire l'ADR de ce lot, numérotée par son issue : ce que le contrat gagne, et les deux points où elle étend l'ADR 2357, avec l'encart de révision sur celle-ci. Fait quand la batterie des ADR passe et que `dev-docs/patterns.md` décrit le contrat étendu.

## 2. La reconstruction groupée passe par le moteur

- [ ] 2.1 Soumettre au porteur le sort d'une nuit que la plateforme ne permet pas de dater, écartée ou en échec, avant de coder ce cas. Fait quand il a tranché, et que sa réponse est écrite dans la conception.
- [ ] 2.2 Écrire les cas rouges dans `ServiceReconstructionPassagesTest`, un par sort : une nuit complétée, dont le rapport arrive par le rappel ; une nuit écartée d'avance pour un point d'écoute inconnu ; une nuit écartée en cours de route parce que la plateforme ne rend aucune donnée ; une nuit en échec sur une erreur inattendue, la suivante étant tout de même complétée ; une nuit annulée pendant sa complétion, défaite et non traitée. Fait quand chacun est rouge pour sa raison.
- [ ] 2.3 Écrire le porteur de la reconstruction selon les décisions 1 à 4, sur le geste unitaire existant. Fait quand 2.2 passe.
- [ ] 2.4 Retirer `reconstruireTout` et ses types de compte rendu, en branchant provisoirement l'écran et la commande sur le moteur sans changer ce qu'ils affichent. Fait quand plus rien n'appelle `reconstruireTout`, ce que `python3 scripts/qualite/appelants.py reconstruireTout` confirme, et que les tests de la modale et de la commande passent.
- [ ] 2.5 Jouer PIT sur le porteur et lire ses survivants un par un. Fait quand chaque survivant est classé : test ajouté, défensif assumé, ou artefact de ciblage.

## 3. « Tout compléter » annonce avant de partir et rend compte nuit par nuit

- [ ] 3.1 Soumettre au porteur les textes de l'annonce et du compte rendu par nuit avant tout code. Fait quand il les a validés, et qu'ils sont écrits dans la conception.
- [ ] 3.2 Écrire les cas rouges dans `ReconstructionModaleViewTest` et `ReconstructionViewModelTest` : l'annonce dit combien de nuits sur combien et nomme les écartées avec leur motif ; renoncer n'écrit rien ; un lot aux trois sorts les rend tous les trois avec leur motif et garde les totaux. Fait quand ils sont rouges sur la modale d'aujourd'hui.
- [ ] 3.3 Donner à la modale l'annonce et le compte rendu par nuit. Fait quand 3.2 passe, et que les deux avancements s'affichent toujours pendant le lot.
- [ ] 3.4 Ajouter les aperçus de l'annonce et du compte rendu par nuit, régénérer ceux de la modale, les ouvrir un par un. Fait quand chaque aperçu montre un état que l'écran rend, sans troncature.

## 4. La commande dit le sort de chaque nuit, et sort en 1 sur un échec

- [ ] 4.1 Soumettre au porteur la marque de rupture : le changement de sortie fait-il monter la version majeure ? Fait quand il a tranché, avant le titre de la demande de ce lot.
- [ ] 4.2 Écrire les cas rouges dans `ReconstruirePassageTest` : une nuit écartée laisse le code à 0 ; une nuit en échec le met à 1 et écrit sa cause sur sa ligne ; le JSON porte les totaux et une entrée par nuit avec son statut et son motif. Fait quand ils sont rouges sur la commande d'aujourd'hui, qui sort toujours en 0.
- [ ] 4.3 Aligner la sortie de `reconstruire-passage --tout` sur celle de `traiter-passages`. Fait quand 4.2 passe.
- [ ] 4.4 Ajouter à `cli.bats` un cas qui joue `--tout` sur le vrai fat-jar. Fait quand il passe en CI, et qu'il rougit si la commande sort en 0 sur une nuit en échec.

## 5. La documentation et la recette disent ce que l'écran et la commande font

- [ ] 5.1 Corriger `docs/ecrans/multisite.md` : annuler défait la nuit en cours, elle ne « va pas au bout ». Y décrire l'annonce et le compte rendu par nuit. Fait quand la page se relit contre l'écran, aperçus à l'appui.
- [ ] 5.2 Documenter `--tout` dans `dev-docs/cli.md`, avec ses deux codes de sortie et la forme de son JSON, et dire le changement de contrat au journal des versions. Fait quand `DocumentationAJourTest` passe.
- [ ] 5.3 Recoller les cases S4-64 et S4-65 de la recette, et ajouter celles de l'annonce, d'une nuit écartée et de l'annulation en cours de nuit, chacune avec son geste et ce qu'on doit observer. Fait quand `CorrespondanceRecetteTest` passe et que chaque case neuve est jouable sur une fixture nommée.
