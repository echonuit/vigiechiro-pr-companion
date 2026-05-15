# M6 - Modale d'import d'une session

> **Type** : modale plein écran ou centrée, déclenchée depuis [M1 - Accueil](M1-accueil.md)
> **Parcours couverts** : [P1 Première utilisation](../Parcours%20utilisateurs/index.md#p1-premiere-utilisation), [P2 Cycle régulier](../Parcours%20utilisateurs/index.md#p2-cycle-regulier)
> **Stories couvertes** : [E2.S1 Importer un dossier](../Story%20mapping/index.md#e2s1-importer-un-dossier-de-session-8-pts), [E2.S5 Reprendre import interrompu](../Story%20mapping/index.md#e2s5-reprendre-un-import-interrompu-3-pts) (COULD)

> Conventions communes des 4 SVG ci-dessous : largeur 640, titre sombre, boutons primaires bleus.

## Étape 1 - Sélection du dossier

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 420" role="img" aria-label="M6 étape 1 - sélection du dossier" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
  <rect x="10" y="10" width="620" height="400" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📥 Importer une nuit de capture</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label">Sélectionnez le dossier qui contient les fichiers de votre nuit de capture (WAV, LogPR, THLog).</text>

  <rect x="80" y="90" width="480" height="170" rx="6" class="dropzone"/>
  <text x="320" y="155" font-size="36" text-anchor="middle">📂</text>
  <text x="320" y="190" class="label" text-anchor="middle" font-weight="600">Glisser-déposer un dossier ici</text>
  <text x="320" y="210" class="hint" text-anchor="middle">ou</text>
  <rect x="260" y="222" width="120" height="28" rx="3" class="btn-secondary"/>
  <text x="320" y="240" class="btn-txt-dark" text-anchor="middle">Parcourir…</text>

  <text x="28" y="296" class="hint" font-weight="600">ℹ Structure attendue :</text>
  <text x="40" y="316" class="hint">• un fichier <tspan font-family="monospace">LogPR&lt;n&gt;.txt</tspan></text>
  <text x="40" y="334" class="hint">• un fichier <tspan font-family="monospace">*_THLog.csv</tspan></text>
  <text x="40" y="352" class="hint">• un dossier <tspan font-family="monospace">wav/</tspan> (ou WAV à la racine)</text>

  <rect x="540" y="372" width="80" height="26" rx="3" class="btn-secondary"/>
  <text x="580" y="389" class="btn-txt-dark" text-anchor="middle">Annuler</text>
</svg>
</div>

## Étape 2 - Vérification (après sélection valide)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 480" role="img" aria-label="M6 étape 2 - vérification" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
    .section { font:600 13px sans-serif; fill:#4a90d9; }
    .ok { fill:#27ae60; }
  </style>
  <rect x="10" y="10" width="620" height="460" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📥 Importer une nuit de capture</text>
  <text x="610" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <text x="28" y="68" class="label" font-weight="600" fill="#27ae60">✅ Dossier reconnu :</text>
  <text x="180" y="68" class="cell" font-family="monospace" font-size="12">/home/marie/PR_avril/22-04</text>

  <text x="28" y="96" class="section">Détecté</text>
  <text x="40" y="116" class="cell">• LogPR1925492.txt</text>
  <text x="240" y="116" class="cell" fill="#27ae60">✅</text>
  <text x="40" y="134" class="cell">• PaRecPR1925492_THLog.csv</text>
  <text x="240" y="134" class="cell" fill="#27ae60">✅</text>
  <text x="40" y="152" class="cell">• wav/ — 1 572 fichiers .wav (5.0 Go)</text>
  <text x="280" y="152" class="cell" fill="#27ae60">✅</text>

  <text x="28" y="184" class="section">Métadonnées extraites</text>
  <text x="40" y="204" class="cell">• PR n°</text>
  <text x="180" y="204" class="cell" font-family="monospace">1925492</text>
  <text x="40" y="222" class="cell">• Date de capture</text>
  <text x="180" y="222" class="cell" font-family="monospace">22/04/2026 20:25 → 23/04/2026 07:48</text>
  <text x="40" y="240" class="cell">• Durée</text>
  <text x="180" y="240" class="cell" font-family="monospace">11 h 22 min</text>
  <text x="40" y="258" class="cell">• Paramètres acquis.</text>
  <text x="180" y="258" class="cell" font-family="monospace">Fe 384 kHz • 8-120 kHz • Gain 0 dB</text>
  <text x="40" y="276" class="cell">• Carré (depuis nom)</text>
  <text x="180" y="276" class="cell" font-family="monospace">640380</text>

  <text x="28" y="308" class="hint">Cette session sera enregistrée comme : <tspan font-family="monospace" fill="#2c3e50">Session 2026-04-22 PR1925492</tspan></text>

  <text x="28" y="344" class="section">Tag (optionnel)</text>
  <rect x="28" y="352" width="380" height="22" rx="3" class="field"/>
  <text x="36" y="367" class="cell">Carré 640380 — Pass 2</text>
  <text x="400" y="367" class="hint">▼</text>

  <rect x="380" y="430" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="420" y="448" class="btn-txt-dark" text-anchor="middle">Annuler</text>
  <rect x="470" y="430" width="150" height="28" rx="3" class="btn-primary"/>
  <text x="545" y="448" class="btn-txt" text-anchor="middle">Importer cette session</text>
</svg>
</div>

## Étape 3 - Progression

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 280" role="img" aria-label="M6 étape 3 - progression" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill:#ffffff; stroke:#2c3e50; stroke-width:1.5; }
    .titlebar { fill:#2c3e50; }
    .titletxt { fill:#ffffff; font:600 13px sans-serif; }
    .label { font:13px sans-serif; fill:#2c3e50; }
    .hint { font:12px sans-serif; fill:#6a737d; }
    .btn-secondary { fill:#ffffff; stroke:#2c3e50; stroke-width:1; }
    .btn-txt-dark { fill:#2c3e50; font:12px sans-serif; }
    .progress-bg { fill:#eef2f5; stroke:#d0d7de; stroke-width:0.5; }
    .progress-fill { fill:#4a90d9; }
  </style>
  <rect x="10" y="10" width="620" height="260" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">📥 Import en cours</text>

  <text x="320" y="80" class="label" text-anchor="middle" font-weight="600">Session du 22/04/2026 — PR n° 1925492</text>

  <rect x="60" y="105" width="520" height="14" rx="4" class="progress-bg"/>
  <rect x="60" y="105" width="245" height="14" rx="4" class="progress-fill"/>
  <text x="320" y="142" class="label" text-anchor="middle" font-weight="600">47 %</text>

  <text x="60" y="178" class="hint">Étape :</text>
  <text x="120" y="178" class="label">Indexation des WAV</text>
  <text x="60" y="198" class="hint">742 / 1 572 fichiers traités</text>
  <text x="60" y="218" class="hint">Temps écoulé : <tspan fill="#2c3e50">0:38</tspan>   |   Restant estimé : <tspan fill="#2c3e50">0:42</tspan></text>

  <rect x="380" y="234" width="170" height="24" rx="3" class="btn-secondary"/>
  <text x="465" y="250" class="btn-txt-dark" text-anchor="middle">Importer en arrière-plan</text>
  <rect x="556" y="234" width="64" height="24" rx="3" fill="#ffffff" stroke="#c0392b" stroke-width="1"/>
  <text x="588" y="250" font="12px sans-serif" fill="#c0392b" text-anchor="middle">Annuler</text>
</svg>
</div>

## Étape 4 - Récapitulatif (succès)

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 320" role="img" aria-label="M6 étape 4 - récapitulatif" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
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
  </style>
  <rect x="10" y="10" width="620" height="300" rx="4" class="frame"/>
  <rect x="10" y="10" width="620" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="620" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">✅ Import terminé</text>

  <text x="28" y="80" class="label" font-weight="600">Session du 22/04/2026 — PR n° 1925492 importée avec succès.</text>

  <text x="40" y="115" class="label">• 1 572 WAV indexés</text>
  <text x="40" y="135" class="label">• 70 mesures T°/H</text>
  <text x="40" y="155" class="label">• 0 anomalie détectée dans le LogPR</text>

  <text x="28" y="200" class="hint">Prochaine étape : récupérez le CSV d'observations Tadarida sur</text>
  <text x="28" y="218" class="hint">VigieChiro et cliquez sur « Charger CSV Tadarida » dans la fiche</text>
  <text x="28" y="236" class="hint">de la session.</text>

  <rect x="380" y="266" width="140" height="28" rx="3" class="btn-primary"/>
  <text x="450" y="284" class="btn-txt" text-anchor="middle">Voir la session</text>
  <rect x="540" y="266" width="80" height="28" rx="3" class="btn-secondary"/>
  <text x="580" y="284" class="btn-txt-dark" text-anchor="middle">Fermer</text>
</svg>
</div>

## Composants & comportements

| Étape | Composant clé | Comportement |
|---|---|---|
| 1 | Zone drag-and-drop | Accepte un dossier (pas un fichier seul). Visuel actif au survol. |
| 1 | Bouton `Parcourir...` | Ouvre le sélecteur de dossier OS natif |
| 1 | Encart `Structure attendue` | Pédagogique - aide Marie à savoir ce qu'elle doit fournir |
| 2 | Liste des fichiers détectés | ✅ vert si trouvé, ❌ rouge si manquant, ⚠️ orange si incertain |
| 2 | Bloc Métadonnées | Affiche ce qui a été extrait. Permet de détecter une erreur de dossier avant import |
| 2 | Champ Tag | Pré-rempli si possible depuis le nom de fichier (ex. `Car640380` → `Carré 640380`) |
| 3 | Barre de progression | Détaillée : nb fichiers / total, étape en cours, temps estimé |
| 3 | `Importer en arrière-plan` | Réduit la modale, l'import continue, M1 affiche `⏳ Import en cours` dans la barre de statut |
| 3 | `Annuler l'import` | Confirmation requise. Marque la session en `❌ Import incomplet` (pour E2.S5) |
| 4 | Bouton `Voir la session` | Ouvre [M2](M2-detail-session.md) sur la session importée |

## Erreurs possibles

| Erreur | Affichage |
|---|---|
| Aucun LogPR détecté | Étape 1 : « ❌ Ce dossier ne contient pas de fichier `LogPR*.txt`. Êtes-vous sûr de la structure ? » + lien vers l'aide |
| Aucun WAV trouvé | Étape 1 : « ❌ Aucun fichier `.wav` trouvé dans ce dossier ni dans `wav/`. » |
| Session déjà importée | Étape 2 : « ⚠️ Une session avec le même PR n° et la même date de capture existe déjà. [Annuler] [Importer en double] [Voir l'existante] » |
| Erreur disque pendant l'import | Étape 3 : « ❌ Erreur d'écriture en base à 47 %. La session est marquée « Import incomplet », vous pourrez réessayer. » |

## Reprise d'un import interrompu (E2.S5, COULD)

Si une session est marquée `❌ Import incomplet` dans M1, un clic dessus propose une modale réduite :

```
┌──────────────────────────────────────────────────────────────┐
│  ⚠️ Import incomplet                                          │
├──────────────────────────────────────────────────────────────┤
│  L'import de la session du 22/04/2026 (PR 1925492) a été     │
│  interrompu à 47 %.                                          │
│                                                              │
│  Que voulez-vous faire ?                                     │
│   ( ) Reprendre l'import là où il s'est arrêté               │
│   (●) Recommencer l'import depuis zéro                       │
│   ( ) Supprimer cette session incomplète                     │
│                                                              │
│                                  [Annuler]   [Confirmer]     │
└──────────────────────────────────────────────────────────────┘
```

## À ne PAS faire

- Pas de modale qui se bloque pendant l'import : autoriser explicitement `Importer en arrière-plan`.
- Pas de barre de progression sans information textuelle : Marie veut savoir ce qui se passe (« Indexation des WAV », « Lecture du LogPR »…).
- Pas d'auto-sélection magique du dossier (par scan du système) : c'est intrusif et fragile.
