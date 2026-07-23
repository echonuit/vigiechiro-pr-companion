# ADR 0027 — Une attente porte toujours un nom, et c'est l'étape qui va attendre qui le pose

- **Statut** : Accepté — 2026-07-19
- **Chantier** : #1931, #1935, #1940, #1946, #1951, #1959
- **Vérification** : humaine — qu'aucune opération longue ne laisse l'écran muet est une propriété temporelle du déroulé, pas un invariant statique

## Contexte

La modale de réactivation montre deux barres depuis #1780, justement parce qu'une barre unique restait **figée à 100 %** pendant la phase d'ancrage : on ne savait pas si le travail continuait. Le principe était posé, mais seulement là où on l'avait regardé.

Sur une nuit réelle de 2042 bruts, l'écran est resté **plus de deux minutes** sur `Régénération 2042/2042`, sans rien d'autre. Le silence n'était pas un temps mort : trois gestes s'y enchaînaient, dont aucun n'était annoncé.

Deux erreurs successives ont été nécessaires pour trouver la règle.

**La première** : #1951 a nommé les étapes de fin de phase (« Vérification de l'audio disponible… », « Recherche de ce qu'il reste à récupérer… ») — sans rien changer pour l'utilisateur. Ces libellés étaient posés **après** le geste qui prenait le temps.

**La seconde** est plus intéressante : le fait que rien ne change a **localisé** le défaut. Puisque les libellés d'après ne s'affichaient jamais, l'attente était forcément avant eux. Elle était dans l'adoption des originaux d'une nuit reconstruite — près de 6700 ordres SQL auto-commités, soit autant de `fsync` (#1959).

## Décision

**1. Aucune opération longue ne laisse l'écran muet.** Tout intervalle où le travail continue sans qu'aucune barre ne bouge est un défaut, au même titre qu'une barre figée à 100 %. Ce que l'utilisateur ne peut pas distinguer d'un plantage doit être nommé.

**2. Le libellé d'une étape est posé par celle qui va attendre, jamais par celle qui vient de finir.** C'est la règle que #1951 a manquée : une étape émise après coup décrit un travail déjà fait, et laisse le suivant muet. Concrètement, l'émission précède l'appel qu'elle annonce.

```java
progresRegeneration.accept(new Progression(ADOPTION_ORIGINAUX, 1.0));
adopterOriginaux(session, originaux, resultat);   // ← ce qui va prendre le temps
```

**3. Une attente nommée est aussi un instrument de mesure.** Quand on ignore où passe le temps, nommer les étapes coûte moins que d'instrumenter, et l'usage réel tranche en une exécution. C'est ce qui a désigné l'adoption : les deux minutes ne s'affichaient sur aucun des libellés posés, donc elles étaient avant.

**4. Nommer ne dispense pas de corriger.** Une attente de deux minutes reste une attente : le libellé la rend supportable, la transaction la fait disparaître. Les deux se font — dans cet ordre de découverte, pas de priorité.

## Conséquences

- Une revue de phase longue se lit dans les deux sens : « chaque barre bouge-t-elle ? » **et** « existe-t-il un intervalle entre deux barres ? ». Le second est le plus facile à oublier, parce qu'il n'apparaît sur aucun écran de test : les jeux d'essai sont trop petits pour que l'intervalle se voie.
- Un test de progression vérifie l'**ordre** des libellés, pas seulement leur présence : `containsSubsequence` plutôt que `contains`. Sans quoi un libellé posé après coup reste vert.
- Le corollaire de performance est écrit dans [performance.md](../performance.md) : une écriture de masse passe par une `UniteDeTravail`.

## Ce qui a été écarté

**Une barre indéterminée pendant les temps morts.** JavaFX la rend gratuitement (`progress = -1`), mais `ProgressionOperation.appliquer` retient `Math.max(fraction, point.fraction())` : une valeur négative serait avalée. Il aurait fallu ouvrir le contrat du socle pour un cas d'affichage ; le libellé sur barre pleine dit la même chose sans y toucher.

**Une ligne d'étape dédiée, alimentée par un troisième canal.** Sémantiquement le bon endroit — la ligne parle quand aucune barre ne le peut — mais il aurait fallu reprendre les **12 implémentations** de `ReactivationModaleController.Travail` pour un libellé. Reste le bon choix le jour où une étape n'appartient à aucune des deux phases.

**Mesurer d'abord, afficher ensuite.** L'ordre inverse a été retenu : l'affichage est utile en soi et coûte deux lignes, alors qu'une instrumentation ne sert qu'une fois (décision 3).
