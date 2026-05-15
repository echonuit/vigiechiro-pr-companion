# M2 - Fiche détail d'une session

> **Type** : vue secondaire (ouverte depuis [M1 - Accueil](M1-accueil.md))
> **Parcours couverts** : [P1 Première utilisation](../Parcours%20utilisateurs.md#p1-premiere-utilisation), [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier)
> **Stories couvertes** : [E1.S2](../Story%20mapping.md#e1s2-voir-le-detail-dune-session-3-pts), [E1.S3](../Story%20mapping.md#e1s3-annoter-une-session-avec-un-commentaire-libre-2-pts), [E1.S4](../Story%20mapping.md#e1s4-marquer-une-session-comme-validation-terminee-2-pts)

## Wireframe

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 880 700" role="img" aria-label="Maquette M2 - Détail d'une session" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .label { font: 13px sans-serif; fill: #2c3e50; }
    .hint { font: 12px sans-serif; fill: #6a737d; }
    .h2 { font: 600 15px sans-serif; fill: #2c3e50; }
    .section { font: 600 13px sans-serif; fill: #4a90d9; }
    .btn-primary { fill: #4a90d9; stroke: #2c3e50; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-danger { fill: #ffffff; stroke: #c0392b; stroke-width: 1; }
    .btn-success { fill: #27ae60; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 12px sans-serif; }
    .btn-txt-danger { fill: #c0392b; font: 12px sans-serif; }
    .field { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .tab-active { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-inactive { fill: #eef2f5; stroke: #6a737d; stroke-width: 1; }
    .progress-bg { fill: #eef2f5; stroke: #d0d7de; stroke-width: 0.5; }
    .progress-fill { fill: #f1c40f; }
  </style>

  <!-- Cadre fenêtre -->
  <rect x="10" y="10" width="860" height="680" rx="4" class="frame"/>

  <!-- Title bar -->
  <rect x="10" y="10" width="860" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="860" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt" font-size="13">← Retour au journal</text>
  <text x="440" y="31" class="titletxt" font-size="13" text-anchor="middle">Session du 22/04/2026 — PR n° 1925492</text>

  <!-- Onglets -->
  <rect x="28" y="60" width="120" height="30" rx="3" class="tab-active"/>
  <text x="88" y="80" class="label" text-anchor="middle" font-weight="600">Métadonnées</text>
  <rect x="148" y="60" width="120" height="30" rx="3" class="tab-inactive"/>
  <text x="208" y="80" class="hint" text-anchor="middle">Diagnostic</text>

  <!-- Section Capture -->
  <text x="40" y="115" class="section">📅 Capture</text>
  <text x="60" y="135" class="label">Début</text>
  <text x="200" y="135" class="cell" font-family="monospace">22/04/2026 20:25:53</text>
  <text x="60" y="153" class="label">Fin</text>
  <text x="200" y="153" class="cell" font-family="monospace">23/04/2026 07:48:00</text>
  <text x="60" y="171" class="label">Durée</text>
  <text x="200" y="171" class="cell" font-family="monospace">11 h 22 min</text>

  <!-- Section Acquisition -->
  <text x="40" y="200" class="section">📡 Acquisition</text>
  <text x="60" y="220" class="label">PR n°</text>
  <text x="200" y="220" class="cell" font-family="monospace">1925492</text>
  <text x="60" y="238" class="label">Fréquence éch.</text>
  <text x="200" y="238" class="cell" font-family="monospace">384 kHz</text>
  <text x="60" y="256" class="label">Bande de freq.</text>
  <text x="200" y="256" class="cell" font-family="monospace">8 — 120 kHz</text>
  <text x="60" y="274" class="label">Gain</text>
  <text x="200" y="274" class="cell" font-family="monospace">0 dB</text>
  <text x="60" y="292" class="label">Filtre passe-haut</text>
  <text x="200" y="292" class="cell" font-family="monospace">aucun</text>
  <text x="60" y="310" class="label">Sensibilité</text>
  <text x="200" y="310" class="cell" font-family="monospace">16 dB</text>

  <!-- Section Fichiers -->
  <text x="40" y="340" class="section">📁 Fichiers</text>
  <text x="60" y="360" class="label">Dossier source</text>
  <text x="200" y="360" class="cell" font-family="monospace">/home/marie/PR_avril/22-04</text>
  <rect x="500" y="348" width="80" height="20" rx="3" class="btn-secondary"/>
  <text x="540" y="362" class="btn-txt-dark" text-anchor="middle">📂 Ouvrir</text>
  <text x="60" y="378" class="label">WAV bruts</text>
  <text x="200" y="378" class="cell" font-family="monospace">1 572</text>
  <text x="60" y="396" class="label">WAV redécoupés</text>
  <text x="200" y="396" class="cell" font-family="monospace">2 114 (Tadarida)</text>
  <text x="60" y="414" class="label">Observations</text>
  <text x="200" y="414" class="cell" font-family="monospace">4 031 (dont 1 247 validées, 22 corrigées)</text>

  <!-- Section Tag -->
  <text x="40" y="445" class="section">🏷 Tag (optionnel)</text>
  <rect x="60" y="453" width="380" height="22" rx="3" class="field"/>
  <text x="68" y="468" class="cell">Carré 640380 — Pass 2</text>
  <text x="430" y="468" class="hint">▼</text>

  <!-- Section Commentaire -->
  <text x="40" y="500" class="section">📝 Commentaire libre</text>
  <rect x="60" y="508" width="780" height="60" rx="3" class="field"/>
  <text x="70" y="525" class="cell">Nuit dégagée, légère brise. Capture lancée avant la pluie.</text>
  <text x="70" y="543" class="cell">RAS sur le matériel.</text>

  <!-- Boutons d'action -->
  <rect x="28" y="588" width="160" height="28" rx="3" class="btn-primary"/>
  <text x="108" y="606" class="btn-txt" text-anchor="middle">📊 Charger CSV Tadarida</text>
  <rect x="198" y="588" width="170" height="28" rx="3" class="btn-secondary"/>
  <text x="283" y="606" class="btn-txt-dark" text-anchor="middle">🎧 Ouvrir la validation</text>
  <rect x="378" y="588" width="110" height="28" rx="3" class="btn-secondary"/>
  <text x="433" y="606" class="btn-txt-dark" text-anchor="middle">📤 Exporter</text>
  <rect x="730" y="588" width="120" height="28" rx="3" class="btn-danger"/>
  <text x="790" y="606" class="btn-txt-danger" text-anchor="middle">🗑 Supprimer</text>

  <!-- Statut + barre de progression -->
  <text x="28" y="648" class="label">Statut : <tspan font-weight="600" fill="#f1c40f">🟡 Validation en cours</tspan> — 1 269 / 4 031 observations passées en revue</text>
  <rect x="28" y="655" width="500" height="8" rx="2" class="progress-bg"/>
  <rect x="28" y="655" width="158" height="8" rx="2" class="progress-fill"/>
  <rect x="558" y="650" width="290" height="22" rx="3" class="btn-success"/>
  <text x="703" y="666" class="btn-txt" text-anchor="middle">✅ Marquer comme validation terminée</text>
</svg>
</div>

## Composants

| Composant | Rôle | Données affichées | Notes |
|---|---|---|---|
| Bouton `← Retour au journal` | Revient à [M1](M1-accueil.md) | — | Persiste les filtres actifs sur M1 |
| Titre de page | Identifie la session affichée | date + n° PR | |
| **Onglets** `Métadonnées / Diagnostic` | Bascule entre les deux vues | — | Onglet `Diagnostic` → [M3](M3-diagnostic-session.md) |
| Bloc Capture | Plage temporelle | début, fin, durée | Extrait du LogPR |
| Bloc Acquisition | Paramètres techniques du PR | Fe, bande, gain, FPH, sensibilité | Extrait de la ligne `Paramètres : ...` du LogPR |
| Bloc Fichiers | Volumétrie + accès au dossier source | nb WAV bruts, nb WAV kal, nb obs (avec décompte validées/corrigées) | Bouton `📂 Ouvrir` lance l'explorateur OS sur le dossier |
| Champ Tag | Tag de la session (E1.S6 SHOULD) | combobox + auto-complétion | Masquer si E1.S6 non livrée |
| **Champ Commentaire libre** | Annotation libre (E1.S3) | TextArea multi-ligne, max 2000 caractères | Sauvegarde immédiate au blur |
| Boutons d'action principale | Actions sur la session | 4 boutons | cf. tableau Interactions |
| Statut + barre de progression | Avancement de la validation | `nb_validées / nb_total` + jauge | |
| Bouton `✅ Marquer comme validation terminée` | E1.S4 | — | Désactivé si toutes les obs n'ont pas été passées en revue, **avec avertissement** au clic (cf. interaction) |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Clic onglet `Diagnostic` | Bascule vers [M3 - Diagnostic](M3-diagnostic-session.md) (même fenêtre) |
| Saisie dans le commentaire | Sauvegarde immédiate (debounce ~500 ms), pas de bouton « Enregistrer » |
| Saisie dans le tag | Auto-complétion sur les tags existants, création d'un nouveau tag si inexistant |
| Clic `📂 Ouvrir` | Lance l'explorateur de fichiers de l'OS sur le dossier source |
| Clic `📊 Charger CSV Tadarida` | Ouvre [M7 - Modale de chargement CSV](M7-modale-csv-tadarida.md) |
| Clic `🎧 Ouvrir la validation` | Ouvre [M4 - Vue de validation](M4-validation.md) (désactivé si CSV non chargé) |
| Clic `📤 Exporter` | Ouvre [M8 - Modale d'export](M8-modale-export.md) (désactivé si statut ≠ Validée ou Exportée) |
| Clic `🗑 Supprimer` | Ouvre [M9 - Modale de suppression](M9-modale-suppression.md) |
| Clic `✅ Marquer validation terminée` | Si toutes les obs validées → applique direct. Sinon → modale d'avertissement « Il reste N observations non passées en revue. Continuer quand même ? [Continuer] [Annuler] » |
| Clic `← Retour au journal` | Retour à [M1](M1-accueil.md), pas de confirmation (tout est sauvegardé) |

## États

| État | Apparence |
|---|---|
| CSV Tadarida non chargé | Bloc Fichiers : `Observations : pas encore chargées`. Boutons `🎧 Ouvrir validation` et `📤 Exporter` désactivés (avec tooltip explicatif). |
| Statut = Importée | Statut affiché : `⬛ Importée - en attente du CSV Tadarida` |
| Statut = Validation en cours | Barre de progression visible |
| Statut = Validée | Bouton `✅ Marquer validation terminée` remplacé par badge `✅ Validation terminée le JJ/MM/AAAA HH:MM` + bouton `↩ Reprendre la validation` (réversible) |
| Statut = Exportée | Badge supplémentaire `📤 Exportée le JJ/MM/AAAA HH:MM vers /chemin/_Vu.csv` ; bouton `📤 Exporter` devient `📤 Ré-exporter` |
| Modifications post-export | Bandeau orange : `⚠️ Vous avez modifié des observations depuis le dernier export. [Ré-exporter]` |

## À ne PAS faire

- Pas de bouton « Enregistrer » sur le commentaire ou le tag (cf. [O7](../../Objectifs%20qualités/Objectifs%20qualités/O7.md) - persistance immédiate).
- Pas de masquer les paramètres techniques (Fe, gain…) dans un onglet « avancé » : Karim et Samuel les regardent souvent.
- Pas de remplacer le bouton `🗑 Supprimer` par une icône seule : action dangereuse, le mot doit être visible.
