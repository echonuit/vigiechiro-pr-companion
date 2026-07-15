---
hide:
  - navigation
  - toc
---

# P0 - Première nuit de Marie - vue plein écran

[← Retour à la fiche P0](P0%20-%20Première%20nuit%20de%20Marie.md)

<style>
  /* Diagramme TB en pleine largeur, hauteur naturelle. Le min-width assure des boîtes confortables. */
  .md-content__inner > .mermaid,
  .md-content__inner > p > .mermaid,
  div.mermaid {
    overflow-x: auto;
    max-width: 100%;
    text-align: center;
  }
  div.mermaid svg {
    min-width: 900px;
    width: auto !important;
    height: auto !important;
    max-width: none !important;
  }
  /* Cache le bouton « Modifier sur GitHub » pour libérer de la place */
  .md-content__button { display: none; }
  /* L'override des couleurs de texte Mermaid est dans docs/stylesheets/extra.css */
</style>

```mermaid
%%{init: {"themeCSS": ".nodeLabel, .nodeLabel p, .nodeLabel span { color: #fff !important; fill: #fff !important; }"}}%%
flowchart TB
    Marie(["👩‍🔬 Marie, débutante mono-site"])
    Marie --> S1
    S1["🌐 <b>Étape 1 · Déclarer le site</b><br/>n° de carré, code du point"]
    S1 --> S2
    S2["<b>Étape 2 · Programmer le PR · déployer · récupérer la SD</b><br/>allumage 30 min avant coucher · extinction 30 min après lever<br/>🦇 sur le terrain (hors application)"]
    S2 --> SD
    SD[("💾 Carte SD pleine - WAV bruts + LogPR + THLog")]
    SD --> S3
    S3["📥 <b>Étape 3 · Importer la nuit</b><br/>copie protégée + renommage <code>CarXXXXXX-AAAA-PassN-YY-</code><br/>chunks 5 s réelles + transformation ×10"]
    S3 --> S4
    S4["🎧 <b>Étape 4 · Vérifier l'enregistrement</b><br/>sound check par échantillonnage<br/>verdict global OK / Utilisable / Inexploitable"]
    S4 --> S5
    S5["📦 <b>Étape 5 · Préparer le dépôt</b><br/>vérification cohérence · ouverture du dossier"]
    S5 --> Lot
    Lot[("📦 Dépôt - séquences + journal + climat")]
    Lot -.téléversement manuel via navigateur.-> VC
    VC(["🌐 vigiechiro.herokuapp.com"])

    classDef step fill:#1e8449,stroke:#0e5128,color:#fff,stroke-width:2px
    classDef offapp fill:#935116,stroke:#5b3009,color:#fff,stroke-width:2px
    classDef artifact fill:#7d6608,stroke:#4d3f00,color:#fff,stroke-width:2px
    classDef ext fill:#34495e,stroke:#17202a,color:#fff,stroke-width:2px
    classDef actor fill:#2874a6,stroke:#154360,color:#fff,stroke-width:2px
    class S1,S3,S4,S5 step
    class S2 offapp
    class SD,Lot artifact
    class VC ext
    class Marie actor
```

## Légende

| Couleur | Signification |
|---|---|
| 🟦 Bleu | Actrice (Marie) |
| 🟩 Vert | Étape réalisée **dans l'application** |
| 🟫 Marron | Étape réalisée **hors application** (terrain) |
| 🟨 Crème (cylindre) | Artefact produit ou consommé (carte SD, dépôt) |
| ⬛ Gris foncé | Système externe (portail Vigie-Chiro) |

## Lecture du parcours

1. **Marie** déclare son site dans l'application (étape 1).
2. Elle se rend **sur le terrain** : programme l'enregistreur, le déploie, le récupère au matin (étape 2). Elle revient avec une **carte SD pleine** de WAV bruts.
3. Elle revient **dans l'application** pour importer la nuit (étape 3 : copie protégée, renommage `CarXXXXXX-AAAA-PassN-YY-`, découpage en tranches de 5 s réelles, expansion temps ×10), vérifier l'enregistrement par échantillonnage (étape 4) et préparer le dépôt (étape 5).
4. Elle obtient un **dépôt** sur disque, qu'elle téléverse **manuellement** via son navigateur sur le portail Vigie-Chiro.

L'application **remplace entièrement** la chaîne d'outils manuels (LupasRename + Kaléidoscope 4.3.1) historiquement utilisée.

[← Retour à la fiche P0](P0%20-%20Première%20nuit%20de%20Marie.md)
