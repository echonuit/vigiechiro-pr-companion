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

### Quand la mutation reste verte

C'est le cas miroir, et il se lit pareil : un vert. Avant d'écrire « garde décoratif », relire ce que
le garde **annonce** : son en-tête, son `CONTRAT` quand il en déclare un (`--contrat` l'imprime), son
message de refus. Deux lectures, qui concluent à l'inverse :

- la propriété mutée **est annoncée** : le garde promet plus qu'il ne tient, c'est le cas ci-dessus ;
- elle **ne l'est pas** : la mutation demandait plus que le garde ne promet.

**La seconde lecture ne se conclut pas en relisant.** Elle doit une seconde mutation, montée contre la
phrase annoncée et **vue rouge** : c'est ce rouge qui dit que le garde tient, et on cite la phrase avec
l'endroit où elle est écrite. Sans lui, « il ne l'a jamais promis » classerait n'importe quel survivant,
et le garde reste non prouvé.

Mesuré sur #5379 : retirer le `if:` d'**une** étape parmi plusieurs laissait
`.github/scripts/verifie_portees_de_ci.py` vert. Sa règle 2, dans son en-tête, annonce « au moins une
étape » conditionnée ; toutes les conditions du job retirées, il rougit et nomme le job. Le garde
tenait ce qu'il annonce, la mutation posait une autre question.

Un garde ne s'élargit pas « pour qu'il voie ce cas » : ce serait lui faire promettre ce que personne
n'a décidé. Si la propriété non promise compte, c'est une trouvaille, et elle se consigne.

### Quand un banc répond « non concluant »

Trois bancs refont cette mutation sur les gardes de leur corpus, dans le job `temoins` de
`lint.yml` : `scripts/adr/verifie_temoins_non_decoratifs.py`,
`scripts/methode/temoins-de-methode-non-decoratifs.py` et
`.github/scripts/temoins_de_ci_non_decoratifs.py`. Ils neutralisent les fonctions du garde, relancent
son auto-test et rendent trois comptes : il **tient**, il est **décoratif**, ou il est **non
concluant**. Le troisième veut dire que l'auto-test a planté avant d'assertir, et un rouge par
plantage ne prouve rien
([ADR 5257](../../../dev-docs/decisions/5257-un-rouge-par-plantage-ne-prouve-rien.md)).

Chaque banc nomme ses non concluants dans une table, `PLANTENT_SOUS_MUTATION`, qu'il confronte dans
les deux sens ([ADR 5743](../../../dev-docs/decisions/5743-un-invariant-se-borne-par-une-liste-nommee.md)).
Un garde neuf dont le témoin plante sous mutation fait donc sortir le banc en 1, et une entrée qui ne
plante plus aussi. Deux gestes lèvent ce rouge : réparer le témoin, ou nommer le garde dans la table
avec sa raison. Le second ne répare rien : il écrit la dette, et la table la garde lisible.

Le remède a été démontré sur `scripts/adr/5087-versions-hors-des-checks.py` (#5637), en deux pas, et
le second n'apparaît qu'après le premier :

1. **Le cas échoue au lieu de lever.** Un `assert` nu laisse une trace de pile, et le banc lit « non
   concluant » dès qu'il en voit une. Le cas passe par l'aide du harnais, qui compte l'échec.
2. **Il compare la valeur entière, jamais un index dedans.** La neutralisation fait rendre `[]` à
   toute fonction : `actives["nocturne"]` lève alors `TypeError`, là où comparer le dictionnaire
   complet échoue proprement. Le témoin y gagne, il épingle toute la dérivation au lieu de quelques
   entrées.

Les trois bancs ne font pas tout pareil, et c'est décidé : ce qu'ils ont en commun est ce qu'ils
rendent ([ADR 5265](../../../dev-docs/decisions/5265-trois-bancs-partagent-ce-qu-ils-rendent-pas-leur-mecanique.md)).

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Le test est vert, donc le garde marche » | Ce vert existerait-il si le dépôt était cassé ? |
| « La mutation est restée verte, donc le garde est décoratif » | Le promettait-il ? Relire ce qu'il annonce, puis le voir rouge sur ce qu'il annonce |
| « Le banc est sorti en 1, je nomme mon garde dans la table » | Nommer ne répare rien. Le témoin peut-il échouer au lieu de lever, et comparer la valeur entière ? |
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
