---
name: harmoniser-l-application
description: Use at closure pass 7, once the tests are covered, to look at the whole application rather than the chantier's delta. An exhaustive audit first, a conceptualisation refactoring second, and the choices are discussed rather than slipped in.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Harmoniser l'application

## Loi d'airain

```
L'AUDIT D'ABORD, LE REFACTORING ENSUITE, ET SUR L'APPLICATION ENTIÈRE
```

Refactorer avant d'avoir cartographié produit une correction locale qui a l'air d'une harmonisation.
La passe porte sur **tout le code**, pas sur les fichiers que le chantier a touchés.

## Annoncer

« J'utilise la compétence harmoniser-l-application sur ce que <le chantier> vient de livrer. »

## Ce qui la distingue du refactor de la boucle

Le troisième temps de rouge-vert-refactor se fait à **chaque petit pas**, à l'échelle du code qu'on
vient d'écrire. Celui-ci se fait **une fois**, à l'échelle de l'application, et il remonte de
l'implémentation vers la conception. Les deux portent le même mot et ne sont pas le même geste.

## Fonction de garde

```
1. CARTOGRAPHIER avant de toucher a quoi que ce soit. Deux questions, posees sur
                 TOUT le code : qu est-ce qui RESSEMBLE a ce qu on vient d ecrire,
                 et qu est-ce qui BENEFICIERAIT du resultat du chantier ?
2. INTERROGER    le graphe du depot, les deux questions etant conceptuelles.
3. NE PAS S ARRETER au premier doublon evident. L audit est exhaustif ou il n a
                 pas eu lieu.
4. COMPRENDRE    le concept reel derriere la demande, au-dela de la formulation
                 du ticket.
5. REFACTORER    pour que la structure exprime ce concept. La duplication et
                 l abstraction sont des OUTILS, pas le but.
6. DISCUTER      tout choix, doute ou consequence qui n est pas evident. On ne
                 s engage pas seul sur l application entiere.
```

## Pourquoi le graphe, et ce qu'il a montré

Les deux questions de l'audit sont des questions de **concept**, et aucune ne se pose en termes
d'identifiants. `grep` ne cherche que des chaînes.

```bash
graphify query "<question>" --budget 2500
graphify explain "<concept>"
```

La règle a été apprise à cette passe. Trois `grep` bien choisis annonçaient **deux** écritures d'une
même règle de dérivation ; le graphe en montrait **trois**. La troisième ne citait aucun des noms
cherchés et n'avait **aucun appelant**, ce qu'aucun `grep` n'aurait dit.

Sa sortie reste une hypothèse : il photographie un commit, il vieillit, et une part de ses arêtes est
inférée. Il oriente la recherche, il ne remplace pas la lecture.

## Le refactoring de conceptualisation, et ce qu'il n'est pas

Le but est que la structure **exprime mieux le concept**, donc qu'elle soit plus lisible.

- Du code répété entre features devient un contrat ou un patron partagé dans `commun`.
- Une classe devenue trop grosse se coupe, le `GodClass` du portail qualité étant le garde-fou.
- Trois copies d'un même geste deviennent un mécanisme d'extension.

**Un code plus court mais moins compréhensible n'est pas une harmonisation.** La duplication et
l'abstraction sont des outils au service de la clarté ; les prendre pour le but produit une
factorisation que le prochain lecteur défera.

## Discuter, ne pas trancher seul

Un refactoring de conceptualisation engage l'application entière, et c'est l'un des rares moments où
l'on remonte de l'implémentation vers la conception.

Dès qu'un choix, un doute ou une conséquence n'est pas évident : soumettre les options, expliciter
les compromis, laisser trancher. Un refactoring glissé sans que la question soit posée est défendable
et n'est pas votre décision.

## Ce que la passe laisse derrière elle

**Elle tranche presque toujours quelque chose**, et l'écriture des ADR la désigne comme l'une de ses
trois sources. Une décision prise ici qu'un lecteur futur pourrait défaire faute d'en connaître la
raison s'écrit.

**Elle casse des écrans sans casser de test**, parce qu'elle touche volontiers au CSS partagé et aux
composants du socle. C'est la raison pour laquelle la revue visuelle vient juste après elle. Des
tests verts ne disent rien de ce qu'on peut lire à l'écran.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je refactore ce que le chantier a touché » | La passe porte sur l'application entière |
| « J'ai trouvé le doublon » | L'audit est exhaustif ou il n'a pas eu lieu. On ne s'arrête pas au premier |
| « Trois `grep` suffisent » | Les questions sont conceptuelles. Trois `grep` ont annoncé deux écritures là où il y en avait trois |
| « C'est plus court, donc c'est mieux » | Plus court et moins compréhensible n'est pas une harmonisation |
| « Le choix est évident, j'avance » | Il engage l'application entière. On soumet les options |
| « Les tests passent, l'harmonisation est bonne » | Elle casse des écrans sans casser de test. La revue visuelle suit |
