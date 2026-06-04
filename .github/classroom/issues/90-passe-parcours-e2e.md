# 🧪 [passe finale] Réactiver les parcours de bout en bout (E2E)

À faire **une fois les 8 features construites**. Le projet contient des **tests de parcours E2E** qui
traversent plusieurs écrans sur la vraie application (navigation réelle, transitions de workflow).
Ils sont livrés **désactivés** car ils supposent l'IHM construite.

## Ce qu'il faut faire

1. Dans `src/test/java/fr/univ_amu/iut/e2e/`, **réactivez** les parcours un par un (retirez les
   `@Disabled`) et faites-les passer :
   - le **fil rouge** import → vérification → préparation → dépôt ;
   - multisite → passage (drill-down) ;
   - passage → validation Tadarida ; passage → diagnostic ;
   - l'ouverture de la bibliothèque.
2. **Réactivez aussi** le test d'intégration `sites → passage` (`SiteDetailVersPassageViewTest`) :
   il est **commenté** (entre `/* */`) car il pilote l'écran M-Passage — décommentez-le maintenant
   que passage est construit.
3. Faites un dernier `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` : **0 skip, 0 fail**.

## Critères d'acceptation

- [ ] **Tous** les tests E2E sont réactivés et **verts**.
- [ ] `SiteDetailVersPassageViewTest` est décommenté et vert.
- [ ] La suite complète ne contient plus aucun test `@Disabled` ni bloc de test commenté côté étudiant.

## Definition of Done

- [ ] `./mvnw clean verify -Dglass.platform=Headless -Dprism.order=sw` **vert** de bout en bout.
- [ ] Modifications livrées via **PR(s) relue(s)**.
