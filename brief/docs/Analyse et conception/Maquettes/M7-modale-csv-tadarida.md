# M7 - Modale de chargement du CSV Tadarida

> **Type** : modale, déclenchée depuis [M2 - Détail session](M2-detail-session.md)
> **Parcours couverts** : [P1 Première utilisation](../Parcours%20utilisateurs/index.md#p1-premiere-utilisation), [P2 Cycle régulier](../Parcours%20utilisateurs/index.md#p2-cycle-regulier)
> **Stories couvertes** : [E2.S4 Charger un CSV d'observations Tadarida](../Story%20mapping/index.md#e2s4-charger-un-csv-dobservations-tadarida-5-pts)

## Étape 1 - Sélection du fichier

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 460" role="img" aria-label="M7 étape 1 - sélection du CSV" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#2c3e50; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .hint { font:12px sans-serif; fill:#6a737d; }
    .btn-secondary { fill:#ffffff; stroke:#2c3e50; stroke-width:1; }
    .btn-txt-dark { fill:#2c3e50; font:12px sans-serif; }
    .dropzone { fill:#f6f8fa; stroke:#4a90d9; stroke-width:1.5; stroke-dasharray:6 4; }
  </style>
  <rect x="10" y="10" width="620" height="440" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📊 Charger les observations Tadarida</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label">Session cible : <tspan font-weight="600">du 22/04/2026 — PR n° 1925492</tspan></text>
  <text x="28" y="92" class="hint">Sélectionnez le fichier CSV téléchargé depuis VigieChiro</text>
  <text x="28" y="108" class="hint">(généralement nommé <tspan font-family="monospace">&lt;uuid&gt;-participation-&lt;uuid&gt;-observations.csv</tspan>).</text>

  <rect x="80" y="130" width="480" height="180" rx="6" class="dropzone"/>
  <text x="320" y="200" font-size="36" text-anchor="middle">📄</text>
  <text x="320" y="240" class="label" text-anchor="middle" font-weight="600">Glisser-déposer le fichier CSV ici</text>
  <text x="320" y="260" class="hint" text-anchor="middle">ou</text>
  <rect x="260" y="272" width="120" height="28" rx="3" class="btn-secondary"/>
  <text x="320" y="290" class="btn-txt-dark" text-anchor="middle">Parcourir…</text>

  <text x="28" y="346" class="hint" font-weight="600">ℹ Formats acceptés :</text>
  <text x="40" y="364" class="hint">• <tspan font-family="monospace">*-observations.csv</tspan> (avec guillemets)</text>
  <text x="40" y="382" class="hint">• <tspan font-family="monospace">*-observations_Vu.csv</tspan> (déjà passé en validation)</text>

  <rect x="540" y="412" width="80" height="26" rx="3" class="btn-secondary"/>
  <text x="580" y="429" class="btn-txt-dark" text-anchor="middle">Annuler</text>
</svg>
</div>

## Étape 2 - Vérification (après sélection)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 500" role="img" aria-label="M7 étape 2 - vérification du CSV" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#2c3e50; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .hint { font:12px sans-serif; fill:#6a737d; }
    .btn-primary { fill:#4a90d9; stroke:#2c3e50; stroke-width:1; }
    .btn-secondary { fill:#ffffff; stroke:#2c3e50; stroke-width:1; }
    .btn-txt { fill:#ffffff; font:600 12px sans-serif; }
    .btn-txt-dark { fill:#2c3e50; font:12px sans-serif; }
    .section { font:600 13px sans-serif; fill:#4a90d9; }
    .panel { fill:#f6f8fa; stroke:#d0d7de; stroke-width:1; }
    .warn { fill:#fff5e6; stroke:#e8a838; stroke-width:1; }
  </style>
  <rect x="10" y="10" width="620" height="480" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📊 Charger les observations Tadarida</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label" font-weight="600" fill="#27ae60">✅ Fichier reconnu</text>
  <text x="28" y="86" class="cell" font-family="monospace" font-size="11">8a4fa63b…-participation-69e9db61…-observations.csv (1.2 Mo)</text>

  <text x="28" y="116" class="hint">Format :</text>
  <text x="100" y="116" class="cell">observations brutes (avec guillemets)</text>
  <text x="28" y="134" class="hint">Encodage :</text>
  <text x="100" y="134" class="cell">UTF-8</text>
  <text x="28" y="152" class="hint">Lignes lues :</text>
  <text x="100" y="152" class="cell" font-weight="600">4 031 observations</text>

  <text x="28" y="184" class="section">Croisement avec la session</text>
  <text x="40" y="204" class="cell" fill="#27ae60">✅ 4 031 / 4 031 observations correspondent à un WAV de la session</text>

  <text x="28" y="236" class="section">Distribution des taxa</text>
  <rect x="28" y="246" width="584" height="86" rx="3" class="panel"/>
  <text x="44" y="266" class="cell">• noise</text>
  <text x="180" y="266" class="cell" text-anchor="end">2 102 (52 %)</text>
  <text x="320" y="266" class="cell">• Pippip</text>
  <text x="460" y="266" class="cell" text-anchor="end">638 (16 %)</text>
  <text x="44" y="286" class="cell">• piaf</text>
  <text x="180" y="286" class="cell" text-anchor="end">649 (16 %)</text>
  <text x="320" y="286" class="cell">• Nyclei</text>
  <text x="460" y="286" class="cell" text-anchor="end">139 (3 %)</text>
  <text x="44" y="306" class="cell">• Tadten</text>
  <text x="180" y="306" class="cell" text-anchor="end">89 (2 %)</text>
  <text x="320" y="306" class="cell">• Rhihip</text>
  <text x="460" y="306" class="cell" text-anchor="end">80 (2 %)</text>
  <text x="44" y="326" class="cell">• 22 autres taxa</text>
  <text x="180" y="326" class="cell" text-anchor="end">363 (9 %)</text>

  <rect x="28" y="350" width="584" height="60" rx="3" class="warn"/>
  <text x="40" y="368" class="label" font-weight="600">⚠ Politique en cas de CSV déjà présent</text>
  <circle cx="50" cy="386" r="5" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="62" y="390" class="label">Remplacer les observations existantes</text>
  <circle cx="50" cy="403" r="5" fill="#fff" stroke="#2c3e50" stroke-width="1.5"/>
  <circle cx="50" cy="403" r="2.5" fill="#2c3e50"/>
  <text x="62" y="407" class="label" font-weight="600">Fusionner (garder les validations utilisateur)</text>

  <rect x="380" y="450" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="420" y="468" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="470" y="450" width="150" height="28" rx="3" class="btn-primary"/>
  <text x="545" y="468" class="btn-txt" text-anchor="middle">Charger ce CSV</text>
</svg>
</div>

## Composants & comportements

| Composant | Rôle | Notes |
|---|---|---|
| Zone drag-and-drop / `Parcourir...` | Sélection du fichier CSV | Filtre extension `.csv` |
| Encart `formats acceptés` | Aide pédagogique | Précise la différence entre `observations.csv` et `observations_Vu.csv` |
| Bloc identification fichier | Affichage du nom + taille | Le nom contenant deux UUID est tronqué visuellement, tooltip = nom complet |
| Bloc Format / Encodage | Diagnostic du parser | `observations brutes` vs `format _Vu` détecté |
| Bloc Croisement | Validation de cohérence | Indique combien d'obs trouvent leur WAV de session |
| Bloc Distribution | Aperçu rapide | Top 6 taxa + agrégat des autres |
| Choix Remplacer / Fusionner | Politique de conflit | Visible uniquement si CSV déjà présent ; **Fusionner par défaut** pour ne jamais perdre les validations utilisateur (cf. [O7](../../Objectifs%20qualités/Objectifs%20qualités/O7.md)) |

## Erreurs possibles

| Erreur | Affichage |
|---|---|
| Format CSV illisible | « ❌ Ce fichier n'est pas un CSV Tadarida valide. Colonnes attendues : `nom du fichier;temps_debut;…`. Trouvé : `…` » |
| Encodage non UTF-8 | « ⚠️ L'encodage détecté est ISO-8859-1. L'application va tenter une conversion. [Continuer] [Annuler] » |
| Aucune obs ne croise un WAV de la session | « ❌ Aucune des 4 031 observations ne correspond à un WAV de cette session. Vérifiez que vous chargez le CSV sur la **bonne session**. [Annuler] » |
| Croisement partiel (<100 %) | Bloc Croisement en orange : « ⚠️ 3 824 / 4 031 obs correspondent (95 %). 207 obs réfèrent à des WAV introuvables. [Voir le détail] [Charger quand même] » |

## États

| État | Apparence |
|---|---|
| Format `_Vu.csv` détecté | Bloc Format : « format de validation (`_Vu.csv`) - les champs `observateur_*` non vides seront importés comme validations existantes » |
| CSV déjà présent | Bandeau d'avertissement + choix Remplacer/Fusionner visible |
| Premier chargement | Choix Remplacer/Fusionner masqué |

## À ne PAS faire

- Pas de chargement automatique « magique » au moment de l'import du dossier de session : le CSV vient plus tard, après passage par VigieChiro, c'est une étape consciente.
- Pas de fusionner sans le dire : afficher explicitement la politique choisie (Remplacer / Fusionner).
- Pas de masquer les obs qui ne croisent pas un WAV : c'est un signal de problème d'import qu'il faut rendre visible.
