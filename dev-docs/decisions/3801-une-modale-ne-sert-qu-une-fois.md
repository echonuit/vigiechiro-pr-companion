---
type: adr
title: "Une modale ne sert qu'une fois, et le dit"
status: stable
article: A23
chantier: "#3801, dette de la clôture des suites de #3458"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "SiteEditCycleDeVieTest#une_seconde_preparation_est_refusee"
verified:
  - by: machine:ci
    at: 2026-08-16
---

# Une modale ne sert qu'une fois, et le dit

## Contexte

PIT laissait **douze mutants** sur `SiteEditViewModel`, dont **neuf** sur la réinitialisation des champs
de `preparerCreation` / `preparerEdition` : supprimer n'importe laquelle de ces affectations ne faisait
rougir personne.

La cause n'était pas un test manquant. `NavigationSites` recharge le FXML à chaque ouverture : le
ViewModel est **neuf**, ses champs portent déjà leur valeur de départ, et ces lignes réécrivaient ce qui
était déjà en place.

## Ce que la mesure a démenti

L'issue posait que « la production n'ouvre jamais deux fois la même instance », et en concluait que le
code de réinitialisation ne servait à rien. C'est vrai de la **production**. Ce ne l'était pas du
**dépôt** : **trois** tests repréparaient une instance, dont deux qui en faisaient un contrat explicite.

| Test | Ce qu'il exigeait |
|---|---|
| `SiteEditPorteeTest#creation_ne_dit_rien` (#1380) | le message de portée **disparaît** quand la même modale sert à déclarer |
| `SiteEditRechercheCarreTest#preparer_creation_efface_le_verdict` (#3458) | le verdict du carré **disparaît** de même |
| `SiteEditRechercheCarreTest#en_edition_le_verdict_n_empeche_rien` (#3806) | rien : il repréparait par **commodité** d'assemblage |

Le contrat de réutilisation n'était donc pas une hypothèse à écarter : il était **écrit**, et deux fois.

## Décision

**Un ViewModel de modale se prépare une fois, et refuse la seconde.**

`preparerCreation` et `preparerEdition` lèvent une `IllegalStateException` si le mode a déjà été choisi.
Le refus est **explicite** plutôt que silencieux : sans lui, une seconde préparation laisserait les
champs de la précédente en place, et l'écran mentirait sans que rien ne le signale.

⚠️ **Cela renverse #1380**, et ce renversement est le point de cette ADR. Ce que #1380 garantissait -
« une déclaration ne parle pas de portée » - reste garanti ; c'est le **chemin** par lequel il
l'éprouvait qui disparaît, au profit de l'état où la production met réellement l'écran.

### Ce qui remplace le code retiré

`preparerCreation` ne pose plus que le **titre** : tout le reste est déjà l'état construit. Ce que la
réinitialisation garantissait est donc reporté sur un cas qui lit cet état de départ
(`un_view_model_neuf_est_deja_en_declaration`), lequel fait rougir quiconque changerait une valeur par
défaut en croyant que `preparerCreation` la rattrape.

### Ce que la simplification a révélé, et qu'on n'avait pas prévu

Retirer ces affectations a fait **tomber le ViewModel sous le seuil de cohésion** du portail qualité
(`GodClass`, TCC 13,5 %). Le réflexe eût été de taire l'avertissement ; le dépôt l'interdit, et il avait
raison de le refuser : la mesure ne se plaignait pas de la simplification, elle rendait **visible** ce
que les longues méthodes masquaient. `preparerCreation` touchait neuf champs, donc « reliait » deux
sujets qui n'ont rien à se dire.

Le versant **« ce carré existe-t-il là-bas ? »** (deux ports optionnels, un verdict, un geste) est donc
extrait dans `CarreExistantViewModel`. Il ne lit du formulaire que le numéro qu'on lui passe.

⚠️ **Deux points d'entrée restent** côté ViewModel - `chercherCarreExistant` et `rapatrierCarre` -
parce que c'est la **saisie** qui décide s'il y a lieu d'appeler le réseau : un carré incomplet ne fait
partir aucune requête. Les déplacer aurait rendu cette garde inatteignable depuis les données qui la
fondent.

## Conséquences

- **PIT : 59/71 tués (83 %) → 72/74 (97 %)**, et les neuf mutants de réinitialisation ont disparu **avec
  le code**, ce qui vaut mieux que d'être tués.
- ⚠️ **Un faux vert a été pris au passage, par la remesure et non par la relecture.** Un premier cas
  écrit pour `effacerRetour` partait d'un carré **invalide** : `enregistrer` refusait avant d'écrire, si
  bien que le test comparait `AUCUN` à `AUCUN`. Il passait, et la ligne qu'il prétendait tenir se
  supprimait sans faire rougir personne.
- ⚠️ **Deux mutants restent, sur le rapatriement**, marqués `NO_COVERAGE` alors que
  `ModaleSiteVerifierCarreViewTest` les exerce : **PIT n'attribue pas ce qui s'exécute sur le fil
  JavaFX**. C'est un artefact de ciblage, pas une lacune - et le dire évite qu'on écrive un test creux
  pour faire tomber un chiffre.
- **Le coût** : un assembleur de test de plus quand un cas veut une modale d'édition. C'est le prix
  d'un contrat qui dit la vérité.

## Alternatives écartées

- **Éprouver la réutilisation** (un test qui prépare une édition puis une création tue les neuf d'un
  coup) : garantit un contrat que **personne n'exerce**, et qu'il faudrait maintenir à chaque champ
  ajouté.
- **Retirer les réinitialisations sans garde** : le mono-usage deviendrait une **hypothèse tacite**, et
  la première réutilisation produirait un écran à moitié rempli, en silence.
- **Ne rien faire** : douze mutants et deux lectures possibles du cycle de vie, laissées au prochain
  lecteur.
