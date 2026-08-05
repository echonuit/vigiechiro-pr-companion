# M-Audit - Audit de cohérence

> **Type** : écran **« Audit de cohérence »**, carte d'action du prisme *Collecte & passages* de [M-Accueil](M-Accueil.md).
> **Persona principal** : [Samuel](../Personas/Samuel.md) (garder une base saine sur la durée, sur un volume que l'œil ne couvre plus).
> **Parcours couverts** : transverse. L'écran ne sert aucun parcours de bout en bout : il se consulte **quand un doute naît**, souvent après un import, un dépôt ou un changement de disque.
> **Épopées couvertes** : [E0.S11](../Story%20mapping/E0%20-%20Fondations%20de%20persistance.md#e0s11).
> **Issues** : #1133 (écran), #1254 (vérification en ligne), #1347 (audit ciblé et navigation vers le passage), #3100 (barre de filtres) - chantiers #1131 et #3092.

> **Trois vérités, trois endroits.** Le travail de l'observateur vit sur le **disque** (les fichiers audio), dans la **base** (ce que l'application en sait) et sur **Vigie-Chiro** (ce que la plateforme a reçu). Ces trois-là divergent, et le plus souvent **sans rien dire** : un disque débranché, un fichier renommé à la main, un dépôt inachevé. Cet écran existe pour que plus rien ne diverge en silence.

## Maquette principale - des écarts détectés

<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 430" role="img" aria-label="Maquette M-Audit - liste des écarts de cohérence détectés entre disque, base et serveur" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .search { fill: #ffffff; stroke: #c5cae9; stroke-width: 1; }
    .search-txt { fill: #9aa0b3; font: 13px sans-serif; }
    .resume { font: 600 14px sans-serif; fill: #2c3e50; }
    .resume-err { font: 600 14px sans-serif; fill: #a93226; }
    .btn { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .btn-off { fill: #f2f4f7; stroke: #dde1e6; stroke-width: 1; }
    .btn-txt { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
    .btn-txt-off { font: 12px sans-serif; fill: #9aa0b3; text-anchor: middle; }
    .tab { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .tab-txt { font: 12px sans-serif; fill: #3f51b5; text-anchor: middle; }
    .puce { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .puce-txt { font: 12px sans-serif; fill: #2c3e50; }
    .list-frame { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .list-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .list-row-alt { fill: #f6f8fa; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-lien { font: 12px sans-serif; fill: #1e5f8a; }
    .grav-err { font: 600 12px sans-serif; fill: #a93226; }
    .grav-info { font: 12px sans-serif; fill: #2c6fad; }
  </style>

  <rect x="0" y="0" width="1000" height="430" fill="#f7f9fb"/>
  <rect x="0" y="0" width="1000" height="44" class="chrome"/>
  <text x="20" y="28" class="chrometxt">VigieChiro Companion</text>
  <text x="210" y="28" class="crumb">Accueil  ›  </text>
  <text x="278" y="28" class="crumb-curr">Audit de cohérence</text>
  <rect x="780" y="12" width="200" height="22" rx="11" class="search"/>
  <text x="794" y="28" class="search-txt">Rechercher (Ctrl+F)</text>

  <!-- Resume chiffre + actions -->
  <text x="30" y="76" class="resume">2 écart(s) : </text>
  <text x="118" y="76" class="resume-err">1 erreur(s)</text>
  <text x="196" y="76" class="resume">, 0 avertissement(s), 1 info(s).</text>
  <rect x="600" y="60" width="118" height="24" rx="3" class="btn"/><text x="659" y="77" class="btn-txt">Relancer l'audit</text>
  <rect x="726" y="60" width="128" height="24" rx="3" class="btn"/><text x="790" y="77" class="btn-txt">Vérifier en ligne</text>
  <rect x="862" y="60" width="118" height="24" rx="3" class="btn-off"/><text x="921" y="77" class="btn-txt-off">Auditer ce passage</text>

  <!-- Onglets de vues memorisees -->
  <rect x="30" y="96" width="54" height="22" rx="3" class="tab"/><text x="57" y="111" class="tab-txt">+ Vue</text>

  <!-- Barre de filtres -->
  <rect x="30" y="128" width="250" height="24" rx="3" class="search"/>
  <text x="42" y="145" class="search-txt">Rechercher (cible, détail…)</text>
  <rect x="290" y="128" width="78" height="24" rx="3" class="puce"/><text x="302" y="145" class="puce-txt">+ Filtre</text>
  <rect x="862" y="128" width="118" height="24" rx="3" class="btn"/><text x="921" y="145" class="btn-txt">Tout effacer</text>

  <!-- Table des constats -->
  <rect x="30" y="166" width="950" height="230" class="list-frame"/>
  <rect x="30" y="166" width="950" height="26" class="list-head"/>
  <text x="42" y="184" class="head-txt">GRAVITÉ</text>
  <text x="140" y="184" class="head-txt">CATÉGORIE</text>
  <text x="330" y="184" class="head-txt">PASSAGE</text>
  <text x="410" y="184" class="head-txt">CIBLE</text>
  <text x="700" y="184" class="head-txt">DÉTAIL</text>

  <rect x="30" y="192" width="950" height="28" fill="#ffffff"/>
  <text x="42" y="211" class="grav-info">Information</text>
  <text x="140" y="211" class="cell">Audio incomplet</text>
  <text x="330" y="211" class="cell-lien">1</text>
  <text x="410" y="211" class="cell-lien">passage 1</text>
  <text x="700" y="211" class="cell">Audio absent (0/3 séquence(s) sur disque).</text>

  <rect x="30" y="220" width="950" height="28" class="list-row-alt"/>
  <text x="42" y="239" class="grav-err">Erreur</text>
  <text x="140" y="239" class="cell">Préfixe non conforme</text>
  <text x="330" y="239" class="cell-lien">1</text>
  <text x="410" y="239" class="cell">PaRec_20260622_213000.wav</text>
  <text x="700" y="239" class="cell">Nom sans le préfixe attendu du passage.</text>
</svg>

### Annotations

- **Le résumé chiffre, la table détaille** (`lblResume`). L'en-tête ventile les constats par gravité - « 2 écarts : 1 erreur, 0 avertissement, 1 info » - avant que l'utilisateur ne lise une seule ligne. Un audit se juge d'abord à sa forme : y a-t-il des erreurs, ou seulement des informations ?
- **⚠️ Le verdict porte sur l'audit ENTIER, jamais sur ce qui est filtré.** Poser une puce masque des lignes ; cela ne rend pas la base saine. Le résumé, le verdict et le **code de sortie** de la commande jumelle continuent donc de juger l'ensemble ([ADR 3092](https://companion-dev.echonuit.fr/decisions/3092-un-filtre-ne-change-que-ce-quon-regarde/)). Sans cette règle, `audit-coherence --gravite INFO` rendrait `0` sur un workspace abîmé, et un script d'intégration en conclurait que tout va bien.
- **Trois critères, aucun présélectionné** (#3100) : **Gravité**, **Catégorie**, **Passage**. Un audit se lit d'abord **en entier** ; on filtre ensuite pour travailler. C'est pourquoi cet écran n'a pas d'entorse comparable à celle de « Sons & validation », où deux puces filtrent dès leur ajout ([ADR 3099](https://companion-dev.echonuit.fr/decisions/3099-une-puce-preselectionnee-annonce-ce-quelle-filtre/)).
- **Aucune vue par défaut**, contrairement aux quatre autres écrans à puces. Une vue « Bloquants seulement » se dessine sur une distribution de gravités **réelle**, que la base de démonstration ne produit pas encore : la proposer sans l'avoir vue à l'œuvre serait deviner ce que l'observateur regarde en premier.
- **Le critère Passage se calcule sur les autres puces.** Lire la liste déjà filtrée ferait s'auto-effondrer la puce : une fois la nuit 42 cochée, le menu n'offrirait plus que 42 ([ADR 3095](https://companion-dev.echonuit.fr/decisions/3095-un-domaine-se-calcule-sans-son-propre-critere/)). Un constat qui ne cite **aucune** nuit - un fichier orphelin, un serveur injoignable - n'entre pas dans la liste des passages : il n'y aurait rien à y désigner.
- **Les gravités et catégories se lisent en français.** Les colonnes ont longtemps affiché `PREFIXE_NON_CONFORME` et `AVERTISSEMENT` : des identifiants de code au milieu d'une interface française. Chaque constante porte désormais son libellé, tiré de sa propre documentation.
- **« Auditer ce passage »** (`boutonAuditerPassage`, #1347) reste **désactivé** tant qu'aucun constat citant une nuit n'est sélectionné. Un bouton désactivé n'affiche pas d'infobulle : l'explication se pose sur son **enveloppe** (socle #789). Après avoir réparé une nuit, on veut vérifier **celle-là**, pas tout le workspace.
- **Le double-clic ouvre le passage accusé.** La table nommait le coupable et laissait l'utilisateur le retrouver à la main, alors que partout ailleurs une ligne de table s'ouvre au double-clic.
- **« Vérifier en ligne »** ajoute les constats **serveur** aux constats disque / base. L'appel réseau se fait derrière un voile d'occupation (#1254) : le bouton se grise le temps du traitement plutôt qu'un `setDisable` posé à la main.
- **Un bandeau de compte rendu** paraît quand des filtres n'ont pas pu être remis en place. Sur cet écran, le cas est la **règle** plutôt que l'exception : relancer l'audit renouvelle les constats, donc les passages qu'un filtre mémorisé désignait ([ADR 3093](https://companion-dev.echonuit.fr/decisions/3093-une-restauration-rend-compte-de-deux-causes/)).

## Variante - rien à signaler

<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 200" role="img" aria-label="Maquette M-Audit - variante base saine, aucun écart détecté" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome2 { fill: #3f51b5; }
    .chrometxt2 { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb2 { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr2 { fill: #ffffff; font: 700 13px sans-serif; }
    .resume2 { font: 600 14px sans-serif; fill: #1e8449; }
    .btn2 { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .btn-txt2 { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
    .frame2 { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .vide2 { font: 13px sans-serif; fill: #6a737d; text-anchor: middle; }
  </style>

  <rect x="0" y="0" width="1000" height="200" fill="#f7f9fb"/>
  <rect x="0" y="0" width="1000" height="44" class="chrome2"/>
  <text x="20" y="28" class="chrometxt2">VigieChiro Companion</text>
  <text x="210" y="28" class="crumb2">Accueil  ›  </text>
  <text x="278" y="28" class="crumb-curr2">Audit de cohérence</text>

  <text x="30" y="76" class="resume2">Cohérence disque / base : aucun écart détecté.</text>
  <rect x="600" y="60" width="118" height="24" rx="3" class="btn2"/><text x="659" y="77" class="btn-txt2">Relancer l'audit</text>
  <rect x="726" y="60" width="128" height="24" rx="3" class="btn2"/><text x="790" y="77" class="btn-txt2">Vérifier en ligne</text>

  <rect x="30" y="98" width="950" height="82" class="frame2"/>
  <text x="505" y="145" class="vide2">Aucun écart de cohérence détecté.</text>
</svg>

### Interactions clés

| Geste | Effet |
|---|---|
| **Relancer l'audit** | Recalcule les constats **disque / base**, hors ligne et rapide |
| **Vérifier en ligne** | Ajoute les constats **serveur** (unités absentes, localités divergentes) |
| **Double-clic** sur une ligne | Ouvre le **passage** que le constat accuse |
| **Sélection d'une ligne citant un passage** | Active **« Auditer ce passage »** |
| **« + Filtre » puis une puce** | Restreint **l'affichage** ; le résumé et le verdict ne bougent pas |
| **« Tout effacer »** | Retire les puces, la recherche **et** le tri, et oublie la mémoire de session |

## Notes pour l'implémentation

- La vue est **pur câblage** : `AuditController` lie la table et le résumé à `AuditViewModel`, qui expose **deux listes** - `constats()` (l'audit entier, d'où viennent le résumé et le verdict) et `constatsFiltres()` (ce que la table montre). Les brancher à l'envers est silencieux.
- La table se pose sur une `SortedList` **par-dessus** la liste filtrée. ⚠️ Une `FilteredList` posée nue est **non modifiable** : `TableView` renonce alors à trier et **vide son `sortOrder` en silence**.
- Les clés `gravite`, `categorie` et `passage` restent **chez cet écran** : elles ne sont partagées avec aucun autre, et `ClesCriteres` ne porte que le commun ([ADR 3096](https://companion-dev.echonuit.fr/decisions/3096-une-cle-de-critere-est-un-contrat-de-serialisation/)). ⚠️ `categorie` n'a **rien à voir** avec le `groupe` taxonomique des autres écrans.
- **En ligne de commande** : `audit-coherence [--passage <id>] [--gravite <niveau>] [--categorie <nature>] [--json] [--online]`. ⚠️ `--passage` **cadre l'audit** (il n'audite que cette nuit), là où la puce **filtre l'affichage** d'un audit global. Les deux répondent à « qu'est-ce qui cloche sur cette nuit ? », par deux chemins qui ne produisent pas exactement le même ensemble.
