# ADR 2348 - Un export d'image se redessine, il ne se capture pas

- **Statut** : Accepté - 2026-07-26
- **Chantier** : EPIC #2348 (Lire ce que la nuit contient)
- **Vérification** : certaine - `ActiviteViewTest#l_export_image_redessine_un_graphe_reellement_dessine`

## Contexte

Le chantier « Lire ce que la nuit contient » produit des **restitutions graphiques** que l'observateur voudra sortir de l'application : la courbe d'activité (lot 2), et demain la synthèse de la nuit (lot 1) et les espèces à enjeu (lot 3). Exporter un graphe semble être un geste d'une ligne : JavaFX sait photographier n'importe quel nœud par `snapshot()`.

Cette facilité est un piège, et son mode d'échec est le pire qui soit : **silencieux**. Un `snapshot()` ne rend que ce qui est effectivement rendu à l'instant où on le prend. Un nœud masqué (onglet en arrière-plan, fenêtre réduite, écran non affiché) ou dont le rendu est accéléré matériellement produit une image **vide ou noire**, sans exception, sans code d'erreur. L'utilisateur obtient un fichier de taille plausible, dans le dossier attendu, portant le bon nom, et ne découvre le problème qu'en l'ouvrant ailleurs, souvent après l'avoir envoyé.

Le second écueil est indépendant du premier : une image de graphe **sans son contexte** devient inexploitable dès qu'elle quitte l'application. Rien sur l'image ne dit de quel carré, de quel point ni de quelle nuit elle parle, ni si un filtre était actif. Deux courbes de forme voisine se confondent, et une courbe filtrée se lit comme un total.

## Décision

**Un export d'image reconstruit ce qu'il exporte, dans une scène transitoire hors écran, à partir des mêmes données que l'affichage.** Il ne photographie jamais le nœud affiché.

Concrètement, pour la courbe d'activité ([`ExportImageActivite`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/analyse/view/ExportImageActivite.java)) : un graphe **neuf** est construit sur un axe neuf, alimenté par les courbes du ViewModel, puis rendu par [`ApercuFx`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/ApercuFx.java), la brique déjà utilisée par les aperçus de documentation. L'export ne dépend donc **pas** de l'état d'affichage de l'écran.

Deux corollaires, qui ne sont pas des détails d'implémentation :

- **Les séries sont reconstruites, jamais empruntées.** Une `XYChart.Series` n'appartient qu'à **un** graphe à la fois : la réutiliser telle quelle la retirerait de la vue affichée sous les yeux de l'utilisateur. La traduction courbe → série est donc partagée (une seule fonction), et **appelée deux fois**.
- **L'image porte son contexte**, estampillé sous le graphe ([`LegendeExportActivite`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/analyse/view/LegendeExportActivite.java), fonction pure) : identité (carré, point, passage, ou portée transverse), réglages (largeur de tranche, **filtres en clair**), provenance (version, date). Le nom du fichier ne suffit pas : un copier-coller le perd.

Cette règle vaut pour **les trois lots** du chantier, et pour tout export graphique ultérieur.

## Conséquences

- L'export **fonctionne écran masqué**, et se teste **headless** : c'est ce qui a permis d'en faire un test d'intégration réel plutôt qu'une vérification manuelle.
- Le test associé ne se contente pas de vérifier que le fichier existe, un fichier noir existe aussi. Il **relit le PNG** et compte ses couleurs distinctes : une image unie trahit exactement le mode d'échec que cette décision élimine.
- Le contexte estampillé dit aussi ce qui **n'est pas** filtré : « aucun filtre » plutôt qu'un silence, qui se lirait comme une information manquante autant que comme un total.
- La scène d'export est **dimensionnée pour l'export** (plus large que l'écran), ce qui évite de dépendre de la taille de fenêtre au moment du clic. La garde de `ApercuFx` refuse au passage une image dont un libellé serait tronqué : un aperçu qui ment est refusé (ADR 0042).

## Alternatives écartées

- **`snapshot()` du nœud affiché.** Une ligne de code, et le défaut décrit plus haut. C'est la solution vers laquelle un futur contributeur reviendra naturellement : d'où cette ADR.
- **Rendre le bouton indisponible quand l'écran est masqué.** Traite le symptôme, laisse l'export dépendre de l'affichage, et interdit un export en lot (plusieurs nuits) qu'on voudra peut-être plus tard.
- **Mettre le contexte dans le nom du fichier.** Robuste tant que le fichier reste un fichier ; perdu dès qu'il est collé dans un message, un rapport ou une présentation : c'est-à-dire à son premier usage.
- **Exporter en SVG plutôt qu'en PNG.** Meilleur pour l'impression, mais JavaFX ne l'écrit pas nativement, et le besoin exprimé est de coller une image dans un compte rendu. À reconsidérer si l'impression devient un usage.
