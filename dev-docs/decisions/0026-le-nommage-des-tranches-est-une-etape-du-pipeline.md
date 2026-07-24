# 0026 - Le nommage des tranches est une étape du pipeline, pas un détail de la découpe

- **Statut** : Accepté
- **Date** : 2026-07-19
- **Chantier** : EPIC #1944 (#1932, #1947, #1948, #1956)
- **Vérification** : humaine — que le nommage des tranches soit une étape du pipeline traversée par tout chemin est un comportement, non un invariant statique

## Contexte

Une réactivation depuis la carte SD rendait 163 séquences de moins que l'import n'en avait produit, sur une nuit de 4789, emportant 417 observations devenues muettes alors que leur audio était sur la carte.

La réactivation n'accepte une tranche régénérée que parce que la transformation est **déterministe** (R11) : mêmes octets en entrée, mêmes octets en sortie, donc l'empreinte capturée à l'import doit se retrouver. C'est une **preuve**, pas une commodité.

Or cette preuve porte sur le contenu, et le contenu n'est pas tout. Le **nom** d'une tranche est la clé de jointure avec l'`observations.csv` : deux fichiers aux octets identiques mais aux noms différents ne sont pas interchangeables. Et le nom, lui, ne se déduit pas d'un brut isolé.

Quand deux enregistrements se chevauchent sur la grille de 5 s - la tranche de queue de l'un porte l'heure de début de l'autre - les deux veulent le même nom horodaté. L'import arbitre : le plus ancien garde `_000`, le perdant passe `_001`. Cet arbitrage a besoin de **voir toute la nuit**.

Trois chemins produisent des tranches. Un seul arbitrait.

## Décision

**Le nommage est une étape du pipeline de transformation, au même titre que la découpe, et tout chemin qui produit des tranches la traverse.**

Trois règles en découlent.

### 1. La règle vit en un seul endroit

`commun.model.NommageSequences` porte la durée d'une tranche, le nom souhaité, le nombre de tranches et l'arbitrage des collisions. `ReconciliationNoms` (import), `TransformationAudio` et les deux voies de réactivation s'y ramènent. Aucune ne redéfinit la règle « à sa façon ».

Le corollaire vaut aussi pour la lecture : `FichierWav.lireEntete` et `FichierWav.lire` partagent **le même balayage de chunks**. Écrire un second analyseur, plus simple, aurait recréé le défaut qu'on corrigeait - une durée doit dire la même chose que le signal qu'on découpe ensuite.

### 2. L'arbitrage est **pur**, le placement ne l'est pas

L'arbitrage ne manipule que des chaînes : il dit quel nom porte quelle tranche. Déplacer ou copier regarde l'appelant.

C'est ce découpage qui permet à un chemin de rejouer l'arbitrage d'une nuit entière **sans régénérer la nuit entière**. La régénération reste brut par brut, temporaire vidé après chacun, empreinte disque inchangée.

### 3. L'entrée de l'arbitrage dépend de ce que le chemin sait

La même fonction sur des entrées différentes rend des noms différents. Il faut donc dire, à chaque appel, **sur quoi** on arbitre :

| Chemin | Entrée | Pourquoi |
|---|---|---|
| Import | les bruts qu'on lui donne | il n'a rien d'autre, et c'est lui la référence |
| Réactivation (voie « bruts ») | les originaux **connus en base** | le dossier désigné peut être incomplet ; un original absent ne doit pas libérer son nom |
| Hydratation (passage reconstruit) | les bruts **présents dans le dossier** | un passage reconstruit ne connaît aucun original |

Les trois retombent sur la même liste quand l'utilisateur redonne la carte entière. C'est le résultat attendu, pas une coïncidence.

## Conséquences

**On préfère le trou au mensonge.** Si un seul brut ne livre pas sa durée, l'hydratation renonce à arbitrer pour toute la nuit. Arbitrer sur un inventaire incomplet décalerait les noms des bruts suivants et ferait rebrancher des tranches sous des noms que l'import n'a jamais écrits : une observation pointerait sur le mauvais son, en silence. Quelques tranches non revendiquées se voient et se disent ; un mauvais appariement, non.

**Une omission se propage en cercle.** L'hydratation ne rejouait pas l'arbitrage, donc une tranche perdante n'était revendiquée par personne, donc son brut - s'il n'avait que celle-là - n'était pas adopté, donc il n'entrait jamais en base, donc l'arbitrage de la réactivation ne pouvait plus produire son nom. Corriger la source défait le cercle ; les bases déjà abîmées demandent un rattrapage (#1952).

**Le garde-fou est une comparaison, pas une assertion.** `EquivalenceImportReactivationTest` fait passer la même fixture par les deux chemins et compare noms **et** octets. C'est la seule forme de test qui attrape ce défaut : chaque moitié était verte de son côté, et la propriété qui compte n'appartenait à aucune.

## Alternatives écartées

**Arbitrer au moment de la revendication** (essayer `_001` si `_000` est pris). Aucune durée nécessaire, mais la régénération est parallèle : l'ordre d'arrivée désignerait le gagnant. L'acceptation étant ici structurelle, un mauvais appariement serait accepté sans bruit.

**Tout régénérer avant de revendiquer.** Exact, mais tous les temporaires vivraient en même temps : l'occupation disque double, ce que l'archivage cherchait précisément à éviter.

## Voir aussi

- ADR 0019 - l'ancrage s'acquiert quand il sert
- [Cycle de vie d'un chantier](../cycle-de-chantier.md), passe 3
