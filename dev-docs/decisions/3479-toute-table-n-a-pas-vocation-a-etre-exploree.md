---
type: adr
title: "Toute table n'a pas vocation à être explorée : trois natures d'écran, un seul contrat"
status: stable
article: A23
chantier: "#3479, extrait de l'audit des filtres tabulaires du 31 juillet 2026 ([trace de conception](../audit-filtres-vues-tabulaires.md))"
decided_at: 2026-08-07
verification: humaine
verification_note: "décider si une table est exploratoire, bornée ou opérationnelle est un jugement sur son usage, pas un motif dans le code"
verified:
  - by: human:nedseb
    at: 2026-08-07
---

# Toute table n'a pas vocation à être explorée : trois natures d'écran, un seul contrat

## Contexte

L'application porte **quinze tables** (mesure de l'audit sur `dde1ac07b` : quatorze en FXML, une construite en Java). Trois d'entre elles - Sons & validation, Espèces & observations, Carte & passages - partagent un socle de filtrage complet : recherche permanente, critères en puces, vues mémorisées. Les autres non, et l'écart se lisait comme un défaut d'uniformité.

L'audit a montré que c'en était un **en partie seulement**. Sept contrats techniques étaient réellement à durcir, et ils l'ont été. Mais l'absence de barre sur la Qualification, la Synthèse ou les suivis d'import n'était pas un oubli : ces tables **suivent une action**, elles ne s'explorent pas.

Sans décision écrite, la question se repose à chaque nouvelle `TableView`, et la réponse par défaut est l'**imitation** : on regarde l'écran d'à côté et on copie. C'est le chemin qui mène à équiper une table transitoire d'un catalogue de critères que personne n'ouvrira, en ajoutant de la surface technique sans améliorer le parcours.

Le standard existait déjà, mais dans un **audit**. Un audit se lit une fois, au moment où on le reçoit ; un contrat se relit chaque fois qu'on ajoute un écran. C'est toute la différence, et c'est pour ça que cette ADR existe.

## Décision

### Trois natures, et c'est la nature qui décide

Avant de se demander *comment* filtrer, on décide *si* la table s'explore.

| Nature | Ce qui la définit | Ce qu'elle reçoit |
|---|---|---|
| **Exploratoire** | potentiellement volumineuse ; filtrer fait partie du travail | le contrat complet ci-dessous |
| **Analytique bornée** | le domaine limite naturellement le volume | quelques **paramètres métier** visibles, pas un catalogue générique |
| **Opérationnelle ou transitoire** | la table suit une action en cours | **rien**, et c'est délibéré |

Exemples au moment de la décision : Sons & validation, Espèces & observations, Carte & passages et l'Audit de cohérence sont exploratoires ; Ma saison et la Synthèse de la nuit sont analytiques bornées (leurs sélecteurs **recalculent** la synthèse, ce ne sont pas des filtres d'affichage) ; la Qualification, les suivis d'import et de dépôt, la modale de reconstruction sont opérationnelles.

**Une table opérationnelle ne reçoit pas la barre par souci d'homogénéité.** L'uniformité visée est celle des **règles**, pas celle des composants.

### Le contrat d'une table exploratoire

1. recherche permanente sur une liste de champs **documentée** ;
2. critères typés, composés en **ET** ;
3. activation prévisible **selon le type** : un booléen filtre dès son ajout, un seuil ou une plage affiche sa valeur initiale, une liste reste neutre jusqu'au choix ;
4. une action visible pour **retirer tous les filtres** ;
5. un compteur calculé sur le **sous-ensemble affiché** ;
6. un état vide qui distingue l'**absence de données** de l'**absence de résultat** ;
7. un export aligné sur le sous-ensemble **affiché** ;
8. une restauration qui **rend compte de toute perte de sens** - critère inconnu, valeur disparue, restauration amputée ;
9. une politique **explicite** de mémoire de session et de vues sauvegardées ;
10. des tests de contrat communs, complétés par les prédicats métier de chaque écran.

Un **raccourci fréquent** se porte par une vue par défaut, pas par une présélection surprenante dans l'éditeur d'une puce.

### Ce que le contrat protège, en une phrase

**Une puce visible a un effet réel, ou dit son erreur.** Le point 8 en est le cas limite : une vue enregistrée qui filtre moins qu'à son enregistrement, sans le dire, produit un résultat silencieusement trop large - le plus coûteux juste avant un export ou une revue ciblée.

## Conséquences

- Ajouter une `TableView` demande d'abord de **nommer sa nature**. C'est la question qui remplace « est-ce que je mets une barre ? ».
- Les dix points ne sont pas une cible : ils sont **acquis** au moment de cette ADR. « Tout effacer » vit dans onze fichiers, `GestionnaireFiltres` documente le compteur sur le sous-ensemble, `ActiviteViewModel` distingue les causes d'un état vide, l'export audio porte les observations affichées, et `aaa963e82` a rendu la restauration bavarde. Cette ADR **écrit ce qui est garanti**, elle ne demande pas un chantier.
- Les catalogues de critères sont ancrés au code par les balises `<!--inv:criteres-*-->` : le point 10 a déjà sa part vérifiée automatiquement.

**Aucune loupe, délibérément.** Une vérification `humaine` peut s'adjoindre une **loupe** ([ADR 2465](2465-une-adr-declare-comment-elle-est-verifiee.md)) : un script qui surface une surface de revue sans jamais bloquer. Ici, lister les `TableView` sans barre de filtres rendrait une demi-douzaine de candidats, **tous légitimes**. C'est la loupe bruyante que la discipline du dépôt écarte (#2468) : un listing que personne ne lit est un faux vert. Le jugement de nature reste humain parce qu'il porte sur l'**usage**, que le code ne montre pas.

## Alternatives écartées

- **Équiper toutes les tables.** Uniformise les composants et désaligne les parcours : une barre sur une table de suivi d'import ajoute un catalogue à maintenir devant une liste qu'on regarde défiler.
- **Laisser la règle implicite dans le socle.** C'est l'état d'avant : le socle rendait le filtrage facile, rien ne disait quand s'en abstenir, et l'imitation décidait à la place de l'usage.
- **Garder le standard dans l'audit.** Il y était complet et bien argumenté. Mais un audit porte une **date** et un périmètre ; le relire pour trancher une question de conception revient à demander à chacun de retrouver un document de circonstance. La trace de conception reste, la règle en sort.
