# M1 - Accueil / Journal des sessions

> **Type** : vue principale (premier écran à l'ouverture de l'application)
> **Parcours couverts** : [P2 Cycle régulier](../Parcours%20utilisateurs/index.md#p2-cycle-regulier)
> **Stories couvertes** : [E1.S1 Voir le journal](../Story%20mapping/index.md#e1s1-voir-le-journal-de-mes-sessions-3-pts), [E1.S6 Tagger par chantier](../Story%20mapping/index.md#e1s6-tagger-une-session-par-chantier-projet-8-pts) (SHOULD)

## Wireframe

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 880 540" role="img" aria-label="Maquette M1 - Accueil / Journal des sessions" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .label { font: 13px sans-serif; fill: #2c3e50; }
    .hint { font: 12px sans-serif; fill: #6a737d; }
    .h2 { font: 600 16px sans-serif; fill: #2c3e50; }
    .btn-primary { fill: #4a90d9; stroke: #2c3e50; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 12px sans-serif; }
    .field { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .table-head { fill: #eef2f5; stroke: #2c3e50; stroke-width: 1; }
    .table-row { fill: #ffffff; stroke: #d0d7de; stroke-width: 0.5; }
    .table-row-alt { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 0.5; }
    .col-head { font: 600 11px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .footer { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .stat-imported { fill: #6a737d; }
    .stat-progress { fill: #f1c40f; }
    .stat-validated { fill: #27ae60; }
    .stat-exported { fill: #4a90d9; }
  </style>
  <!-- Cadre fenêtre -->
  <rect x="10" y="10" width="860" height="520" rx="4" class="frame"/>

  <!-- Title bar -->
  <rect x="10" y="10" width="860" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="860" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">🦇 VigieChiro PR Companion</text>
  <rect x="685" y="17" width="115" height="18" rx="3" fill="#34495e" stroke="#fff" stroke-width="0.5"/>
  <text x="742" y="30" class="titletxt" font-size="11" text-anchor="middle">⚙ Préférences</text>
  <text x="826" y="31" class="titletxt" font-size="14">─ □ ✕</text>

  <!-- Header section -->
  <text x="28" y="68" class="h2">Mes sessions</text>
  <rect x="540" y="55" width="180" height="26" rx="3" class="btn-primary"/>
  <text x="555" y="72" class="btn-txt">+ Importer une nuit</text>
  <rect x="730" y="55" width="120" height="26" rx="3" class="btn-secondary"/>
  <text x="745" y="72" class="btn-txt-dark">↻ Actualiser</text>

  <!-- Filter bar -->
  <text x="28" y="108" class="label">Filtres :</text>
  <text x="80" y="108" class="hint">Statut</text>
  <rect x="120" y="96" width="120" height="20" rx="3" class="field"/>
  <text x="128" y="110" class="cell">Tous ▼</text>
  <text x="252" y="108" class="hint">Tag</text>
  <rect x="280" y="96" width="120" height="20" rx="3" class="field"/>
  <text x="288" y="110" class="cell">Aucun ▼</text>
  <rect x="720" y="96" width="130" height="20" rx="3" class="field"/>
  <text x="728" y="110" class="hint">🔍 Recherche…</text>

  <!-- Table header -->
  <rect x="28" y="135" width="822" height="28" class="table-head"/>
  <text x="40" y="153" class="col-head">📅 Date ▼</text>
  <text x="160" y="153" class="col-head">PR n°</text>
  <text x="260" y="153" class="col-head">Durée</text>
  <text x="340" y="153" class="col-head">WAV</text>
  <text x="420" y="153" class="col-head">Obs.</text>
  <text x="510" y="153" class="col-head">Tag</text>
  <text x="730" y="153" class="col-head">Statut</text>

  <!-- Rows -->
  <rect x="28" y="163" width="822" height="28" class="table-row"/>
  <text x="40" y="181" class="cell">2026-04-22</text>
  <text x="160" y="181" class="cell">1925492</text>
  <text x="260" y="181" class="cell">11h22</text>
  <text x="340" y="181" class="cell">1 572</text>
  <text x="420" y="181" class="cell">4 031</text>
  <text x="510" y="181" class="cell">Carré 640380</text>
  <circle cx="724" cy="177" r="5" class="stat-imported"/>
  <text x="734" y="181" class="cell">Importée</text>

  <rect x="28" y="191" width="822" height="28" class="table-row-alt"/>
  <text x="40" y="209" class="cell">2026-04-15</text>
  <text x="160" y="209" class="cell">1925492</text>
  <text x="260" y="209" class="cell">11h05</text>
  <text x="340" y="209" class="cell">1 408</text>
  <text x="420" y="209" class="cell">3 621</text>
  <text x="510" y="209" class="cell">Carré 640380</text>
  <circle cx="724" cy="205" r="5" class="stat-progress"/>
  <text x="734" y="209" class="cell">En cours</text>

  <rect x="28" y="219" width="822" height="28" class="table-row"/>
  <text x="40" y="237" class="cell">2026-04-08</text>
  <text x="160" y="237" class="cell">1925492</text>
  <text x="260" y="237" class="cell">10h47</text>
  <text x="340" y="237" class="cell">892</text>
  <text x="420" y="237" class="cell">2 104</text>
  <text x="510" y="237" class="cell">Carré 640380</text>
  <circle cx="724" cy="233" r="5" class="stat-validated"/>
  <text x="734" y="237" class="cell">Validée</text>

  <rect x="28" y="247" width="822" height="28" class="table-row-alt"/>
  <text x="40" y="265" class="cell">2026-03-28</text>
  <text x="160" y="265" class="cell">1925487</text>
  <text x="260" y="265" class="cell">09h38</text>
  <text x="340" y="265" class="cell">421</text>
  <text x="420" y="265" class="cell">985</text>
  <text x="510" y="265" class="cell">Test_Maison</text>
  <circle cx="724" cy="261" r="5" class="stat-exported"/>
  <text x="734" y="265" class="cell">Exportée</text>

  <rect x="28" y="275" width="822" height="28" class="table-row"/>
  <text x="40" y="293" class="cell">2026-03-22</text>
  <text x="160" y="293" class="cell">1925487</text>
  <text x="260" y="293" class="cell">10h11</text>
  <text x="340" y="293" class="cell">1 109</text>
  <text x="420" y="293" class="cell">3 204</text>
  <text x="510" y="293" class="cell hint">—</text>
  <circle cx="724" cy="289" r="5" class="stat-exported"/>
  <text x="734" y="293" class="cell">Exportée</text>

  <text x="40" y="320" class="hint">…</text>

  <!-- Footer / status bar -->
  <rect x="28" y="488" width="822" height="32" rx="3" class="footer"/>
  <text x="44" y="508" class="label">5 sessions • 4 402 observations • <tspan font-weight="600">3 nuits restant à valider</tspan></text>
</svg>
</div>

## Composants

| Composant | Rôle | Données affichées | Notes |
|---|---|---|---|
| Barre de titre | Identité visuelle, accès aux préférences | nom de l'app, bouton ⚙ | |
| Bouton `+ Importer une nuit` | Déclenche [M6 - Modale d'import](M6-modale-import.md) | — | Action principale, mise en avant visuelle |
| Bouton `↻ Actualiser` | Recharge la liste depuis la base | — | Utile si plusieurs imports en parallèle |
| Filtre statut | Restreint la liste aux sessions de tel statut | menu déroulant : `Tous / Importée / En cours / Validée / Exportée` | |
| Filtre tag | Restreint aux sessions taggées | auto-complétion sur les tags utilisés (E1.S6, SHOULD) | À masquer si E1.S6 non livrée |
| Champ recherche `🔍` | Filtre full-text (n° PR, date, tag) | — | Optionnel |
| **Tableau des sessions** | Liste tabulaire triée par date décroissante par défaut | une ligne par session | Colonnes triables au clic sur l'en-tête |
| Colonne Date | date de capture (nuit) | format `AAAA-MM-JJ` | |
| Colonne PR n° | numéro de série du PR | extrait du LogPR ou du nom de fichier | |
| Colonne Durée | durée de la session de capture | format `HHhMM` | calculée début → fin du LogPR |
| Colonne WAV | nombre de fichiers WAV bruts | entier | |
| Colonne Obs. | nombre d'observations Tadarida (vide si CSV non chargé) | entier ou `—` | |
| Colonne Tag | libellé tag (E1.S6) | texte ou `—` | À masquer si E1.S6 non livrée |
| Colonne Statut | état du cycle de vie de la session | enum + icône colorée | |
| Barre de statut | Bilan global | nb de sessions, nb d'obs total, nb de sessions à valider | Mise à jour réactive |

## Légende des statuts

| Icône | Statut | Signification |
|---|---|---|
| ⬛ | Importée | WAV + LogPR importés, pas de CSV Tadarida |
| 🟡 | En cours | CSV chargé, validation en cours |
| ✅ | Validée | Toutes les observations passées en revue |
| 📤 | Exportée | CSV `_Vu` produit, prêt à téléverser sur VigieChiro |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Clic sur une ligne du tableau | Ouvre [M2 - Détail de session](M2-detail-session.md) |
| Double-clic sur une ligne | Ouvre directement [M4 - Vue de validation](M4-validation.md) si CSV chargé |
| Clic en-tête de colonne | Tri par cette colonne (toggle asc/desc, indicateur ▲/▼) |
| Clic `+ Importer une nuit` | Ouvre [M6 - Modale d'import](M6-modale-import.md) |
| Drag-and-drop d'un dossier sur la fenêtre | Équivalent à `+ Importer une nuit` avec dossier pré-rempli |
| Sélection d'un statut dans le filtre | Filtre client-side immédiat, pas de rechargement |
| Sélection multiple (Ctrl+clic, Maj+clic) | Active une barre d'actions contextuelles : `Comparer` (E6.S4 WON'T), `Supprimer` (E1.S5) |

## États

| État | Apparence |
|---|---|
| Application vide (aucune session) | Tableau remplacé par un encart central : « Aucune session importée pour le moment. Cliquez sur **+ Importer une nuit** pour démarrer. » |
| Filtre vide (aucun résultat) | Tableau remplacé par : « Aucune session ne correspond aux filtres. [Réinitialiser les filtres] » |
| Import en cours en arrière-plan | Barre de statut affiche : « ⏳ Import de la session du 2026-04-22… 47 % » |

## À ne PAS faire

- Pas de menu hamburger : les actions doivent être visibles directement.
- Pas d'icônes seules sans label dans le tableau (Marie ne doit jamais deviner la signification d'un pictogramme).
- Pas de pagination : on affiche toutes les sessions, le tri/filtre suffit même à plusieurs centaines de lignes (cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)).
