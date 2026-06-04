# 🗂️ [passage] 4/6 — Implémenter RattachementViewModel (modale)

Quatrième sous-tâche : le **ViewModel de la modale** « Modifier le rattachement » (corriger année +
n° de passage).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/passage/viewmodel/RattachementViewModel.java`

## Contexte

Propriétés (année, numéro, récap, message) et getters fournis ; le constructeur garde des écouteurs à
compléter (balisés). Vous écrivez le corps de `ouvrirSur` (pré-remplissage) et `valider` (qui renvoie
un booléen, amorce fournie).

## Démarche (TDD)

1. **Activez** `RattachementViewModelTest`, **constatez le rouge**.
2. `ouvrirSur` : lisez le détail du passage, mémorisez les valeurs actuelles, pré-remplissez les champs
   et recalculez le récapitulatif des conséquences. `valider` : contrôlez les bornes (n° ≥ 1, année à 4
   chiffres) puis appliquez le nouveau rattachement auprès du service ; renvoyez le succès ; les erreurs
   passent par le message.
3. Patron : feature `sites`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `RattachementViewModelTest` réactivé et **vert**.

## Definition of Done

- [ ] **Un seul fichier modifié** : `RattachementViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
