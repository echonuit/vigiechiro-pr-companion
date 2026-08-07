# ADR 2750 - Un chiffre que le code sait recalculer ne s'écrit pas à la main, et un chiffre qu'il ignore ne s'écrit pas du tout

- **Statut** : Accepté - 2026-08-07
- **Chantier** : #2750 (garde-fous documentaires) ; lot 5 de l'EPIC #2720, clôture #2725
- **Vérification** : certaine - `DocumentationAJourTest#chaque_chiffre_balise_egale_l_inventaire_reel`
- **Amende** : [ADR 2385](2385-la-doc-chiffree-est-adossee-au-code.md), sur le **caractère facultatif** du balisage ; sa décision reste entière, c'est sa réserve qui tombe.

## Contexte

L'[ADR 2385](2385-la-doc-chiffree-est-adossee-au-code.md) a adossé au code les chiffres **balisés**, et
posé explicitement une réserve :

> Poser une balise n'est **pas** obligatoire partout : la règle est que **là où un chiffre est balisé,
> il est juste**.

Le raisonnement se tenait : garder la liberté d'écrire un nombre en prose, et vérifier seulement ce
qu'on a choisi d'ancrer. Quinze jours plus tard, le lot 5 a mesuré ce que cette liberté avait produit.

**Les onze balises étaient toutes justes. Aucun chiffre en prose ne l'était.**

| Ce que la prose annonçait | Le réel | Facteur |
|---|---|---|
| « les 21 tests bats », à **trois** endroits | **89** | ×4,2 |
| « treize écrans » (README) | **16** | |
| « 31 tables après `V02`→`V35` » | **33** après `V38` | |
| « ~3600 séquences » pour une vraie nuit | **2109** | ×1,7 |
| colonne « Séquences » du banc O3 | quatre fois trop grande | ×4 |

Et **personne n'avait mal fait**. Chacun de ces nombres était juste le jour où il a été écrit. Le
découpage audio a changé (#504) et a laissé ses chiffres derrière lui ; le harnais bats a grandi ;
`V36` et `V38` ont ajouté deux tables. Un chiffre juste devient faux **tout seul**, sans que personne
ne le touche : c'est précisément ce contre quoi une relecture ne peut rien.

La réserve de 2385 n'était donc pas une souplesse, c'était l'endroit exact où la dérive s'est logée.

## Décision

**Si le code sait recalculer un chiffre, la documentation le porte en balise. Sinon, elle ne l'écrit
pas du tout.** Trois cas, trois gestes :

1. **Le code sait compter** → balise `<!--inv:clé-->N<!--/inv-->`, **plus une entrée dans
   `DocumentationAJourTest`**. Ce n'est plus facultatif : un chiffre recalculable écrit en prose est
   un défaut, pas un choix de rédaction.
2. **Le code ne sait pas** (un commentaire de workflow, une note de PR) → **écrire la phrase sans le
   nombre**. « Les tests bats, qui lancent chacun un JVM » dit ce qu'il faut sans rien promettre.
3. **Le chiffre est une mesure datée** (« 66 aperçus sur 138 différaient le 6 août ») → il **reste en
   dur**, et c'est **juste** : ce n'est pas un inventaire, c'est un constat, et un constat a une date.

### Un décompte se prend sur l'état appliqué, pas sur le texte qui le produit

Corollaire de méthode, appris en ancrant le nombre de tables. Compter les `CREATE TABLE` des
migrations donne un faux : une reconstruction de table crée une jumelle temporaire, copie, supprime
l'originale et **renomme**. Le `CREATE` et le `DROP` ne se compensent que par hasard, et
l'`ALTER TABLE ... RENAME` n'apparaît dans aucun des deux.

⚠️ Sur ce schéma, le comptage statique tombait **juste par coïncidence** - la pire situation pour un
garde, puisque rien ne signale qu'il mesure la mauvaise chose. Le décompte se prend donc en
appliquant les migrations et en interrogeant `sqlite_master`.

### Deux inventaires du même concept déclarent leur écart

Quand deux gardes comptent la même notion par deux chemins, leur différence est **déclarée en code**,
jamais laissée à l'interprétation. `FICHE_PAR_ECRAN` part des FXML et en connaît 15 ;
`<!--inv:ecrans-->` compte les fiches et en trouve 16. L'écart est légitime - la recherche globale est
un **chrome**, sans FXML - mais invisible : `FICHES_SANS_ECRAN_FXML` le nomme, et le garde vérifie
qu'il n'y en a pas d'autre.

## Conséquences

- Trois clés d'inventaire nouvelles : `tests-bats`, `tables`, `ecrans`. La discipline reste légère -
  ajouter une migration, un écran, un test bats met à jour un nombre que le test désigne.
- **Le troisième cas se confond avec le premier au premier coup d'œil**, et c'est le piège que cette
  ADR doit surtout transmettre. Un balayage a compté « 51, 66, 108 aperçus » comme des inventaires
  divergents : c'étaient trois **deltas** d'ADR, et le total, 138, était juste partout. Lire la phrase
  entière avant de conclure à une dérive.
- Un chiffre en prose devient un **signal** : soit il est daté, soit il manque une balise.
- **Un chiffre faux a des jumeaux, et le balayage qui corrige n'est pas celui qui compte.** La
  correction de #2749 avait trouvé deux « 21 tests bats » ; l'audit d'harmonisation de la clôture
  en a rendu un **troisième**, dans un encadré du même fichier, survivant d'une journée. Corriger
  une occurrence et chercher ses jumelles sont deux gestes distincts.

## Alternatives écartées

- **Baliser aussi les mesures datées.** Transformerait un constat en inventaire, et ferait rougir une
  phrase **juste** dès que le code bouge - alors qu'elle décrit un instant, pas un état.
- **Garder la réserve de 2385 et compter sur la relecture.** C'est ce qui a été retenu il y a quinze
  jours. Mesuré : aucun chiffre en prose n'a survécu. Une relecture ne voit pas un nombre vieillir.
- **Interdire tout chiffre non balisé.** Le cas 3 existe et est légitime ; l'interdire pousserait à
  écrire des phrases vagues là où une mesure datée est exactement ce qui manque.
