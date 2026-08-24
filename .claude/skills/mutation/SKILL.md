---
name: mutation
description: Use when a behaviour is complete, when adding or rewriting any guard (test, ratchet, architecture rule, inventory), or before trusting a green verdict. Covers PIT scoping, reading survivors, and the rule that a guard proves nothing until it has been seen red on its own mutation.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Mutation

## Loi d'airain

```
UN GARDE NE DIT CE QU'IL VÉRIFIE QU'APRÈS AVOIR ÉTÉ VU ROUGE SUR SA PROPRE MUTATION
```

Un dispositif vert n'est pas un dispositif vérifié. Tant que vous n'avez pas cassé à la main
exactement ce qu'il prétend attraper et regardé le rouge apparaître, vous ne savez pas ce qu'il
tient. Enfreindre la lettre de cette règle, c'est en enfreindre l'esprit.

## Annoncer

« J'utilise la compétence mutation pour éprouver <le garde ou la classe>. »

## Deux questions distinctes, deux outils

| Question | Outil | Ce qu'il ne répond pas |
|---|---|---|
| Cette ligne est-elle couverte par un test ? | PIT | si le garde attrape le défaut qu'il nomme |
| Ce garde attrape-t-il le défaut qu'il nomme ? | mutation à la main | si le reste du code est couvert |

Les deux se posent. Ne jamais répondre à l'une en croyant avoir répondu à l'autre.

## Partie 1 : PIT, dès qu'un comportement est complet

**Pas à la clôture.** La passe 6 exige PIT, mais elle arrive souvent plusieurs PR après l'écriture :
le trou découvert porte alors sur du code livré, dans un contexte froid. PIT tourne **dès que le
comportement tient debout**, sur les classes que l'issue vient de livrer. La passe 6 devient une
vérification que ça a été fait.

### Fonction de garde

```
1. CIBLER    des classes pures. Une façade de délégation ne rend que des survivants sans valeur.
2. LANCER    avec une phase : `test-compile` au minimum.
3. LIRE      les survivants un par un. Jamais le pourcentage.
4. CLASSER   chaque survivant : trou réel / défensif inatteignable / artefact de ciblage.
5. AGIR      trou réel -> on écrit le test. Défensif -> on l'assume, sans test creux.
             Artefact -> on élargit `targetTests` et on remesure.
```

Sauter l'étape 3 en lisant le pourcentage, c'est ne pas avoir mesuré.

### Ce qui fait échouer la mesure en silence

| Symptôme | Cause | Correctif |
|---|---|---|
| `MINION_DIED` sans message | but lancé **sans phase** | ajouter `test-compile` |
| Survivants nombreux et vides de sens | cible = façade de délégation | cibler les classes pures |
| Survivants qui disparaissent en élargissant | artefact de `targetTests` | élargir, puis remesurer |

### Le piège de lecture qui a coûté un correctif inutile

Une couverture de mutation dit **« aucun test ne couvre cette ligne »**. Elle ne dit **jamais**
« cette ligne est atteignable ». Confondre les deux fait écrire un correctif pour un défaut qui
n'existe pas. C'est arrivé.

## Partie 2 : la mutation à la main, pour tout dispositif

Elle s'applique à tout ce qu'on écrit pour empêcher un défaut précis de revenir : garde
d'architecture, cliquet, test de parcours, inventaire, garde de CI.

### Affirmation, exigence, et ce qui ne suffit pas

| Affirmation | Exige | Ne suffit pas |
|---|---|---|
| « Ce garde attrape le défaut X » | X cassé à la main, garde vu rouge | le garde est vert |
| « Ce cliquet tient la règle » | la règle enfreinte, cliquet vu rouge | le compteur est au plancher |
| « Ce test couvre le parcours » | une étape retirée, test vu rouge | le test passe |
| « L'inventaire est complet » | une entrée retirée, garde vu rouge | les nombres concordent |
| « Le garde tient encore » après réécriture | la mutation **refaite** | il était rouge avant la réécriture |

### Ce qui rend une mutation valable

- **Elle laisse le test s'exécuter.** Renommer une méthode casse la compilation : le test ne tourne
  plus, et un test qui ne tourne pas ne prouve rien. Simuler un cas de plus, neutraliser un corps de
  méthode, retirer une classe CSS : oui.
- **Elle porte sur le sujet, pas sur le détecteur.** Casser le détecteur vérifie sa non-vacuité,
  ce qui est un second contrôle utile mais distinct, et à faire aussi.
- **Le message d'échec se lit.** Il nomme le coupable du jour, il ne rend pas un `expected: true`.
  C'est lui qu'on lira dans six mois, pas le test.
- **Après toute réécriture du garde ou du sujet, on refait la mutation.**

### Quand la mutation est impossible à monter

Ce n'est pas un échec, c'est une information : le garde **promet plus qu'il ne tient**. On l'écrit
dans son en-tête plutôt que d'emprunter la solidité du voisin.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Le test est vert, donc le garde marche » | Ce vert existerait-il si le dépôt était cassé ? |
| « J'ai relu le garde, il est correct » | Trois dispositifs ont passé la relecture et échoué à la mutation |
| « La mutation est évidente, je la saute » | Trois formes du défaut ne se voient qu'en la montant |
| « Le pourcentage est bon » | Le pourcentage ne dit rien. Lisez les survivants |
| « J'ai réécrit le garde, il est toujours vert » | Vert après réécriture ne vaut rien sans nouvelle mutation |
| « Ce garde ressemble à celui d'à côté » | Copier un test hérite de sa dette |

## Trois échecs réels, tous d'une seule journée

Aucun n'aurait été démasqué par une relecture.

| Dispositif | Ce qu'il prétendait | Ce que la mutation a montré |
|---|---|---|
| Cliquet d'annonces | nommer ses cinq débiteurs | il parcourait **son propre fichier**, dont la documentation les citait : il n'en gardait aucun |
| Test de fraîcheur | vérifier le rechargement | vert **sans** le mécanisme : une écriture voisine annonçait, et son rechargement asynchrone relisait après le geste silencieux |
| Garde d'élision | attraper un libellé rogné | vert après réécriture du composant : la façon de lire avait changé en même temps que la chose lue |

Trois formes d'un même défaut : un vert qui existerait à l'identique sur un dépôt cassé ; un fait
tenu par **un autre dispositif que celui qu'on croit** ; un garde dont on a changé les deux côtés à
la fois.
