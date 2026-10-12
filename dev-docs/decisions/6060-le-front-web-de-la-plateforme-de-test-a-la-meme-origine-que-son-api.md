---
type: adr
title: "Le front web de la plateforme de test a la même origine que son API"
status: stable
article: A2
chantier: "#6060, lot 1 du sous-chantier #5645"
decided_at: 2026-10-12
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/api/plateforme/FrontWebSurLaPlateformeDeTestTest.java"
  - "src/test/java/fr/univ_amu/iut/commun/api/plateforme/ImageDeLApiSansRevisionTest.java"
verification_note: "le premier lit la page d'entrée à l'adresse que la plateforme rend, puis l'adresse de l'API DANS le script construit, et exige qu'elle mène à l'API ; le second exige que l'image refuse sans l'épingle du front. Trois mutations tuées : l'adresse amont figée, une révision du front par défaut, le front non servi"
relations:
  prolonge: ["5641-les-tests-connectes-ont-deux-cibles"]
verified:
  - by: machine:ci
    at: 2026-10-12
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-12
---

# Le front web de la plateforme de test a la même origine que son API

## Contexte

L'ADR 0020 a mesuré trois défauts d'écriture que seule la fiche web de Vigie-Chiro a attrapés. Le
sous-chantier #5645 veut qu'un test relise sur cette fiche ce que Companion a écrit. Il faut d'abord
que la plateforme de test serve le front web, ce qu'elle ne faisait pas.

Deux faits du front amont décident de la forme. Son adresse d'API est figée à la construction,
dans `settings.coffee`, alors que le port publié de l'API n'est connu qu'au démarrage. Et l'API fige
son CORS sur une seule origine, `http://localhost:9000` par défaut.

## Décision

**Le front est construit dans l'image de l'API, et l'API le sert à sa racine.** C'est le mode
`FRONTEND_HOSTED` que l'API amont porte déjà : il sert le dossier `static` de `/api`. Le front est
à `/`, l'API à `/api/v1`, sur le même port.

- L'adresse de l'API est réécrite en `/api/v1`, **relative**, avant `grunt build`. Elle vaut quel que
  soit le port publié, et le navigateur la résout contre la page.
- Il n'y a qu'une origine, donc aucun CORS à régler.
- `PlateformeDeTest.Acces.urlDuFront()` dérive l'adresse du front de celle de l'API, au lieu de rendre
  deux adresses qui pourraient diverger.
- La révision du front est une épingle de `epingles.properties`, comme celle de l'API, et l'image
  refuse de se construire sans elle.

Le front se construit comme son amont le déploie : Node 22, `bower`, `grunt build`.

## Ce qui a été écarté

- **Deux origines et le CORS réglé.** Il aurait fallu connaître le port publié de l'API avant de
  construire le front, et régler `CORS_ORIGIN` sur un port du front lui aussi publié au démarrage.
- **Un relais nginx devant les deux.** Un conteneur de plus, qui refait ce que l'API sait déjà faire.
- **Une seconde image, construite seulement quand un test demande le front.** Les tests de #6062 à
  #6064 joueront dans le même job et la demanderont : elle n'économiserait rien, pour un mécanisme de
  plus. Choix du porteur, le 12 octobre 2026.

## Conséquences

Chaque test de la plateforme de test paie la construction du front, environ une minute.
L'[ADR 5666](5666-la-plateforme-de-test-se-construit-sans-cache.md) dit ce que cela fait à la durée
du job.

Le constructeur de Testcontainers garde l'image de l'étape du front à chaque reconstruction. Ses
dépendances sont donc retirées dans l'instruction qui la construit : sans cela, trois reconstructions
avaient rempli le disque d'un poste.

Ce que le front rend sans les ressources qu'il charge du dehors (cdnjs, Google Maps) n'est pas encore
mesuré. C'est le lot suivant, avec le navigateur (#6061).
