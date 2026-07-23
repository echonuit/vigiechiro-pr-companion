# ADR 0044 — Le mécanisme de parallélisme se choisit sur la nature de l'attente, la borne se chiffre sur autre chose

- **Statut** : Accepté — 2026-07-20
- **Chantier** : #2040 (EPIC #2116)
- **Vérification** : humaine — le choix du mécanisme de parallélisme selon la nature de l'attente est un jugement de conception, non observable dans le code

## Contexte

L'application parallélise à sept endroits en production, par deux mécanismes différents :

| Mécanisme | Où | Nature du travail |
|---|---|---|
| Fils virtuels + `Semaphore` | `ExecutionParallele`, `DecoupageParallele`, `PreparationOriginaux`, et par elles `HydratationDepuisBruts` et `PlateformeReconstruction` | calcul + disque, **et** attente réseau |
| `newFixedThreadPool` | `CompacteurDepot`, `DepotVigieChiro` | calcul, **et** attente réseau |

Les deux colonnes se recouvrent : chaque mécanisme sert les deux natures de travail. Vu de loin, la
répartition paraît arbitraire.

Elle ne l'est pas tout à fait. **La règle existe déjà, écrite une seule fois**, dans la Javadoc de
`PlateformeReconstruction` :

> Nombre d'appels de détail menés **de front** (#1814). C'est une borne d'**entrée/sortie**, pas de
> calcul : chaque tâche est un GET qui passe son temps à attendre le réseau (fils virtuels). La borne
> sert donc à rester **poli** avec la plateforme, pas à occuper les cœurs.

Écrite là, elle gouverne une classe. Elle n'a jamais été énoncée pour l'application.

**L'analyse d'ouverture de #2040 s'est trompée de suspect.** Elle tenait la divergence de
`DepotVigieChiro` pour justifiée (« travail bloquant réseau, plafond métier de 5 ») et celle de
`CompacteurDepot` pour douteuse. C'est l'inverse : `CompacteurDepot` compresse en DEFLATE, du calcul
pur, sur un pool calé sur `availableProcessors()` — le cas d'école de la règle. `DepotVigieChiro`
attend le réseau sur des threads de plateforme, exactement ce que `PlateformeReconstruction` fait avec
des fils virtuels. Le seul des deux qui s'écarte est celui que l'issue disait conforme.

## Décision

**1. La nature de l'attente choisit le mécanisme.**

| Ce que la tâche fait | Mécanisme | Pourquoi |
|---|---|---|
| Elle **attend** (réseau, disque) | fil virtuel + `Semaphore` | un fil bloqué ne coûte presque rien ; la borne est un choix, pas une contrainte de la machine |
| Elle **calcule** (DEFLATE, transformation audio) | `newFixedThreadPool(availableProcessors())` | des fils virtuels ne multiplient pas les cœurs ; le pool *est* la borne, un `Semaphore` ferait doublon |

**2. La borne ne se chiffre pas sur le mécanisme, mais sur ce qu'elle protège.** C'est une question
distincte, et c'est là que se trouve la vraie diversité du dépôt :

| Borne | Valeur | Ce qu'elle protège |
|---|---|---|
| `PlateformeReconstruction.DETAILS_DE_FRONT` | 8 | la **politesse** envers la plateforme |
| `DepotVigieChiro.NB_UPLOADS_PARALLELES` | 5 | idem, calquée sur le front web (`max_concurrent_uploads`) |
| `SourceArchivesRegenerables.FENETRE` | 2 | le **pic disque** ([ADR 0033](0033-la-fenetre-borne-le-disque.md)) |
| `DecoupageParallele`, `PreparationOriginaux`, `CompacteurDepot` | `availableProcessors()` | les **cœurs**, et le débit du support source |

Un même mécanisme sert donc des bornes qui n'ont rien à voir entre elles. Confondre les deux questions
est ce qui faisait paraître la répartition arbitraire.

**3. `DepotVigieChiro` garde son pool de plateforme, et c'est une tolérance déclarée, pas un oubli.**
Le migrer vers `ExecutionParallele` serait conforme et sans bénéfice mesurable : **cinq** threads
immobilisés sur de l'attente, c'est cinq piles. L'argument des fils virtuels paye à des milliers de
tâches en vol, pas à cinq.

Deux raisons positives de ne pas y toucher :

- `ExecutionParallele.cartographier` rend une **liste de résultats dans l'ordre**. Le dépôt n'en veut
  pas : il accumule dans des compteurs partagés parce que chaque unité **commite au fil de l'eau**, et
  que la reprise se lit en base, pas dans une liste rendue en fin de course.
- Le pool *est* la borne. Y ajouter un `Semaphore` de même valeur serait une borne en double.

La tolérance vaut **tant que le plafond reste de cet ordre**. Si `NB_UPLOADS_PARALLELES` devait monter
d'un ordre de grandeur, la règle du point 1 reprend la main.

**4. Le socle a un nom, et il s'appelle `ExecutionParallele`.** Tout nouveau parallélisme d'attente
passe par lui. Le cas de `DepotVigieChiro` est la seule exception, et cet ADR est ce qui l'autorise.

## Conséquences

La règle cesse d'être une propriété de `PlateformeReconstruction` pour devenir une propriété du dépôt.
Un contributeur qui parallélise n'a plus à deviner laquelle des deux formes copier : il regarde si sa
tâche attend ou si elle calcule.

`DepotVigieChiro:217` porte désormais une divergence **motivée**. Une divergence non écrite se propage
par copie — c'est ainsi que trois copies du patron d'exécution parallèle ont pu coexister (#2039).

Cet ADR ne referme pas #2039 : documenter le choix du mécanisme ne dédouble pas les implantations.
Il le rend en revanche jugeable, puisque la règle contre laquelle juger existe.

## Ce qui a été écarté

**Uniformiser `DepotVigieChiro` sur `ExecutionParallele`.** Conforme, sans bénéfice mesurable à cinq
téléversements, et coûteux : il faudrait plier le commit au fil de l'eau à une API qui rend ses
résultats en fin de course. #2040 demandait d'écrire pourquoi, pas d'uniformiser.

**Un réglage exposant le degré de parallélisme.** Même raison qu'en [ADR 0033](0033-la-fenetre-borne-le-disque.md)
pour la fenêtre : un arbitrage que l'utilisateur n'a aucun moyen d'évaluer, contre une surface de
configuration et un axe de variation en test.

**Un test qui interdirait `newFixedThreadPool` hors des cas listés.** Tentant, dans un dépôt qui tient
ses règles par des tests. Mais la règle porte sur la **nature du travail**, que rien dans la signature
ne révèle : un tel test vérifierait une liste d'exceptions, c'est-à-dire lui-même. Ce qu'on gagnerait
en garde-fou, on le perdrait en confiance dans le garde-fou.
