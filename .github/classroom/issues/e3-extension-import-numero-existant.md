# 🧩 [extension] Import : prévenir un numéro de passage déjà utilisé

> Extension **optionnelle** de la feature **importation**.

## Objectif

Avant de créer un passage (import / rattachement), **vérifier que le n° de passage n'existe pas déjà**
pour le triplet (point, année, n°) et le signaler **proprement**, **avant** la violation de contrainte
SQL d'unicité.

## Pistes

- Contrôle proactif via le DAO des passages au moment du rattachement.
- Message clair côté M-Import (proposer le prochain n° libre ?), sans exception brute.

## Critères d'acceptation

- [ ] Rattacher une nuit sur un quadruplet **déjà existant** affiche un **avertissement clair**, et
      n'aboutit pas à une exception non gérée.

## Definition of Done

- [ ] Couvert par un test (quadruplet existant → avertissement) ; suite verte ; PR relue. DoD commune (`e0`).
