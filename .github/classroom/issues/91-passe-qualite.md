# 🏅 [passe finale] Objectifs qualité (Spotless, PMD, couverture)

À mener **en continu** et à **verrouiller en fin de projet**. La qualité de développement fait partie
de l'évaluation (couplage R2.03).

## Ce qu'il faut faire

1. **Formatage** : `./mvnw spotless:apply` (corrige) puis `./mvnw spotless:check` (vérifie) — vert.
2. **Code smells (PMD)** : `./mvnw -Pquality-gate verify` — aucune violation. Lisez les messages : ils
   pointent des défauts de conception réels (méthodes trop longues, duplication, complexité…). Corrigez
   à la **source** plutôt que d'ajouter des exceptions.
3. **Couverture (JaCoCo)** : `./mvnw verify` produit un rapport dans `target/site/jacoco/`. Visez les
   seuils du projet (lignes ≥ 85 %, branches ≥ 70 % hors `outils/`). Comblez les trous par des tests
   utiles, pas du test « pour faire du chiffre ».
4. **Architecture** : les tests **ArchUnit** doivent rester verts (MVVM respecté : pas de `javafx.scene`
   dans les ViewModels, pas de logique/JDBC dans les vues, *slices* sans cycle).

## Critères d'acceptation

- [ ] `./mvnw spotless:check` vert.
- [ ] `./mvnw -Pquality-gate verify` vert (0 violation PMD).
- [ ] Couverture conforme aux seuils ; tests ArchUnit verts.

## Definition of Done

- [ ] La **CI est verte** (tous les portails GitHub Actions) sur `main`.
- [ ] Aucune dette ajoutée : pas de `@SuppressWarnings` ni d'exclusion PMD non justifiée.
