---
type: adr
title: "Une attente qui lit le graphe de scène le lit sur le fil JavaFX, ou elle lit ce qu'un autre écrit"
status: stable
article: A2
chantier: "#5277 (soixante-trois attentes hors du fil), lot #5278"
decided_at: 2026-09-05
verification: probable
enforced_by:
  - "scripts/adr/5278-attente-hors-du-fil.py"
ratchet: 63
verified:
  - by: machine:suspects
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
---

# Une attente qui lit le graphe de scène le lit sur le fil JavaFX

## Le contexte

`Attente.queSurLeFil` existe depuis #4408, et sa javadoc dit sa raison :

> Un prédicat qui touche le graphe de scène doit être lu sur le fil FX, qui n'est pas partageable :
> sinon il lit un graphe qu'un autre fil est en train d'écrire.

Le patron n'a pas essaimé. Mesure du 2026-09-05 : **138** appels à `Attente.que` ou `queSurLeFil`,
dont **huit** sur le fil, et **63** qui lisent le graphe depuis le fil du test, dans 31 classes.

## Ce que cela produit

`RetourApresVerificationE2ETest.depuis_multisite_la_verification_se_propage` a levé en CI :

```
java.lang.IndexOutOfBoundsException: Index 6 out of bounds for length 6
```

Son prédicat lisait les `getItems()` d'une `TableView` que le chargement asynchrone remplaçait. Un
index **égal à la longueur** est la signature d'une lecture concurrente, pas d'un décalage
applicatif.

Le banc figure au relevé des bancs instables : deux chutes en tête sur 884 tirages, trois de plus
comme victime. Il n'est pas seul, et c'est ce que le cliquet compte.

## La décision

**Un prédicat d'attente qui lit le graphe de scène se lit sur le fil JavaFX**, ou son site est compté
par un cliquet qui descend.

Le cliquet plutôt que l'invariant, parce que la population est de 63 dans 31 classes et que la
convertir d'un coup ferait une demande qu'aucune relecture ne tiendrait. Il s'ouvre à 63, et les lots
#5269 et #5279 le descendent à 0.

**La règle se dérive des lectures de nœuds** : `lookup(`, `queryAs`, `getItems()`, `getScene()`,
`getChildren()`, `getText()`. Énumérer les classes fautives donnerait une liste à tenir à la main, qui
dérive de ce qu'elle décrit sans que rien ne rougisse. L'ADR 5258 vient de mesurer ce défaut sur une
autre population du même dépôt.

## Ce que la décision ne dit pas

**Elle n'ordonne pas la conversion de chaque site.** `queSurLeFil` fait un aller-retour sur le fil FX
à chaque tour de boucle. Là où le prédicat ne touche le graphe que par un chemin sûr, la conversion
coûterait sans rien tenir. Le garde **compte** ; le jugement reste au site, et un site laissé en `que`
écrit sa raison plutôt que de sortir du compte en silence.

**Elle ne change rien au comportement de `Attente` sur une exception.** `lireSurLeFil` re-lève
délibérément un prédicat qui a levé, et sa raison est écrite dans le code : « le taire ferait expirer
l'attente sur un délai, en accusant la lenteur là où il y a une exception ». Cette décision-ci la
confirme au lieu de la défaire. L'hypothèse inverse avait été écrite en ouvrant #5269, et elle était
fausse.

## Pourquoi l'article A2

« Un garde est vu rouge sur sa propre mutation. » Une attente qui lit le graphe hors du fil est un
dispositif qui **ne peut pas rougir de façon fiable** : il rend vrai, faux, ou lève, selon la vitesse
de la machine. Un banc dont le verdict dépend de l'ordonnancement n'a pas été vu rouge sur ce qu'il
prétend attraper, il a été vu vert le plus souvent.

## Les alternatives écartées

**Allonger les délais.** C'est le remède qui a l'air de marcher : le banc redevient vert parce que la
fenêtre de course se referme plus souvent. Il ne supprime pas la lecture concurrente, il la rend plus
rare, et il ralentit la suite pour tout le monde.

**Avaler l'exception du prédicat.** Elle ferait expirer l'attente sur un délai et accuserait la
lenteur là où il y a une faute de fil. C'est la décision que #4408 a déjà prise dans l'autre sens.
