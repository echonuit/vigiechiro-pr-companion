# M5 - Panneau de filtre des observations

> **Type** : panneau latéral / popover, déclenché depuis [M4 - Validation](M4-validation.md)
> **Parcours couverts** : [P2 Cycle régulier](../Parcours%20utilisateurs/index.md#p2-cycle-regulier)
> **Stories couvertes** : [E4.S2 Filtrer les observations](../Story%20mapping/index.md#e4s2-filtrer-les-observations-5-pts)

## Wireframe

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 720" role="img" aria-label="Maquette M5 - Panneau de filtre des observations" style="max-width: 420px; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .titlebar { fill: #2c3e50; }
    .titletxt { fill: #ffffff; font: 600 13px sans-serif; }
    .label { font: 13px sans-serif; fill: #2c3e50; }
    .hint { font: 12px sans-serif; fill: #6a737d; }
    .section { font: 600 13px sans-serif; fill: #4a90d9; }
    .btn-primary { fill: #4a90d9; stroke: #2c3e50; stroke-width: 1; }
    .btn-secondary { fill: #ffffff; stroke: #2c3e50; stroke-width: 1; }
    .btn-txt { fill: #ffffff; font: 600 12px sans-serif; }
    .btn-txt-dark { fill: #2c3e50; font: 12px sans-serif; }
    .field { fill: #ffffff; stroke: #6a737d; stroke-width: 1; }
    .panel { fill: #f6f8fa; stroke: #d0d7de; stroke-width: 1; }
  </style>

  <!-- Cadre fenêtre -->
  <rect x="10" y="10" width="380" height="700" rx="4" class="frame"/>

  <!-- Title bar -->
  <rect x="10" y="10" width="380" height="32" rx="4" class="titlebar"/>
  <rect x="10" y="26" width="380" height="16" class="titlebar"/>
  <text x="28" y="31" class="titletxt">🔍 Filtrer les observations</text>
  <text x="370" y="31" class="titletxt" font-size="14" text-anchor="end">✕</text>

  <!-- Section Taxon -->
  <text x="28" y="68" class="section">📊 Par taxon</text>
  <rect x="28" y="76" width="345" height="22" rx="3" class="field"/>
  <text x="36" y="91" class="hint">Filtre rapide…</text>

  <rect x="28" y="100" width="345" height="220" rx="3" class="panel"/>

  <rect x="38" y="110" width="14" height="14" rx="2" fill="#4a90d9" stroke="#2c3e50" stroke-width="1"/>
  <text x="44" y="121" fill="white" font: 11px sans-serif text-anchor="middle">✓</text>
  <text x="60" y="122" class="label">noise</text>
  <text x="350" y="122" class="hint" text-anchor="end">2 102</text>

  <rect x="38" y="132" width="14" height="14" rx="2" fill="#4a90d9" stroke="#2c3e50" stroke-width="1"/>
  <text x="44" y="143" fill="white" font: 11px sans-serif text-anchor="middle">✓</text>
  <text x="60" y="144" class="label">piaf</text>
  <text x="350" y="144" class="hint" text-anchor="end">649</text>

  <rect x="38" y="154" width="14" height="14" rx="2" class="field"/>
  <text x="60" y="166" class="label">Pippip</text>
  <text x="350" y="166" class="hint" text-anchor="end">638</text>

  <rect x="38" y="176" width="14" height="14" rx="2" class="field"/>
  <text x="60" y="188" class="label">Nyclei</text>
  <text x="350" y="188" class="hint" text-anchor="end">139</text>

  <rect x="38" y="198" width="14" height="14" rx="2" class="field"/>
  <text x="60" y="210" class="label">Tadten</text>
  <text x="350" y="210" class="hint" text-anchor="end">89</text>

  <rect x="38" y="220" width="14" height="14" rx="2" class="field"/>
  <text x="60" y="232" class="label">Rhihip</text>
  <text x="350" y="232" class="hint" text-anchor="end">80</text>

  <rect x="38" y="242" width="14" height="14" rx="2" class="field"/>
  <text x="60" y="254" class="label">Tetvir</text>
  <text x="350" y="254" class="hint" text-anchor="end">67</text>

  <rect x="38" y="264" width="14" height="14" rx="2" class="field"/>
  <text x="60" y="276" class="label">Phanan</text>
  <text x="350" y="276" class="hint" text-anchor="end">47</text>

  <text x="60" y="305" class="hint">…</text>

  <!-- Boutons raccourcis -->
  <rect x="28" y="330" width="100" height="22" rx="3" class="btn-secondary"/>
  <text x="78" y="345" class="btn-txt-dark" text-anchor="middle">Tout cocher</text>
  <rect x="135" y="330" width="100" height="22" rx="3" class="btn-secondary"/>
  <text x="185" y="345" class="btn-txt-dark" text-anchor="middle">Tout décocher</text>
  <rect x="28" y="358" width="207" height="22" rx="3" class="btn-secondary"/>
  <text x="131" y="373" class="btn-txt-dark" text-anchor="middle">Chiroptères seulement</text>

  <!-- Section probabilité -->
  <text x="28" y="412" class="section">🎯 Par probabilité Tadarida</text>
  <text x="28" y="432" class="label">Min :</text>
  <line x1="80" y1="430" x2="320" y2="430" stroke="#d0d7de" stroke-width="3"/>
  <line x1="80" y1="430" x2="200" y2="430" stroke="#4a90d9" stroke-width="3"/>
  <circle cx="200" cy="430" r="7" fill="#4a90d9" stroke="#2c3e50" stroke-width="1"/>
  <text x="335" y="435" class="cell" font-weight="600">0.50</text>
  <text x="80" y="448" class="hint" font-size="10">0</text>
  <text x="320" y="448" class="hint" font-size="10" text-anchor="end">1</text>

  <!-- Section statut -->
  <text x="28" y="478" class="section">✅ Par statut de validation</text>
  <circle cx="38" cy="498" r="5" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="50" y="502" class="label">Tous</text>
  <circle cx="38" cy="518" r="5" fill="#fff" stroke="#2c3e50" stroke-width="1.5"/>
  <circle cx="38" cy="518" r="2.5" fill="#2c3e50"/>
  <text x="50" y="522" class="label" font-weight="600">Non passées en revue</text>
  <circle cx="38" cy="538" r="5" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="50" y="542" class="label">Validées</text>
  <circle cx="38" cy="558" r="5" fill="#fff" stroke="#6a737d" stroke-width="1"/>
  <text x="50" y="562" class="label">Corrigées</text>

  <!-- Section plage horaire -->
  <text x="28" y="592" class="section">🕐 Par plage horaire <tspan font-size="11" fill="#6a737d">(SHOULD)</tspan></text>
  <text x="28" y="614" class="label">De</text>
  <rect x="55" y="601" width="70" height="20" rx="3" class="field"/>
  <text x="63" y="615" class="cell">20:00 ▼</text>
  <text x="135" y="614" class="label">à</text>
  <rect x="155" y="601" width="70" height="20" rx="3" class="field"/>
  <text x="163" y="615" class="cell">03:00 ▼</text>

  <!-- Footer -->
  <line x1="28" y1="640" x2="372" y2="640" stroke="#d0d7de" stroke-width="1"/>
  <text x="28" y="660" class="hint" font-weight="600">3 filtres actifs</text>
  <text x="28" y="676" class="label">Affiche : <tspan font-weight="600">1 247 / 4 031 obs</tspan></text>

  <rect x="200" y="660" width="80" height="26" rx="3" class="btn-secondary"/>
  <text x="240" y="678" class="btn-txt-dark" text-anchor="middle">↺ Réinit.</text>
  <rect x="285" y="660" width="88" height="26" rx="3" class="btn-primary"/>
  <text x="329" y="678" class="btn-txt" text-anchor="middle">Appliquer</text>
</svg>
</div>

## Composants

| Composant | Rôle | Données | Notes |
|---|---|---|---|
| Bouton `✕` | Ferme le panneau sans appliquer si l'utilisateur a fait des modifs | — | Demande confirmation si modifs en attente |
| **Filtre par taxon** | Multi-sélection | liste des taxa présents dans la session avec leur effectif | Trié par effectif décroissant |
| Champ recherche taxon | Filtre rapide dans la liste | texte | Utile quand on veut isoler un genre précis |
| Boutons raccourcis | Sélections groupées | `Tout cocher`, `Tout décocher`, `Cocher uniquement chiroptères` | Le 3e exclut `noise` et `piaf` (préset très utilisé) |
| **Filtre par probabilité** | Seuil minimum | slider 0-1 avec affichage de la valeur | Utile pour tri par confiance |
| **Filtre par statut** | Radio | `Tous / Non passées / Validées / Corrigées` | Statut côté utilisateur (cf. M4) |
| Filtre par plage horaire (SHOULD - non MVP) | Borne start/end | menus heures | Dérivé de la plage temporelle de chaque obs (croisement avec horodatage du WAV) - peut être différé |
| Compteur en pied | Bilan vivant | `nb filtres actifs`, `obs affichées / obs totales` | Mis à jour à chaque modification |
| Bouton `Appliquer` | Persiste les filtres et ferme | — | Raccourci `Entrée` |
| Bouton `↺ Réinitialiser` | Efface tous les filtres | — | Confirmation si plus de 3 filtres actifs |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Toggle d'un taxon | Compteur en pied recalculé immédiatement |
| Mouvement du slider probabilité | Compteur recalculé en temps réel |
| Saisie dans le champ recherche taxon | Filtre la liste affichée sans modifier les sélections |
| Clic raccourci `Cocher uniquement chiroptères` | Décoche `noise` et `piaf`, coche tout le reste |
| Clic `Appliquer` | Ferme le panneau, refilter la TableView de M4, scroll en haut |
| Clic `Réinitialiser` | Tous les taxa cochés, slider à 0, statut `Tous`, plage horaire vide |
| Esc | Annule et ferme |

## États

| État | Apparence |
|---|---|
| Aucun filtre actif | Pas d'indicateur sur le bouton Filtres de M4 |
| Filtres actifs | Badge sur bouton `[🔍 Filtres ▼ (3)]` indiquant le nombre |
| Filtre incohérent (0 obs résultantes) | Compteur en pied : `0 / 4031 obs`, fond du compteur en orange, bouton `Appliquer` reste actif (l'utilisateur peut décider d'avoir 0 obs) |

## Persistance

Les filtres actifs sont **mémorisés par session** : si on revient sur M4 d'une autre session puis on rouvre celle-ci, les derniers filtres appliqués sont restaurés. Cela évite à Karim de recliquer 5 fois après chaque navigation.

## À ne PAS faire

- Pas de panneau modal qui bloque la fenêtre principale : laisser visible la TableView pour permettre la rétroaction visuelle pendant la modification des filtres.
- Pas de séparer les filtres dans plusieurs onglets : tout doit tenir sur un panneau scrollable d'un coup d'œil.
- Pas de pré-cocher `Cocher uniquement chiroptères` au premier lancement : laisser le choix à l'utilisateur (Marie veut peut-être justement écouter un piaf douteux).
