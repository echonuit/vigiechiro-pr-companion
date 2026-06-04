# 🧩 [extension] Écoute : normaliser le niveau sonore

> Extension **optionnelle** touchant l'**écoute** (M-Vision-Tadarida, qualification, bibliothèque).

## Objectif

**Normaliser le niveau sonore** des séquences **à l'écoute** (au rendu : les fichiers restent
inchangés — R9), pour une amplitude homogène (les cris ont des amplitudes très variables).

## Découpage

- La normalisation est une capacité du **composant fourni `audio-view`** → une issue dédiée existe sur
  son dépôt (`IUTInfoAix-S201/audio-view`).
- **Côté VigieChiro** : une fois l'option disponible dans le composant, **l'activer à la lecture** et
  exposer éventuellement un réglage **on/off** dans les écrans d'écoute.

## Critères d'acceptation

- [ ] Le niveau sonore à l'écoute est homogène entre séquences ; les fichiers source ne sont pas modifiés.

## Definition of Done

- [ ] Comportement vérifié (manuellement et/ou test) ; suite verte ; PR relue. Voir la DoD commune (`e0`).
