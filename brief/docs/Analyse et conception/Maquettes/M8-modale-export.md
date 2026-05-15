# M8 - Modale d'export VigieChiro

> **Type** : modale, déclenchée depuis [M2 - Détail session](M2-detail-session.md)
> **Parcours couverts** : [P4 Export VigieChiro](../Parcours%20utilisateurs/index.md#p4-export-vigiechiro)
> **Stories couvertes** : [E5.S1 Exporter le CSV](../Story%20mapping/index.md#e5s1-exporter-le-csv-de-validation-3-pts), [E5.S2 Récapitulatif d'export](../Story%20mapping/index.md#e5s2-recapitulatif-dexport-2-pts) (SHOULD), [E5.S3 Marquer comme exportée](../Story%20mapping/index.md#e5s3-marquer-la-session-comme-exportee-3-pts)

## Étape 1 - Vérification pré-export

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 460" role="img" aria-label="M8 étape 1 - vérification pré-export" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
  <rect x="10" y="10" width="620" height="440" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📤 Exporter pour VigieChiro</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label">Session : <tspan font-weight="600">du 22/04/2026 — PR n° 1925492</tspan></text>

  <text x="28" y="98" class="section">Bilan de validation</text>
  <rect x="28" y="108" width="584" height="118" rx="3" class="panel"/>
  <circle cx="48" cy="129" r="5" fill="#27ae60"/>
  <text x="62" y="133" class="label">Validées (Tadarida correct)</text>
  <text x="600" y="133" class="cell" text-anchor="end" font-weight="600">1 247</text>
  <circle cx="48" cy="153" r="5" fill="#e8a838"/>
  <text x="62" y="157" class="label">Corrigées (autre taxon)</text>
  <text x="600" y="157" class="cell" text-anchor="end" font-weight="600">22</text>
  <circle cx="48" cy="177" r="5" fill="#6a737d"/>
  <text x="62" y="181" class="label">Non passées en revue</text>
  <text x="595" y="181" class="cell" text-anchor="end" font-weight="600" fill="#e8a838">2 762 ⚠</text>
  <line x1="500" y1="195" x2="600" y2="195" stroke="#2c3e50" stroke-width="0.8"/>
  <text x="62" y="215" class="label">Total observations</text>
  <text x="600" y="215" class="cell" text-anchor="end" font-weight="600">4 031</text>

  <rect x="28" y="244" width="584" height="92" rx="3" class="warn"/>
  <text x="40" y="262" class="label" font-weight="600">⚠ Il reste 2 762 observations non passées en revue.</text>
  <text x="40" y="280" class="hint">Si vous exportez maintenant, ces observations garderont la classification Tadarida par défaut.</text>
  <circle cx="50" cy="305" r="5" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="62" y="309" class="label">Les laisser au taxon Tadarida (recommandé pour ne rien perdre)</text>
  <circle cx="50" cy="324" r="5" fill="#fff" stroke="#2c3e50" stroke-width="1.5"/>
  <circle cx="50" cy="324" r="2.5" fill="#2c3e50"/>
  <text x="62" y="328" class="label" font-weight="600">Continuer la validation avant l'export</text>

  <rect x="380" y="402" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="420" y="420" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="470" y="402" width="150" height="28" rx="3" class="btn-primary"/>
  <text x="545" y="420" class="btn-txt" text-anchor="middle">⟶ Valider d'abord</text>
</svg>
</div>

## Étape 1 bis - Validation 100 % (passage direct à l'export)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 320" role="img" aria-label="M8 étape 1bis - validation complète" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .field { fill:#ffffff; stroke:#6a737d; stroke-width:1; }
  </style>
  <rect x="10" y="10" width="620" height="300" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📤 Exporter pour VigieChiro</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label" font-weight="600" fill="#27ae60">✅ Toutes les observations ont été passées en revue.</text>

  <text x="28" y="100" class="hint" font-weight="600">Bilan :</text>
  <text x="40" y="120" class="label">• 4 009 validées (Tadarida correct)</text>
  <text x="40" y="140" class="label">• 22 corrigées (autre taxon)</text>

  <text x="28" y="180" class="hint" font-weight="600">Destination de l'export :</text>
  <rect x="28" y="190" width="490" height="22" rx="3" class="field"/>
  <text x="36" y="205" class="cell" font-family="monospace" font-size="11">/home/marie/PR_avril/22-04/8a4fa…-observations_Vu.csv</text>
  <rect x="525" y="190" width="90" height="22" rx="3" class="btn-secondary"/>
  <text x="570" y="205" class="btn-txt-dark" text-anchor="middle">Modifier…</text>

  <rect x="350" y="262" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="390" y="280" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="440" y="262" width="180" height="28" rx="3" class="btn-primary"/>
  <text x="530" y="280" class="btn-txt" text-anchor="middle">📤 Exporter maintenant</text>
</svg>
</div>

## Étape 2 - Récapitulatif post-export (E5.S2 SHOULD)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 420" role="img" aria-label="M8 étape 2 - récapitulatif post-export" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#27ae60; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .hint { font:12px sans-serif; fill:#6a737d; }
    .btn-primary { fill:#4a90d9; stroke:#2c3e50; stroke-width:1; }
    .btn-secondary { fill:#ffffff; stroke:#2c3e50; stroke-width:1; }
    .btn-txt { fill:#ffffff; font:600 12px sans-serif; }
    .btn-txt-dark { fill:#2c3e50; font:12px sans-serif; }
    .section { font:600 13px sans-serif; fill:#4a90d9; }
    .panel { fill:#f6f8fa; stroke:#d0d7de; stroke-width:1; }
  </style>
  <rect x="10" y="10" width="620" height="400" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">✅ Export réussi</text>

  <text x="28" y="72" class="section">📤 Fichier produit</text>
  <rect x="28" y="80" width="584" height="44" rx="3" class="panel"/>
  <text x="40" y="100" class="cell" font-family="monospace" font-size="11">8a4fa…-observations_Vu.csv (912 Ko)</text>
  <text x="40" y="118" class="hint" font-family="monospace" font-size="11">/home/marie/PR_avril/22-04/</text>

  <text x="28" y="156" class="section">Récapitulatif</text>
  <text x="40" y="178" class="label">• 4 031 observations exportées</text>
  <text x="40" y="198" class="label">• <tspan fill="#27ae60" font-weight="600">1 247 validées</tspan> par vous</text>
  <text x="40" y="218" class="label">• <tspan fill="#e8a838" font-weight="600">22 corrigées</tspan> par vous</text>
  <text x="40" y="238" class="label">• <tspan fill="#6a737d">2 762 laissées au taxon Tadarida</tspan> (probabilité Tadarida initiale)</text>
  <text x="40" y="258" class="label">• 18 avec commentaire libre</text>

  <text x="28" y="298" class="hint">Prochaine étape : connectez-vous à VigieChiro et téléversez le</text>
  <text x="28" y="316" class="hint">fichier <tspan font-family="monospace">_Vu.csv</tspan>.</text>

  <rect x="28" y="362" width="180" height="28" rx="3" class="btn-secondary"/>
  <text x="118" y="380" class="btn-txt-dark" text-anchor="middle">📂 Ouvrir le dossier</text>
  <rect x="218" y="362" width="180" height="28" rx="3" class="btn-secondary"/>
  <text x="308" y="380" class="btn-txt-dark" text-anchor="middle">🌐 Aller sur VigieChiro</text>
  <rect x="540" y="362" width="80" height="28" rx="3" class="btn-primary"/>
  <text x="580" y="380" class="btn-txt" text-anchor="middle">Fermer</text>
</svg>
</div>

## Composants & comportements

| Étape | Composant | Notes |
|---|---|---|
| 1 | Bilan ventilé en 3 catégories | Couleurs : vert validées, bleu corrigées, gris « non passées » |
| 1 | Encart d'avertissement | Visible uniquement si `non passées > 0` |
| 1 | Choix radio | Par défaut : `Continuer la validation` (le plus prudent). Marie peut basculer si elle est consciente |
| 1bis | Affichage du chemin de destination | Pré-rempli au dossier source de la session, dans un sous-dossier ou à la racine selon préférence |
| 1bis | Bouton `[Modifier...]` | Ouvre un sélecteur de dossier OS |
| 2 | Récapitulatif détaillé | Bilan complet, sans cacher d'information |
| 2 | Bouton `[📂 Ouvrir le dossier d'export]` | Ouvre l'explorateur OS sur le dossier produit |
| 2 | Bouton `[🌐 Aller sur VigieChiro]` | Ouvre `https://www.vigienature.fr/fr/chauves-souris` dans le navigateur par défaut. Ne se substitue pas à l'authentification utilisateur |

## Format du CSV produit

Le fichier respecte le format `_Vu.csv` attendu par VigieChiro :

- séparateur `;`
- pas de guillemets autour des champs (cf. exemple [`samples/kal/…_Vu.csv`](https://github.com/IUTInfoAix-S201/brief/tree/main/samples/kal))
- valeurs vides codées `""""` (4 guillemets - encodage spécifique au format `_Vu`)
- encodage UTF-8
- en-tête identique à la version `observations.csv`
- une ligne par observation, dans le même ordre que le CSV d'entrée

Pour chaque ligne :
- `nom du fichier`, `temps_debut`, `temps_fin`, `frequence_mediane`, `tadarida_taxon`, `tadarida_probabilite`, `tadarida_taxon_autre` : reprises telles quelles
- `observateur_taxon`, `observateur_probabilite` : remplis avec la décision utilisateur (vide / `tadarida_taxon` / autre taxon)
- `validateur_taxon`, `validateur_probabilite` : laissés vides (réservés à l'étape suivante côté VigieChiro)

## Erreurs possibles

| Erreur | Affichage |
|---|---|
| Dossier de destination en lecture seule | « ❌ Impossible d'écrire dans ce dossier. Choisissez un autre emplacement. » |
| Fichier `_Vu.csv` déjà présent | « ⚠️ Un fichier `_Vu.csv` existe déjà à cet emplacement. [Écraser] [Choisir un autre nom] » |
| Disque plein | « ❌ Espace disque insuffisant. » |

## Statut de la session après export

- Statut passe à `📤 Exportée` (E5.S3).
- Date+heure et chemin de l'export tracés en base et visibles dans [M2](M2-detail-session.md).
- Si l'utilisateur modifie une observation après l'export, un bandeau apparaît dans M2 : « ⚠️ Modifications non exportées. [Ré-exporter] ».

## À ne PAS faire

- Pas d'export silencieux : toujours afficher le recapitulatif (même si la session est validée à 100 %).
- Pas de cocher « Ne plus me demander » sur l'avertissement « obs non validées » : c'est un garde-fou intentionnel.
- Pas de produire un format différent en mode bonus (ex. JSON) : la sortie de référence est le `_Vu.csv` réinjectable par VigieChiro, point.
