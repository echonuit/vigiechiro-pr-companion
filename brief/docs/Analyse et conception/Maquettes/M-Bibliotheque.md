# M-Bibliotheque - Bibliothèque de sons de référence

> **⚠️ CIBLE ÉTIRÉE HORS MVP STRICT.** À engager **uniquement** si le fil rouge et la validation ([M-Vision-Tadarida](M-Vision-Tadarida.md)) sont solidement livrés.
>
> **Type** : écran **« Bibliothèque de sons »** (liste + détail + export). Alimenté par les observations marquées « **référence** » pendant la validation taxonomique ([M-Vision-Tadarida](M-Vision-Tadarida.md), [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md)).
> **Persona principal** : [Samuel](../Personas/Samuel.md) (transmission pédagogique, constitution d'une réothèque par espèce).
> **Priorité** : 🟩 COULD (bonus, hors fil rouge).
> **Parcours couverts** : [P10 - Exporter une bibliothèque de sons de référence](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md).
> **Stories couvertes** : [E8.S2 - Marquer des séquences comme référence et exporter une bibliothèque par espèce](../Story%20mapping/E8%20-%20Productivité%20avancée%20Tadarida.md#e8s2).

L'écran rassemble les **meilleurs exemples sonores par espèce** que l'utilisateur a retenus pendant la validation. À gauche, la **table des sons de référence** (espèce retenue, séquence source, fréquence) ; à droite, le **détail** de l'élément sélectionné (commentaire), un **lecteur audio** pour le réécouter, et le bouton d'**export** qui écrit un récapitulatif CSV et copie les fichiers `.wav` dans un dossier choisi. C'est l'aboutissement d'un usage pédagogique : se constituer une réothèque transmissible.

> **Cohérence visuelle** : l'écran réutilise le **composant d'écoute audio** partagé avec [M-Qualification](M-Qualification.md) et [M-Vision-Tadarida](M-Vision-Tadarida.md) (sonogramme + spectrogramme + contrôles de lecture).

## Wireframe principal - 3 sons de référence retenus

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 600" role="img" aria-label="Maquette M-Bibliotheque - bibliotheque de sons de reference" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .pagetitle { font: 700 22px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .section-title { font: 600 15px sans-serif; fill: #2c3e50; }
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 0.5; }
    .col-head { font: 600 12px sans-serif; fill: #2c3e50; }
    .row { fill: #ffffff; }
    .row-alt { fill: #f3f5f7; }
    .row-sel { fill: #cce4f7; stroke: #2563a3; stroke-width: 1; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sp { font: 600 12px sans-serif; fill: #1a5276; }
    .cell-mono { font: 11px monospace; fill: #2c3e50; }
    .scroll { fill: #e1e6eb; stroke: #c4ccd4; stroke-width: 0.5; }
    .scroll-thumb { fill: #b8c2cc; }
    .detail-txt { font: 12px sans-serif; fill: #2c3e50; }
    .player-bar { fill: #1c2530; }
    .player-btn { fill: #2c3e50; stroke: #4a6785; stroke-width: 1; }
    .player-btn-txt { fill: #ffffff; font: 11px sans-serif; }
    .player-time { fill: #bdc3c7; font: 11px monospace; }
    .specto { fill: #0e0e0e; }
    .specto-split { stroke: #2c3e50; stroke-width: 1; }
    .export-txt { font: 12px sans-serif; fill: #4a6785; }
    .btn-primary { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
  </style>

  <rect x="0" y="0" width="1000" height="600" fill="#f7f9fb"/>
  <text x="30" y="40" class="pagetitle">🔊 Bibliothèque de sons</text>
  <text x="30" y="62" class="pagesub">3 son(s) de référence.</text>

  <!-- ===== Colonne gauche : table des sons de référence ===== -->
  <text x="30" y="96" class="section-title">🗇 Sons de référence</text>
  <rect x="30" y="106" width="365" height="470" class="panel"/>
  <!-- entête colonnes -->
  <rect x="30" y="106" width="365" height="26" class="table-head"/>
  <text x="44" y="124" class="col-head">Espèce retenue</text>
  <text x="180" y="124" class="col-head">Séquence source</text>
  <text x="320" y="124" class="col-head">Fréq…</text>
  <!-- lignes -->
  <rect x="31" y="132" width="363" height="24" class="row-sel"/>
  <text x="44" y="149" class="cell-sp">Nyclei</text>
  <text x="180" y="149" class="cell-mono">seqA_000.wav</text>
  <text x="320" y="149" class="cell">27000</text>
  <rect x="31" y="156" width="363" height="24" class="row"/>
  <text x="44" y="173" class="cell-sp">Pippip</text>
  <text x="180" y="173" class="cell-mono">seqB_000.wav</text>
  <text x="320" y="173" class="cell">45000</text>
  <rect x="31" y="180" width="363" height="24" class="row-alt"/>
  <text x="44" y="197" class="cell-sp">Rhihip</text>
  <text x="180" y="197" class="cell-mono">seqC_000.wav</text>
  <text x="320" y="197" class="cell">23000</text>
  <!-- lignes vides -->
  <rect x="31" y="204" width="363" height="24" class="row"/>
  <rect x="31" y="228" width="363" height="24" class="row-alt"/>
  <rect x="31" y="252" width="363" height="24" class="row"/>
  <rect x="31" y="276" width="363" height="24" class="row-alt"/>
  <rect x="31" y="300" width="363" height="24" class="row"/>
  <!-- scrollbar horizontale -->
  <rect x="30" y="560" width="365" height="14" class="scroll"/>
  <rect x="40" y="563" width="150" height="8" rx="4" class="scroll-thumb"/>

  <!-- ===== Colonne droite : détail + écoute + export ===== -->
  <rect x="415" y="76" width="555" height="500" class="panel"/>

  <!-- Détail -->
  <text x="432" y="102" class="section-title">🔍 Détail</text>
  <line x1="415" y1="116" x2="970" y2="116" class="specto-split"/>
  <text x="432" y="138" class="detail-txt">Commentaire : Cri social typique, capté en fin de nuit.</text>

  <!-- Écoute (composant audio partagé) -->
  <text x="432" y="172" class="section-title">🎧 Écoute</text>
  <rect x="432" y="184" width="520" height="270" class="specto"/>
  <!-- barre de contrôles -->
  <rect x="432" y="184" width="520" height="34" class="player-bar"/>
  <rect x="442" y="191" width="60" height="20" rx="3" class="player-btn"/><text x="472" y="205" class="player-btn-txt" text-anchor="middle">Lecture</text>
  <rect x="508" y="191" width="62" height="20" rx="3" class="player-btn"/><text x="539" y="205" class="player-btn-txt" text-anchor="middle">Temps +</text>
  <rect x="576" y="191" width="60" height="20" rx="3" class="player-btn"/><text x="606" y="205" class="player-btn-txt" text-anchor="middle">Temps -</text>
  <rect x="642" y="191" width="56" height="20" rx="3" class="player-btn"/><text x="670" y="205" class="player-btn-txt" text-anchor="middle">Fréq. +</text>
  <rect x="704" y="191" width="54" height="20" rx="3" class="player-btn"/><text x="731" y="205" class="player-btn-txt" text-anchor="middle">Fréq. -</text>
  <text x="775" y="205" class="player-time">0.00 / 0.00 s</text>
  <!-- séparation sonogramme / spectrogramme -->
  <line x1="432" y1="320" x2="952" y2="320" class="specto-split"/>

  <!-- Export -->
  <text x="432" y="488" class="section-title">📚 Export</text>
  <text x="432" y="512" class="export-txt">Écrit le récapitulatif CSV et copie les fichiers son de référence dans le dossier choisi.</text>
  <rect x="432" y="528" width="200" height="30" rx="4" class="btn-primary"/>
  <text x="532" y="548" class="btn-txt" text-anchor="middle">📚 Exporter la bibliothèque…</text>
</svg>
</div>

### Annotations

- **Titre + compteur** : « Bibliothèque de sons » et le nombre de sons de référence (`3 son(s) de référence.`).
- **Table « Sons de référence »** (`TableView`) : une ligne par observation marquée référence, colonnes **Espèce retenue** (code taxon : `Nyclei`, `Pippip`, `Rhihip`…), **Séquence source** (fichier `.wav`), **Fréquence**. Tri/sélection standard ; la sélection alimente le panneau de détail.
- **Détail** : le **commentaire** associé à la séquence retenue (saisi pendant la validation).
- **Écoute** : le **composant audio partagé** (sonogramme + spectrogramme + contrôles `Lecture` / `Temps +` / `Temps -` / `Fréq. +` / `Fréq. -` + timecode). Permet de réécouter l'exemple sélectionné.
- **Export** : libellé explicatif + bouton **« Exporter la bibliothèque… »** qui ouvre un sélecteur de dossier, écrit un **récapitulatif CSV** et **copie les fichiers `.wav`** de référence (organisés par espèce, cf. [P10](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md)).

## Variante - aucun son de référence (état vide)

Tant qu'aucune observation n'a été marquée « référence » pendant la validation, la table affiche **« Aucun contenu dans la table »**, le sous-titre invite à l'action (« Aucun son de référence : marquez des observations « référence » pendant la validation. ») et le bouton **« Exporter la bibliothèque… » est désactivé**.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 170" role="img" aria-label="Maquette M-Bibliotheque - etat vide" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .pagetitle { font: 700 20px sans-serif; fill: #2c3e50; }
    .pagesub { font: 13px sans-serif; fill: #6a737d; }
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 0.5; }
    .col-head { font: 600 12px sans-serif; fill: #2c3e50; }
    .empty { font: 12px sans-serif; fill: #8a949e; }
    .btn-disabled { fill: #e1e6eb; stroke: #c4ccd4; stroke-width: 1; }
    .btn-disabled-txt { fill: #9aa4ae; font: 600 12px sans-serif; }
  </style>
  <rect x="0" y="0" width="1000" height="170" fill="#f7f9fb"/>
  <text x="30" y="36" class="pagetitle">🔊 Bibliothèque de sons</text>
  <text x="30" y="58" class="pagesub">Aucun son de référence : marquez des observations « référence » pendant la validation.</text>
  <rect x="30" y="74" width="365" height="80" class="panel"/>
  <rect x="30" y="74" width="365" height="24" class="table-head"/>
  <text x="44" y="91" class="col-head">Espèce retenue</text>
  <text x="180" y="91" class="col-head">Séquence source</text>
  <text x="197" y="124" class="empty" text-anchor="middle">Aucun contenu dans la table</text>
  <rect x="432" y="120" width="200" height="30" rx="4" class="btn-disabled"/>
  <text x="532" y="140" class="btn-disabled-txt" text-anchor="middle">📚 Exporter la bibliothèque…</text>
</svg>
</div>

### Interactions clés

| Élément | Action |
|---|---|
| Sélection d'une ligne | Charge le **commentaire** (Détail) et l'audio (Écoute) de la séquence |
| `Lecture` / `Temps ±` / `Fréq. ±` | Contrôles du composant audio (lecture, zoom temporel et fréquentiel) |
| Bouton **Exporter la bibliothèque…** | Sélecteur de dossier → écrit le CSV récapitulatif + copie les `.wav` par espèce |
| Aucun son marqué référence | Table vide, sous-titre incitatif, bouton Export **désactivé** |

## Notes pour l'implémentation

- **Alimentation par la validation** : la table liste les observations dont l'attribut « référence » est vrai, marquées via le bouton dédié de [M-Vision-Tadarida](M-Vision-Tadarida.md) ([E7.S4](../Story%20mapping/E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s4)). La sélection « référence » est **persistée en BD** ([E0.S5](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s5)) et réutilisable d'une session à l'autre.
- **`TableView` JavaFX** : colonnes liées aux propriétés de l'observation (code espèce, fichier source, fréquence). État vide géré nativement (`placeholder`).
- **Composant audio fourni** : la zone Écoute est le **composant partagé** (cf. [M-Qualification](M-Qualification.md) / [M-Vision-Tadarida](M-Vision-Tadarida.md)) ; les étudiants l'instancient avec le `wav:Path` de la séquence sélectionnée, sans réimplémenter le rendu spectral.
- **Bouton Export lié à l'état** : `disableProperty` du bouton liée à « la liste est non vide » (`BooleanBinding` sur la taille de la collection). L'export ouvre un `DirectoryChooser`, écrit le CSV et copie les fichiers (travail d'I/O à faire **hors du thread JavaFX**, avec retour d'état via `lblMessage`).
- **Organisation par espèce** : à l'export, regrouper les fichiers par code/espèce (cf. arborescence décrite dans [P10](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md)). La **variante document récapitulatif** (HTML/PDF avec spectrogrammes) reste une extension facultative.
