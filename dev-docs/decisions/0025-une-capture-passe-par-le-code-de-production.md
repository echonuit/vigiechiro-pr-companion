# ADR 0025 — Une capture passe par le code de production, elle ne le reconstruit pas

- **Statut** : Accepté — 2026-07-18
- **Chantier** : #1468, #1865 (suites de la clôture de #1838)
- **Vérification** : humaine — qu'une capture appelle le code de production plutôt qu'un fac-similé est une discipline de conception, jugée en revue

## Contexte

Les captures d'écran de ce dépôt ne sont pas des illustrations : elles sont **régénérées depuis le code** à chaque évolution, et servent de documentation vivante de l'état réel de l'application. La [passe 8](../cycle-de-chantier.md) de chaque clôture pose qu'« une conséquence visible qui n'a pas de capture n'est pas documentée : elle dérivera en silence ».

Certaines surfaces résistent à ce principe. Un `Alert` se montre par `showAndWait`, qui **bloque** ; une modale de progression n'existe que le temps d'un travail ; un `MenuButton` n'expose pas la scène de son popup. Faute de pouvoir les obtenir, les outils de capture les **reconstruisaient à l'identique**.

**Et « à l'identique » n'engage personne.** #1468 l'a découvert en ouvrant les images : la documentation montrait des dialogues qui avaient **dérivé du produit**, jusqu'à une confirmation entière qui manquait. La reconstruction n'échoue jamais, ne rougit aucun test, et se périme silencieusement — exactement le défaut que les captures existent pour empêcher.

#1468 a posé le remède sur un cas (`ConfirmationNavigation.dialogue`). #1865 l'a appliqué trois fois de plus, et a montré que c'était une règle et non un expédient. Elle n'était écrite nulle part.

## Décision

**1. Une capture appelle le code que la production appelle.** Elle n'assemble pas de fac-similé, même « à l'identique », même commenté comme tel.

**2. Quand ce code est soudé à un effet, on sépare construire de montrer.** `showAndWait`, `new Stage()`, l'exécution d'un travail : la classe qui montre expose de quoi **construire sans montrer**, et le nom dit l'usage.

| Ce qui bloquait | Le seam |
|---|---|
| `Alert.showAndWait()` | `ConfirmationNavigation.dialogue(message)` (#1468) |
| `Stage` + travail asynchrone | `DialogueProgression.apercu(titre, etape)` (#1865) |
| Message composé dans la vue | `PublicationCorrectionsViewModel.recapitulatif(tri, ancrageAVenir)` (#1865) |
| État transitoire d'un contrôleur | `ReactivationModaleController.apercuPhasesEnCours(…)` (#1780) |

**3. Les données d'une capture sont des données de démonstration ; le texte et la structure ne le sont jamais.** Un jeu de comptes plausibles est légitime ; une phrase recopiée ne l'est pas.

**4. Un libellé partagé entre la production et une capture vit dans une constante citée**, jamais dans deux chaînes jumelles (`AcquisitionAncrage.LIBELLE`, #1867). C'est le corollaire de l'[ADR 0022](0022-le-verbe-dit-le-sens-de-l-echange.md) : un libellé recopié se renomme une fois sur deux.

## Conséquences

- Des classes de production gagnent une méthode publique que **seule la capture appelle**. C'est assumé, à une condition : elle **délègue** au chemin de production. Un crochet qui duplique est le défaut ; un crochet qui délègue est le remède.
- La contrainte **corrige parfois une couche**. Le récapitulatif de publication vivait dans la vue ; il a fallu constater que composer une phrase à partir d'un `TriPublication` est de la **présentation** pour trouver où le rendre accessible. Le ViewModel portait déjà son jumeau.
- Photographier un état transitoire suppose de pouvoir le **poser**. D'où les méthodes d'aperçu, qui figent un avancement sans lancer d'opération.
- **Poser un état, ce n'est pas en inventer un.** L'aperçu de progression ne pose aucune référence temporelle : l'estimation du temps restant s'extrapolant du temps écoulé, elle annoncerait « ~0 s restant » à un quart d'avancement. L'image montre donc un état **réel** de l'opération (avant qu'une estimation soit possible) plutôt qu'une durée fabriquée.
- Ce qu'on ne peut obtenir sans déclencher l'effet réel **reste non documenté** plutôt que simulé. Une image absente se voit ; une image fausse, non.

## Alternatives écartées

- **Reconstruire à l'identique, avec rigueur.** C'est l'état d'avant #1468, et la rigueur n'a pas suffi : la dérive a été constatée, pas redoutée. Rien dans la chaîne ne peut la signaler.
- **Piloter l'IHM réelle jusqu'à l'état voulu**, à la manière d'un test TestFX. Coûteux, fragile, et impraticable dans le harnais headless, où la plateforme refuse un nouveau `Stage` après l'attente de chargement audio.
- **Renoncer à documenter les modales.** C'était l'état avant #1865 : la surface qui bouge le plus (menu ☰, confirmation, progression) était précisément l'angle mort. Les cinq défauts d'IHM des chantiers #1405/#1431 ont tous été trouvés **en ouvrant une capture**, aucun par un test.
