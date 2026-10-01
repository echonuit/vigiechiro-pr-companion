---
type: adr
title: "La plateforme de test se construit sans cache, tant que son job n'allonge pas le run"
status: stable
article: A5
chantier: "#5666, clôture de l'EPIC #5642 (chantier #5640)"
decided_at: 2026-10-01
verification: humaine
verification_note: "la condition porte sur la duree d un job rapportee aux autres jobs du meme run, qui varient d un facteur deux selon le runner ; aucun garde ne la tient sans fabriquer de faux rouges, et elle se remesure a la main avec la commande donnee ici"
enforced_by: []
verified:
  - by: humain
    at: 2026-10-01
relations:
  prolonge: ["5641-les-tests-connectes-ont-deux-cibles"]
generated:
  by: "process:assistance-par-agents"
---

# La plateforme de test se construit sans cache, tant que son job n'allonge pas le run

## Le contexte

Le job `plateforme-de-test` de `maven.yml` (#5666) construit deux images à chaque run : l'API
Vigie-Chiro à la révision épinglée, par téléchargement de son archive puis `pip install`, et le faux
S3. Le corps du lot disait : « sans cache, chaque run paie la construction. Un cache de construction
est dû. » Son critère de fin demandait la durée d'un second run avec cache.

Testcontainers construit ces images par l'API Docker. Le cache que GitHub offre aux constructions
passe par `buildx`, que cette voie n'emprunte pas : l'obtenir demandait de changer la façon dont
l'extension `PlateformeDeTest` construit ses images.

## La décision

**Pas de cache.** Le lot a mesuré d'abord, et la mesure a dit que la construction ne pesait pas.

En local, l'image de l'API se construit en 20 s et celle du faux S3 en quelques secondes. En
intégration continue, sur les huit derniers runs verts de `main` au 1er octobre (de `fc1d64e67` à
`a90c6572f`), l'étape qui construit les images, monte la plateforme et joue les tests dure de 90 à
131 s, et le job de 100 à 138 s.

Surtout, ce job tourne **en parallèle** des treize autres jobs de `maven.yml`, et il n'est pas le
plus long. Sur les trois derniers runs, il finit neuvième sur quatorze, quand le plus long dure de 535
à 912 s (`build`, `ordre-alternatif`, `fuseau-alternatif`). Gagner vingt secondes sur ce job ne
raccourcit donc pas l'attente d'un verdict.

Le bloc de prise l'avait annoncé : si la construction ne pesait pas, le critère deviendrait « durée
mesurée, cache jugé inutile, chiffres à l'appui ». La livraison (#5713) l'a tenu sous cette forme,
mais le corps du lot a gardé sa première lettre. Cette ADR est l'endroit où l'écart est écrit.

## Quand la question se rouvre

La décision tient tant que le job n'est pas le plus long de son run. Elle se rouvre si cela change,
par exemple parce que les lots de #5640 lui ajoutent le front web et un navigateur, ou parce que les
autres jobs raccourcissent.

La mesure se refait sur les runs verts de `main`, en comparant la durée du job `plateforme-de-test` à
celle du plus long job de chaque run, par l'API des jobs (`/actions/runs/<id>/jobs`, champs
`started_at` et `completed_at`). Une mesure prise sur un seul run ne suffit pas : les durées varient
d'un facteur deux d'un runner à l'autre.

## Ce qui a été écarté

**Construire les images par `buildx` hors de l'extension**, puis les lui passer par leur nom : c'est
le « sans changer l'extension » que le bloc de #5666 excluait. On
gagnait le cache de GitHub, mais l'extension cessait de monter seule sa plateforme : un test local
demanderait une étape de construction préalable, que rien ne rappelle.

**Publier les images dans un registre**, examiné à la clôture. Une image publiée devient une seconde épingle, à côté de
`epingles.properties`, et l'[ADR 5641](5641-les-tests-connectes-ont-deux-cibles.md) fait de
l'épinglage un réglage unique du dépôt.
