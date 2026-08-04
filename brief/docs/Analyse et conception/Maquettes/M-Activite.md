# M-Activite - Activité de la nuit

> **Type** : écran **« Activité »** d'un passage (atteint depuis [M-Passage](M-Passage.md) pour un passage dont les résultats d'identification ont été importés).
> **Persona principal** : [Marie](../Personas/Marie.md) (comprendre la forme de sa nuit) et [Karim](../Personas/Karim.md) (repérer un dispositif douteux).
> **Parcours couverts** : prolonge [P11 - Inventaire des espèces détectées](../Parcours%20utilisateurs/P11%20-%20Inventaire%20des%20espèces%20détectées.md).
> **Issue** : #2352 (chantier #2348, lot 2).

L'écran trace le **nombre de contacts par tranche horaire et par espèce**, sur un axe qui couvre la nuit et non la journée. Deux nuits à 300 contacts n'ont rien à voir selon que l'activité s'étale ou qu'elle tient en quarante minutes : c'est cette forme, effacée par le comptage total, que l'écran restitue.

> **L'axe court de 18 h à 8 h**, pas de 0 h à 24 h : découper une nuit à minuit la coupe en deux et rend le graphe illisible. La **fenêtre réelle entre coucher et lever du soleil**, que l'application sait déjà calculer au point d'écoute, est matérialisée par un aplat : de l'activité qui déborde en période diurne est un signal de dispositif autant qu'écologique.

## Maquette principale - nuit avec observations

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 620" role="img" aria-label="Maquette M-Activite - courbes d'activite horaire par espece sur l'axe nocturne" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .pagesub { font: 13px sans-serif; fill: #4a6785; }
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .section-title { font: 600 13px sans-serif; fill: #2c3e50; }
    .filt { font: 12px sans-serif; fill: #2c3e50; }
    .filt-off { font: 12px sans-serif; fill: #9aa0b3; }
    .filt-num { font: 11px sans-serif; fill: #6a737d; text-anchor: end; }
    .ctrl { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .ctrl-txt { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
    .ctrl-on { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .ctrl-on-txt { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .chart-bg { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .nightband { fill: #3f51b5; fill-opacity: 0.06; }
    .grid { stroke: #e1e6ec; stroke-width: 1; }
    .axis { stroke: #b8c2cc; stroke-width: 1; }
    .axis-txt { font: 10px sans-serif; fill: #6a737d; }
    .axis-title { font: 11px sans-serif; fill: #6a737d; }
    .s1 { fill: none; stroke: #2a78d6; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .s2 { fill: none; stroke: #1baf7a; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .s3 { fill: none; stroke: #4a3aa7; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .lbl { font: 600 11px sans-serif; fill: #2c3e50; }
    .legend-txt { font: 12px sans-serif; fill: #2c3e50; }
    .statusbar { fill: #eceff5; stroke: #d0d7de; stroke-width: 1; }
    .status-txt { font: 12px sans-serif; fill: #4a6785; }
  </style>

  <rect x="0" y="0" width="1000" height="620" fill="#f7f9fb"/>
  <rect x="0" y="0" width="1000" height="44" class="chrome"/>
  <text x="20" y="28" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="28" class="crumb">Accueil  ›  Mes sites  ›  Carré 640380  ›  Passage N° 2  ›  </text>
  <text x="600" y="28" class="crumb-curr">Activité</text>
  <rect x="780" y="12" width="200" height="22" rx="11" class="search"/>
  <text x="794" y="28" class="search-txt">Rechercher (Ctrl+F)</text>

  <text x="30" y="72" class="pagesub">Nuit du 2026-06-22 · coucher 21:51 · lever 06:27 · 939 contacts de chiroptères</text>

  <!-- Panneau de filtres -->
  <rect x="30" y="90" width="210" height="470" rx="4" class="panel"/>
  <text x="44" y="112" class="section-title">Filtres</text>

  <text x="44" y="140" class="filt">▾ Carrés</text><text x="226" y="140" class="filt-num">1 / 1</text>
  <text x="44" y="164" class="filt">▸ Points</text><text x="226" y="164" class="filt-num">1 / 1</text>
  <text x="44" y="188" class="filt">▸ Passages</text><text x="226" y="188" class="filt-num">1 / 1</text>
  <text x="44" y="212" class="filt">▸ Nuits</text><text x="226" y="212" class="filt-num">1 / 1</text>

  <line x1="44" y1="228" x2="226" y2="228" stroke="#e1e6ec" stroke-width="1"/>
  <text x="44" y="252" class="section-title">▾ Espèces</text><text x="226" y="252" class="filt-num">3 / 19</text>
  <rect x="46" y="266" width="11" height="11" rx="2" fill="#2a78d6"/><text x="66" y="276" class="filt">Pippip</text><text x="226" y="276" class="filt-num">302</text>
  <rect x="46" y="290" width="11" height="11" rx="2" fill="#1baf7a"/><text x="66" y="300" class="filt">Nyclei</text><text x="226" y="300" class="filt-num">168</text>
  <rect x="46" y="314" width="11" height="11" rx="2" fill="#4a3aa7"/><text x="66" y="324" class="filt">Pipkuh</text><text x="226" y="324" class="filt-num">141</text>
  <rect x="46" y="338" width="11" height="11" rx="2" fill="#ffffff" stroke="#c4ccd4"/><text x="66" y="348" class="filt-off">Eptser</text><text x="226" y="348" class="filt-num">29</text>
  <rect x="46" y="362" width="11" height="11" rx="2" fill="#ffffff" stroke="#c4ccd4"/><text x="66" y="372" class="filt-off">Plesp</text><text x="226" y="372" class="filt-num">13</text>
  <rect x="46" y="386" width="11" height="11" rx="2" fill="#ffffff" stroke="#c4ccd4"/><text x="66" y="396" class="filt-off">Barbar</text><text x="226" y="396" class="filt-num">9</text>

  <rect x="44" y="450" width="128" height="22" rx="3" class="ctrl"/><text x="108" y="465" class="ctrl-txt">Tout effacer</text>

  <!-- En-tete du graphe -->
  <text x="264" y="112" class="section-title">Activité par tranche horaire</text>
  <rect x="640" y="96" width="52" height="22" rx="3" class="ctrl"/><text x="666" y="111" class="ctrl-txt">15 min</text>
  <rect x="698" y="96" width="52" height="22" rx="3" class="ctrl-on"/><text x="724" y="111" class="ctrl-on-txt">30 min</text>
  <rect x="756" y="96" width="52" height="22" rx="3" class="ctrl"/><text x="782" y="111" class="ctrl-txt">60 min</text>
  <rect x="820" y="96" width="150" height="22" rx="3" class="ctrl"/><text x="895" y="111" class="ctrl-txt">Exporter l'image…</text>

  <!-- Zone de graphe -->
  <rect x="264" y="130" width="706" height="330" class="chart-bg"/>
  <!-- bande nocturne : coucher 21:51 -> lever 06:27 -->
  <rect x="450" y="146" width="410" height="270" class="nightband"/>
  <text x="456" y="162" class="axis-txt">nuit (coucher → lever)</text>
  <!-- grille + axe Y -->
  <line x1="310" y1="146" x2="950" y2="146" class="grid"/><text x="302" y="150" class="axis-txt" text-anchor="end">40</text>
  <line x1="310" y1="213.5" x2="950" y2="213.5" class="grid"/><text x="302" y="217" class="axis-txt" text-anchor="end">30</text>
  <line x1="310" y1="281" x2="950" y2="281" class="grid"/><text x="302" y="285" class="axis-txt" text-anchor="end">20</text>
  <line x1="310" y1="348.5" x2="950" y2="348.5" class="grid"/><text x="302" y="352" class="axis-txt" text-anchor="end">10</text>
  <line x1="310" y1="416" x2="950" y2="416" class="axis"/><text x="302" y="420" class="axis-txt" text-anchor="end">0</text>
  <text x="286" y="281" class="axis-title" transform="rotate(-90 286 281)" text-anchor="middle">Contacts / 30 min</text>

  <!-- axe X : 18h -> 07h -->
  <text x="310" y="434" class="axis-txt" text-anchor="middle">18h</text>
  <text x="359" y="434" class="axis-txt" text-anchor="middle">19h</text>
  <text x="408" y="434" class="axis-txt" text-anchor="middle">20h</text>
  <text x="457" y="434" class="axis-txt" text-anchor="middle">21h</text>
  <text x="507" y="434" class="axis-txt" text-anchor="middle">22h</text>
  <text x="556" y="434" class="axis-txt" text-anchor="middle">23h</text>
  <text x="605" y="434" class="axis-txt" text-anchor="middle">00h</text>
  <text x="655" y="434" class="axis-txt" text-anchor="middle">01h</text>
  <text x="704" y="434" class="axis-txt" text-anchor="middle">02h</text>
  <text x="753" y="434" class="axis-txt" text-anchor="middle">03h</text>
  <text x="803" y="434" class="axis-txt" text-anchor="middle">04h</text>
  <text x="852" y="434" class="axis-txt" text-anchor="middle">05h</text>
  <text x="901" y="434" class="axis-txt" text-anchor="middle">06h</text>
  <text x="950" y="434" class="axis-txt" text-anchor="middle">07h</text>
  <text x="630" y="452" class="axis-title" text-anchor="middle">Heure</text>

  <!-- Series -->
  <polyline class="s1" points="310,409 334,402 359,383 383,357 408,325 432,293 457,261 482,235 506,213 531,220 556,232 580,245 605,264 630,283 654,296 679,309 704,322 728,334 753,347 777,354 802,360 827,367 851,373 876,380 900,386 925,386 950,393"/>
  <polyline class="s2" points="310,416 334,416 359,409 383,402 408,389 432,370 457,344 482,312 506,286 531,293 556,312 580,331 605,350 630,370 654,383 679,389 704,396 728,402 753,402 777,409 802,409 827,409 851,416 876,416 900,416 925,416 950,416"/>
  <polyline class="s3" points="310,416 334,409 359,396 383,377 408,357 432,338 457,325 482,325 506,331 531,338 556,344 580,357 605,363 630,370 654,377 679,377 704,383 728,389 753,389 777,396 802,396 827,402 851,402 876,402 900,409 925,409 950,409"/>

  <!-- pics : marques seules, l'identification passe par la legende et le survol -->
  <circle cx="506" cy="213" r="4" fill="#2a78d6" stroke="#ffffff" stroke-width="2"/>
  <circle cx="506" cy="286" r="4" fill="#1baf7a" stroke="#ffffff" stroke-width="2"/>
  <circle cx="457" cy="325" r="4" fill="#4a3aa7" stroke="#ffffff" stroke-width="2"/>

  <!-- Legende -->
  <rect x="46" y="490" width="11" height="11" rx="2" fill="#2a78d6"/><text x="66" y="500" class="legend-txt">Pipistrelle commune (Pippip)</text>
  <rect x="300" y="490" width="11" height="11" rx="2" fill="#1baf7a"/><text x="320" y="500" class="legend-txt">Noctule de Leisler (Nyclei)</text>
  <rect x="540" y="490" width="11" height="11" rx="2" fill="#4a3aa7"/><text x="560" y="500" class="legend-txt">Pipistrelle de Kuhl (Pipkuh)</text>

  <!-- Barre de statut -->
  <rect x="0" y="600" width="1000" height="20" class="statusbar"/>
  <text x="12" y="614" class="status-txt">Carré 640380 · A1 · N° 2</text>
  <text x="500" y="614" class="status-txt" text-anchor="middle">3 espèces affichées sur 19 · tranche 30 min</text>
  <text x="988" y="614" class="status-txt" text-anchor="end">611 contacts dans la sélection</text>
</svg>
</div>

### Annotations

- **Axe des abscisses** (`axeHeures`) : de **18 h à 8 h**, une graduation par heure. Une nuit à cheval sur deux dates reste **une** nuit : le rattachement se fait par bascule à midi, pas par changement de date.
- **Bande nocturne** (`bandeNuit`) : aplat très pâle entre le coucher et le lever du soleil calculés au point d'écoute. C'est la même source que la cohérence horaire de [M-Diagnostic](M-Diagnostic.md). Quand le GPS du point manque, la bande disparaît et le graphe reste traçable.
- **Axe des ordonnées** (`axeContacts`) : nombre de contacts dans la tranche. Le pas se choisit sur des valeurs rondes ; le titre d'axe rappelle la tranche courante, qui change avec le sélecteur.
- **Sélecteur de tranche** (`groupeTranche`) : 15, 30 ou 60 minutes. Le graphe se recalcule, la sélection d'espèces est conservée.
- **Barre de filtres** (`barreFiltres`) : recherche permanente et puces « + Filtre » (lieu, nuit, taxon parent, nature de la nuit, espèces à enjeu), sur le socle partagé avec les quatre autres tableaux exploratoires. Chaque puce se retire d'un clic, et le sous-ensemble filtré est **ré-agrégé** en direct : filtrer, c'est re-tracer.
- **Onglets** (`barreOnglets`) : socle des vues mémorisées, partageant les taxons par **catégorie du référentiel** (Chiroptères, Orthoptères et cigales, Autres mammifères), plus « Tout ». Tadarida ne détecte pas que des chauves-souris, et sans cette séparation la présélection des plus contactés peut retenir une sauterelle. L'utilisateur enregistre ses propres vues à côté.
- **Sémantique du vide** : une sélection vide signifie « rien », pas « pas de filtre », l'écran le dit alors en nommant la dimension responsable.
- **Espèces** (`listeEspeces`) : cochées par défaut sur les **cinq plus contactées**. Au-delà, le graphe devient illisible. Le compteur à droite de chaque espèce respecte les autres filtres actifs.
- **Identité des courbes** : légende systématique sous le graphe, et **survol** d'un point pour l'espèce, l'heure exacte et le nombre de contacts. L'étiquette directe au pic, prévue à la conception pour ne pas dépendre de la seule couleur, s'est révélée illisible à cinq courbes : elle a été retirée à l'usage, la légende portant l'identification. L'arbitrage est assumé, la dépendance résiduelle à la couleur avec.
- **Barre de statut** : contexte du passage, nombre d'espèces affichées et tranche courante, volumétrie de la sélection.

## Variante - état vide

Quand le graphe n'a rien à tracer, le message **nomme la dimension effectivement vide** au lieu d'un générique. « Aucun point sélectionné » se corrige, « aucune donnée » ne se corrige pas.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 180" role="img" aria-label="Maquette M-Activite - variante etat vide nommant la dimension responsable" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chart-bg { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .empty-txt { font: 600 14px sans-serif; fill: #4a6785; text-anchor: middle; }
    .empty-sub { font: 12px sans-serif; fill: #6a737d; text-anchor: middle; }
    .sub-grey { font: 12px sans-serif; fill: #6a737d; }
  </style>
  <rect x="0" y="0" width="1000" height="180" fill="#f7f9fb"/>
  <rect x="264" y="20" width="706" height="110" class="chart-bg"/>
  <text x="617" y="65" class="empty-txt">Aucun point sélectionné.</text>
  <text x="617" y="88" class="empty-sub">Cochez au moins un point d'écoute dans la colonne de gauche.</text>
  <text x="30" y="160" class="sub-grey">Le message nomme la première dimension vide de la cascade : carrés, puis points, puis passages, puis nuits, puis espèces.</text>
</svg>
</div>

### Interactions clés

| Élément | Action |
|---|---|
| Cocher / décocher une espèce | Ajoute ou retire la courbe ; les couleurs des autres séries **ne changent pas** |
| Changer la tranche | Recalcule le graphe, conserve la sélection |
| Survol d'un point | Infobulle : espèce, heure de la tranche, nombre de contacts |
| **Exporter l'image…** | Image **redessinée** hors écran, portant carré, point, passage, tranche, filtres actifs, version et date. Le résultat, réussite comme échec, est dit dans le bandeau de retour |
| Sélection vide | Message nommant la dimension responsable |

### Deux règles nées de l'usage réel

- **Plusieurs nuits se replient sur une.** L'axe est celui d'**une** nuit : sur un sous-ensemble qui en couvre plusieurs, les tranches de même heure de nuit sont **sommées**. Sans ce repliement, chaque nuit repose ses points sur le même axe et la ligne, qui suit l'ordre chronologique, repart en arrière, la courbe prend un aspect de dents de scie qui ne décrit rien.
- **Une tranche sans contact vaut zéro, là où l'on écoutait.** Sinon deux contacts séparés d'un silence de trois heures sont reliés par une droite, qui donne à voir une activité continue. La plage des zéros est la **fenêtre réellement enregistrée** du passage, ou à défaut la plage observée : hors d'elle, l'absence de contact ne dit rien (le capteur pouvait être éteint) et la courbe s'abstient plutôt que d'affirmer un silence constaté.

## Notes pour l'implémentation

- **`LineChart` JavaFX** sur `NumberAxis` en minutes depuis 18 h, étiquettes `HH`. C'est le même patron que le graphe climatique de [M-Diagnostic](M-Diagnostic.md), avec plus de séries et une bande de fond.
- **La couleur suit l'espèce, jamais son rang** : filtrer ne doit pas repeindre les séries survivantes.
- **L'heure vient du nom de fichier**, qui porte l'horodatage réel de la séquence et sert déjà de clé de jointure avec les observations. C'est la source la plus fiable disponible.
- **Agrégation pure** dans `analyse/model` (liste d'observations en entrée, séries en sortie), testable sans interface.
- **Export d'image redessiné, pas capturé** : une capture d'un nœud masqué ou accéléré matériellement peut produire une image vide ou noire. L'export porte son propre contexte, faute de quoi l'image devient inexploitable dès qu'elle quitte l'application.
- **Fonctionnalité optionnelle** (`activite-nuit`).
