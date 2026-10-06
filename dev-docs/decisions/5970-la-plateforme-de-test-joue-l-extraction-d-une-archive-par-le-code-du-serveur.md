---
type: adr
title: "La plateforme de test joue l'extraction d'une archive par le code du serveur, sans embarquer son worker"
status: stable
article: A5
chantier: "#5970, lot 3 du chantier #5967 (les suites de la clôture de #5596)"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/api/plateforme/ArchiveDuRepliSurLaPlateformeDeTestTest.java"
verification_note: "la classe dépose par le client de l application sur la plateforme de test, fait jouer l extraction au serveur à la révision épinglée, et relit la participation : sans rien en ligne aucun doublon, deux sons en ligne et l archive de toute la nuit deux doublons, deux sons en ligne et l archive des deux absents aucun ; vue rouge quand l archive ne contient pas ce que le cas attend ; le job plateforme-de-test la joue à chaque demande, et l auto-test du script tient la moitié pure du bilan"
relations:
  complete: ["5641-les-tests-connectes-ont-deux-cibles"]
verified:
  - by: machine:ci
    at: 2026-10-06
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-06
---

# La plateforme de test joue l'extraction d'une archive par le code du serveur, sans embarquer son worker

## Contexte

L'[ADR 5641](5641-les-tests-connectes-ont-deux-cibles.md) écarte le worker de traitement de la plateforme de test : une image lourde et
des binaires anciens. Une archive déposée y reste donc une archive.

Le repli manuel de l'[ADR 5867](5867-le-repli-manuel-s-offre-apres-un-refus-sans-recours.md) posait une
question que cet état ne tranche pas : que fait le serveur d'un son qu'une archive lui rend alors qu'il
l'a déjà ? L'ADR tenait la réponse pour une hypothèse, et le cas de recette `S4-105` en attendait une
autre que celle que le code du serveur laissait lire. Le porteur a demandé de jouer le scénario.

## Décision

**La plateforme de test fait jouer au serveur la moitié de son worker qui répond, et rien de plus.**
`joue_l_extraction.py` tourne dans le conteneur de l'API, comme `amorcer.py`, où le code du serveur
est déjà à la révision épinglée. Il y lance `extract_zipped_files_in_participation`, puis
`Participation.load_pjs`, et rend un bilan : combien de sons avant et après, lesquels en double,
combien de données pour l'analyse.

**Le code joué est celui du serveur, sans retouche.** Un bouchon qui imiterait l'extraction ne
prouverait que ce qu'on y aurait écrit. Ce qu'on observe doit rougir le jour où le serveur change :
remonter l'épingle rejoue le banc contre le code neuf.

**Deux accès au stockage sont remplacés, et eux seuls.** `get_file_from_s3` rend l'archive depuis un
dossier du conteneur, et `delete_fichier_and_s3` retire le document sans appeler S3. Ils ne décident
rien de ce qu'on observe, à savoir quel fichier est inséré, sous quel titre et combien de fois, et le
conteneur de l'API ne joint pas le faux S3. Les rebrancher referait le réseau de la plateforme pour
ne rien apprendre de plus.

**L'image de l'API porte `unzip`.** Le serveur extrait par ce binaire, et non par la bibliothèque de
Python. La couche est posée après les dépendances pour ne pas refaire la leur. Le porteur l'a accordé
le 5 octobre 2026.

**Le geste vit dans `WorkerDeLaPlateformeDeTest`.** Ajouté à `PlateformeDeTest`, il lui faisait
franchir le seuil de taille que le cliquet 4617 tient. La classe à part dit aussi ce qu'elle est : la
part du worker que la plateforme sait jouer.

## Ce que cela ne change pas dans 5641

La plateforme de test n'embarque toujours pas le worker. Tadarida n'y tourne pas, un calcul lancé y
reste planifié, et une participation « traitée » y vient toujours d'un état déclaré. Ce qui s'ajoute
est un geste à la demande d'un banc, pas un service.

## Ce qui a été écarté

**Embarquer le worker.** Écarté par 5641, et sa raison tient : la question ne regarde pas un
traitement, seulement une extraction.

**Lire le code du serveur et conclure.** Le lot l'avait fait d'abord, et la lecture était juste. Mais
une lecture ne rougit pas quand le serveur change, et l'ADR 5867 aurait gardé une hypothèse, mieux
fondée.

**Jouer le scénario sur le portail national.** Il y faut une vraie participation, un dépôt à la main
dans un navigateur et une analyse : le cas `S4-105` le garde, hors de portée d'un banc.

## Ce qui la tient

`ArchiveDuRepliSurLaPlateformeDeTestTest` porte trois cas, que le job `plateforme-de-test` joue à
chaque demande. Sans rien en ligne, aucun doublon. Deux sons en ligne et l'archive de toute la nuit :
deux doublons. Deux sons en ligne et l'archive des deux absents : aucun, et c'est le remède de
l'[ADR 5975](5975-les-archives-du-repli-ne-rendent-au-serveur-que-ce-qu-il-n-a-pas.md). Vu rouge : une
archive des seuls absents faisait échouer le cas du doublon sur son compte de fichiers.

L'auto-test de `joue_l_extraction.py`, que `lint.yml` lance, joue cinq cas sur la moitié pure, le
bilan, sans serveur. Trois mutations jouées à la main, trois tuées.

## Ce qu'elle ne prouve pas

Le dépôt à la main dans le navigateur du portail, et l'analyse.

Une trouvaille est laissée telle quelle : à l'extraction, le serveur réinsère aussi l'archive
elle-même comme un fichier, parce qu'elle se trouve dans le dossier qu'il parcourt. C'est le
comportement de l'amont, sans effet sur ce que ce banc établit.
