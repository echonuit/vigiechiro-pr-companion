# Instructions pour GitHub Copilot

La méthode de ce dépôt est dans **[AGENTS.md](../AGENTS.md)**, à la racine. Lisez-le avant de
proposer quoi que ce soit : il porte les garde-fous, le cycle d'un chantier et les douze passes de
clôture, et rien d'important n'est répété ici.

Ce fichier ne redit que les règles dont l'oubli coûte le plus cher, parce qu'elles se violent en une
ligne et se rattrapent en une PR.

- **Jamais de push direct sur `main`.** Une branche, une PR.
- **Tout travail de branche se fait dans un worktree dédié**, jamais dans la copie principale :
  plusieurs sessions travaillent en parallèle sur ce dépôt, et un arbre partagé produit des dégâts
  silencieux. `AGENTS.md` en donne la liste, tous vécus le même jour.
- **Titre de PR en Conventional Commits**, sans espace avant les deux-points : `type(scope): sujet`.
  Le dépôt fusionne en squash avec le titre de la PR pour sujet, et c'est lui que lit
  semantic-release. La forme `type(scope) :` a fait cesser la publication pendant 58 commits, en
  finissant vert à chaque fois. Voir l'ADR 0040.
- **Pas de tiret cadratin**, nulle part. Un garde le refuse.
- **Jamais `@SuppressWarnings` ni `//NOPMD`** pour taire un avertissement de qualité : refactorer.
- **Doc-comments `///`** (JEP 467), noms de classes en français sans accents.
- **Le test avant le code.** Sur un défaut, le premier test reproduit le défaut.

Environnement : JDK 25 standard, JavaFX 26, tests headless (`-Dglass.platform=Headless`, lancés avec
`env -u DISPLAY`). Le détail est dans [CONTRIBUTING.md](../CONTRIBUTING.md) et
[TESTING.md](../TESTING.md).
