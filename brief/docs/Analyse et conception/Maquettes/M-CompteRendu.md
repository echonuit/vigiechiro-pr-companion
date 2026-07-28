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

> **Ce composant n'ajoute presque aucune donnée.** Le rapport d'import, le bilan de publication et le rapport de réactivation contiennent l'essentiel de ce qui est affiché ici (la seule exception est notée en fin de page). Ce qui change est la **forme** : des proportions à la place des puces, et une action à la place d'un acquittement.

## La forme retenue : un résultat, le détail à la demande

Le compte rendu **n'est pas un rapport**. Un document à sections empilées repose la même exigence que les puces qu'il remplace : lire pour savoir si l'on peut continuer. Il tient donc en une bande dense d'environ 250 px, dans cet ordre de lecture :

1. le **verdict chiffré**, lisible sans rien parcourir ;
2. la **barre du devenir**, qui répond à « dans quelles proportions » ;
3. le **coût disque**, en deux barres à échelle commune ;
4. ce qui **reste vrai** et mérite attention ;
5. **l'action suivante**.

Les noms des fichiers rejetés ne sont pas dans cette bande : la ligne de pied les résume par motif (« 8 rejetés : 6 déjà expansés, 2 en-tête illisible ») et un accès les déplie. C'est le seul détail qui coûte un geste, parce que c'est le seul dont la réponse aux trois questions ne dépend pas.

Une variante en deux colonnes (verdict à gauche, motifs et avertissements à droite) a été écartée : elle montre tout d'un coup, mais elle est plus large, plus haute - donc une modale plutôt qu'un panneau - et sa colonne de droite est vide dans le cas courant, celui d'un import sans rejet.

## Maquette principale - fin d'import, avec rejets et avertissement

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 300" role="img" aria-label="Maquette M-CompteRendu - compte rendu chiffre de fin d'import" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .title { font: 650 15px sans-serif; fill: #22303c; }
    .lab { font: 12px sans-serif; fill: #4a6785; text-anchor: end; }
    .leg { font: 12px sans-serif; fill: #22303c; }
    .sub { font: 11.5px sans-serif; fill: #6a737d; }
    .inbar { font: 600 11px sans-serif; fill: #ffffff; }
    .s-ok { fill: #1e8449; }
    .s-ign { fill: #b9770e; }
    .s-rej { fill: #a93226; }
    .v-lu { fill: #9fb2c9; }
    .v-bruts { fill: #2f4a7a; }
    .v-seq { fill: #1e8449; }
    .pill { fill: #eaf3ea; stroke: #1e8449; stroke-width: 1; }
    .pill-t { font: 600 12px sans-serif; fill: #196f3d; text-anchor: middle; }
    .pri { fill: #2563a3; stroke: #1d4f80; stroke-width: 1; }
    .pri-t { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .sec-t { font: 12px sans-serif; fill: #2563a3; }
    .warn-t { font: 12px sans-serif; fill: #7e5109; }
  </style>

  <rect x="0" y="0" width="1000" height="300" fill="#f7f9fb"/>
  <rect x="40" y="20" width="920" height="260" rx="4" class="panel"/>

  <text x="60" y="50" class="title">Import terminé - nuit du 22/06/2026, carré 640380 · A1</text>
  <rect x="780" y="36" width="160" height="20" rx="10" class="pill"/>
  <text x="860" y="50" class="pill-t">583 / 612 importés</text>

  <!-- Devenir : 612 sur 840 px. 583 -> 800,2 | 21 -> 28,8 | 8 -> 11,0 . Somme = 840,0 -->
  <rect x="60" y="70" width="800.2" height="22" class="s-ok"/>
  <rect x="860.2" y="70" width="28.8" height="22" class="s-ign"/>
  <rect x="889" y="70" width="11" height="22" class="s-rej"/>
  <text x="72" y="86" class="inbar">583 importés</text>

  <rect x="61" y="104" width="10" height="10" rx="2" class="s-ok"/>
  <text x="78" y="113" class="leg">Importés · 583 (95,3 %)</text>
  <rect x="264" y="104" width="10" height="10" rx="2" class="s-ign"/>
  <text x="281" y="113" class="leg">Déjà présents · 21 (3,4 %)</text>
  <rect x="484" y="104" width="10" height="10" rx="2" class="s-rej"/>
  <text x="501" y="113" class="leg">Rejetés · 8 (1,3 %)</text>

  <!-- Volumes : echelle commune 6,8 Go sur 600 px = 88,2 px/Go -->
  <text x="168" y="152" class="lab">Lu sur la carte</text>
  <rect x="180" y="140" width="441.2" height="16" class="v-lu"/>
  <text x="632" y="152" class="sub">5,0 Go · 612 fichiers</text>

  <text x="168" y="180" class="lab">Écrit sur le disque</text>
  <rect x="180" y="168" width="441.2" height="16" class="v-bruts"/>
  <rect x="621.2" y="168" width="158.8" height="16" class="v-seq"/>
  <text x="790" y="180" class="sub">6,8 Go - bruts 5,0 · séquences 1,8</text>

  <line x1="60" y1="204" x2="940" y2="204" stroke="#e8edf2" stroke-width="1"/>
  <circle cx="66" cy="226" r="5" fill="#b9770e"/>
  <text x="78" y="230" class="warn-t">Relevé climatique absent : le diagnostic de la nuit sera partiel, le dépôt reste possible.</text>

  <rect x="60" y="244" width="150" height="26" rx="3" class="pri"/>
  <text x="135" y="261" class="pri-t">Ouvrir le passage</text>
  <text x="226" y="261" class="sec-t">Vérifier l'enregistrement</text>
  <text x="940" y="261" class="sub" text-anchor="end">8 rejetés : 6 déjà expansés, 2 en-tête illisible - voir</text>
</svg>
</div>

### La preuve d'échelle fait partie de la maquette

Une barre qui ne respecte pas les quantités qu'elle représente est **pire qu'un tableau** : elle donne une vue fausse avec l'autorité du visuel. La maquette qui précédait celle-ci proclamait la règle et la violait : deux échelles différentes dans le même bloc (128 px/Go sur une barre, 94 sur l'autre), et une barre empilée dont le segment majoritaire occupait toute la largeur, les deux autres peints par-dessus. Les largeurs ci-dessus sont donc calculées, et leur vérification est publiée avec elles :

| Barre | Segments dessinés | Contrôle |
|---|---|---|
| Devenir (612 sur 840 px) | 583 → 800,2 px · 21 → 28,8 px · 8 → 11,0 px | somme **840,0 / 840 px** |
| Lu (5,0 Go) | 441,2 px | **88,2 px/Go** |
| Écrit (6,8 Go) | bruts 441,2 px · séquences 158,8 px | **88,2 px/Go**, même échelle que « Lu » |
| Légende | 95,3 % + 3,4 % + 1,3 % | **100,0 %** |

Les pourcentages sont arrondis **au dixième** et non à l'unité : à l'unité, cet import se lirait « 95 + 3 + 1 = 99 % », et un compte rendu qui ne fait pas 100 % laisse chercher le point manquant.

### Annotations

- **Titre et pastille** (`lblTitre`, `badgeResultat`) : l'opération, son objet, et le résultat chiffré. La pastille porte un **libellé chiffré**, jamais une couleur seule : une couleur ne se lit pas quand on ne la distingue pas.
- **Barre du devenir** (`barreVentilation`) : ventilation **exhaustive** de l'ensemble. La somme des segments fait le total, et le reliquat porte un nom. Un segment « autres » silencieux masque exactement ce que l'utilisateur cherchait. La légende chiffre chaque part en valeur **et** en pourcentage.
- **Barres de volume** (`barresVolume`) : « lu » et « écrit » partagent leur **échelle**, sans quoi la comparaison qu'elles invitent à faire est fausse. « Écrit » se ventile bruts / séquences, ce qui répond à la question du coût disque.
- **Ligne d'avertissement** (`ligneAvertissements`) : ce qui reste **vrai à la fin** de l'opération, distinct des erreurs. Un avertissement levé en cours de route et devenu faux n'a rien à faire dans un compte rendu final (#1488).
- **Pied** (`boutonActionSuivante`, `lienMotifs`) : l'action suivante à gauche, mise en avant ; le résumé des motifs de rejet à droite, avec son accès au détail. Le compte rendu ne se termine pas sur « Fermer » : il propose ce qu'on fait ensuite, parce que c'est la question réelle de l'utilisateur à cet instant.
- **Aucun texte d'exception brut n'est jamais l'unique message** : une cause technique peut accompagner un message écrit pour un humain, elle ne peut pas le remplacer (#2076).

## Variante - rien à signaler (le cas courant)

Un import sans rejet ni avertissement ne doit pas afficher de cadres vides. Les blocs qui n'ont rien à dire **disparaissent** ; la bande se referme sur l'essentiel et ne fait plus que 180 px.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 220" role="img" aria-label="Maquette M-CompteRendu - variante sans rejet ni avertissement" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .title { font: 650 15px sans-serif; fill: #22303c; }
    .lab { font: 12px sans-serif; fill: #4a6785; text-anchor: end; }
    .leg { font: 12px sans-serif; fill: #22303c; }
    .sub { font: 11.5px sans-serif; fill: #6a737d; }
    .inbar { font: 600 11px sans-serif; fill: #ffffff; }
    .s-ok { fill: #1e8449; }
    .v-lu { fill: #9fb2c9; }
    .v-bruts { fill: #2f4a7a; }
    .v-seq { fill: #1e8449; }
    .pill { fill: #eaf3ea; stroke: #1e8449; stroke-width: 1; }
    .pill-t { font: 600 12px sans-serif; fill: #196f3d; text-anchor: middle; }
    .pri { fill: #2563a3; stroke: #1d4f80; stroke-width: 1; }
    .pri-t { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
  </style>
  <rect x="0" y="0" width="1000" height="220" fill="#f7f9fb"/>
  <rect x="40" y="20" width="920" height="180" rx="4" class="panel"/>

  <text x="60" y="50" class="title">Import terminé - nuit du 20/06/2026, carré 640380 · A1</text>
  <rect x="780" y="36" width="160" height="20" rx="10" class="pill"/>
  <text x="860" y="50" class="pill-t">584 / 584 importés</text>

  <!-- Un seul segment : la ventilation reste exhaustive, elle n'a qu'une part. -->
  <rect x="60" y="70" width="840" height="22" class="s-ok"/>
  <text x="72" y="86" class="inbar">584 importés</text>
  <rect x="61" y="104" width="10" height="10" rx="2" class="s-ok"/>
  <text x="78" y="113" class="leg">Importés · 584 (100,0 %) - aucun fichier ignoré, aucun rejet</text>

  <text x="168" y="152" class="lab">Lu sur la carte</text>
  <rect x="180" y="140" width="441.2" height="16" class="v-lu"/>
  <text x="632" y="152" class="sub">5,0 Go · 584 fichiers</text>
  <text x="168" y="180" class="lab">Écrit sur le disque</text>
  <rect x="180" y="168" width="441.2" height="16" class="v-bruts"/>
  <rect x="621.2" y="168" width="158.8" height="16" class="v-seq"/>
  <text x="790" y="180" class="sub">6,8 Go - bruts 5,0 · séquences 1,8</text>

  <rect x="740" y="100" width="160" height="26" rx="3" class="pri"/>
  <text x="820" y="117" class="pri-t">Ouvrir le passage</text>
</svg>
</div>

## Variante - opération en échec

La structure ne change pas : ce sont les proportions qui parlent. Le compte rendu dit ce qui a **tout de même** été fait, ce qui a échoué et pourquoi, puis propose la reprise plutôt qu'un acquittement.

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 210" role="img" aria-label="Maquette M-CompteRendu - variante operation en echec" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .panel { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .title { font: 650 15px sans-serif; fill: #22303c; }
    .leg { font: 12px sans-serif; fill: #22303c; }
    .sub { font: 11.5px sans-serif; fill: #6a737d; }
    .inbar { font: 600 11px sans-serif; fill: #ffffff; }
    .s-ok { fill: #1e8449; }
    .s-rej { fill: #a93226; }
    .pill-ko { fill: #fbeceb; stroke: #a93226; stroke-width: 1; }
    .pill-ko-t { font: 600 12px sans-serif; fill: #8c2a20; text-anchor: middle; }
    .pri { fill: #2563a3; stroke: #1d4f80; stroke-width: 1; }
    .pri-t { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .sec-t { font: 12px sans-serif; fill: #2563a3; }
  </style>
  <rect x="0" y="0" width="1000" height="210" fill="#f7f9fb"/>
  <rect x="40" y="20" width="920" height="170" rx="4" class="panel"/>

  <text x="60" y="50" class="title">Téléversement interrompu - 9 archives sur 14</text>
  <rect x="780" y="36" width="160" height="20" rx="10" class="pill-ko"/>
  <text x="860" y="50" class="pill-ko-t">5 en échec</text>

  <!-- Devenir : 14 sur 840 px. 9 -> 540,0 | 5 -> 300,0 . Somme = 840,0 -->
  <rect x="60" y="70" width="540" height="22" class="s-ok"/>
  <rect x="600" y="70" width="300" height="22" class="s-rej"/>
  <text x="72" y="86" class="inbar">9 déposées</text>
  <text x="612" y="86" class="inbar">5 en échec</text>

  <rect x="61" y="104" width="10" height="10" rx="2" class="s-rej"/>
  <text x="78" y="113" class="leg">Connexion interrompue · 5 archives (35,7 %). Le dépôt reprendra là où il s'est arrêté.</text>
  <text x="60" y="140" class="sub">Aucune archive n'a été perdue : les 9 déjà déposées ne seront pas renvoyées.</text>

  <rect x="60" y="154" width="170" height="26" rx="3" class="pri"/>
  <text x="145" y="171" class="pri-t">Retenter les échecs</text>
  <text x="246" y="171" class="sec-t">Plus tard</text>
</svg>
</div>

## Interactions clés

| Élément | Action |
|---|---|
| **voir** (résumé des motifs, en pied) | Déplie la liste des fichiers rejetés, groupés par motif |
| Survol d'un segment de barre | Infobulle : effectif, pourcentage, définition du segment |
| **Action suivante** (bouton primaire) | Enchaîne sur l'étape logique (ouvrir le passage, retenter, vérifier) |
| Opération sans rejet ni avertissement | Les blocs correspondants **disparaissent**, ils ne s'affichent pas vides |
| Opération en échec | Même structure, proportions inversées, reprise proposée |

## Ce que le livré a précisé

La maquette a été écrite avant le code. Cinq points ont été **tranchés en chemin**, et ce sont eux qu'un
lecteur doit connaître avant d'ajouter une quatrième surface.

**1. Chaque mention porte sa sévérité.** La maquette ne prévoyait qu'un bloc « avertissements ». La
première capture de la réactivation a montré un triangle d'alerte devant « L'audio est de nouveau
complet » et devant un indice explicitement annoncé non bloquant. Une mention porte donc son registre :
coche pour une bonne nouvelle, « i » pour un fait de contexte, triangle pour ce sur quoi il faut revenir.

**2. Le compte rendu textuel ne disparaît pas partout, et le critère est le consommateur.** Là où une
**commande en ligne** rend le même bilan (la réactivation), il reste. Là où la seule surface était l'écran
(la publication des corrections), le chiffré le remplace.

**3. Un bilan qui n'a rien à ventiler garde le textuel.** Un passage **reconstruit** n'a pas subi de
réactivation : une barre « 0 sur 30 » y ferait croire à une tentative qui a échoué.

**4. La teinte de la seconde part d'un volume ne s'emploie pas dans une ventilation.** Elle partage la
couleur de la part retenue — « bruts + séquences » se lit comme un tout. Employée pour une catégorie
distincte, elle fait lire un écart comme une réussite (constaté sur « sans ancrage », dans la
publication).

**5. La bande vit dans des largeurs très différentes** — 900 px sous l'écran d'import, ~560 px dans une
modale. La légende **reflue**, les intitulés s'enroulent, et le résumé des motifs **assume** de s'abréger
puisque son contenu est à un clic. C'est le garde-fou anti-troncature des captures qui l'a imposé, dont
une fois en intégration continue seulement : ses métriques de police diffèrent de neuf pixels par entrée.

**Et la donnée que le lot devait ajouter est ajoutée** : le volume **lu sur la carte** n'était mesuré
nulle part. Il ne l'a pas fallu calculer — le garde-fou d'espace disque parcourait déjà les originaux
pour décider, il jetait le chiffre. Il le rend (`VolumesImport`).

## Notes pour l'implémentation

- **Composant présentationnel pur** dans `commun/view` : il reçoit un modèle de compte rendu et l'affiche. Il ne va rien chercher, ne décide de rien, et n'appartient à aucune feature.
- **Un modèle distinct de `CompteRendu`** : celui-ci (ADR 0031) porte des **phrases** - titre, constats, détail par sujet - et sert déjà quatre écrans et la CLI. Le compte rendu chiffré porte des **quantités** : les deux coexistent, et une même surface peut montrer l'un puis l'autre.
- **Les règles tenues par le modèle, pas par la vigilance** : une ventilation dont les segments ne font pas le total est **refusée à la construction**, ce qui contraint l'appelant à nommer le reliquat ; les largeurs se calculent depuis les quantités ; l'échelle des barres de volume est commune à l'ensemble.
- **Blocs facultatifs** : chaque bloc se masque quand il n'a rien à dire (cf. la variante « rien à signaler »).
- **Petits segments** : un segment minuscule reçoit une largeur minimale lisible **et** sa valeur en légende, jamais un arrondi silencieux à zéro.
- **Prérequis de donnée, à livrer avec le branchement import** : les octets écrits n'existent pas aujourd'hui. `RapportImport` porte les comptes par statut, `ResultatImport` le nombre d'originaux et de séquences ; les barres de volume supposent d'ajouter cette mesure au moteur d'import. C'est la seule donnée que ce lot ajoute.
- **Thème sombre** : aucune couleur de fond codée en dur hors feuille de style, pour que le composant survive au câblage de `DarkTheme.css` (#1037).
- **Icônes** : `FontIcon` Ikonli, pas d'emoji (règle #700).
- **Forme d'insertion** : panneau intégré sous l'écran d'origine pour l'import et le dépôt, qui gardent ainsi leur contexte ; la réactivation, déjà modale, y affiche la même bande.
