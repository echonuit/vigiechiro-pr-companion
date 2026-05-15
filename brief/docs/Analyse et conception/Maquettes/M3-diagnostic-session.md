# M3 - Diagnostic de session

> **Type** : onglet de [M2 - Détail session](M2-detail-session.md)
> **Parcours couverts** : [P5 Suivi du matériel](../Parcours%20utilisateurs/index.md#p5-suivi-du-materiel)
> **Stories couvertes** : [E6.S1 Courbe T°/H](../Story%20mapping/index.md#e6s1-visualiser-la-courbe-th-dune-session-5-pts), [E6.S2 Niveau de batterie](../Story%20mapping/index.md#e6s2-voir-le-niveau-de-batterie-debutfin-2-pts), [E6.S3 Évènements anormaux](../Story%20mapping/index.md#e6s3-lister-les-evenements-anormaux-du-logpr-3-pts) (COULD)

## Wireframe

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 880 640" role="img" aria-label="Maquette M3 - Diagnostic de session" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 14px sans-serif; }
    .label { font: 13px sans-serif; fill: #2c3e50; }
    .hint { font: 12px sans-serif; fill: #6a737d; }
    .h2 { font: 600 15px sans-serif; fill: #2c3e50; }
    .section { font: 600 13px sans-serif; fill: #4a90d9; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt-dark { fill: #2c3e50; font: 12px sans-serif; }
    .tab-active { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .tab-inactive { fill: #eef2f5; stroke: #6a737d; stroke-width: 1; }
    .panel { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
    .ok { fill: #27ae60; }
    .warn { fill: #f1c40f; }
    .crit { fill: #c0392b; }
    .axis { stroke: #6a737d; stroke-width: 0.7; fill: none; }
  </style>

  <!-- Cadre fenêtre -->
  <rect x="10" y="10" width="860" height="620" rx="4" class="frame"/>

  <!-- Title bar -->
  <rect x="10" y="10" width="860" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="860" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt" font-size="13">← Retour au journal</text>
  <text x="440" y="31" class="titletxt" font-size="13" text-anchor="middle">Session du 22/04/2026 — PR n° 1925492</text>

  <!-- Onglets -->
  <rect x="28" y="60" width="120" height="30" rx="3" class="tab-inactive"/>
  <text x="88" y="80" class="hint" text-anchor="middle">Métadonnées</text>
  <rect x="148" y="60" width="120" height="30" rx="3" class="tab-active"/>
  <text x="208" y="80" class="label" text-anchor="middle" font-weight="600">Diagnostic</text>

  <!-- Section Batteries -->
  <text x="40" y="115" class="section">🔋 Batteries</text>
  <rect x="60" y="125" width="780" height="60" rx="3" class="panel"/>
  <text x="80" y="148" class="label">Interne</text>
  <text x="180" y="148" class="hint">début</text>
  <text x="225" y="148" class="cell" font-family="monospace">4.1 V (90 %)</text>
  <text x="350" y="148" class="hint">→</text>
  <text x="380" y="148" class="hint">fin</text>
  <text x="415" y="148" class="cell" font-family="monospace">4.0 V (90 %)</text>
  <circle cx="800" cy="143" r="6" class="ok"/>
  <text x="80" y="170" class="label">Externe</text>
  <text x="180" y="170" class="hint">début</text>
  <text x="225" y="170" class="cell" font-family="monospace">0.0 V (0 %)</text>
  <text x="350" y="170" class="hint">→</text>
  <text x="380" y="170" class="hint">fin</text>
  <text x="415" y="170" class="cell" font-family="monospace">0.0 V (0 %)</text>
  <circle cx="800" cy="165" r="6" fill="#d0d7de"/>
  <text x="815" y="170" class="hint">non utilisée</text>

  <!-- Graphes T° et H -->
  <text x="40" y="215" class="section">🌡 Température sur la nuit</text>
  <text x="460" y="215" class="section">☔ Hygrométrie sur la nuit</text>

  <rect x="40" y="225" width="400" height="120" rx="3" class="panel"/>
  <polyline class="axis" points="60,335 60,240 430,240"/>
  <polyline points="60,300 90,290 120,310 150,295 180,275 210,265 240,278 270,285 300,272 330,260 360,255 390,265 420,280" stroke="#e8a838" stroke-width="2" fill="none"/>
  <text x="50" y="338" class="hint" font-size="10">20:25</text>
  <text x="395" y="338" class="hint" font-size="10">07:48</text>
  <text x="240" y="280" class="hint" font-size="11" font-style="italic">19 °C</text>
  <text x="60" y="358" class="hint" font-size="11">min 17.4 °C • moy 18.6 °C • max 19.4 °C</text>

  <rect x="460" y="225" width="380" height="120" rx="3" class="panel"/>
  <polyline class="axis" points="478,335 478,240 830,240"/>
  <polyline points="478,290 510,275 540,285 570,295 600,310 630,300 660,290 690,295 720,300 750,290 780,285 810,295" stroke="#4a90d9" stroke-width="2" fill="none"/>
  <text x="468" y="338" class="hint" font-size="10">20:25</text>
  <text x="795" y="338" class="hint" font-size="10">07:48</text>
  <text x="650" y="285" class="hint" font-size="11" font-style="italic">60 %</text>
  <text x="478" y="358" class="hint" font-size="11">min 58 % • moy 60 % • max 64 %</text>

  <!-- Évènements anormaux -->
  <text x="40" y="400" class="section">⚠ Évènements anormaux extraits du LogPR</text>

  <rect x="60" y="412" width="780" height="80" rx="3" class="panel"/>
  <text x="78" y="432" class="cell" font-family="monospace">22/04 23:14:02</text>
  <text x="200" y="432" class="cell">Réveil non programmé</text>
  <circle cx="800" cy="427" r="5" class="warn"/>
  <text x="78" y="453" class="cell" font-family="monospace">23/04 02:47:31</text>
  <text x="200" y="453" class="cell">Erreur écriture SD (1 fichier perdu)</text>
  <circle cx="800" cy="448" r="5" class="warn"/>
  <text x="78" y="474" class="cell" font-family="monospace">23/04 04:12:18</text>
  <text x="200" y="474" class="cell">Redémarrage suite à anomalie</text>
  <circle cx="800" cy="469" r="5" class="crit"/>

  <text x="60" y="510" class="hint">État alternatif si nuit sans anomalie : « ✅ Aucun évènement anormal détecté sur cette nuit. »</text>

  <!-- Bouton export -->
  <rect x="28" y="580" width="220" height="28" rx="3" class="btn-secondary"/>
  <text x="138" y="598" class="btn-txt-dark" text-anchor="middle">📥 Exporter le diagnostic en CSV</text>
</svg>
</div>

## Composants

| Composant | Rôle | Données affichées | Notes |
|---|---|---|---|
| Bloc Batteries (E6.S2) | État début/fin de chaque batterie | tension V + pourcentage + indicateur ✅/⚠️/🔴 | Lu depuis les lignes `Bat. Interne X.XV (Y%)` du LogPR ; seuils : ≥80 % ✅, 50-80 % ⚠️, <50 % 🔴 |
| Graphique Température (E6.S1) | Évolution T° sur la nuit | courbe simple | Une mesure toutes les 600 s (cf. THLog.csv) |
| Graphique Hygrométrie (E6.S1) | Évolution humidité sur la nuit | courbe simple | Idem |
| Stats T° / H (sous chaque graphe) | Min, moyenne, max | calcul simple | |
| Bloc Évènements anormaux (E6.S3, COULD) | Détection des anomalies dans le LogPR | tableau date / description / sévérité | Si rien : message « Aucun évènement anormal détecté ✅ » |
| Bouton `📥 Exporter le diagnostic en CSV` | Sortie des graphes + anomalies en CSV | — | Optionnel - utile pour Karim qui prépare un rapport client |

## Catégories d'évènements anormaux à détecter

| Pattern dans le LogPR | Catégorie | Sévérité |
|---|---|---|
| `Wakeup by ALARM... Cpt N` (N > 1) ou hors plage horaire programmée | Réveil non programmé | ⚠️ |
| `Erreur écriture SD` ou `Carte SD pleine` | Erreur SD | ⚠️ ou 🔴 selon le contexte |
| `Redémarrage` non précédé d'une mise en veille | Redémarrage anormal | 🔴 |
| `Bat. Interne` < 30 % en cours de nuit | Batterie critique en cours de capture | 🔴 |
| Mise en veille avant l'horaire programmé | Mise en veille prématurée | ⚠️ |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Survol d'un point sur les graphes T°/H | Tooltip affichant la valeur précise + horodatage |
| Clic sur une ligne d'évènement anormal | Ouvre une popup avec le bloc complet du LogPR autour de l'évènement (3 lignes avant, 3 après) |
| Clic `📥 Exporter le diagnostic en CSV` | Dialogue de sauvegarde, format `<session>_diagnostic.csv` |
| Clic onglet `Métadonnées` | Retour à [M2](M2-detail-session.md) |

## États

| État | Apparence |
|---|---|
| Pas de THLog.csv (sonde absente ou défaillante) | Graphes T°/H remplacés par : « Pas de données environnementales pour cette session (sonde absente ou défaillante). » |
| Pas de batterie externe | Ligne « Externe » du bloc Batteries grisée avec mention « non utilisée » |

## À ne PAS faire

- Pas de séries temporelles compliquées (zoom, brush, sélection) - on reste sur des graphes simples lisibles d'un coup d'œil.
- Pas de mélanger T° et H sur le même axe Y (sauf si vous proposez un design BACI propre, mais à arbitrer en équipe).
- Pas de masquer le bloc « Aucun évènement anormal » : un retour positif explicite rassure l'utilisateur (« je n'ai pas oublié de regarder, c'est juste qu'il n'y a rien »).
