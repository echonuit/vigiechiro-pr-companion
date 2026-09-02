# M-Synthese - Synthèse d'une nuit

> **Type** : écran **« Synthèse de la nuit »** d'un passage (atteint depuis [M-Passage](M-Passage.md) pour un passage dont les résultats d'identification ont été importés).
> **Persona principal** : [Marie](../Personas/Marie.md) (lire ce que sa nuit contient) et [Karim](../Personas/Karim.md) (replacer une nuit dans un contexte régional).
> **Parcours couverts** : prolonge [P7 - Valider les résultats Tadarida](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20resultats%20Tadarida.md) et [P11 - Inventaire des espèces détectées](../Parcours%20utilisateurs/P11%20-%20Inventaire%20des%20especes%20detectees.md).
> **Issue** : #2351 (chantier #2348, lot 1).

L'écran répond à une question que l'inventaire ne traite pas : **718 contacts de Pipistrelle de Kuhl, est-ce beaucoup ?** Il présente, pour la nuit courante, un tableau par espèce (contacts, fichiers distincts, groupe taxonomique) et une colonne **Activité** qui replace ce comptage dans un **référentiel** de saison, de région et de milieu, sous forme de quatre classes : Faible, Moyenne, Forte, Très forte.

> **Une lecture, pas un verdict** : les quantiles retenus sont affichés à côté de la classe, de sorte que l'utilisateur voit *pourquoi* une valeur est dite « Forte » et peut en discuter. Une mise en garde permanente rappelle qu'une classe d'activité n'est pas un niveau d'enjeu de conservation et que les contacts ne se comparent pas d'une espèce à l'autre.

## Maquette principale - nuit avec référentiel disponible

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 620" role="img" aria-label="Maquette M-Synthese - synthese d'une nuit avec niveaux d'activite de reference" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagesub { font: 13px sans-serif; fill: #4a6785; }
    .section-title { font: 600 15px sans-serif; fill: #2c3e50; }
    .ctrl { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .ctrl-txt { font: 12px sans-serif; fill: #2c3e50; }
    .ctrl-pri { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .ctrl-pri-txt { font: 600 12px sans-serif; fill: #ffffff; }
    .list-frame { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .list-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .list-row-alt { fill: #f6f8fa; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-b { font: 600 12px sans-serif; fill: #2c3e50; }
    .cell-num { font: 12px sans-serif; fill: #2c3e50; text-anchor: end; }
    .cell-sub { font: 11px sans-serif; fill: #6a737d; }
    .pill-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .p-tresforte { fill: #7b2d5e; }
    .p-forte { fill: #b9770e; }
    .p-moyenne { fill: #2c6fad; }
    .p-faible { fill: #6a737d; }
    .p-enjeu { fill: #6c3483; }
    .warn-box { fill: #f6f8fa; stroke: #c4ccd4; stroke-width: 1; }
    .warn-txt { font: 11px sans-serif; fill: #4a6785; }
    .statusbar { fill: #eceff5; stroke: #d0d7de; stroke-width: 1; }
    .status-txt { font: 12px sans-serif; fill: #4a6785; }
  </style>

  <rect x="0" y="0" width="1000" height="620" fill="#f7f9fb"/>
  <rect x="0" y="0" width="1000" height="44" class="chrome"/>
  <text x="20" y="28" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="28" class="crumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="600" y="28" class="crumb-curr">Synthèse</text>
  <rect x="780" y="12" width="200" height="22" rx="11" class="search"/>
  <text x="794" y="28" class="search-txt">Rechercher</text>

  <!-- Barre de contexte et de contrôles -->
  <text x="30" y="72" class="pagesub">Nuit du 2026-06-22 · 4 448 contacts détectés · 19 espèces de chiroptères</text>
  <rect x="30" y="86" width="14" height="14" rx="2" class="ctrl"/>
  <text x="52" y="98" class="ctrl-txt">Identifications validées seulement</text>
  <text x="700" y="98" class="ctrl-txt" text-anchor="end">Milieu :</text>
  <rect x="710" y="84" width="120" height="22" rx="3" class="ctrl"/>
  <text x="720" y="99" class="ctrl-txt">Forêt          ▾</text>
  <rect x="845" y="84" width="125" height="22" rx="3" class="ctrl-pri"/>
  <text x="907" y="99" class="ctrl-pri-txt" text-anchor="middle">Exporter CSV…</text>

  <!-- Répartition par groupe -->
  <text x="30" y="130" class="cell-sub">Par groupe : Chiroptères 939 · Orthoptères 2 708 · Oiseaux 3 · Bruit 798</text>

  <!-- Tableau -->
  <rect x="30" y="144" width="940" height="316" class="list-frame"/>
  <rect x="30" y="144" width="940" height="26" class="list-head"/>
  <text x="42" y="161" class="head-txt">Espèce</text>
  <text x="300" y="161" class="head-txt">Groupe</text>
  <text x="470" y="161" class="head-txt" text-anchor="end">Contacts</text>
  <text x="560" y="161" class="head-txt" text-anchor="end">Fichiers</text>
  <text x="600" y="161" class="head-txt">Activité (référentiel été · Nouvelle-Aquitaine · forêt)</text>

  <!-- ligne 1 -->
  <text x="42" y="189" class="cell-b">Pipistrelle de Kuhl</text><text x="180" y="189" class="cell-sub">Pipkuh</text>
  <text x="300" y="189" class="cell">Chiroptères</text>
  <text x="470" y="189" class="cell-num">718</text><text x="560" y="189" class="cell-num">359</text>
  <rect x="600" y="178" width="58" height="16" rx="8" class="p-forte"/><text x="629" y="190" class="pill-txt">Forte</text>
  <text x="668" y="189" class="cell-sub">Q75 = 261 · Q98 = 1804</text>

  <!-- ligne 2 -->
  <rect x="31" y="199" width="938" height="30" class="list-row-alt"/>
  <text x="42" y="219" class="cell-b">Pipistrelle commune</text><text x="190" y="219" class="cell-sub">Pippip</text>
  <text x="300" y="219" class="cell">Chiroptères</text>
  <text x="470" y="219" class="cell-num">302</text><text x="560" y="219" class="cell-num">296</text>
  <rect x="600" y="208" width="70" height="16" rx="8" class="p-moyenne"/><text x="635" y="220" class="pill-txt">Moyenne</text>
  <text x="680" y="219" class="cell-sub">Q25 = 96 · Q75 = 640</text>

  <!-- ligne 3 -->
  <text x="42" y="249" class="cell-b">Noctule de Leisler</text><text x="180" y="249" class="cell-sub">Nyclei</text>
  <text x="300" y="249" class="cell">Chiroptères</text>
  <text x="470" y="249" class="cell-num">168</text><text x="560" y="249" class="cell-num">168</text>
  <rect x="600" y="238" width="80" height="16" rx="8" class="p-tresforte"/><text x="640" y="250" class="pill-txt">Très forte</text>
  <text x="690" y="249" class="cell-sub">Q98 = 112</text>

  <!-- ligne 4 : espece a enjeu -->
  <rect x="31" y="259" width="938" height="30" class="list-row-alt"/>
  <text x="42" y="279" class="cell-b">Barbastelle d'Europe</text><text x="190" y="279" class="cell-sub">Barbar</text>
  <rect x="238" y="268" width="52" height="16" rx="8" class="p-enjeu"/><text x="264" y="280" class="pill-txt">à enjeu</text>
  <text x="300" y="279" class="cell">Chiroptères</text>
  <text x="470" y="279" class="cell-num">13</text><text x="560" y="279" class="cell-num">13</text>
  <rect x="600" y="268" width="70" height="16" rx="8" class="p-moyenne"/><text x="635" y="280" class="pill-txt">Moyenne</text>
  <text x="680" y="279" class="cell-sub">(indicatif : 14 occurrences dans le référentiel)</text>

  <!-- ligne 5 -->
  <text x="42" y="309" class="cell-b">Oreillard sp.</text><text x="140" y="309" class="cell-sub">Plesp</text>
  <text x="300" y="309" class="cell">Chiroptères</text>
  <text x="470" y="309" class="cell-num">9</text><text x="560" y="309" class="cell-num">9</text>
  <rect x="600" y="298" width="58" height="16" rx="8" class="p-faible"/><text x="629" y="310" class="pill-txt">Faible</text>
  <text x="668" y="309" class="cell-sub">Q25 = 24</text>

  <!-- ligne 6 : hors referentiel -->
  <rect x="31" y="319" width="938" height="30" class="list-row-alt"/>
  <text x="42" y="339" class="cell-b">Leptophyes punctatissima</text><text x="230" y="339" class="cell-sub">Leppun</text>
  <text x="300" y="339" class="cell">Orthoptères</text>
  <text x="470" y="339" class="cell-num">1 108</text><text x="560" y="339" class="cell-num">1 108</text>
  <text x="600" y="339" class="cell-sub">non couvert par le référentiel</text>

  <!-- ligne totale -->
  <line x1="31" y1="418" x2="969" y2="418" stroke="#c4ccd4" stroke-width="1"/>
  <text x="42" y="439" class="cell-b">TOTAL (chiroptères)</text>
  <text x="470" y="439" class="cell-num">939</text><text x="560" y="439" class="cell-num">912</text>

  <!-- Mise en garde permanente -->
  <rect x="30" y="474" width="940" height="66" rx="4" class="warn-box"/>
  <text x="44" y="494" class="warn-txt">Lecture : l'unité est le contact par nuit. Ne comparez pas les contacts entre espèces, la détectabilité varie fortement de l'une à l'autre.</text>
  <text x="44" y="512" class="warn-txt">Une classe d'activité n'est pas un niveau d'enjeu de conservation. Valable sous réserve du protocole (matériel conforme, micro à moins de 6 m, métropole).</text>
  <text x="44" y="530" class="warn-txt">Source du référentiel : à citer (nom, version, date) - cf. décision du chantier #2348.</text>

  <!-- Barre de statut -->
  <rect x="0" y="600" width="1000" height="20" class="statusbar"/>
  <text x="12" y="614" class="status-txt">Carré 640380 · A1 · N° 2</text>
  <text x="500" y="614" class="status-txt" text-anchor="middle">4 448 contacts · 1 identifiée validée</text>
  <text x="988" y="614" class="status-txt" text-anchor="end">Référentiel : été · Nouvelle-Aquitaine · forêt</text>
</svg>
</div>

### Annotations

- **Fil d'Ariane et retour** : portés par le **chrome**, comme sur toutes les vues secondaires d'un passage. Emplacement affiché : `Accueil › Mes sites › Carré N › Passage N° X › Synthèse`.
- **Ligne de contexte** (`lblContexte`) : date de la nuit, nombre total de contacts détectés et richesse en chiroptères. C'est le cadre de lecture du tableau, pas un titre d'écran (celui-ci serait redondant avec le fil d'Ariane).
- **Bascule « Identifications validées seulement »** (`chkValideesSeulement`) : recalcule **l'ensemble** du tableau, colonne Activité comprise. Deux lectures cohabitent volontairement : ce que la machine propose, et ce que l'observateur a confirmé.
- **Sélecteur de milieu** (`cbMilieu`) : forêt, agricole, urbain, rivière et combinaisons, plus l'entrée « national » qui est la **valeur par défaut**. Le milieu ne se devine pas depuis les données dont dispose l'application : il reste un choix explicite. La saison, elle, se déduit de la date du passage, et la région du numéro de carré.
- **Tableau** (`tableSynthese`) : une ligne par espèce retenue, triée par nombre de contacts décroissant. Le taxon retenu privilégie l'identification de l'observateur sur la proposition automatique. `Fichiers` compte les **séquences distinctes**, qui peuvent être moins nombreuses que les contacts.
- **Colonne Activité** (`colActivite`) : quatre classes bornées par les quantiles du référentiel, `Faible < Q25 ≤ Moyenne < Q75 ≤ Forte < Q98 ≤ Très forte`. Les **quantiles retenus sont affichés à côté de la classe** : une classe seule est un verdict, une classe accompagnée de ses bornes est une lecture contestable, donc scientifique.
- **Mention « (indicatif) »** : apparaît quand la déclinaison retenue repose sur trop peu d'occurrences. La règle de repli remonte de milieu à région puis à national et retient la **première déclinaison fiable**, pas la plus fine : un seuil précis mais peu fiable produit une classe plus fausse, pas plus juste.
- **Espèces hors référentiel** : orthoptères, oiseaux et bruit affichent explicitement « non couvert par le référentiel », jamais une cellule vide qui se lirait comme une absence de données.
- **Repère « à enjeu »** (`badgeEnjeu`, issue #2353) : porté par la ligne d'espèce, indépendant de la classe d'activité. Les deux lectures ne se substituent pas l'une à l'autre : une espèce à enjeu peut être en activité faible, et c'est précisément une information.
- **Bloc de mise en garde** (`lblAvertissement`) : **permanent**, jamais repliable, et **recopié dans le CSV exporté** avec l'en-tête de contexte. Si l'avertissement ne voyage pas avec la donnée, il ne sert à rien.
- **Barre de statut** : contexte du passage à gauche, volumétrie au centre, référentiel effectivement employé à droite.

## Variante - référentiel indisponible

Quand aucun référentiel n'est chargé (ressource absente, ou fonctionnalité désactivée), la colonne Activité **disparaît** au lieu d'afficher des cellules vides, le sélecteur de milieu est masqué, et un bandeau discret l'explique. Le tableau de comptages reste entièrement exploitable et exportable.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 150" role="img" aria-label="Maquette M-Synthese - variante referentiel indisponible" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .banner { fill: #f6f8fa; stroke: #6a737d; stroke-width: 1; }
    .banner-txt { font: 13px sans-serif; fill: #4a6785; }
    .sub-grey { font: 12px sans-serif; fill: #6a737d; }
  </style>
  <rect x="0" y="0" width="1000" height="150" fill="#f7f9fb"/>
  <rect x="30" y="24" width="800" height="36" rx="4" class="banner"/>
  <text x="48" y="47" class="banner-txt">Référentiel d'activité indisponible : les comptages restent affichés, sans classe d'activité.</text>
  <text x="30" y="92" class="sub-grey">La colonne « Activité » et le sélecteur de milieu sont retirés, pas vidés.</text>
  <text x="30" y="118" class="sub-grey">Le tableau, le total et l'export CSV restent disponibles.</text>
</svg>
</div>

### Interactions clés

| Élément | Action |
|---|---|
| Bascule « validées seulement » | Recalcule contacts, fichiers, total et classes d'activité |
| Changement de **milieu** | Recalcule la colonne Activité et met à jour le référentiel affiché en barre de statut |
| Survol d'une classe | Infobulle : référentiel employé, quantiles, nombre d'occurrences, fiabilité |
| Double-clic sur une ligne | Ouvre la fiche de l'espèce |
| **Exporter CSV…** | Écrit l'en-tête de contexte, les quantiles, puis le bloc d'avertissement et sa source |
| Référentiel absent | Colonne et sélecteur retirés, bandeau explicatif, reste de l'écran intact |

## Notes pour l'implémentation

- **`TableView` JavaFX** avec une `cellFactory` dédiée pour la colonne Activité (pastille + bornes). La pastille porte un libellé, jamais une couleur seule.
- **Référentiel dans `commun/model`** (`ReferentielActivite`), sans dépendance JavaFX, donc testable isolément. Agrégation à côté de `AgregationAnalyse` dans `analyse/model`.
- **Ressource embarquée versionnée et datée**, avec sa provenance en en-tête de fichier : c'est une donnée de référence, soumise aux mêmes exigences de traçabilité que la liste des taxons ([C14](../Modele%20conceptuel/C14%20-%20Taxon.md)).
- **Fonctionnalité optionnelle** (`referentiel-activite`) : l'écran doit rester cohérent quand elle est désactivée, d'où la variante ci-dessus.
- **Export** : séparateur et encodage alignés sur les exports existants ; l'avertissement est recopié **dans le fichier**, pas seulement affiché avant l'écriture.
- **Icônes** : `FontIcon` Ikonli, pas d'emoji (règle #700).
