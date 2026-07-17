# Décisions d'architecture (ADR)

Ce journal consigne les **décisions structurantes** du projet : les choix qui engagent l'architecture ou le domaine sur la durée, et surtout **pourquoi** ils ont été faits. Une décision oubliée se re-débat ; écrite, elle se relit.

## Ce qu'est (et n'est pas) une ADR

Une **ADR** (Architecture Decision Record) décrit **une** décision : son contexte, ce qui a été tranché, ses conséquences, et les pistes écartées. Elle est **immuable** une fois acceptée : on ne la réécrit pas, on en écrit une nouvelle qui la remplace (statut « Remplacée par ADR NNNN »).

Une ADR n'est **pas** :

- un compte rendu de chantier (ça, c'est le **bilan**, déposé dans le corps de l'EPIC à sa clôture) ;
- une description de l'implémentation (ça, c'est le code et sa Javadoc, ou [Patterns et principes](../patterns.md)) ;
- une note de rappel opérationnelle (« attention au piège X ») : ces notes vivent ailleurs.

On écrit une ADR quand un chantier prend une décision qu'un développeur futur **pourrait raisonnablement remettre en cause** faute d'en connaître les raisons : « pourquoi ne pas simplement comparer l'empreinte ? », « pourquoi une fenêtre courte plutôt qu'une moyenne ? ».

## Quand en écrire une

Au fil d'un chantier, à la **passe 3 (doc développeur)** de sa [clôture](../cycle-de-chantier.md) : chaque décision structurante prise pendant le chantier donne une ADR. La **passe 10 (bilan)** s'y réfère plutôt que de dupliquer le raisonnement.

## Format

Copier le squelette suivant dans `NNNN-titre-court.md` (numéro à 4 chiffres, incrémental) :

```markdown
# ADR NNNN — La décision, formulée comme une affirmation

- **Statut** : Accepté — AAAA-MM-JJ
- **Chantier** : EPIC #NNNN (titre court)

## Contexte
Les forces en présence, le problème, ce qui contraint.

## Décision
Ce qui est tranché, à l'impératif.

## Conséquences
Ce que cela implique, en bien comme en moins bien.

## Alternatives écartées
Les autres pistes, et pourquoi elles ont perdu.
```

## Journal

| # | Décision | Chantier |
|---|---|---|
| [0001](0001-reactivation-passage-reconstruit-identite-structurelle.md) | Identité d'un passage reconstruit : régénération structurelle, l'acoustique en indice | #1653 |
| [0002](0002-detection-acoustique-energie-de-pointe.md) | Détection acoustique par énergie de pointe, pas par moyenne globale | #1653 |
