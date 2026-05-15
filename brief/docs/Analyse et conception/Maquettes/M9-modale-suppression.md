# M9 - Modale de suppression d'une session

> **Type** : modale, déclenchée depuis [M2 - Détail session](M2-detail-session.md) (ou en sélection multiple depuis [M1](M1-accueil.md))
> **Stories couvertes** : [E1.S5 Supprimer une session](../Story%20mapping.md#e1s5-supprimer-une-session-3-pts)

## Étape 1 - Choix du périmètre

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 460" role="img" aria-label="M9 étape 1 - choix du périmètre" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#c0392b; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .hint { font:12px sans-serif; fill:#6a737d; }
    .btn-secondary { fill:#ffffff; stroke:#2c3e50; stroke-width:1; }
    .btn-warn { fill:#ffffff; stroke:#c0392b; stroke-width:1; }
    .btn-txt-dark { fill:#2c3e50; font:12px sans-serif; }
    .btn-txt-warn { fill:#c0392b; font:600 12px sans-serif; }
    .panel { fill:#f6f8fa; stroke:#d0d7de; stroke-width:1; }
  </style>
  <rect x="10" y="10" width="620" height="440" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">🗑 Supprimer la session</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label">Vous êtes sur le point de supprimer :</text>

  <rect x="28" y="80" width="584" height="100" rx="3" class="panel"/>
  <text x="40" y="100" class="label" font-weight="600">📌 Session du 22/04/2026 — PR n° 1925492</text>
  <text x="56" y="122" class="label">• 1 572 WAV bruts (5.0 Go)</text>
  <text x="56" y="140" class="label">• 4 031 observations</text>
  <text x="56" y="158" class="label">• 1 269 validations utilisateur</text>
  <text x="56" y="176" class="label">• Tag : <tspan font-family="monospace">Carré 640380 — Pass 2</tspan></text>

  <text x="28" y="216" class="label" font-weight="600">Que voulez-vous supprimer ?</text>

  <circle cx="40" cy="240" r="6" fill="#fff" stroke="#2c3e50" stroke-width="1.5"/>
  <circle cx="40" cy="240" r="3" fill="#2c3e50"/>
  <text x="56" y="244" class="label" font-weight="600">Seulement les métadonnées de l'application</text>
  <text x="56" y="262" class="hint">la session disparaît du journal, les fichiers WAV restent sur votre disque</text>
  <text x="56" y="278" class="hint">dans <tspan font-family="monospace">/home/marie/PR_avril/22-04</tspan></text>

  <circle cx="40" cy="316" r="6" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="56" y="320" class="label">Tout effacer, y compris les fichiers WAV</text>
  <text x="56" y="338" class="hint" fill="#c0392b">libère ~5 Go d'espace disque — ⚠ irréversible</text>

  <rect x="380" y="402" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="420" y="420" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="470" y="402" width="150" height="28" rx="3" class="btn-warn"/>
  <text x="545" y="420" class="btn-txt-warn" text-anchor="middle">⟶ Continuer</text>
</svg>
</div>

## Étape 2 - Confirmation finale

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 380" role="img" aria-label="M9 étape 2 - confirmation finale" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#c0392b; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .hint { font:12px sans-serif; fill:#6a737d; }
    .btn-secondary { fill:#ffffff; stroke:#2c3e50; stroke-width:1; }
    .btn-warn-disabled { fill:#f6f8fa; stroke:#a3a8ad; stroke-width:1; }
    .btn-txt-dark { fill:#2c3e50; font:12px sans-serif; }
    .btn-txt-disabled { fill:#a3a8ad; font:600 12px sans-serif; }
    .field { fill:#ffffff; stroke:#6a737d; stroke-width:1; }
    .warn-banner { fill:#fff5e6; stroke:#e8a838; stroke-width:1; }
  </style>
  <rect x="10" y="10" width="620" height="360" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">⚠ Confirmation requise</text>

  <text x="28" y="68" class="label">Vous êtes sur le point d'effacer définitivement :</text>

  <text x="40" y="98" class="label">• Toutes les métadonnées de la session du 22/04/2026</text>
  <text x="40" y="118" class="label">• 1 269 validations utilisateur</text>
  <text x="40" y="138" class="label">• 22 corrections de taxon</text>
  <text x="40" y="158" class="label">• 18 commentaires libres</text>

  <rect x="28" y="180" width="584" height="36" rx="3" class="warn-banner"/>
  <text x="40" y="203" class="label" font-weight="600">⚠ Cette action est irréversible.</text>

  <text x="28" y="248" class="label">Pour confirmer, tapez le numéro de PR : <tspan font-family="monospace" font-weight="600">1925492</tspan></text>
  <rect x="28" y="258" width="400" height="26" rx="3" class="field"/>
  <text x="36" y="277" class="hint" font-style="italic">Saisie attendue…</text>

  <text x="28" y="304" class="hint">Le bouton « Supprimer » reste désactivé tant que le n° n'est pas correctement saisi.</text>

  <rect x="320" y="324" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="360" y="342" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="410" y="324" width="210" height="28" rx="3" class="btn-warn-disabled"/>
  <text x="515" y="342" class="btn-txt-disabled" text-anchor="middle">🗑 Supprimer définitivement</text>
</svg>
</div>

## Étape 3 - Confirmation de suppression

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 220" role="img" aria-label="M9 étape 3 - suppression effective" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#27ae60; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .btn-primary { fill:#4a90d9; stroke:#2c3e50; stroke-width:1; }
    .btn-txt { fill:#ffffff; font:600 12px sans-serif; }
  </style>
  <rect x="10" y="10" width="620" height="200" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">✅ Session supprimée</text>

  <text x="28" y="76" class="label">La session du 22/04/2026 a été retirée du journal.</text>

  <text x="40" y="116" class="label">• Métadonnées effacées</text>
  <text x="40" y="136" class="label">• 5.0 Go libérés sur le disque <tspan fill="#6a737d">(option « tout effacer »)</tspan></text>

  <rect x="540" y="162" width="80" height="28" rx="3" class="btn-primary"/>
  <text x="580" y="180" class="btn-txt" text-anchor="middle">Fermer</text>
</svg>
</div>

## Composants & comportements

| Étape | Composant | Notes |
|---|---|---|
| 1 | Bloc rappel session | Affiche les éléments qui seront supprimés (volume, validations, tag) pour donner pleinement conscience de l'enjeu |
| 1 | Choix radio périmètre | Par défaut : **« Seulement les métadonnées »** (le moins destructif). L'option « Tout effacer » est explicite |
| 1 | Précision sur le chemin | Quand on sélectionne « Seulement métadonnées », montrer où resteront les WAV |
| 2 | Demande de saisie du n° PR | Garde-fou anti-clic-distrait : oblige à taper exactement le n° de série du PR pour confirmer |
| 2 | Bouton `Supprimer définitivement` | **Désactivé** tant que le n° PR n'est pas correctement saisi |
| 3 | Toast ou modale courte | Confirmation visible mais brève |

## Comportement en sélection multiple

Si plusieurs sessions sont sélectionnées dans [M1](M1-accueil.md) avant l'action `Supprimer` :

- Étape 1 affiche un **bloc rappel agrégé** : `5 sessions sélectionnées, 8 014 WAV, 25 Go au total, 4 327 validations utilisateur`.
- Étape 2 demande de taper `SUPPRIMER` (le mot, pas un n° PR) pour confirmer.
- Étape 3 récapitule par session : `5 sessions supprimées, 25 Go libérés`.

## États

| État | Apparence |
|---|---|
| Saisie incorrecte du n° PR (étape 2) | Bouton `Supprimer` reste désactivé, hint en dessous : « Tapez exactement le numéro de série pour confirmer » |
| Suppression d'une session déjà exportée | Étape 1 ajoute un encart : « ℹ️ Cette session a déjà été exportée vers VigieChiro (le fichier `_Vu.csv` n'est PAS supprimé). » |
| Erreur d'effacement disque | Étape 3 : « ⚠️ Métadonnées supprimées, mais certains fichiers n'ont pas pu être effacés (permissions). [Voir le détail] » |

## À ne PAS faire

- Pas de garde-fou unique ([Annuler] / [Supprimer]) : Marie peut cliquer trop vite. Le 2 clics + saisie du n° PR est intentionnel.
- Pas d'option « Cocher pour me souvenir de cette confirmation » : la confirmation doit être systématique.
- Pas de bouton rouge énorme « SUPPRIMER » qui attire l'œil : le bouton de confirmation est sobre, désactivé par défaut.
