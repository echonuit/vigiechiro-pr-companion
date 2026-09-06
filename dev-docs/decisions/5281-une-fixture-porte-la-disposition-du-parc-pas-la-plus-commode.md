---
type: adr
title: "Une fixture porte la disposition du parc"
status: stable
article: A9
chantier: "#5357 (les suites de la convergence des bancs filmés), lot #5281"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "DispositionDesBrutsTest#par_defaut_les_wav_sont_a_la_racine"
  - "DispositionDesBrutsTest#le_champ_est_lu_depuis_le_yaml"
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Une fixture porte la disposition du parc

## Le défaut, et ce qui le rendait discret

Les enregistreurs déposent leurs WAV **à la racine** de la carte SD. Les seize specs de recette
produisaient pourtant un sous-dossier `bruts/`, **sans qu'aucune ne puisse en décider** : le champ
n'existait pas.

Le produit est correct : `InspecteurDossier` accepte les deux dispositions, et ses tests unitaires
couvrent les deux branches.

```java
Path bruts = dossierSource.resolve(SOUS_DOSSIER_BRUTS);
Path ou = Files.isDirectory(bruts) ? bruts : dossierSource;
```

**Le `else` est le cas du parc, et il n'était joué par aucune recette.** Toute régression qui ne se
serait manifestée qu'à la racine traversait la recette entière sans rougir. Et les parcours filmés
montraient une carte que le lecteur n'a pas sous les yeux.

## La décision

**Le défaut de la spec est la disposition du PARC**, pas celle qui est commode à engendrer. Une spec
qui ne dit rien décrit ce qu'un enregistreur produit.

**Une seule carte garde `bruts/`.** La disposition existe chez ceux qui rangent, le produit la gère
exprès, et la retirer entièrement échangerait un trou contre un autre. Le choix est tombé sur
`sd-prefixee` : ses bruts sont **déjà préfixés**, c'est la carte de quelqu'un qui a organisé.

## Ce que la décision oblige ailleurs

**Un contrôle qui sert le produit ne peut pas être plus strict que lui.** `carte_utilisable`, dans le
banc de documentation, **exigeait** `bruts/` et rejetait donc une carte du parc réel. Les WAV s'y
cherchent maintenant partout sous la carte.

## Conséquences

**Un champ lu d'un fichier demande DEUX familles de cas.** Trois cas éprouvaient le champ en
construisant la spec à la main : aucun ne traversait `LecteurSpec`. Une mutation qui faisait ignorer
la clé du YAML ne faisait rougir personne - `sd-prefixee` retombait à plat, la carte restait
inspectable. Le quatrième cas, ajouté à la clôture, lit le vrai fichier.

**Ce que deux cliquets verrouillent n'était pas ce qu'on croyait.** L'issue annonçait que
`GenerationCartesSDCliquetTest` et `GenerationCartesSDImportCliquetTest` figeaient l'arbre « à l'octet
près », et rougiraient « par construction ». Ils sont restés verts : le premier vérifie que
l'inspection constate la pathologie déclarée, le second qu'un import réel rejette les faux WAV.
**Aucun ne fige la disposition.** Une clause du critère de fin reposait donc sur une hypothèse fausse.

**La branche du parc est enfin jouée** : `ScenarioImportNominalTest` passe sur une carte plate, ce
qu'aucun cas de recette ne faisait avant.
