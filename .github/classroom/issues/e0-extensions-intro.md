# 🧩 Pour aller plus loin : propositions d'extension (optionnel)

Les issues `[extension]` qui suivent sont **optionnelles** : à tenter **une fois le travail
obligatoire terminé** (les 8 features + les passes finales vertes). Elles correspondent à de vraies
évolutions du produit, capturées dans le backlog.

## Esprit

- Contrairement aux tâches obligatoires (un fichier chacune), une extension **touche plusieurs
  couches** (modèle / service / IHM) : c'est l'occasion de travailler une feature **de bout en bout**.
- **Choisissez librement** selon l'envie et le temps restant — inutile de toutes les faire.
- Le **socle reste votre garde-fou** : ajoutez des tests, ne cassez rien, respectez le MVVM et la
  qualité (Spotless / PMD / ArchUnit).

## Definition of Done (commune à toute extension)

- [ ] La fonctionnalité est **testée** (au moins un test qui la couvre) et la suite reste **verte**.
- [ ] Pas de régression ; `spotless:check` + `-Pquality-gate verify` verts ; MVVM respecté.
- [ ] Livrée via **branche + PR relue**, avec une courte note expliquant le choix de conception.
