---
type: adr
title: "La couverture et la complétude sont deux axes, et aucun ne se déduit de l'autre"
status: stable
article: A12
heuristiques:
  - "nielsen-1"
chantier: "#4980 (le retour de terrain de la 2.189.0), lots #5030 et #5093"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "PariteCoherenceHoraireTest#les_deux_axes_sont_independants"
verified:
  - by: machine:ci
    at: 2026-09-06
relations:
  complete: ["4984"]
generated:
  by: "process:assistance-par-agents"
---

# La couverture et la complétude sont deux axes, et aucun ne se déduit de l'autre

## Contexte

L'[ADR 4984](4984-le-protocole-est-un-plancher-et-ce-qu-on-ignore-ne-se-decide-pas.md) a posé qu'une
nuit **couvre** ou non la fenêtre exigée par le protocole. Son quatrième point annonçait ce qu'elle ne
livrait pas :

> Le troisième niveau n'est pas livré, et c'est dit plutôt que simulé. Une nuit **interrompue en son
> milieu** est plus grave qu'une nuit trop courte. Elle n'est pas rendue, parce que la donnée n'existe
> pas là où le diagnostic la lirait. Le lot #5030 la persistera.

**#5030 l'a persistée, et #5093 l'a rendue.** L'annonce est tenue, et la décision qu'elle appelait
n'était écrite nulle part : ce n'est pas un troisième **niveau** de couverture qui a été livré, c'est
un **second axe**.

## Décision

**Une nuit se juge sur deux axes indépendants, et l'écran les affiche côte à côte.**

| L'axe | Ce qu'il lit | Ce qu'il vaut quand la donnée manque |
|---|---|---|
| **Couverture** | les heures d'enregistrement, contre la fenêtre calculée aux éphémérides | `INDISPONIBLE` |
| **Complétude** | le journal du capteur, qui dit comment le cycle s'est terminé | `INCONNUE` |

**Aucun ne se déduit de l'autre**, et les quatre combinaisons existent. Une nuit peut couvrir la
fenêtre et s'être interrompue : commencée à l'heure, l'exigence dépassée, et arrêtée en son milieu.
C'est le cas que l'aperçu du diagnostic montre, précisément parce qu'aucun autre ne le montrait.

**Chacun se replie sur son propre « je ne sais pas ».** Le journal du capteur est **circulaire** :
quand la carte se remplit, il efface ses entrées les plus anciennes. Son silence ne prouve donc pas
que la nuit fut entière, et `INCONNUE` n'est ni un défaut ni une nuit saine. C'est l'article A12 de
l'ADR 4984, appliqué au second axe.

### Ce que la présentation ne doit pas conclure

Le journal atteste **sa propre fin**, jamais celle de l'audio : il peut cesser d'écrire pendant que
l'enregistrement continue. Un libellé qui dit « les enregistrements s'arrêtent là » affirme donc plus
que ce qui est su, et il l'a fait pendant des semaines sur un écran qui affichait la plage enregistrée
entière trois lignes plus haut (#5352).

La règle vaut pour les deux surfaces : le terminal expose `couvertureDuProtocole` et
`completudeDeLaNuit` comme deux champs, jamais l'un dérivé de l'autre, et `PariteCoherenceHoraireTest`
les tient ensemble.

## Conséquences

Le quatrième point de l'ADR 4984 est **levé** : le troisième niveau annoncé n'a pas été livré comme
niveau, mais comme axe, et cette ADR dit pourquoi ce n'est pas la même chose. Un niveau supplémentaire
aurait ordonné la gravité - « incomplète » puis « interrompue » - et forcé un arbitrage entre deux
mesures qui ne se comparent pas.

Ce que la mesure avait refusé en #4984 le reste : **déduire une interruption d'un intervalle sans
enregistrement**. Une nuit calme et une nuit interrompue s'y ressemblent. La complétude est lue là où
elle est écrite, ou elle vaut `INCONNUE`.
