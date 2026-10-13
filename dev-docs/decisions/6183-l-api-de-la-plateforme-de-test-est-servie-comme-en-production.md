---
type: adr
title: "L'API de la plateforme de test est servie comme en production"
status: stable
article: A2
chantier: "#6183, sous-chantier #5645"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/api/plateforme/JournauxDeLaPlateformeDeTestTest.java"
verification_note: "le banc exige de lire dans le journal de l'API le démarrage de gunicorn, puis une requête marquée ; revenir au serveur de développement de Flask fait rougir le premier cas, et un journal d'accès muet le second, ce que le premier tir du remède a montré en CI"
relations:
  prolonge: ["5641-les-tests-connectes-ont-deux-cibles"]
verified:
  - by: machine:ci
    at: 2026-10-13
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# L'API de la plateforme de test est servie comme en production

## Contexte

Le job `plateforme-de-test` rougissait par intermittence, sur `main` comme sur des demandes sans
rapport avec lui : une écriture restait sans réponse, et la JVM lisait `HTTP/1.1 header parser
received no bytes` (#5818). Six premiers tirs sur vingt-trois avaient rougi sur `main` entre le 7 et
le 13 octobre 2026. Son rouge ne disait plus si une demande cassait quelque chose.

Rien ne permettait de lire ce rouge : la JVM ne voyait que l'écriture perdue. #6186 a fait verser
par chaque conteneur son journal, que le job dépose en artefact quand il rougit.

Le premier rouge lu avec ses journaux, sur `main` à `09b3b24474`, a montré ceci. Le PATCH qui
échoue **manque au journal de l'API**, alors que les requêtes d'avant y figurent toutes, servies en
quelques dizaines de millisecondes. L'API n'a ni redémarré ni levé d'erreur. Elle n'a pas répondu, sans
défaut de son côté.

L'image lançait l'API par `app.run()`, le serveur de développement de Flask. L'amont, lui, la lance
en production par `gunicorn` : c'est ce que dit son `Procfile`.

## Décision

**L'image de la plateforme lance l'API par `gunicorn`, avec des workers synchrones**, réglés dans
`scripts/plateforme-de-test/gunicorn.conf.py`.

- `gunicorn` est déjà dans le `requirements.txt` de l'API à la révision épinglée. L'image n'installe
  rien de plus.
- Un worker synchrone ne garde aucune connexion ouverte : chaque réponse ferme la sienne. La JVM ne
  peut plus réutiliser une connexion que le serveur aurait fermée entre-temps.
- Quatre workers, pour qu'un navigateur qui charge la fiche web et une écriture ne s'attendent pas.
  L'API ne garde en mémoire que le front servi, qui ne change pas, et un cache par requête : plusieurs
  processus ne divergent pas.
- Le journal d'accès va sur la sortie. En se chargeant, l'API appelle `dictConfig` sans garder les
  journaux existants, ce qui désactive celui de `gunicorn` : le réglage le rouvre dans
  `post_worker_init`, une fois l'API chargée dans chaque worker.

## Ce qui a été écarté

- **Faire fermer chaque connexion par les tests**, par un en-tête `Connection: close`. Le remède
  serait dans le client de production ou dans chaque test, pour un défaut du serveur de test, et la
  plateforme resterait servie autrement que la production.
- **Rejouer une écriture perdue.** Un PATCH rejoué à l'aveugle peut s'appliquer deux fois, et le
  rouge suivant deviendrait une lenteur que personne ne lirait.
- **Des workers à fils**, qui gardent les connexions ouvertes un temps : la réutilisation d'une
  connexion fermée redeviendrait possible.

## Ce que la mesure établit, et ce qu'elle n'établit pas

Après le remède, dix-neuf tirs du job sans cette signature : neuf sur le commit `eb36c43984`, puis
dix verts de suite sur `c6056ba359`. Au taux d'avant, dix-neuf tirs sans elle avaient moins d'une
chance sur cent de sortir par hasard.

Le mécanisme, lui, reste une hypothèse. Le journal dit que l'API n'a pas répondu ; il ne dit pas si
la requête a atteint le conteneur. La réutilisation par la JVM d'une connexion fermée s'accorde avec
tout ce qui a été lu, et rien ne l'a montrée directement.

Le remède change le serveur de test, pas le client. Il ne dit donc pas si Companion peut perdre une
écriture de cette façon contre la plateforme nationale : #5818 l'instruit.

Les dix tirs ont été joués sur une branche de mesure, au même commit que `main`, par relances du job
seul. L'atelier annule un run en cours quand un autre part sur la même branche : relancé sur `main`,
le job aurait annulé les runs des fusions voisines.

## Conséquences

Un rouge du job redevient une information. Les autres causes que la mesure a trouvées étaient des
courses du banc qui lit la fiche web, corrigées par #6189 et #6206.

Qui change la révision épinglée de l'API vérifie que `gunicorn` reste dans ses dépendances : sans
lui, l'image ne démarre plus, et le job le dit au premier tir.
