# Consigne des agents d'extraction sémantique

Cette page est lue par chaque agent qui extrait un lot de la couche sémantique du graphe. Elle se
donne telle quelle, avec le numéro du lot et le dossier de travail. Le périmètre de la couche, son
coût et la raison de chaque règle sont dans l'ADR 5790.

Le graphe porte déjà une couche de structure : un nœud `page` par fichier et un nœud `heading` par
titre de section. Votre travail est la couche sémantique, c'est-à-dire les concepts, les décisions,
les règles et les relations que la prose porte et que les titres ne disent pas.

## Ce que vous lisez d'abord

Trois fichiers du dossier de travail, préparés par `couche_semantique.py decoupe`.

| Fichier | Ce qu'il porte |
|---|---|
| `lot_NN.json` | pour chaque page du lot : son nombre de `lignes`, ses nœuds de `structure`, et ses nœuds de `semantique` déjà présents avec leur justification d'avant |
| `index-du-code.json` | les classes du dépôt, avec leur identifiant. À interroger par `grep`, jamais à lire en entier |
| `index-des-pages.json` | un identifiant par page de documentation, pour résoudre un lien vers une autre page |

## Lire chaque page en entier

Chaque page se lit de la première à la dernière ligne. Au-delà de 1 500 lignes, paginez avec
`offset` et `limit` jusqu'au nombre de `lignes` annoncé. Nommer des sections d'après une table des
matières sans les avoir lues est le défaut que cette règle empêche : une extraction partielle
remplace en silence une extraction plus riche.

## Ce qu'il faut extraire

- Un nœud par concept, entité, règle métier, décision, principe, mécanisme, garde, outil, écran,
  persona ou critère de qualité **nommé**. `file_type` vaut `concept` pour une chose, `rationale`
  pour une décision, une règle ou un principe.
- Le pourquoi, en français, dans un attribut `rationale` d'une ou deux phrases : le compromis,
  l'incident, l'intention. Pas de nœud pour un fragment, une phrase ou un exemple.
- Chaque section de fond rend au moins un nœud. Une ADR rend au moins sa décision, ce qu'elle
  écarte, et comment elle est vérifiée quand elle le dit. Un titre qui ne fait que regrouper des
  sous-sections n'a pas à en rendre un : ses sous-sections le font.
- Les libellés sont en français, comme la page les écrit, et se lisent seuls.

## Les identifiants

- La forme : `{racine}_{entité}`, en minuscules, `[a-z0-9_]` seulement. La racine est le chemin du
  fichier sans son extension, chaque segment gardé ; les accents se translittèrent, `é` devient `e`.
- **Vous ne réémettez jamais un identifiant de `structure`, de l'index du code ni de l'index des
  pages.** Ces nœuds existent : vous vous y reliez par une arête. Le dédoublonnage fusionnerait
  sinon votre nœud avec celui de structure, et c'est l'identifiant de structure qui disparaîtrait.
- Ne nommez pas un nœud comme le titre de la section qui le porte : choisissez un libellé qui dit le
  concept, pas le titre. La fusion replie ces homonymes sur le titre, et votre nœud n'y gagne rien.
- Aucun numéro de lot ni suffixe dans un identifiant.

## Les arêtes

Chaque extrémité d'arête est un nœud que vous émettez, ou un identifiant recopié **octet pour
octet** depuis l'un des trois fichiers. Certains portent des accents : copiez-les depuis le JSON,
ne les retapez pas.

- **Ancrez chaque nœud** : une arête `references`, `EXTRACTED`, depuis le titre de la section qui
  le définit. Quand ce titre manque à `structure`, un `###`, un encart ou une ligne d'un grand
  tableau, prenez le titre parent qui y figure, et la page si aucun n'y figure.
- **Vers le code** : quand la prose nomme une classe, une arête `references` de votre nœud vers
  son identifiant, seulement si le nom désigne une entrée unique de l'index, ou si le chemin lève
  l'ambiguïté. Jamais de nœud pour une classe. Un nom que l'index ne porte pas, une table SQL, un
  type d'une bibliothèque tierce ou un script, ne reçoit aucune arête : on ne la devine pas.
- **Vers une autre page** : un lien ou une mention explicite donne une arête `cites`, depuis le
  nœud qui porte la mention ou depuis le titre. Une page absente de l'index n'en reçoit pas.
- **Entre vos nœuds** : `implements`, `conceptually_related_to`, `rationale_for`,
  `shares_data_with`, `semantically_similar_to`.

La confiance : `EXTRACTED` vaut `1.0`. `INFERRED` prend une seule valeur parmi `0.95`, `0.85`,
`0.75`, `0.65`, `0.55`, jamais `0.5` ; entre deux, prenez celle du dessous. `AMBIGUOUS` va de `0.1`
à `0.3`.

Trois hyperarêtes au plus **par lot**, et non par page, quand trois nœuds ou plus forment un flux
que les arêtes ne disent pas.

## Les champs, exactement

Ces clés sont celles que l'audit et la fusion lisent. Une clé d'un autre nom n'est pas lue, et ce
qu'elle portait se perd sans message.

| Objet | Clés |
|---|---|
| Nœud | `id`, `label`, `file_type`, `source_file`, `source_location`, `_origin`, et `rationale` quand il y a un pourquoi |
| Arête | `source`, `target`, `relation`, `confidence` (`EXTRACTED`, `INFERRED` ou `AMBIGUOUS`), `confidence_score`, `source_file`, `source_location`, `_origin` |
| Hyperarête | `id`, `label`, `nodes` (la liste des identifiants membres), `relation` (`participate_in`), `confidence`, `confidence_score`, `source_file`, `source_location`, `_origin` |

Le champ `file_type` d'un nœud vaut `concept` ou `rationale`, comme dit plus haut. Ne le confondez
pas avec le champ `rationale`, qui porte le pourquoi en une ou deux phrases.

Une hyperarête porte un `id`, formé comme celui d'un nœud. Sans lui elle tombe à la fusion
suivante ; l'outil lui en donne un, mais le vôtre dit mieux ce qu'elle regroupe.

## Les champs imposés

| Champ | Valeur |
|---|---|
| `source_file` | le chemin relatif au dépôt, tel qu'il est écrit en clé de `lot_NN.json`. Jamais absolu |
| `source_location` | toujours `null`. Une valeur comme `"L42"` fait passer votre nœud pour de la structure, et la fusion efface alors les vrais nœuds de structure du fichier |
| `_origin` | toujours `"semantic"`, sur chaque nœud et chaque arête |

## Mettre à jour une page déjà extraite

Quand la liste `semantique` d'une page n'est pas vide, la page a déjà été extraite et elle a
changé depuis. D'autres pages portent des arêtes vers ces identifiants. Chaque entrée donne le
libellé et la justification d'avant : comparez-les à la page, au lieu de les réécrire à l'aveugle.

- **Votre rendu remplace toute la couche sémantique de la page.** Un identifiant que vous ne
  réémettez pas est effacé, et les arêtes qui le visaient se rompent.
- Réémettez donc sous le **même identifiant** chaque nœud dont le concept est encore dans la page,
  avec son libellé et sa justification réécrits d'après ce que la page dit **maintenant**.
- Quand la page dit désormais le contraire d'un ancien nœud, gardez l'identifiant et corrigez le
  libellé. Ne laissez pas l'ancienne affirmation.
- Une page qui a peu changé se traite de la même façon : tous ses nœuds existants se réémettent,
  et les sections de fond qui n'en avaient pas en reçoivent.
- Vous ne lâchez un identifiant que si la page ne porte plus du tout son concept, et vous le
  **déclarez** dans le champ `laches` du rendu. L'audit refuse un identifiant lâché sans l'être.

## Le rendu

Un seul fichier, `rendu_NN.json`, dans le dossier de travail :

```
{"nodes": [...], "edges": [...], "hyperedges": [...], "laches": [...]}
```

Écrivez vos brouillons dans un sous-dossier `travail_NN/` que vous créez, avec le numéro de
votre lot : d'autres agents travaillent à côté, chacun dans le sien, et un nom générique s'écrase.

L'audit complet, `couche_semantique.py audite`, attend tous les lots : il n'est pas à votre
portée. Avant de finir, vérifiez donc vous-même, en Python, ce qu'il vérifiera :
aucun identifiant de structure réémis, chaque extrémité d'arête connue, `source_location` nul,
`_origin` posé, chaque page du lot avec au moins un nœud, chaque identifiant de `semantique`
réémis ou déclaré dans `laches`.

Votre réponse finale tient en une ligne par page, `chemin : N nœuds`, puis les totaux.
