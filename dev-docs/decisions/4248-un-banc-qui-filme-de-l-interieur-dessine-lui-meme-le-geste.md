---
type: adr
title: "Un banc qui filme de l'intérieur dessine lui-même le geste"
status: stable
article: A23
chantier: "#4248, EPIC #4133"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "CalqueDesGestesTest#la_fleche_se_voit_et_sa_pointe_tombe_sur_la_position"
verified:
  - by: machine:ci
    at: 2026-08-23
---

# Un banc qui filme de l'intérieur dessine lui-même le geste

## Contexte

Le banc bash filme l'écran : `ffmpeg -f x11grab` photographie ce que le serveur X affiche, donc le
pointeur de la souris y figure parce que le système l'y dessine, et la fenêtre y a le chrome que le
gestionnaire de fenêtres lui a posé.

Le banc Java ne filme pas l'écran. Il appelle `Scene.snapshot` et **rend le graphe de scène**. Or le
pointeur n'appartient pas au graphe de scène, et une touche pressée n'y laisse aucune trace du tout.
Ce qui était donné par le système disparaît donc en même temps que le système.

Le résultat s'est vu dès les premiers clips, et le retour de revue l'a dit mot pour mot :

> dans S1-27, le clic qui ouvre la modale n'a pas le halo

Une modale s'ouvrait, sans qu'on voie ce qui l'avait ouverte. Le même manque avait déjà été signalé sur
le banc bash pour une autre raison, un pointeur qui se téléporte
([#4177](https://github.com/echonuit/vigiechiro-pr-companion/issues/4177), corrigé par #4240) : là, le
pointeur existait mais arrivait trop tard. Ici, il n'existe pas.

## Décision

**Un banc qui filme depuis l'intérieur de la boîte à outils dessine lui-même le geste.** Le calque pose
trois choses par-dessus l'image rendue :

- le **halo** de l'appui, qui grandit en s'effaçant sur 300 ms ;
- la **flèche** du pointeur, ancrée par sa pointe sur la position rapportée ;
- le **badge** du raccourci, dans la bande basse de la toile, pendant 800 ms.

Le calque **n'invente rien** : il observe les `MouseEvent` et les `KeyEvent` de la scène par des filtres
qui ne consomment pas. Pas d'événement, pas de dessin.

## Conséquences

**Le clip montre quelque chose que l'écran de l'utilisateur ne montre pas.** C'est délibéré, et c'est le
même arbitrage que l'[ADR 3483](3483-un-apercu-pose-ce-qu-il-ne-peut-pas-reproduire.md) pour les
aperçus : l'alternative est un clip qui montre des effets sans leurs causes, c'est-à-dire un clip qu'il
faut expliquer par écrit pour être compris. Un cas de recette qui a besoin d'une légende pour être lu
n'est pas une démonstration.

**La règle « pas d'événement, pas de dessin » a un coût qu'il faut assumer plutôt que corriger.** Un
test qui obtient l'effet d'un raccourci sans presser la touche ne produit aucun badge : c'est le cas de
`MainViewTest.ctrl_f_active_la_recherche`, qui lance le `Runnable` de l'accélérateur
([#4242](https://github.com/echonuit/vigiechiro-pr-companion/issues/4242)). Le défaut est alors dans le
test, pas dans le calque. Faire dessiner le calque « quand même » reviendrait à filmer un geste que
personne n'a fait, et le clip mentirait dans le sens rassurant.

**Le dessin se fait en AWT, hors de la typographie du produit.** C'est un défaut connu et suivi
([#4241](https://github.com/echonuit/vigiechiro-pr-companion/issues/4241)) : la police logique
`Font.SANS_SERIF` se résout depuis le poste, et le badge s'est rendu en serif sur le runner. La règle
posée ici ne change pas, seul son rendu doit être réparé.

## Alternatives écartées

- **Ne rien dessiner.** C'est l'état d'origine du spike, et c'est ce que la revue a refusé.
- **Composer le pointeur du système par-dessus l'image.** Il n'y en a pas : sous Monocle Headless,
  aucun pointeur n'est rendu nulle part.
- **Revenir à une capture d'écran.** C'est le banc bash, qui ne tourne ni sous Windows ni sous macOS.
  C'est précisément le problème que le banc Java existe pour résoudre.
