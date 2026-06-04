# 🖥️ [vérification] Lancer l'application et naviguer dans tous les écrans

Une **vérification manuelle** (les tests automatisés ne remplacent pas un coup d'œil humain sur l'IHM).

## Ce qu'il faut faire

Lancez l'appli : `./mvnw javafx:run` (dans le devcontainer / Codespaces, via noVNC si besoin). Puis
parcourez et **cochez** chaque écran :

- [ ] **Accueil** : les 4 cartes (Mes sites, Importer une nuit, Bibliothèque de sons, Vue multi-sites).
- [ ] **Mes sites** + **détail d'un site** (fournis) : la référence à imiter.
- [ ] **Importer une nuit** : assistant complet (dossier → inspection → rattachement → import).
- [ ] **Vue multi-sites** : tableau, filtres, tri, export, modale des vues ; double-clic → passage.
- [ ] **M-Passage** : fiche + stepper + boutons vers les autres écrans.
- [ ] **Vérifier** (M-Qualification), **Diagnostic** (M-Diagnostic), **Préparer le dépôt** (M-Lot),
      **Validation Tadarida** (M-Vision-Tadarida), **Bibliothèque de sons**.

Pour chaque écran : pas de placeholder « à construire », pas de zone vide inattendue, les actions
réagissent, l'audio se lit (écrans concernés).

## Critères d'acceptation

- [ ] Tous les écrans s'ouvrent et affichent leur vraie vue ; aucune exception dans la console au
      lancement / à la navigation.
- [ ] Le fil rouge se fait **à la souris** : importer une nuit, la vérifier, la déposer, la valider.

## Definition of Done

- [ ] Captures d'écran (ou courte démo) jointes au compte rendu d'équipe.
- [ ] Les éventuels défauts visuels relevés ont été ouverts en issues et corrigés.
