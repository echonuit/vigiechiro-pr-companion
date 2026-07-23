---
hide:
  - navigation
  - toc
---

# Topologie des parcours - vue plein écran

[← Retour au sommaire des parcours](index.md)

<style>
  .md-content__inner > .mermaid,
  .md-content__inner > p > .mermaid,
  div.mermaid {
    overflow-x: auto;
    max-width: 100%;
    text-align: center;
  }
  div.mermaid svg {
    min-width: 1400px;
    width: auto !important;
    height: auto !important;
    max-width: none !important;
  }
  .md-content__button { display: none; }
</style>

```mermaid
%%{init: {"flowchart": {"defaultRenderer": "elk"}, "themeCSS": ".nodeLabel, .nodeLabel p, .nodeLabel span { color: #fff !important; fill: #fff !important; }"}}%%
flowchart LR
    P1[🌐 P1 - Déclarer<br/>un site] --> P2[📥 P2 - Importer<br/>une nuit]
    P2 --> P3[🎧 P3 - Vérifier<br/>l'enregistrement]
    P3 --> P4[📦 P4 - Préparer<br/>le dépôt]
    P4 -->|dépôt sur<br/>VigieChiro| P7[✅ P7 - Valider les<br/>résultats Tadarida]

    P1 --> P5[🗂 P5 - Naviguer<br/>multi-sites]
    P5 --> P2
    P3 --> P6[🩺 P6 - Diagnostiquer<br/>le matériel]

    P7 --> P9[🔁 P9 - Regrouper<br/>les nuits par point]
    P7 --> P10[🎼 P10 - Exporter<br/>sons de référence]

    classDef must fill:#1e8449,stroke:#0e5128,color:#fff,stroke-width:2px
    classDef should fill:#b9770e,stroke:#7e5109,color:#fff,stroke-width:2px
    classDef could fill:#5d6d7e,stroke:#283747,color:#fff,stroke-width:2px
    class P1,P2,P3,P4 must
    class P5,P6,P7 should
    class P9,P10 could
```

## Légende

| Couleur | Signification | Parcours |
|---|---|---|
| 🟩 Vert (MUST) | Chaîne minimale livrable - le fil rouge [P0](P0%20-%20Première%20nuit%20de%20Marie.md) en est la concaténation | P1, P2, P3, P4 |
| 🟧 Orange (SHOULD) | Approfondissements qui montent en charge ou ouvrent la cible étirable | P5, P6, P7 |
| ⬜ Gris (COULD) | Cibles étirées et idées long terme issues des retours Samuel | P9, P10 |

> Ce coloriage est la **priorité de conception** (MoSCoW), fixée au cadrage initial. Il ne dit **pas** le statut de livraison : la plupart de ces parcours sont **livrés** aujourd'hui (cf. le [sommaire des parcours](index.md)), à l'exception de **P9** (regroupement de nuits), qui reste une cible non livrée.

## Comment lire le diagramme

- Les **flèches pleines** sont les enchaînements directs entre parcours.
- La **flèche pointillée** entre P4 et P7 matérialise le **dépôt sur Vigie-Chiro** (par l'application, repli navigateur possible) + l'attente du retour Tadarida (24-48 h hors application).
- Une **boucle** P5 → P2 indique que le parcours multi-sites alimente plusieurs imports successifs.
- Les parcours P9 et P10 sont des **branches optionnelles** qui ne bloquent pas le fil principal.

[← Retour au sommaire des parcours](index.md)
