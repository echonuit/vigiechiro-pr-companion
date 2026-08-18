# ADR 3470 - Un message d'erreur ne montre jamais le nom de son enveloppe

- **Statut** : Accepté - 2026-08-18
- **Chantier** : #3470, finitions de recette avant la campagne 2 (#3424)
- **Prolonge** : [ADR 2635](2635-un-refus-dit-ce-qui-manque-la-surface-dit-quoi-faire.md)
- **Vérification** : certaine - `CauseLisibleTest#l_enveloppe_de_reflexion_ne_masque_pas_la_panne`

## Contexte

En désignant la racine de sa carte SD, un utilisateur a lu ceci, et rien d'autre de tout l'incident :

> **Une erreur inattendue est survenue**
> `java.lang.reflect.InvocationTargetException`

Le message n'était pas **absent** : il était **présent et inutile**. C'est pourquoi aucun test ne le
signalait - un texte non vide ressemble à un message.

Le filet global composait le contenu de l'alerte par `String.valueOf(erreur.getMessage())`.

## La cause, et elle se généralise

Quand FXML invoque un `onAction="#methode"` par réflexion, la chaîne se construit en trois temps :

| | Ce qui est levé | Son `getMessage()` |
|---|---|---|
| 1 | `IOException("Accès refusé : E:\")` | `Accès refusé : E:\` |
| 2 | `InvocationTargetException(l'IOException)` | `null` |
| 3 | `RuntimeException(l'InvocationTargetException)` | `java.lang.reflect.InvocationTargetException` |

⚠️ **Ce n'est pas propre à la réflexion**, et c'est la trouvaille. `RuntimeException(Throwable)` - comme
**tous** les constructeurs `(Throwable)` de la bibliothèque standard - pose comme message le
`toString()` de sa cause. La même chaîne inutile sort donc de n'importe quelle enveloppe.

## Décision

**Ce qu'on montre d'une exception est le message le plus profond de sa chaîne QUI PARLE**, jamais celui
du maillon qu'on tient.

Un message est retenu s'il n'est ni `null`, ni vide, **et** s'il n'est pas simplement le `toString()`
de sa propre cause.

### Ce n'est pas « prendre la cause racine », et la nuance décide

La plus profonde peut être un `NullPointerException` **muet**, moins parlant que son parent :

```java
new IOException("Le journal du capteur est illisible", new NullPointerException())
```

Dérouler jusqu'au bout aurait rendu l'alerte **plus pauvre** qu'avant, tout en ayant l'air de la
corriger.

### La troisième condition est celle qu'on n'aurait pas écrite

Sans elle, la règle « non vide, donc informatif » aurait retenu `java.lang.IllegalStateException` : on
aurait **remplacé un nom de classe par un autre**. Elle a été trouvée par un **rouge inattendu**, sur un
test qui vérifiait tout autre chose.

## Conséquences

- **Trois chemins non nominaux sont tenus** : chaîne entièrement muette (le type et où regarder, jamais
  le mot « null » qui s'affichait), chaîne **cyclique** (`initCause` permet de la refermer, et le filet
  global est le dernier endroit où l'on peut boucler - cf. #3700 et ses 16 217 tours), exception absente.
- **La règle vit dans une classe pure**, hors de la lambda de `App`. Même raison qu'en #3700 : une
  lambda posée dans `start` ne s'éprouve pas.
- **Le geste conseillé est cité, pas recopié.** Le repli renvoie vers le menu, et son libellé est lu
  depuis `ActionOuvrirJournaux.LIBELLE`. La première rédaction disait « Journaux », entrée qui n'existe
  pas : c'est l'[ADR 3854](3854-un-refus-ne-conseille-que-ce-qu-il-a-verifie.md) tenue par construction.
- ⚠️ **Et sans pictogramme.** La deuxième rédaction employait « ☰ » ; `PoliceCouvreLIhmTest` l'a refusée,
  ce caractère n'étant pas dans la Noto Sans embarquée. Il se rendait sur la machine qui l'écrivait,
  précisément parce qu'une police système le couvre, et aurait divergé ailleurs
  ([ADR 0035](0035-un-pictogramme-est-une-icone-pas-un-caractere.md)). La forme retenue est celle du
  reste de l'application : « menu principal > Entrée ».

## Ce que cette décision NE fait pas

Elle ne prétend pas **reconnaître** une panne pour en déduire un geste. Nommer le cas de la racine de
carte SD - « copiez les fichiers dans un sous-dossier » - suppose d'en connaître la cause, ce qui reste
l'objet de #3461. Un message qui devinerait serait exactement le défaut que l'ADR 3854 vient de fermer
ailleurs.

En revanche elle **rend #3461 instruisible** : la prochaine fois, l'alerte nommera la vraie panne au
lieu de la masquer.
