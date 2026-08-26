# Audit des filtres des vues tabulaires

**Date de l'audit initial :** 31 juillet 2026  
**Révision examinée :** `dde1ac07b`  
**Suivi :** 7 août 2026 sur `d1695bc44`  
**Périmètre :** vues JavaFX contenant une `TableView`, filtres associés, vues mémorisées,
documentation utilisateur et captures générées du dépôt.

!!! success "Audit historique, un seul point encore ouvert"
    Ce document conserve le diagnostic posé sur `dde1ac07b`. Entre cet audit et la révision de suivi,
    le chantier d'uniformisation a corrigé l'essentiel des risques relevés : restauration complète,
    listes cascadées, clés sémantiques, fabriques communes, mémoire de session, action « Tout effacer »,
    filtres de l'Audit et garde documentaire. La section suivante relie chaque constat à son traitement.

    Il reste **l'unicité des clés d'un catalogue** ([#3492](https://github.com/echonuit/vigiechiro-pr-companion/issues/3492)), et une **surveillance** de volumétrie sur la fiche site. Le reste est traité, décidé ou assumé.

## Synthèse

L'application possède déjà un **socle de filtrage commun et solide**, employé par les trois grandes
vues exploratoires : **Sons & validation**, **Espèces & observations** et **Carte & passages**. Ces
écrans partagent le même patron : recherche permanente, bouton « + Filtre », critères sous forme de
puces et vues mémorisées.

L'impression de manque d'uniformité reste néanmoins fondée :

- le socle n'est pas appliqué selon une politique explicite à toutes les tables qui pourraient en
  bénéficier ;
- deux barres visuellement semblables n'ont pas toujours les mêmes règles d'activation, de remise à
  zéro ou de mémorisation ;
- quelques contrats techniques peuvent rendre une vue enregistrée plus large qu'annoncé ;
- plusieurs concepts communs sont dupliqués entre fonctionnalités ;
- la documentation a déjà commencé à diverger du catalogue réel.

Il ne s'agit donc pas de remplacer le mécanisme existant, mais de **formaliser son domaine d'emploi et
de durcir ses contrats**.

## Suivi des constats au 7 août 2026

| Constat initial | État | Traitement |
|---|---|---|
| Critère disparu ignoré à la restauration | **Corrigé** | `aaa963e82` : la restauration rend désormais critères inconnus et valeurs perdues |
| Domaines de valeurs figés à l'ouverture | **Corrigé** | `45a821951` et `320871d2d` : listes cascadées sur les autres critères |
| Activation différente selon le type de critère | **Assumée** | distinction conservée entre booléen, seuil/plage et liste ; les raccourcis restent aussi portés par les vues par défaut |
| Réinitialisation et mémoire différentes | **Corrigé** | `bb695a9bc` : mémoire commune sur les quatre écrans initiaux et action « Tout effacer » |
| Année invalide mais puce apparemment active | **Corrigé** | `013f76e39` : une année illisible n'est plus décrite comme un filtre actif |
| Clés ambiguës et critères communs dupliqués | **Corrigé** | `7f1654520`, `0092b522d` et `cfbfdb059` : clés par concept, critère booléen et critère Lieu mutualisés |
| Documentation désynchronisée | **Corrigé et gardé** | `8c4990d8e` : les catalogues sont ancrés dans `DocumentationAJourTest` |
| Audit de cohérence sans filtres | **Corrigé** | `3d4a4c39a` : recherche, Gravité, Catégorie, Passage, vues et mémoire de session |
| Ma saison sans recherche ni « Reste à faire » | **Corrigé** | #3103 : recherche de lieu et case « Reste à faire », écrites une fois dans `FiltresSaison` |
| Ne pas généraliser aux tables opérationnelles | **Décidé** | [ADR 3479](decisions/3479-toute-table-n-a-pas-vocation-a-etre-exploree.md) : trois natures d'écran, et c'est la nature qui décide |
| Résumé de Ma saison sur le sous-ensemble affiché | **Écarté** | divergence assumée et écrite dans `SaisonController` : « chercher un lieu ne change pas ce qu'il y a à faire cette année ». Cohérent avec l'ADR 3479, qui classe cet écran en **analytique borné** et non en exploratoire |
| Unicité des clés d'un catalogue | **Ouvert** | [#3492](https://github.com/echonuit/vigiechiro-pr-companion/issues/3492) : `ClesCriteres` a levé l'ambiguïté entre écrans, mais rien ne refuse deux critères de même clé dans un même catalogue |
| Fiche site sans filtre | **Surveillance** | volontaire tant que la liste des passages reste bornée ; une recherche légère avant d'introduire les vues mémorisées |

Ce tableau a lui-même sous-déclaré son avancement : deux lignes (Ma saison, tables opérationnelles)
décrivaient des travaux **faits** que le suivi ne mentionnait pas, et l'unicité des clés, seul reste
réel, n'y figurait pas du tout. Le vérifier a demandé d'ouvrir le code, pas de relire le tableau.

### Les critères de réussite, un par un

L'audit se ferme sur sept critères. Six sont tenus ; le premier a été **reformulé** plutôt qu'atteint.

| Critère | État |
|---|---|
| Deux contrôles de même apparence ont la même règle d'activation | **reformulé** : la distinction par type est conservée et **écrite** (ADR 3479, point 3). L'audit visait l'uniformité du geste, le produit a choisi la prévisibilité de la règle |
| Toute puce visible a un effet réel ou affiche son erreur | tenu (`013f76e39`) |
| Aucune restauration amputée ne reste silencieuse | tenu (`aaa963e82`) |
| Même chemin de remise à zéro sur les vues exploratoires | tenu (`bb695a9bc`) |
| Vocabulaire des clés non ambigu **et testé** | **à moitié** : non ambigu (`7f1654520`), pas encore testé (#3492) |
| Le catalogue documenté correspond au catalogue présenté | tenu et **gardé** (`8c4990d8e`) |
| Les tables opérationnelles restent volontairement simples | tenu, et devenu une décision (ADR 3479) |

Les sections suivantes restent formulées comme au moment de l'audit : elles expliquent les défauts qui
ont motivé ces changements et servent de trace de conception.

## Méthode et périmètre

L'inventaire trouve **15 tables** : 14 déclarées dans les fichiers FXML et une créée en Java dans
`TableNuits`. Une seule barre peut piloter plusieurs tables : c'est notamment le cas d'Analyse, où les
trois tables reposent sur le même sous-ensemble d'observations.

L'audit distingue trois natures d'écran :

1. **exploratoire et potentiellement volumineux** : filtrer fait partie du travail principal ;
2. **analytique mais fortement borné** : quelques paramètres métier valent mieux qu'un catalogue
   générique ;
3. **opérationnel ou transitoire** : la table suit une action et n'a pas vocation à être explorée.

Cette distinction évite de conclure que toute `TableView` doit mécaniquement recevoir une barre de
filtres.

## Inventaire par vue à la révision auditée

| Vue | Mécanisme observé sur `dde1ac07b` | Évaluation initiale |
|---|---|---|
| **Sons & validation** | Recherche, 10 critères, puces, 4 vues par défaut, vues utilisateur et mémoire de session | Référence actuelle |
| **Espèces & observations** | Recherche, 5 critères, puces, 6 vues par défaut et vues utilisateur | Conforme au socle |
| **Carte & passages** | Recherche, 7 critères, puces, 6 vues par défaut, vues utilisateur et bouton de réinitialisation | Conforme, avec des particularités |
| **Ma saison** | Sélecteurs fixes Année et Campagne | Cohérent avec le domaine, mais limité si le nombre de points augmente |
| **Synthèse de la nuit** | Bascule « Identifications validées seulement » et choix du milieu | Écart justifié : ces contrôles recalculent la synthèse |
| **Audit de cohérence** | Aucun filtre | Lacune principale parmi les tables exploratoires |
| **Fiche site** | Aucun filtre sur les passages du site | Acceptable tant que la liste reste naturellement bornée |
| **Qualification** | Aucun filtre sur la sélection d'écoute | Justifié : table opérationnelle d'un passage |
| **Importation, dépôt et reconstruction** | Aucun filtre sur les tables de suivi ou de sélection | Justifié : tables transitoires liées à une action |

Les captures illustrent clairement ces familles :

- [Sons & validation avec une puce active](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/.github/assets/apercu-sons-validation-filtres.png) ;
- [Carte & passages avec un filtre de carré](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/.github/assets/apercu-multisite-filtre.png) ;
- [Espèces & observations](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/.github/assets/apercu-analyse.png) ;
- [Ma saison et ses deux sélecteurs fixes](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/.github/assets/apercu-saison.png) ;
- [Synthèse et sa bascule métier](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/.github/assets/apercu-synthese.png) ;
- [Audit sans outil de filtrage](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/.github/assets/apercu-audit.png).

## Ce qui est déjà bien uniformisé

Le composant [`GestionnaireFiltres`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/commun/view/GestionnaireFiltres.java)
centralise correctement :

- la recherche texte permanente ;
- la composition des prédicats ;
- le menu des critères encore disponibles ;
- la création et le retrait des puces ;
- la description sémantique d'un état de filtres ;
- la restauration d'un état mémorisé.

[`CritereListe`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/commun/view/CritereListe.java) fournit en outre des
éditeurs réutilisables : choix simple, choix multiple, valeurs d'énumération et présélection explicite.
[`GestionnaireVues`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/commun/view/GestionnaireVues.java) complète le
dispositif par les vues nommées et la mémorisation des colonnes.

Les trois catalogues principaux reposent effectivement sur ce socle :

- [`CriteresAudio`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/audio/view/CriteresAudio.java) ;
- [`CriteresAnalyse`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/analyse/view/CriteresAnalyse.java) ;
- [`CriteresMultisite`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/multisite/view/CriteresMultisite.java).

La vue non tabulaire **Activité de la nuit** réutilise également le même composant, ce qui montre que
le modèle sait filtrer une collection métier sans dépendre directement d'une `TableView`.

## Constats prioritaires

### 1. Un critère disparu est ignoré silencieusement lors d'une restauration

`GestionnaireFiltres.restaurer` signale les **valeurs** qu'un critère connu n'a pas réussi à replacer,
mais ignore entièrement un critère dont la clé n'existe plus dans le catalogue. L'appel à
`critereParNom(...).ifPresent(...)` n'a pas de branche d'échec.

Le compte rendu de `GestionnaireVues` ne reçoit alors aucune anomalie. Après une évolution de
l'application, une vue enregistrée peut donc filtrer **moins** qu'au moment de son enregistrement tout
en conservant le même nom.

**Risque :** résultat silencieusement trop large, particulièrement gênant avant un export ou une revue
ciblée.

**Recommandation :** remplacer la simple liste de valeurs disparues par un résultat structuré contenant
au minimum :

- les critères inconnus ;
- les valeurs disparues des critères connus ;
- les éventuelles valeurs invalides ;
- le caractère complet ou amputé de la restauration.

Le même principe devrait s'appliquer à `GestionnaireFiltres.poser`, qui documente déjà le fait qu'il
jette une valeur non reconnue.

### 2. Les domaines de valeurs sont des instantanés, pas des listes réellement cascadées

Les fournisseurs de lieux, groupes ou espèces sont évalués lors de la construction de l'éditeur de la
puce. Modifier ensuite un autre filtre ne reconstruit pas sa liste.

Deux situations sont alors possibles :

- la liste continue de proposer des valeurs qu'un autre filtre vient de rendre impossibles ;
- une puce ouverte dans un sous-ensemble restreint ne récupère pas les valeurs qui redeviennent
  disponibles lorsque l'autre filtre est retiré.

Le comportement se situe à mi-chemin entre un domaine **stable** calculé sur toute la source et un
domaine **réactif** calculé sur le sous-ensemble courant.

**Recommandation :** choisir et documenter une seule politique :

- soit des domaines stables issus de la source complète, plus simples et prévisibles ;
- soit de vraies listes cascadées, réactualisées à chaque changement tout en préservant les sélections
  encore valides.

### 3. Une puce nouvellement ajoutée n'a pas une sémantique uniforme

La règle générale de `CritereListe` est : **ajouter une puce à liste n'écarte rien tant qu'aucune valeur
n'est choisie**. Plusieurs critères Audio font exception :

- Statut sélectionne immédiatement « À revoir » ;
- Taxon parent sélectionne immédiatement « Chiroptères » quand il existe ;
- Proba applique immédiatement 50 % ;
- Heure applique immédiatement la plage nocturne.

Les critères booléens filtrent eux aussi dès leur ajout, ce qui est naturel puisque leur présence porte
leur valeur.

Le problème n'est pas l'existence d'une valeur par défaut, mais l'absence de règle perceptible depuis
l'interface.

**Recommandation :** formaliser les catégories suivantes :

- booléen : actif dès l'ajout ;
- seuil ou plage : actif avec une valeur initiale explicitement affichée ;
- liste ou énumération : neutre jusqu'au choix ;
- raccourci fréquent : porté par une vue par défaut, plutôt que par une présélection surprenante dans
  l'éditeur.

Les vues « À valider » et « Chiroptères » existent déjà et peuvent porter ces raccourcis.

### 4. Réinitialisation et mémoire de session diffèrent entre écrans similaires

Seule la vue Carte & passages affiche un bouton **Réinitialiser**, alors que les trois écrans utilisent
le même gestionnaire et possèdent tous une vue « Tout ».

Sons & validation possède en plus une mémoire automatique du tri et des filtres via
[`MemoireRevueAudio`](https://github.com/echonuit/vigiechiro-pr-companion/blob/dde1ac07b/src/main/java/fr/univ_amu/iut/audio/view/MemoireRevueAudio.java). Analyse et
Multisite ont des vues sauvegardées, mais pas cette mémoire libre de session.

Ces écarts peuvent être justifiés par le besoin de reprendre une revue audio, mais ils doivent relever
d'une politique explicite plutôt que du câblage propre à chaque contrôleur.

**Recommandation :**

- exposer « Tout effacer » de la même façon sur chaque barre commune ;
- décider si la mémoire de session est un comportement du socle ou une capacité opt-in clairement
  documentée ;
- distinguer visuellement « revenir à Tout » et « restaurer ma dernière vue libre ».

### 5. Une valeur invalide peut laisser une puce active mais neutre

Le critère Année de Carte & passages utilise un champ texte. Une saisie non numérique produit un
prédicat neutre sans signaler l'erreur. La puce semble donc active mais n'écarte aucune ligne ; sa
description mémorisée ne contient même plus la valeur invalide.

**Recommandation :** utiliser un `TextFormatter<Integer>`, une liste des années présentes ou un état de
validation visible. Un filtre affiché comme actif ne doit jamais échouer silencieusement à filtrer.

### 6. Les clés sémantiques ne forment pas encore un vocabulaire sûr

Les clés sont persistées dans les vues enregistrées et servent parfois au transport entre écrans. Elles
constituent donc une API interne versionnée.

Or :

- `statut` désigne un statut d'observation dans Audio et Analyse, mais un statut de workflow dans
  Multisite ;
- `lieu` porte bien un concept partagé, mais ses helpers sont réécrits dans plusieurs fonctionnalités ;
- les conventions de nommage alternent, par exemple entre `a_enjeu`, `non_identifie` et `natureNuit` ;
- le gestionnaire ne vérifie pas à la construction que deux critères d'un même catalogue ont des clés
  uniques.

**Recommandation :** introduire des clés partagées seulement lorsque la sémantique est réellement la
même, par exemple `statut_observation` et `statut_workflow`, et valider l'unicité du catalogue au
démarrage.

### 7. La documentation dérive du catalogue réel

Le catalogue Audio contient dix critères, mais la table de la documentation utilisateur omet
**Douteux** et **Non identifiés**. Plusieurs commentaires de contrôleurs annoncent encore deux ou quatre
critères alors que leur liste en contient davantage.

Cette dérive est un symptôme de la dispersion des métadonnées : la clé, le libellé, la valeur initiale,
la description métier et la documentation ne vivent pas ensemble.

**Recommandation :** enrichir le descripteur de critère avec ses métadonnées stables, puis au minimum
tester les catalogues attendus. Une génération partielle des tableaux documentaires peut être envisagée,
mais un test de contrat sur les clés et libellés apporterait déjà un garde-fou peu coûteux.

## Vues à faire évoluer

### Audit de cohérence : priorité fonctionnelle

L'Audit est une vue globale et non bornée. Ses colonnes correspondent directement à des dimensions de
filtrage utiles :

- Gravité ;
- Catégorie ;
- Passage ;
- Cible et détail en recherche libre.

Il devrait être le premier écran supplémentaire à adopter le socle commun. Des vues par défaut
« Erreurs », « Avertissements » et éventuellement « Avec passage » répondraient aux tâches courantes
sans masquer l'inventaire complet.

### Ma saison : à confirmer par la volumétrie et les usages

Année et Campagne sont des **paramètres de périmètre** cohérents et doivent rester visibles. Pour une
grande saison, deux filtres complémentaires pourraient devenir utiles :

- recherche par carré, point ou site ;
- filtre rapide « Reste à faire ».

Le résumé doit continuer à être calculé sur exactement le même sous-ensemble que la table.

### Tables contextuelles et opérationnelles : ne pas généraliser sans besoin

La Synthèse, la Qualification, les suivis d'import/dépôt et la modale de reconstruction ne doivent pas
recevoir le patron complet par simple souci d'homogénéité. Leur périmètre est court ou leur table sert une
action précise. Ajouter une barre y augmenterait la surface technique sans améliorer le parcours.

La table des passages d'une fiche site mérite seulement une surveillance de volumétrie. Si elle devient
difficile à parcourir, une recherche légère peut suffire avant d'introduire les vues mémorisées.

## Standard cible proposé

!!! success "Ce standard est devenu une décision"
    Il ne se lit plus ici pour être appliqué : il vit dans
    [l'ADR 3479](decisions/3479-toute-table-n-a-pas-vocation-a-etre-exploree.md), avec la taxonomie
    des trois natures qui décide **qui** doit le respecter. Un audit se lit une fois, un contrat se
    relit à chaque écran ajouté. La section ci-dessous reste la formulation d'origine, comme trace
    de conception.


Une table **exploratoire ou potentiellement volumineuse** devrait respecter le contrat suivant :

1. recherche permanente sur une liste de champs explicitement documentée ;
2. critères typés et composés en ET ;
3. activation prévisible selon le type de critère ;
4. action visible pour retirer tous les filtres ;
5. compteur calculé sur le sous-ensemble affiché ;
6. état vide distinguant l'absence de données de l'absence de résultat ;
7. export aligné sur le sous-ensemble affiché ;
8. restauration qui rend compte de toute perte de sens ;
9. politique explicite de mémoire de session et de vues sauvegardées ;
10. tests de contrat communs, complétés par les prédicats métier de chaque écran.

Les autres tables peuvent rester sans filtres dès lors que leur caractère **borné, contextuel ou
opérationnel** est assumé.

## Plan de traitement recommandé

### Étape 1 : sécuriser les contrats

- rendre visibles les critères inconnus et les valeurs perdues à la restauration ;
- faire remonter les échecs de `poser` ;
- refuser les clés dupliquées dans un catalogue ;
- valider les valeurs saisies, notamment l'année ;
- ajouter les tests de non-régression correspondants.

### Étape 2 : fixer la politique commune

- décider domaine stable ou liste réellement cascadée ;
- uniformiser la règle d'activation ;
- uniformiser la remise à zéro ;
- décider ce qui est mémorisé automatiquement entre deux ouvertures.

### Étape 3 : réduire la duplication

- extraire une fabrique de critère booléen ;
- extraire une fabrique de champ texte ou numérique validé ;
- mutualiser les dimensions et libellés géographiques ;
- centraliser les clés réellement partagées ;
- conserver dans chaque fonctionnalité uniquement son prédicat métier.

### Étape 4 : étendre avec discernement

- équiper d'abord l'Audit ;
- mesurer le besoin sur Ma saison et la fiche site ;
- ne pas équiper les tables transitoires sans scénario utilisateur concret.

### Étape 5 : remettre la documentation sous contrôle

- corriger les catalogues documentés ;
- supprimer les commentaires périmés ;
- ajouter un test de contrat sur les critères exposés par chaque vue.

## Critères de réussite

L'harmonisation pourra être considérée comme terminée lorsque :

- deux contrôles de même apparence auront la même règle d'activation ;
- toute puce visible aura un effet réel ou affichera clairement son erreur ;
- aucune restauration amputée ne pourra rester silencieuse ;
- toutes les vues exploratoires offriront le même chemin de remise à zéro ;
- le vocabulaire des clés persistées sera non ambigu et testé ;
- le catalogue documenté correspondra au catalogue réellement présenté ;
- les tables opérationnelles resteront volontairement simples.
