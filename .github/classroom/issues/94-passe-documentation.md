# 📚 [passe finale] Documentation

Un code livré se documente. On reste sobre : on documente le **pourquoi**, pas le **comment** évident.

## Ce qu'il faut faire

- [ ] **Doc-comments** : chaque ViewModel / controleur que vous avez écrit a un doc-comment de tête
      (format Markdown `///`, comme le code fourni) qui dit son rôle. Pas de paraphrase ligne à ligne.
- [ ] **README d'équipe** : complétez un court `README` (ou une section) qui explique comment lancer
      l'appli, l'état d'avancement des features, et la répartition du travail dans l'équipe.
- [ ] **Galerie d'aperçus** : vérifiez que les captures d'écran de `.github/assets/` sont **à jour**
      avec vos écrans (elles se régénèrent automatiquement à chaque push sur `main`). Référencez-les
      dans votre README si utile.
- [ ] **Cohérence des consignes** : aucune mention « à construire / placeholder » ne subsiste dans le
      code ou la doc des features terminées.

## Critères d'acceptation

- [ ] Chaque classe IHM écrite par l'équipe a un doc-comment `///` de tête pertinent.
- [ ] Le README d'équipe permet à un nouveau venu de lancer le projet et de comprendre l'avancement.

## Definition of Done

- [ ] `./mvnw spotless:check` reste vert (les `///` sont bien formatés).
- [ ] Documentation livrée via **PR relue**.
