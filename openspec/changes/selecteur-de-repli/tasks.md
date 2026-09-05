## 1. La couture, avant toute implémentation

- [ ] 1.1 Écrire la fabrique qui rend le porteur de désignation à partir d'une fenêtre et du réglage. Vérifié par un test qui, sur un réglage posé sur chaque valeur, obtient un porteur délégant au dispositif attendu.
- [ ] 1.2 Router les douze constructions en dur vers la fabrique. Vérifié en ne trouvant plus aucun `new SelecteurFichierJavaFx` hors de la fabrique, et par les tests des douze écrans qui restent verts.
- [ ] 1.3 Poser le garde qui refuse `new SelecteurFichierJavaFx` ailleurs que dans la fabrique. Vérifié en le voyant rougir sur une treizième construction plantée exprès, et redevenir vert quand elle est retirée.

## 2. Le contenu du dialogue, éprouvé sans fenêtre

- [ ] 2.1 Écrire le contenu qui montre le dossier courant, son contenu et le chemin désigné. Vérifié par des tests joués sans fenêtre, sur le patron de `ContenuChoixSauvegardeTest`.
- [ ] 2.2 Ajouter le champ de nom pré-rempli et le filtre de type pour l'enregistrement. Vérifié par un test qui trouve le nom proposé dans le champ et le type annoncé.
- [ ] 2.3 Ajouter la création d'un dossier depuis le dialogue. Vérifié par un test qui crée un dossier absent puis y enregistre.
- [ ] 2.4 Ajouter la saisie d'un chemin, avec refus explicite de ce qui n'est pas atteignable. Vérifié par deux tests : un chemin lisible accepté, un chemin hors d'atteinte refusé avec son message, et jamais un résultat vide.

## 3. La fenêtre, et ce qu'elle ne doit pas empiler

- [ ] 3.1 Écrire l'implémentation qui porte le contenu dans une fenêtre de l'application, sur le patron de `ChoixSauvegardeJavaFx`. Vérifié par un test qui obtient le chemin choisi et un autre qui obtient un résultat vide sur renoncement.
- [ ] 3.2 Fermer la fenêtre avant tout enchaînement vers un autre dialogue, comme le socle l'exige (#2642). Vérifié par un test qui constate qu'une seule fenêtre est ouverte à la fois.
- [ ] 3.3 Confronter les deux dispositifs sur les mêmes appels. Vérifié en rejouant les tests des actions appelantes contre chacun, avec le même verdict.

## 4. Le réglage

- [ ] 4.1 Ajouter le réglage à l'onglet des emplacements, sur le dialogue du système par défaut. Vérifié par un test de l'écran qui trouve le réglage et son défaut.
- [ ] 4.2 Le faire persister. Vérifié par un test qui pose la valeur, relit après un redémarrage simulé, et la retrouve.
- [ ] 4.3 Documenter le réglage dans `docs/ecrans/reglages.md`, en disant ce qu'il change et ce qu'il ne change pas du bac à sable. Vérifié par les gardes de la documentation utilisateur.

## 5. Le geste continu, de bout en bout

- [ ] 5.1 Faire poser le réglage par le banc filmé. Vérifié en obtenant un clip où le dialogue de l'application paraît, sans composition après coup.
- [ ] 5.2 Écrire sur la page qui porte le film que la configuration montrée n'est pas le défaut, et pourquoi cela ne tombe pas sous l'ADR 3788. Vérifié par la relecture de la page et ses gardes de prose.
