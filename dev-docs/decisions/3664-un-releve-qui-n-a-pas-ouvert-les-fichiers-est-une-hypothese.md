# ADR 3664 - Un relevé qui n'a pas ouvert les fichiers est une hypothèse

- **Statut** : Accepté - 2026-08-13
- **Chantier** : #3664, suites des lots 1 et 2 du chantier #3518
- **Vérification** : certaine - `ParcoursDeDossierTest#tout_parcours_rattrape_l_echec_de_parcours`

## Contexte

Trois suites, trois issues. **Deux portaient une prémisse fausse, et les deux étaient de moi.**

**#3632 annonçait trois sites**, relevés en lisant le code autour du défaut connu. Il y en avait
**neuf**, et le plus grave n'était pas dans les trois : `effacerAuMieux`, dont le doc-comment promet
« ne lève jamais », levait depuis des `finally` où une exception **remplace** le résultat de
l'opération. Ce n'est pas une relecture plus attentive qui l'a trouvé, c'est le **cliquet** : posé, il
a immédiatement nommé deux fichiers que j'avais classés cosmétiques.

**#3661 affirmait l'exact contraire de la réalité** : « onze scripts de CI rendent des jugements, un
seul est éprouvé ». **Dix sur onze portent un `--auto-test`**, lancés par `lint.yml`, avec des cas
substantiels qui vont jusqu'aux contrôles négatifs. J'avais cherché les tests dans `src/test` et dans
les chemins cités par les workflows - donc partout sauf **à l'intérieur** des scripts, où cette
discipline vit depuis #2947.

Ces deux-là s'ajoutent à trois autres du même jour : les deux constats du lot 2 (#3515 sur un « faux
positif » qui n'en était pas, #3508 sur une variance mesurée sur sept exécutions quand trente en
révélaient deux au double de la médiane), et le relevé initial de #3627, élargi par #3634.

**Cinq sur cinq.**

## Décision

**Un relevé se rejoue avant de fonder une décision, et un `grep` qui ne trouve rien est une hypothèse,
jamais un constat.**

Ce n'est pas de la prudence, c'est une étape : elle a changé le **dispositif** et pas seulement le
chiffre, quatre fois sur cinq.

- #3508 : une variance re-mesurée a écarté le butoir au profit de deux médianes glissantes ;
- #3515 : un « faux positif » vérifié s'est révélé un artefact réellement inutile, dont le retrait
  réglait deux lignes du rapport d'un coup ;
- #3632 : de trois sites à neuf, dont un dixième - `BancImport` - que j'avais dispensé en invoquant
  une ADR qui ne dispense pas de cela ;
- #3661 : la discipline existait, et l'issue proposait de la construire.

### Comment on rejoue, selon ce qu'on cherche

| Ce qu'on veut savoir | Ce qui répond | Ce qui ne répond pas |
|---|---|---|
| « ce comportement est-il bien celui-là ? » | une **sonde** jetable qui l'exécute | lire la signature |
| « combien de sites ? » | un **cliquet** qui les énumère | un `grep` sur le symptôme |
| « est-ce couvert ? » | **ouvrir** les fichiers, ou muter | chercher les tests là où on croit qu'ils sont |
| « ce chiffre tient-il ? » | le **re-mesurer** sur un échantillon plus large | citer le relevé daté |

### Ce qui a été écarté

**Ajouter une case « relevé rejoué » au cycle de chantier.** Le cycle a déjà douze passes ; une case de
plus se coche sans se faire. Ce qui a mordu, ce sont des **dispositifs** - un cliquet qui nomme les
fichiers, une mutation qui désigne un chemin non couvert, une sonde qui contredit une signature. La
décision porte donc sur le geste, pas sur une case.

## Conséquences

- `ParcoursDeDossierTest` : tout `Files.walk` du produit rattrape l'`UncheckedIOException`, **sans
  aucune exclusion**. La dispense que j'avais écrite invoquait l'ADR 2746, qui régit la **direction des
  dépendances** et la surface livrée - pas une exemption de correction. La retirer aligne le cliquet
  sur ce que cette ADR dit vraiment.
- L'[ADR 3661](3661-un-garde-de-ci-porte-ses-propres-cas.md) amende l'[ADR 3560] : un garde de CI porte
  ses cas, et la seconde façon de faire que j'avais introduite est annulée.
- ⚠️ **Trois trous restent ouverts**, tous trouvés par un dispositif et non par une relecture :
  #3678 (la CLI affiche l'instant du serveur en anglais et en UTC - le jumeau de #3640, sur une autre
  surface), #3681 (la **raison** d'un effacement qui résiste n'est vérifiée par rien, alors que
  l'ADR 3574 en fait la justification de tout son contrat), et les deux constats de l'ADR 3661 sur
  `construit-appimage.sh` et les auto-tests sans contrôle négatif.
