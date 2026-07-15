---
hide:
  - navigation
  - toc
---

# Gantt prospectif de la SAE 2.01 - vue plein écran

[← Retour à la planification](Planification.md)

<style>
  .md-content__inner > .mermaid,
  .md-content__inner > p > .mermaid,
  div.mermaid {
    overflow-x: auto;
    max-width: 100%;
  }
  div.mermaid svg {
    min-width: 1800px;
    height: auto !important;
    max-width: none !important;
  }
  .md-content__button { display: none; }
</style>

```mermaid
gantt
    title SAE 2.01 VigieChiro PR Companion - calendrier 2026
    dateFormat YYYY-MM-DD
    axisFormat %d/%m
    excludes weekends

    section Amorçage (en parallèle d'autres modules)
    Présentation du projet         :milestone, m1, 2026-05-22, 0d
    Lecture brief, équipes, repo   :a1, 2026-05-22, 4d
    Premières estimations          :a2, 2026-05-26, 1d
    Assemblage PR (si pièces OK)   :a3, 2026-05-27, 2d
    Première nuit d'enregistrement test  :a4, 2026-05-28, 2d

    section Sprint 1 - dev exclusif
    Démarrage SAE temps plein      :milestone, m2, 2026-06-01, 0d
    E0 socle BD (S1-S5)            :s1a, 2026-06-01, 3d
    E1 Sites et points             :s1b, 2026-06-02, 3d
    E2 Import + transformation     :s1c, 2026-06-03, 5d
    E3 Vérification d'enregistr.   :s1d, 2026-06-08, 2d
    Démo intermédiaire S1          :milestone, mS1, 2026-06-09, 0d

    section Sprint 2 - finition + soutenance
    E4 Dépôt                :s2a, 2026-06-10, 2d
    SHOULD opportunistes (E5/E6/E7):s2b, 2026-06-11, 4d
    Stabilisation + tests          :s2c, 2026-06-15, 2d
    Préparation soutenance         :s2d, 2026-06-15, 3d

    section Livraison
    Code freeze + diaporama        :milestone, m3, 2026-06-18, 0d
    Test individuel                :test, 2026-06-18, 1d
    Soutenance + démo (Samuel)     :milestone, m4, 2026-06-18, 0d
```

## Lecture du Gantt

- **Section 1 - Amorçage** (22/05 → 31/05, en parallèle d'autres modules) : pas de développement intensif, juste la mise en place (lecture du brief, repo, équipes, estimations) et l'assemblage du PR de l'équipe si les pièces sont arrivées à temps.
- **Section 2 - Sprint 1** (01/06 → 09/06, dev exclusif) : la chaîne fil rouge MUST sur les fondations BD ([E0 socle](Story%20mapping/E0%20-%20Fondations%20de%20persistance.md), [E1](Story%20mapping/E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md), [E2](Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md), [E3](Story%20mapping/E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md)). [E2.S6](Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6) est le point dur critique à sécuriser dès le J1.
- **Section 3 - Sprint 2** (10/06 → 17/06, dev exclusif) : finition [E4](Story%20mapping/E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md), SHOULD opportunistes choisies en fonction de la vélocité observée Sprint 1, stabilisation et préparation soutenance en parallèle.
- **Section 4 - Livraison** (18/06) : code freeze + diaporama le matin, test individuel l'après-midi, soutenance + démo devant Samuel Busson en clôture.

Les barres orange marquent les milestones (présentation, démarrage SAE, démo intermédiaire, code freeze, soutenance).

[← Retour à la planification](Planification.md)
