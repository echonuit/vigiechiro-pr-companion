# Consignes générales

## Travail en équipe et organisation

Vous travaillerez en équipe constituée par l'équipe pédagogique au début de la SAE. Chaque membre de l'équipe doit contribuer **techniquement** au projet : pas de répartition « celui qui code, celui qui rédige, celui qui présente ». Le développement, l'analyse et la rédaction sont des tâches partagées et tournantes.

Vous travaillerez **uniquement** dans le dépôt que vous aurez créé via le lien Classroom communiqué **au démarrage de la SAE**.

## Conventions Git et workflow

Appliquez le [GitHub Flow](https://docs.github.com/en/get-started/quickstart/github-flow) pour la gestion de vos branches et de vos pull requests :

- `main` = branche stable, jamais de commit direct.
- Une branche par fonctionnalité (`feat/import-session`, `fix/parser-encoding`, `docs/personas`...).
- Une **pull request** par branche, avec **revue par au moins un autre membre** de l'équipe avant fusion.
- La CI doit être verte pour qu'une PR soit fusionnable.

La **qualité de votre historique** et de vos messages de commit interviendra dans votre notation. Conventions attendues :

- Messages au format [Conventional Commits](https://www.conventionalcommits.org/fr/v1.0.0/) (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).
- Messages en français, axés sur le **pourquoi** plutôt que le **quoi**.
- Pas de commit fourre-tout du type `WIP`, `update`, `corrections diverses`.

> Le workflow Git/PR/review est pratiqué dans les TP du module R2.02 mais évalué formellement dans le module **R2.03 (AC15.02)**. Soignez-le particulièrement.

## Squelette de code

Un squelette de code Java vous sera fourni au démarrage de la SAE. Vous devrez :

- **Écrire le corps des méthodes non implémentées** marquées `TODO` dans le squelette.
- **Ajouter des classes, méthodes et attributs** là où cela vous semble utile, en respectant les principes présentés en R2.03.
- **Écrire autant de tests unitaires que nécessaire** pour assurer une couverture fonctionnelle satisfaisante.
- **Privilégier le FXML** pour l'écriture de l'IHM, afin de pouvoir la modifier sans toucher au code des contrôleurs.
- **Sauf indication explicite** de la part des enseignants, **ne pas modifier la signature** des méthodes / attributs / classes qui vous sont donnés.

***Le non-respect de ces consignes impliquera une pénalité sur la note finale du projet.***

## Outils à votre disposition

Pour la conception graphique de l'IHM (à partir des wireframes basse fidélité fournis dans le dossier d'analyse) :

- [SceneBuilder](https://gluonhq.com/products/scene-builder/) - éditeur visuel FXML, recommandé pour JavaFX
- [Figma](https://www.figma.com/) ou [Excalidraw](https://excalidraw.com/) - retravailler les wireframes si vous proposez une variante

Pour le diaporama de phase 2, n'importe quel outil convient : Keynote, PowerPoint, Google Slides, [DeckDeckGo](https://deckdeckgo.com/), [Marp](https://marp.app/), Slidev, Reveal.js... Choisissez celui que vous maîtrisez le mieux.

## Comportement attendu

- **Probité** : tout code emprunté à une source extérieure (Stack Overflow, dépôt public, IA générative) doit être cité explicitement dans le commit qui l'introduit. Le copier-coller silencieux entre équipes est de la fraude et sera traité comme telle.
- **Assistance IA** : l'usage d'un assistant comme Copilot, ChatGPT ou Claude n'est pas interdit, **à condition** que vous compreniez ce que produit l'outil et que vous puissiez le défendre en soutenance. Les questions individuelles en oral porteront notamment sur ce point.
- **Communication avec l'équipe pédagogique** : exprimez vos blocages et vos doutes au plus tôt. Mieux vaut signaler une difficulté tôt que rendre un livrable incomplet sans avoir prévenu.
