# ADR 0032 — Le plan de dépôt précède l'écriture des archives

- **Statut** : Accepté — 2026-07-19
- **Chantier** : #1991 (lots #1993 à #1999)
- **Vérification** : humaine — que le plan de dépôt précède l'écriture des archives est un ordonnancement du moteur, vérifié par ses tests, pas par un motif

## Contexte

Le moteur de dépôt consommait une `List<Path>` construite d'avance :

```java
Map<String, Path> fichiersParIdentifiant = parIdentifiant(fichiers);
depotUnites.synchroniserPlan(idPassage, plan(idPassage, fichiersParIdentifiant));
```

Toutes les archives devaient donc **exister sur le disque** avant que la première ne parte. C'est ce
qui imposait de générer tout le lot, puis de téléverser : un pic d'occupation égal à la somme des
archives, et deux phases sérialisées alors qu'elles pouvaient se recouvrir.

Cette dépendance était plus faible qu'il n'y paraissait. `PlanificateurArchives.partitionner` est un
glouton à **ordre préservé** sur les tailles source, et `ecrireArchive` nomme ses sorties
`<préfixe>-N.zip`. Le plan complet, **noms compris**, est donc calculable avant le moindre octet
écrit — `ArchivePlanifiee` existait déjà pour ça, et son commentaire le disait : « ce que l'on connaît
dès l'établissement du plan (avant toute écriture) ».

Ce qui manquait n'était pas l'information, c'était la **séparation** entre « ce qu'il y a à déposer »
et « où se trouve le fichier ».

## Décision

**1. Le moteur ne connaît que des identifiants jusqu'au moment d'envoyer.** `deposer` prend une
`SourceDepot` qui expose ses identifiants (connus tôt), résout un chemin au dernier moment
(`Optional`, faillible), et sait libérer ce qu'elle a matérialisé. Le plan `depot_unite` se pose donc
sans qu'aucune archive existe.

**2. Une archive absente se régénère, elle ne change pas le mode de dépôt.** C'était un défaut réel,
antérieur au pipeline : sans archives sur le disque, le choix de source basculait sur les séquences
WAV, et `synchroniserPlan` supprimait alors les unités ZIP du suivi. La progression partielle était
jetée en silence. Les identifiants venant maintenant de la partition et non du contenu du dossier, ce
basculement n'a plus lieu.

**3. Le plan porte l'empreinte de sa liste source.** Le déterminisme de `partitionner` ne tient qu'à
**liste source inchangée** : une séquence ajoutée, retirée ou re-transformée décale la partition, et
l'archive `N` porterait le même nom pour un contenu différent. `EmpreinteLot` couvre le nom et la
taille de chaque fichier dans l'ordre — exactement ce dont la partition dépend. Elle ne lit pas le
contenu : hacher des dizaines de gigaoctets à chaque pose de plan coûterait bien plus cher que le
défaut évité.

**4. Une empreinte qui a changé refuse la régénération.** Le refus est explicite et nomme l'issue
(réinitialiser le dépôt). Une archive silencieusement différente de celle déjà en ligne serait un
défaut côté serveur, sans trace locale.

## Conséquences

`SourceDepot` a deux implantations : `SourceFichiers` (des fichiers déjà présents, le comportement
d'origine, utilisé par le mode WAV et par les appelants qui raisonnent en liste) et
`SourceArchivesRegenerables` (les archives, produites à la demande).

`liberer` et `parallelismeMax` sont **par défaut sans effet** sur l'interface. Ce n'est pas une
commodité : en mode WAV, les identifiants désignent les **séquences d'origine** de la nuit. Une
libération par défaut active les détruirait.

La CLI (`deposer-vigiechiro`) est un second consommateur du moteur : elle suit la même signature, et
ses options `--archives` / `--wav` continuent de forcer un mode ponctuel.

L'empreinte est calculée **à l'appel** et non à la construction de la source : une source peut désigner
des archives qui n'existent pas encore, et seul le moment où l'on pose le plan se situe après
l'écriture.

## Ce qui a été écarté

**Persister la composition de chaque archive** (la liste des WAV qu'elle contient). Cela garantirait
une régénération exacte même après modification du lot, mais duplique une information dérivable et
alourdit le schéma. L'empreinte rend le défaut *impossible* plutôt que *réparable* — pour un cas qui ne
devrait pas se produire, c'est le bon compromis.

**S'appuyer sur le seul déterminisme, sans rien persister.** C'est ce qui aurait été le plus simple, et
c'est précisément le scénario où un lot modifié entre deux reprises produit une archive de même nom et
de contenu différent, sans aucun signal.

**Garder une surcharge `deposer(Long, List<Path>, BooleanSupplier, SuiviDepot)`** pour épargner les
appelants. Elle formait avec la variante `SourceDepot` une paire que le type d'un seul argument
distingue, ce que les matchers `any()` des tests ne savent pas départager. Les appelants emballent
explicitement leur liste.
