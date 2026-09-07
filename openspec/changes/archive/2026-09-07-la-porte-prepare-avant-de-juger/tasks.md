## 1. Déclarer les outils, avant de poser quoi que ce soit

- [x] 1.1 Déclarer `graphifyy` dans `pyproject.toml`, avec la correspondance distribution -> commande (`graphifyy` s'installe, `graphify` s'appelle). Vérifié par `verifie-dependances-declarees.py` étendu aux commandes, vu **rouge** sur une déclaration retirée avant d'être cru.
- [x] 1.2 Corriger la méthode là où elle prescrit un nom qui installe autre chose : `AGENTS.md`, `CONTRIBUTING.md`, `dev-docs/chercher-dans-le-depot.md`. Vérifié en cherchant `graphify` dans la prose et en constatant que chaque prescription nomme aussi `graphifyy`.
- [x] 1.3 Mesurer ce que `graphify install --project` écrit **réellement**, puis trancher les trois issues du design sur l'accord avec `synchronise-adaptateurs.py`. Vérifié en lançant le garde après l'installation, dans les deux sens.

## 2. Le crochet pose ce qui est bon marché

- [x] 2.1 Poser openspec et ruff à la création d'un worktree, dans `.githooks/post-checkout`, sur le cas du SHA nul que le crochet détecte déjà. Vérifié par un worktree neuf où les gardes OpenSpec rendent un verdict au lieu de refuser.
- [x] 2.2 Constater avant de poser, et ne rien reposer. Vérifié en créant deux worktrees de suite et en constatant que le second ne réinstalle pas.
- [x] 2.3 Échouer sans bloquer, en une ligne. Vérifié en coupant la commande d'installation exprès : le worktree se crée quand même, et le dit.

## 3. La porte pose ce qui dépend du diff

- [x] 3.1 Produire `target/pmd.xml` avant le cliquet qui le lit, **seulement** si le diff engage du `.java`. Vérifié par l'auto-test de la porte, deux cas opposés - un diff de prose, un diff de code.
- [x] 3.2 Rendre la ligne de refus telle quelle pour ce qui n'est pas posable. Vérifié en constatant que le comportement actuel ne change pas sur un garde qui refuse pour un paquet réel.
- [x] 3.3 Dire ce qui a été posé, et ce qui a échoué. Vérifié par l'auto-test sur une préparation qui échoue exprès : la porte le nomme, poursuit, et le garde refuse.

## 4. Éprouver le dispositif que la CI ne joue pas

- [x] 4.1 Voir l'auto-test **rouge sur sa propre mutation** : une préparation qui ne pose rien doit faire rougir le cas qui prétend qu'elle pose. Sans quoi ce changement n'a aucun gardien - la CI installe tout et ne joue jamais ce chemin.
- [x] 4.2 Mesurer le coût réel du crochet sur un `worktree add`, et l'écrire. Vérifié par un chronométrage reproductible, pas une estimation.
- [x] 4.3 Vérifier que la porte conclut **sans** le crochet : `core.hooksPath` n'est posé qu'au premier `./mvnw`, et un clone neuf ne l'a pas.

  **Mesurée à la clôture, pas pendant le lot** - elle avait été écrite et jamais jouée. Un clone neuf
  de `main` rend `core.hooksPath` **non posé** : le crochet n'existe pas tant que `./mvnw` n'a pas
  tourné, et `git-build-hook-maven-plugin` ne s'exécute qu'alors. La porte y conclut quand même,
  `code=0`. La supposition tenait, et elle ne tenait que par chance : rien ne l'avait éprouvée.

## 5. Consigner

- [x] 5.1 Écrire l'ADR de la décision structurante - un verdict local porte sur le code, pas sur l'état du poste ; ce qui est déclaré se pose, ce qui ne l'est pas se refuse en le disant.
- [x] 5.2 Recoller `ouvrir-une-pr` et `worktree` : ce que l'agent devait faire à la main devient ce que le dépôt fait, et la prose doit cesser de prescrire le geste manuel comme s'il restait dû.
