# M4 - Vue principale de validation

> **Type** : vue principale (l'écran le plus utilisé pendant la SAE)
> **Parcours couverts** : [P2 Cycle régulier](../Parcours%20utilisateurs/index.md#p2-cycle-regulier), [P3 Validation approfondie](../Parcours%20utilisateurs/index.md#p3-validation-approfondie)
> **Stories couvertes** : [E4.S1 Liste obs](../Story%20mapping/index.md#e4s1-voir-la-liste-des-observations-dune-session-3-pts), [E4.S3 Valider](../Story%20mapping/index.md#e4s3-valider-une-observation-tadarida-est-correct-3-pts), [E4.S4 Corriger](../Story%20mapping/index.md#e4s4-corriger-une-observation-proposer-un-autre-taxon-3-pts), [E4.S5 Commenter obs](../Story%20mapping/index.md#e4s5-annoter-une-observation-avec-un-commentaire-libre-2-pts) (SHOULD), [E3.S1 Lecture audio](../Story%20mapping/index.md#e3s1-lecture-audio-ralentie-dun-wav-8-pts), [E3.S2 Vitesse](../Story%20mapping/index.md#e3s2-regler-la-vitesse-de-lecture-3-pts) (SHOULD), [E3.S4 Navigation](../Story%20mapping/index.md#e3s4-lecture-des-observations-adjacentes-2-pts) (SHOULD)

## Wireframe

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 700" role="img" aria-label="Maquette M4 - Vue de validation" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .label { font: 13px sans-serif; fill: #2c3e50; }
    .hint { font: 12px sans-serif; fill: #6a737d; }
    .section { font: 600 13px sans-serif; fill: #4a90d9; }
    .btn-primary { fill: #4a90d9; stroke: #2c3e50; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-success { fill: #27ae60; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 12px sans-serif; }
    .field { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .table-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-sel { fill: #e3edf7; stroke: #4a90d9; stroke-width: 1; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .panel { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .stat-todo { fill: #6a737d; }
    .stat-validated { fill: #27ae60; }
    .stat-corrected { fill: #e8a838; }
  </style>

  <!-- Cadre fenêtre -->
  <rect x="10" y="10" width="1060" height="680" rx="4" class="frame"/>

  <!-- Title bar -->
  <rect x="10" y="10" width="1060" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="1060" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt" font-size="13">← Détail session</text>
  <text x="540" y="31" class="titletxt" font-size="13" text-anchor="middle">Validation — Session du 22/04/2026 — PR n° 1925492</text>
  <text x="1036" y="31" class="titletxt" font-size="14">─ □ ✕</text>

  <!-- Barre filtres + compteur -->
  <rect x="28" y="55" width="100" height="26" rx="3" class="btn-secondary"/>
  <text x="78" y="73" class="btn-txt-dark" text-anchor="middle">🔍 Filtres ▼</text>
  <text x="148" y="73" class="label">4 031 obs • <tspan fill="#27ae60" font-weight="600">1 269 validées</tspan> • <tspan fill="#e8a838" font-weight="600">22 corrigées</tspan> • 2 740 à voir</text>

  <!-- Panneau gauche : tableau observations -->
  <rect x="28" y="95" width="600" height="565" rx="3" class="panel"/>

  <!-- Header tableau -->
  <rect x="36" y="100" width="584" height="24" class="table-head"/>
  <text x="50" y="116" class="col-head">St</text>
  <text x="80" y="116" class="col-head">Fichier ▲</text>
  <text x="290" y="116" class="col-head">Début</text>
  <text x="350" y="116" class="col-head">Fin</text>
  <text x="410" y="116" class="col-head">Freq</text>
  <text x="475" y="116" class="col-head">Taxon</text>
  <text x="555" y="116" class="col-head">Prob</text>

  <!-- Lignes -->
  <rect x="36" y="124" width="584" height="22" class="table-row"/>
  <circle cx="52" cy="135" r="4" class="stat-todo"/>
  <text x="80" y="139" class="cell" font-family="monospace">…20262_000</text>
  <text x="290" y="139" class="cell">0.4</text>
  <text x="350" y="139" class="cell">2.5</text>
  <text x="410" y="139" class="cell">153</text>
  <text x="475" y="139" class="cell">noise</text>
  <text x="555" y="139" class="cell">0.86</text>

  <rect x="36" y="146" width="584" height="22" class="table-row-alt"/>
  <circle cx="52" cy="157" r="4" class="stat-todo"/>
  <text x="80" y="161" class="cell" font-family="monospace">…20262_000</text>
  <text x="290" y="161" class="cell">0.2</text>
  <text x="350" y="161" class="cell">0.3</text>
  <text x="410" y="161" class="cell">9</text>
  <text x="475" y="161" class="cell">piaf</text>
  <text x="555" y="161" class="cell">0.54</text>

  <rect x="36" y="168" width="584" height="22" class="table-row"/>
  <circle cx="52" cy="179" r="4" class="stat-validated"/>
  <text x="80" y="183" class="cell" font-family="monospace">…20264_000</text>
  <text x="290" y="183" class="cell">2.1</text>
  <text x="350" y="183" class="cell">2.2</text>
  <text x="410" y="183" class="cell">188</text>
  <text x="475" y="183" class="cell">noise</text>
  <text x="555" y="183" class="cell">0.59</text>

  <!-- Ligne sélectionnée -->
  <rect x="36" y="190" width="584" height="22" class="table-row-sel"/>
  <circle cx="52" cy="201" r="4" class="stat-todo"/>
  <text x="80" y="205" class="cell" font-family="monospace" font-weight="600">…20281_000</text>
  <text x="290" y="205" class="cell">0.7</text>
  <text x="350" y="205" class="cell">1.2</text>
  <text x="410" y="205" class="cell">47</text>
  <text x="475" y="205" class="cell">Pippip</text>
  <text x="555" y="205" class="cell" fill="#c0392b" font-weight="600">0.45</text>

  <rect x="36" y="212" width="584" height="22" class="table-row-alt"/>
  <circle cx="52" cy="223" r="4" class="stat-validated"/>
  <text x="80" y="227" class="cell" font-family="monospace">…20281_000</text>
  <text x="290" y="227" class="cell">1.1</text>
  <text x="350" y="227" class="cell">1.4</text>
  <text x="410" y="227" class="cell">41</text>
  <text x="475" y="227" class="cell">Pippip</text>
  <text x="555" y="227" class="cell">0.92</text>

  <rect x="36" y="234" width="584" height="22" class="table-row"/>
  <circle cx="52" cy="245" r="4" class="stat-corrected"/>
  <text x="80" y="249" class="cell" font-family="monospace">…20283_000</text>
  <text x="290" y="249" class="cell">0.3</text>
  <text x="350" y="249" class="cell">1.8</text>
  <text x="410" y="249" class="cell">41</text>
  <text x="475" y="249" class="cell">Pipkuh</text>
  <text x="555" y="249" class="cell">0.78</text>

  <rect x="36" y="256" width="584" height="22" class="table-row-alt"/>
  <circle cx="52" cy="267" r="4" class="stat-validated"/>
  <text x="80" y="271" class="cell" font-family="monospace">…20284_000</text>
  <text x="290" y="271" class="cell">0.6</text>
  <text x="350" y="271" class="cell">0.9</text>
  <text x="410" y="271" class="cell">19</text>
  <text x="475" y="271" class="cell">Tadten</text>
  <text x="555" y="271" class="cell">0.83</text>

  <text x="80" y="295" class="hint">…</text>

  <!-- Légende statuts -->
  <text x="40" y="615" class="hint" font-weight="600">Légende :</text>
  <circle cx="115" cy="611" r="4" class="stat-todo"/>
  <text x="125" y="615" class="hint">Non passée</text>
  <circle cx="200" cy="611" r="4" class="stat-validated"/>
  <text x="210" y="615" class="hint">Validée</text>
  <circle cx="270" cy="611" r="4" class="stat-corrected"/>
  <text x="280" y="615" class="hint">Corrigée</text>

  <!-- Panneau droit : détail observation -->
  <rect x="638" y="95" width="412" height="565" rx="3" class="panel"/>
  <text x="650" y="118" class="section">Détail de l'observation</text>

  <text x="650" y="142" class="hint">📁 Fichier</text>
  <text x="720" y="142" class="cell" font-family="monospace" font-size="11">…_20260422_202817_000.wav</text>
  <text x="650" y="160" class="hint">⏱ Plage</text>
  <text x="720" y="160" class="cell">de 0.7 s à 1.2 s (durée 0.5 s)</text>
  <text x="650" y="178" class="hint">📊 Fréq.</text>
  <text x="720" y="178" class="cell">47 kHz (médiane)</text>

  <rect x="650" y="195" width="390" height="40" rx="3" fill="#fff5e6" stroke="#e8a838" stroke-width="1"/>
  <text x="660" y="213" class="label" font-weight="600">Tadarida → Pippip</text>
  <text x="660" y="228" class="hint">Pipistrellus pipistrellus — probabilité <tspan font-weight="600" fill="#c0392b">0.45</tspan></text>

  <!-- Forme d'onde -->
  <rect x="650" y="248" width="390" height="60" rx="3" fill="#ffffff" stroke="#d0d7de" stroke-width="1"/>
  <polyline points="660,278 670,275 680,272 690,265 700,260 710,255 720,260 730,268 740,275 750,283 760,290 770,295 780,288 790,280 800,272 810,266 820,260 830,256 840,252 850,256 860,262 870,270 880,278 890,283 900,288 910,290 920,287 930,283 940,278 950,275 960,272 970,275 980,278 990,280 1000,283 1010,285 1020,287" stroke="#4a90d9" stroke-width="1.2" fill="none"/>
  <text x="660" y="265" class="hint" font-size="10">Forme d'onde</text>

  <!-- Lecteur -->
  <text x="650" y="328" class="label">▶ Lecture ralentie</text>
  <rect x="785" y="316" width="50" height="18" rx="3" class="field"/>
  <text x="810" y="329" class="cell" text-anchor="middle">×10 ▼</text>
  <text x="850" y="328" class="hint">0:02 / 0:05</text>

  <rect x="650" y="345" width="35" height="22" rx="3" class="btn-secondary"/>
  <text x="667" y="360" class="btn-txt-dark" text-anchor="middle">⏮</text>
  <rect x="690" y="345" width="35" height="22" rx="3" class="btn-secondary"/>
  <text x="707" y="360" class="btn-txt-dark" text-anchor="middle">⏯</text>
  <rect x="730" y="345" width="35" height="22" rx="3" class="btn-secondary"/>
  <text x="747" y="360" class="btn-txt-dark" text-anchor="middle">⏭</text>
  <rect x="780" y="345" width="115" height="22" rx="3" class="btn-secondary"/>
  <text x="837" y="360" class="btn-txt-dark" text-anchor="middle">↺ Recommencer</text>

  <!-- Séparateur -->
  <line x1="650" y1="385" x2="1040" y2="385" stroke="#d0d7de" stroke-width="1"/>

  <!-- Validation -->
  <text x="650" y="408" class="section">Votre validation</text>
  <circle cx="660" cy="430" r="6" fill="#fff" stroke="#2c3e50" stroke-width="1.5"/>
  <circle cx="660" cy="430" r="3" fill="#2c3e50"/>
  <text x="675" y="434" class="label">Tadarida est correct (Pippip)</text>

  <circle cx="660" cy="455" r="6" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="675" y="459" class="label">Corriger en</text>
  <rect x="755" y="448" width="100" height="20" rx="3" class="field"/>
  <text x="763" y="462" class="cell">Pipkuh ▼</text>

  <text x="675" y="482" class="hint">Probabilité</text>
  <rect x="755" y="471" width="60" height="20" rx="3" class="field"/>
  <text x="763" y="485" class="cell">0.85</text>

  <!-- Commentaire -->
  <text x="650" y="520" class="section">📝 Commentaire</text>
  <rect x="650" y="528" width="390" height="50" rx="3" class="field"/>
  <text x="660" y="546" class="cell">Pic 39 kHz, morphologie atypique</text>

  <!-- Boutons -->
  <rect x="650" y="600" width="120" height="28" rx="3" class="btn-success"/>
  <text x="710" y="618" class="btn-txt" text-anchor="middle">✅ Valider</text>
  <rect x="780" y="600" width="140" height="28" rx="3" class="btn-secondary"/>
  <text x="850" y="618" class="btn-txt-dark" text-anchor="middle">↺ Réinitialiser</text>
</svg>
</div>

## Composants

### Panneau gauche - Liste des observations

| Composant | Rôle | Données | Notes |
|---|---|---|---|
| Bouton `[🔍 Filtres ▼]` | Ouvre [M5 - Panneau filtre](M5-filtre-observations.md) | indicateur du nb de filtres actifs si > 0 | |
| Compteur global | Bilan de la session | `total / validées / corrigées / à voir` | Mis à jour réactivement |
| **TableView** | Liste paginée virtuellement | une ligne par observation | E4.S1 |
| Colonne Fichier | Nom du WAV (tronqué) | suffixe + `_000` | Tooltip = nom complet |
| Colonne Début/Fin | Bornes temporelles dans le WAV | secondes (1 décimale) | |
| Colonne Freq | Fréquence médiane | Hz (entier) | |
| Colonne Tax. Tad. | Taxon Tadarida | code 6 lettres | Tooltip = nom latin complet |
| Colonne probabilité (non visible mais triable) | Probabilité Tadarida | float 0-1 | Visualisable via tri |
| Indicateur de statut (1re colonne) | État de validation | ● ✅ 🔄 💬 selon `validation_state` | combiné avec couleur de fond ligne |

### Panneau droit - Détail de l'observation sélectionnée

| Composant | Rôle | Données | Notes |
|---|---|---|---|
| En-tête fichier | Identification | nom WAV cliquable (ouvre dans l'OS) | |
| Plage temporelle | Bornes dans le WAV | `de X.X s à Y.Y s (durée Z.Z s)` | |
| Fréquence médiane | Métrique Tadarida | `xx kHz` | |
| Bloc proposition Tadarida | Lecture du CSV | code + nom latin + probabilité | Couleur indicateur de confiance |
| Forme d'onde | Visualisation audio (E3.S3 COULD) | rendu simple PNG ou Canvas | À masquer si E3.S3 non livrée |
| **Lecteur audio** | Lecture ralentie (E3.S1, E3.S2) | bouton ▶, sélecteur vitesse, position courante / durée | E3.S2 vitesse réglable dans [×5, ×10, ×20] |
| Boutons navigation | E3.S4 (SHOULD) | ⏮ obs précédente, ⏯ play/pause, ⏭ obs suivante, ↺ recommencer | Raccourcis clavier : flèches haut/bas pour précédente/suivante, espace pour play/pause |
| Bloc validation | Saisie utilisateur | radio buttons + combobox + champ probabilité | E4.S3, E4.S4 |
| Champ commentaire (E4.S5) | Annotation libre | TextArea | SHOULD - masquer si non livré |
| Bouton `✅ Valider` | Persiste la décision | — | Désactivé tant que rien n'a été choisi |
| Bouton `↺ Réinitialiser` | Annule la décision | — | Re-passe à `non passée en revue` |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Sélection d'une ligne | Charge le détail dans le panneau droit + sélection de l'audio |
| Tri par colonne (clic en-tête) | Tri immédiat (E4.S1, performance < 100 ms cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)) |
| Clic `[🔍 Filtres ▼]` | Ouvre [M5](M5-filtre-observations.md) (panneau latéral ou popover) |
| Clic ▶ | Démarre la lecture audio à la vitesse sélectionnée |
| Sélection vitesse | Change le facteur de ralentissement, sans interrompre la lecture (E3.S2) |
| Clic ⏭ | Sélectionne l'obs suivante dans la liste filtrée |
| Choix d'un autre taxon | Le statut « Tadarida est correct » bascule automatiquement sur « Corriger » |
| Clic `✅ Valider` | Persiste, marque l'obs comme `validée` ou `corrigée` selon le radio actif, puis avance automatiquement à l'obs suivante |
| Saisie commentaire | Sauvegarde immédiate (debounce ~500 ms) |
| Raccourcis clavier | `↓` / `↑` = navigation, `espace` = play/pause, `Entrée` = valider, `c` = focus champ commentaire |

## États

| État | Apparence |
|---|---|
| Aucune obs sélectionnée | Panneau droit affiche : « Sélectionnez une observation à gauche pour commencer la validation. » |
| Lecture en cours | Bouton ▶ devient ⏸, position courante mise à jour 10× par seconde |
| Audio absent (WAV manquant) | Lecteur remplacé par : « ⚠️ Le fichier WAV correspondant est introuvable dans `kal/`. Vérifiez l'import. » |
| Liste vide après filtre | TableView remplacée par : « Aucune observation ne correspond aux filtres actifs. [Effacer les filtres] » |
| Validation enregistrée | Toast discret en bas à droite : « ✅ Observation validée » (1 seconde) |

## Performance attendue

- Affichage initial : < 1 s sur le sample (473 obs).
- Tri / filtre : < 100 ms sur le full dataset (4031 obs) - cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md).
- Démarrage lecture audio : < 200 ms après clic.
- Décodage WAV ralenti : sans glitch perceptible (cf. [O4](../../Objectifs%20qualités/Objectifs%20qualités/O4.md)).

## À ne PAS faire

- Pas de modale au moment de valider une observation : la validation doit être au clic, sans interstitiel (Marie traite 200 obs par session).
- Pas de scroll infini dans la liste : si la performance le permet pas, virtualiser la TableView (le composant JavaFX `TableView` le fait nativement).
- Pas d'affichage du panneau de détail dans une fenêtre séparée : split-pane vertical fixe pour garder le contexte.
