# M-Recherche - Recherche globale

> **Type** : élément du chrome (champ « Rechercher » du bandeau, présent sur **tous les écrans**) ; ce n'est pas un écran à part entière mais une **liste déroulante** de résultats.
> **Persona principal** : tous.
> **Parcours couverts** : [P8 - Rechercher globalement](../Parcours%20utilisateurs/P8%20-%20Rechercher%20globalement.md).

La recherche globale permet de **sauter directement** à un site, un point, un passage ou une **espèce** depuis n'importe quel écran. On la déclenche par le champ **« 🔍 Rechercher »** du bandeau (ou **Ctrl+F**) ; la liste de résultats s'ouvre au fil de la frappe, **groupée par type**.

## Maquette - liste de résultats ouverte

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 560" role="img" aria-label="Maquette M-Recherche - liste de résultats de la recherche globale" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #fafbfc;">
  <style>
    .frame { fill: #ffffff; stroke: #2c3e50; stroke-width: 1.5; }
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb-active { fill: #ffffff; font: 600 13px sans-serif; }
    .crumb-link { fill: #c5cae9; font: 400 13px sans-serif; }
    .search-active { fill: #ffffff; stroke: #ffffff; stroke-width: 2; }
    .search-q { fill: #2c3e50; font: 13px sans-serif; }
    .dim { fill: #eef0f3; }
    .popup { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .grp { font: 700 11px sans-serif; fill: #6a737d; letter-spacing: 0.5px; }
    .res-main { font: 600 14px sans-serif; fill: #2c3e50; }
    .res-detail { font: 12px sans-serif; fill: #6a737d; }
    .res-sel { fill: #e8eefc; }
    .sep { stroke: #eef2f5; stroke-width: 1; }
    .hint { font: 12px sans-serif; fill: #6a737d; }
    .kbd { fill: #f6f8fa; stroke: #c5cae9; stroke-width: 1; }
    .kbd-txt { font: 11px monospace; fill: #2c3e50; }
    .bg-label { font: 13px sans-serif; fill: #9aa0b3; }
  </style>

  <rect x="10" y="10" width="1180" height="540" rx="4" class="frame"/>

  <!-- Bandeau (chrome) avec champ de recherche actif -->
  <rect x="10" y="10" width="1180" height="44" rx="4" class="chrome"/>
  <rect x="10" y="26" width="1180" height="28" class="chrome"/>
  <text x="28" y="38" class="chrometxt">VigieChiro Companion</text>
  <text x="260" y="38" class="crumb-link">Accueil  ›  </text>
  <text x="332" y="38" class="crumb-active">Mes sites</text>
  <rect x="820" y="20" width="340" height="26" rx="13" class="search-active"/>
  <text x="838" y="38" class="search-q">640380</text>
  <text x="1144" y="38" class="search-q" text-anchor="end">⌫</text>

  <!-- Écran de fond (estompé) -->
  <rect x="10" y="54" width="1180" height="496" class="dim"/>
  <text x="600" y="320" class="bg-label" text-anchor="middle">(écran courant, estompé pendant que la liste est ouverte)</text>

  <!-- Liste déroulante de résultats, ancrée sous le champ -->
  <rect x="820" y="56" width="340" height="470" rx="6" class="popup"/>

  <!-- Groupe Sites -->
  <text x="838" y="80" class="grp">SITES</text>
  <rect x="822" y="88" width="336" height="44" class="res-sel"/>
  <text x="838" y="108" class="res-main">Étang de la Tuilière</text>
  <text x="838" y="124" class="res-detail">Carré 640380</text>
  <line x1="822" y1="134" x2="1158" y2="134" class="sep"/>

  <!-- Groupe Points -->
  <text x="838" y="156" class="grp">POINTS</text>
  <text x="838" y="180" class="res-main">640380 / A1</text>
  <text x="838" y="196" class="res-detail">Carré 640380 · près du grand chêne</text>
  <line x1="822" y1="206" x2="1158" y2="206" class="sep"/>

  <!-- Groupe Passages -->
  <text x="838" y="228" class="grp">PASSAGES</text>
  <text x="838" y="252" class="res-main">640380 / A1 · n° 1</text>
  <text x="838" y="268" class="res-detail">Passage 2026 · 2026-06-08</text>
  <text x="838" y="296" class="res-main">640380 / A1 · n° 2</text>
  <text x="838" y="312" class="res-detail">Passage 2026 · 2026-06-22</text>
  <text x="838" y="340" class="res-main">640381 / B2 · n° 1</text>
  <text x="838" y="356" class="res-detail">Passage 2026 · 2026-06-15</text>
  <line x1="822" y1="374" x2="1158" y2="374" class="sep"/>

  <!-- Groupe Espèces -->
  <text x="838" y="396" class="grp">ESPÈCES</text>
  <text x="838" y="420" class="res-main">Pipistrelle commune</text>
  <text x="838" y="436" class="res-detail">Pippip · 638 observations</text>
  <text x="838" y="464" class="res-main">Petit rhinolophe</text>
  <text x="838" y="480" class="res-detail">Rhifer · 80 observations</text>

  <text x="838" y="508" class="res-detail">Résultats limités par type pour rester lisibles.</text>

  <!-- Aide navigation clavier -->
  <rect x="36" y="470" width="20" height="18" rx="3" class="kbd"/><text x="46" y="483" class="kbd-txt" text-anchor="middle">↓</text>
  <text x="64" y="483" class="hint">entrer dans la liste</text>
  <rect x="230" y="470" width="56" height="18" rx="3" class="kbd"/><text x="258" y="483" class="kbd-txt" text-anchor="middle">Entrée</text>
  <text x="294" y="483" class="hint">ouvrir l'élément</text>
  <rect x="430" y="470" width="44" height="18" rx="3" class="kbd"/><text x="452" y="483" class="kbd-txt" text-anchor="middle">Échap</text>
  <text x="482" y="483" class="hint">fermer la liste</text>
</svg>
</div>

### Annotations

- **Champ actif** : la saisie (`640380`) alimente la liste **au fil de la frappe**, **insensible à la casse et aux accents**.
- **Résultats groupés par type** : **Sites** (par n° de carré ou nom), **Points** (par code ou description), **Passages** (par carré, code de point, n° de passage, année ou date) et **Espèces** (par nom ou code, avec le nombre d'observations). Chaque groupe porte un en-tête ; chaque ligne montre un libellé principal et un détail de contexte. Le nombre de résultats par type est **borné** (au plus 8) pour garder la liste lisible.
- **Sélection** : à la souris, ou au clavier (la ligne mise en évidence suit les flèches).

### Interactions clés

| Élément | Action |
|---|---|
| Champ **🔍 Rechercher** (ou **Ctrl+F**) | Ouvre / focalise la recherche depuis n'importe quel écran |
| Saisie | Met à jour la liste (anti-rebond : les frappes rapides sont regroupées) |
| **↓** / **↑** | Parcourt les résultats ; **Entrée** ouvre l'élément (fiche du site ou écran du passage) |
| **Échap** | Ferme la liste (puis vide la recherche) |
| Clic sur un résultat | Ouvre l'écran de l'élément ([M-Site-detail](M-Site-detail.md), [M-Passage](M-Passage.md), ou [M-Analyse](M-Analyse.md) sur l'espèce) |

### Notes pour l'implémentation

- La recherche vit dans le **socle** (`commun`) : le chrome consomme un contrat `RechercheGlobale` sans dépendre d'une fonctionnalité ; l'implémentation agrège les services `sites` (sites, points), `multisite` (passages) et l'inventaire des **espèces observées**.
- La saisie est **anti-rebondie** (frappes regroupées après une courte pause) ; la liste se ferme quand le focus quitte la zone de recherche.
- Chaque résultat est **exposé aux lecteurs d'écran** (libellé accessible) : la recherche se pilote entièrement au clavier.
