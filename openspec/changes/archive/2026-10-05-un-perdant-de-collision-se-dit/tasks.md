Toutes ces tâches forment un seul lot, #5720, livré par une seule demande de fusion.

## 1. Reconnaître un perdant de collision

- [x] 1.1 Écrire les cas rouges de la règle de nom : un nom horodaté en `_001` ou `_002` est un perdant, un nom horodaté en `_000` ne l'est pas, un nom sans horodatage en `_001` ne l'est pas. Fait quand les trois cas passent sur la règle portée par `NommageSequences`.
- [x] 1.2 Écrire le cas rouge de la réactivation : une nuit dont la base porte une paire de collision, réactivée depuis un dossier qui ne contient que les `_000`, compte le `_001` comme perdant et non comme introuvable. Rouge avant le correctif, où il sort « aucun fichier de ce nom dans le dossier ».
- [x] 1.3 Faire compter les perdants par le bilan de la réactivation, à l'origine `DOSSIER` seulement, en plus de `manquantes`. Fait quand 1.2 passe et que les tests de réactivation existants restent verts.

## 2. Le dire, sur les deux surfaces

- [x] 2.1 Écrire le cas rouge du compte rendu textuel : le constat des perdants, en avertissement, avec son nombre, sa cause, le conseil de réactiver depuis les enregistrements bruts, la phrase conditionnelle sur Kaleidoscope et les noms en détails ; le constat des introuvables ne compte que les autres ; une nuit où seuls des perdants manquent donne un compte rendu en avertissement.
- [x] 2.2 Composer ce constat dans le compte rendu textuel, celui du terminal, avec une phrase que le chiffré lira aussi. Fait quand 2.1 passe.
- [x] 2.3 Écrire le cas rouge du compte rendu chiffré de la modale : un segment de barre à part pour les perdants, « Manquantes » sans eux, un motif qui les nomme, la phrase du constat parmi les mentions, et une sévérité d'avertissement. Puis le composer. Fait quand ce cas passe et que les tests existants du chiffré restent verts.
- [x] 2.4 Ajouter la clé `perdantsDeCollision` à la sortie `--json` de `reactiver`, `manquantes` inchangée. Fait quand le test de projection lit `2` et `3` sur deux perdants et une séquence introuvable.
- [x] 2.5 Ajouter un cas `bats` qui lance `reactiver` sur une nuit portant une paire de collision et lit le constat dans le terminal. S'il faut une fixture que les `bats` n'ont pas, le dire dans la demande plutôt que de la bricoler.

## 3. Ce qui le montre

- [x] 3.1 Ajouter au rendu des captures de réactivation un état avec des perdants de collision, et l'ouvrir. Fait quand le constat se lit en entier, sans troncature.
- [x] 3.2 Poser la case de recette avec sa carte générée. Elle va en S2, la seule session qui joue les cartes du générateur (S4 et S8 réactivent de vraies cartes) : fait quand S2-84 et `sd-collision` existent et que le générateur valide la carte.
- [x] 3.3 Dire dans la doc utilisateur de la réactivation ce que signifie ce constat et le geste qu'il conseille.
