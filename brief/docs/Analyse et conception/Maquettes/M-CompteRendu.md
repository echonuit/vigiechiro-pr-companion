# M-CompteRendu - Compte rendu d'une opération lourde

> **Type** : **composant transverse** de restitution, affiché à la fin d'une opération longue. Premier point d'application : la fin d'import ([M-Import](M-Import.md)). Vocation à servir aussi la fin de dépôt ([M-Lot](M-Lot.md)) et la fin de réactivation d'un passage archivé.
> **Persona principal** : tous. C'est le moment où l'utilisateur décide s'il continue.
> **Parcours couverts** : [P2 - Importer une nuit d'enregistrement](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), puis [P4 - Préparer un lot prêt à déposer](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md) et [P12 - Récupérer une nuit déposée sur VigieChiro](../Parcours%20utilisateurs/P12%20-%20Récupérer%20une%20nuit%20déposée%20sur%20VigieChiro.md) (compte rendu de réactivation).
> **Issue** : #2358 (chantier #2350, lot 2).

Une opération lourde brasse plusieurs gigaoctets pendant plusieurs minutes, sans que l'utilisateur puisse rien vérifier par lui-même. Quand elle se termine, il a trois questions, et aucune n'appelle une liste :

| Ce qu'il se demande | Ce que le compte rendu montre |
|---|---|
| « Est-ce que ça s'est bien passé ? » | la part de ce qui est passé, la part de ce qui a été écarté |
| « Qu'est-ce que ça m'a coûté sur le disque ? » | le volume écrit, ventilé bruts / séquences |
| « Qu'est-ce que je fais maintenant ? » | l'action suivante, pas un bouton « Fermer » |

> **Ce composant n'ajoute aucune donnée.** Le rapport d'import, le bilan de publication et le rapport de réactivation contiennent déjà tout ce qui est affiché ici. Ce qui change est la **forme** : des proportions à la place des puces, et une action à la place d'un acquittement.

## Maquette principale - fin d'import, avec rejets et avertissement

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 620" role="img" aria-label="Maquette M-CompteRendu - compte rendu chiffre de fin d'import" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .title { font: 700 15px sans-serif; fill: #2c3e50; }
    .section-title { font: 600 13px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-sub { font: 11px sans-serif; fill: #6a737d; }
    .lab { font: 12px sans-serif; fill: #4a6785; text-anchor: end; }
    .barlbl { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .b-lu { fill: #8fa0e6; }
    .b-bruts { fill: #3f51b5; }
    .b-seq { fill: #1e8449; }
    .b-ok { fill: #1e8449; }
    .b-ignore { fill: #b9770e; }
    .b-rejet { fill: #a93226; }
    .legend-txt { font: 12px sans-serif; fill: #2c3e50; }
    .list-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .list-frame { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .list-row-alt { fill: #f6f8fa; }
    .num { font: 12px sans-serif; fill: #2c3e50; text-anchor: end; }
    .link { font: 12px sans-serif; fill: #2563a3; }
    .warn-box { fill: #fef9e7; stroke: #b9770e; stroke-width: 1; }
    .warn-title { font: 600 12px sans-serif; fill: #7e5109; }
    .warn-txt { font: 12px sans-serif; fill: #5d4e00; }
    .ctrl { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .ctrl-txt { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
    .ctrl-pri { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .ctrl-pri-txt { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .pill-ok { fill: #1e8449; }
    .pill-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
  </style>

  <rect x="0" y="0" width="1000" height="620" fill="#f7f9fb"/>
  <rect x="40" y="24" width="920" height="570" rx="6" class="panel"/>

  <text x="62" y="56" class="title">Import terminé : nuit du 22/06/2026, carré 640380 · A1</text>
  <rect x="800" y="42" width="140" height="18" rx="9" class="pill-ok"/>
  <text x="870" y="55" class="pill-txt">583 / 612 importés</text>

  <!-- Bloc 1 : volumes -->
  <text x="62" y="94" class="section-title">Volume traité</text>
  <text x="150" y="122" class="lab">Carte SD</text>
  <rect x="164" y="110" width="640" height="18" rx="3" class="b-lu"/>
  <text x="484" y="123" class="barlbl">5,0 Go lus · 612 fichiers</text>
  <text x="150" y="152" class="lab">Écrit</text>
  <rect x="164" y="140" width="470" height="18" rx="3" class="b-bruts"/>
  <text x="399" y="153" class="barlbl">bruts conservés · 5,0 Go</text>
  <rect x="638" y="140" width="170" height="18" rx="3" class="b-seq"/>
  <text x="723" y="153" class="barlbl">séquences · 1,8 Go</text>

  <!-- Bloc 2 : ventilation -->
  <text x="62" y="196" class="section-title">Devenir des 612 enregistrements</text>
  <rect x="62" y="208" width="836" height="24" rx="3" class="b-ok"/>
  <rect x="858" y="208" width="30" height="24" class="b-ignore"/>
  <rect x="890" y="208" width="8" height="24" rx="3" class="b-rejet"/>
  <text x="460" y="225" class="barlbl">583</text>
  <text x="873" y="225" class="barlbl">21</text>

  <rect x="64" y="246" width="11" height="11" rx="2" class="b-ok"/><text x="84" y="256" class="legend-txt">Importés · 583 (95 %)</text>
  <rect x="284" y="246" width="11" height="11" rx="2" class="b-ignore"/><text x="304" y="256" class="legend-txt">Ignorés, déjà présents · 21 (3 %)</text>
  <rect x="584" y="246" width="11" height="11" rx="2" class="b-rejet"/><text x="604" y="256" class="legend-txt">Rejetés · 8 (1 %)</text>

  <!-- Bloc 3 : motifs -->
  <text x="62" y="298" class="section-title">Motifs de rejet</text>
  <rect x="62" y="310" width="836" height="80" class="list-frame"/>
  <rect x="62" y="310" width="836" height="24" class="list-head"/>
  <text x="76" y="326" class="head-txt">Motif</text>
  <text x="700" y="326" class="head-txt" text-anchor="end">Fichiers</text>
  <text x="76" y="354" class="cell">Fichier déjà expansé (traité par un autre outil)</text>
  <text x="700" y="354" class="num">6</text>
  <text x="740" y="354" class="link">Voir la liste</text>
  <rect x="63" y="362" width="834" height="27" class="list-row-alt"/>
  <text x="76" y="381" class="cell">En-tête WAV illisible</text>
  <text x="700" y="381" class="num">2</text>
  <text x="740" y="381" class="link">Voir la liste</text>

  <!-- Bloc 4 : avertissements encore vrais -->
  <text x="62" y="428" class="section-title">Avertissements encore vrais</text>
  <rect x="62" y="440" width="836" height="58" rx="4" class="warn-box"/>
  <text x="80" y="462" class="warn-title">Relevé climatique absent</text>
  <text x="80" y="482" class="warn-txt">Sonde non installée ou défaillante. Le diagnostic de la nuit sera partiel ; le dépôt reste possible.</text>

  <!-- Pied : action suivante -->
  <line x1="62" y1="520" x2="898" y2="520" stroke="#e1e6ec" stroke-width="1"/>
  <rect x="62" y="536" width="150" height="28" rx="3" class="ctrl-pri"/>
  <text x="137" y="555" class="ctrl-pri-txt">Ouvrir le passage</text>
  <rect x="224" y="536" width="200" height="28" rx="3" class="ctrl"/>
  <text x="324" y="555" class="ctrl-txt">Vérifier l'enregistrement</text>
  <text x="898" y="548" class="cell-sub" text-anchor="end">Les originaux occupent 5,0 Go sur le disque.</text>
  <text x="898" y="564" class="cell-sub" text-anchor="end">Réglages ▸ Import pour ne plus les conserver.</text>
</svg>
</div>

### Annotations

- **Titre et pastille de résultat** (`lblTitre`, `badgeResultat`) : l'opération, son objet, et le résultat chiffré en un coup d'œil. La pastille porte un **libellé chiffré**, pas une couleur seule.
- **Bloc « Volume traité »** (`blocVolumes`) : barres **proportionnelles à l'échelle**. Une barre qui ne respecte pas les quantités qu'elle représente est pire qu'un tableau : elle donne une impression fausse avec l'autorité du visuel. La ventilation bruts / séquences répond à la question du coût disque, qui est la seconde question réelle après « est-ce que ça a marché ».
- **Bloc « Devenir »** (`blocVentilation`) : barre empilée **exhaustive**. La somme des segments fait le total, et le reliquat porte un nom. Un segment « autres » silencieux masque exactement ce que l'utilisateur cherchait. La légende chiffre chaque part en valeur **et** en pourcentage.
- **Bloc « Motifs »** (`tableMotifs`) : une ligne par motif, avec son effectif et un accès à la liste correspondante. Le détail reste accessible, il n'est simplement plus la première chose qu'on lit.
- **Bloc « Avertissements encore vrais »** (`blocAvertissements`) : distinct des erreurs, et **filtré** sur ce qui reste vrai à la fin de l'opération. Un avertissement levé en cours de route n'a rien à faire dans un compte rendu final.
- **Pied : l'action suivante** (`boutonActionSuivante`) : le compte rendu ne se termine pas sur « Fermer ». Il propose ce qu'on fait ensuite, parce que c'est la question réelle de l'utilisateur à cet instant. Les informations de contexte sur l'espace disque restent secondaires, à droite.
- **Aucun texte d'exception brut n'est jamais l'unique message** : une cause technique peut accompagner un message écrit pour un humain, elle ne peut pas le remplacer.

## Variante - opération en échec

Quand l'opération échoue, la structure ne change pas : ce sont les proportions qui parlent. Le compte rendu dit ce qui a tout de même été fait, ce qui a échoué et pourquoi, puis propose la reprise plutôt qu'un acquittement.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 260" role="img" aria-label="Maquette M-CompteRendu - variante operation en echec" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .title { font: 700 15px sans-serif; fill: #2c3e50; }
    .section-title { font: 600 13px sans-serif; fill: #2c3e50; }
    .legend-txt { font: 12px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .barlbl { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .b-ok { fill: #1e8449; }
    .b-rejet { fill: #a93226; }
    .pill-ko { fill: #a93226; }
    .pill-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .ctrl-pri { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .ctrl-pri-txt { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .ctrl { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .ctrl-txt { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
  </style>
  <rect x="0" y="0" width="1000" height="260" fill="#f7f9fb"/>
  <rect x="40" y="16" width="920" height="228" rx="6" class="panel"/>
  <text x="62" y="46" class="title">Téléversement interrompu : 9 archives sur 14</text>
  <rect x="800" y="32" width="140" height="18" rx="9" class="pill-ko"/>
  <text x="870" y="45" class="pill-txt">5 en échec</text>
  <text x="62" y="82" class="section-title">Devenir des 14 archives</text>
  <rect x="62" y="94" width="538" height="24" rx="3" class="b-ok"/>
  <rect x="604" y="94" width="294" height="24" rx="3" class="b-rejet"/>
  <text x="331" y="111" class="barlbl">9 déposées</text>
  <text x="751" y="111" class="barlbl">5 en échec</text>
  <rect x="64" y="132" width="11" height="11" rx="2" class="b-rejet"/>
  <text x="84" y="142" class="legend-txt">Connexion interrompue · 5 archives. Le dépôt reprendra là où il s'est arrêté.</text>
  <text x="62" y="172" class="cell">Aucune archive n'a été perdue : les 9 déposées ne seront pas renvoyées.</text>
  <rect x="62" y="192" width="170" height="28" rx="3" class="ctrl-pri"/>
  <text x="147" y="211" class="ctrl-pri-txt">Retenter les échecs</text>
  <rect x="244" y="192" width="130" height="28" rx="3" class="ctrl"/>
  <text x="309" y="211" class="ctrl-txt">Plus tard</text>
</svg>
</div>

### Interactions clés

| Élément | Action |
|---|---|
| **Voir la liste** sur un motif | Ouvre la liste des fichiers concernés par ce motif |
| Survol d'un segment de barre | Infobulle : effectif, pourcentage, définition du segment |
| **Action suivante** (bouton primaire) | Enchaîne sur l'étape logique (ouvrir le passage, retenter, vérifier) |
| Opération sans rejet ni avertissement | Les blocs correspondants **disparaissent**, ils ne s'affichent pas vides |
| Opération en échec | Même structure, proportions inversées, reprise proposée |

## Notes pour l'implémentation

- **Composant présentationnel pur** dans `commun/view` : il reçoit un modèle de compte rendu et l'affiche. Il ne va rien chercher, ne décide de rien, et n'appartient à aucune feature.
- **Les données existent déjà** : rapport d'import, bilan de publication, rapport de réactivation. Le lot consiste à les **projeter** sur ce modèle, pas à les produire.
- **Blocs facultatifs** : chaque bloc se masque quand il n'a rien à dire. Un compte rendu sans rejet ne doit pas afficher un cadre vide intitulé « Motifs de rejet ».
- **Proportions à l'échelle** : la largeur des barres se calcule sur les valeurs réelles ; les très petits segments reçoivent une largeur minimale lisible **et** leur valeur en légende, jamais un arrondi silencieux à zéro.
- **Thème sombre** : aucune couleur de fond codée en dur hors feuille de style, pour que le composant survive au câblage de `DarkTheme.css` (#1037).
- **Icônes** : `FontIcon` Ikonli, pas d'emoji (règle #700).
- **Forme d'insertion à trancher** : fenêtre modale, panneau intégré à l'écran d'origine, ou les deux selon l'opération. La décision se prend sur les trois cas d'usage réels, pas dans l'abstrait.
