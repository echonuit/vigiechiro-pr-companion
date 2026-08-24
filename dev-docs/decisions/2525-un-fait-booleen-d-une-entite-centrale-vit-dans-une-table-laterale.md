---
type: adr
title: "Un fait booléen d'une entité centrale vit dans une table latérale, pas dans une colonne"
status: stable
article: A16
chantier: "#2525 (participations opportunistes), EPIC #2349"
decided_at: 2026-07-27
verification: humaine
verification_note: "le compromis se juge à la **revue d'une migration** : combien de sites de construction l'entité a-t-elle, et le fait concerne-t-il une minorité de lignes ? Aucun scan ne répond à la seconde question, et un test qui figerait le nombre de composantes d'un record serait un contrôle creux, cassé au premier ajout légitime."
verified:
  - by: human:nedseb
    at: 2026-07-27
---

# Un fait booléen d'une entité centrale vit dans une table latérale, pas dans une colonne

## Contexte

Deux fois dans le même chantier, il a fallu attacher un **fait booléen** à une entité centrale : « ce passage est une participation opportuniste » (#2525), puis « ce carré appartient à un tiers » (#2525, dérivation API).

Le réflexe est d'ajouter une colonne, donc une composante au record. Le coût réel a été mesuré (au
2026-07-27, `grep -rIo "new Xxx(" src/main src/test`) :

- `Passage` est construit en **111 endroits** (33 `main`, 78 `test`) ;
- `Site` en **82** (15 `main`, 67 `test`).

Ces nombres bougent au fil des chantiers ; ce qui compte n'est pas leur valeur exacte mais leur **ordre
de grandeur**, qui place ces deux records hors d'atteinte d'un ajout de composante bon marché.

À ce volume, une composante de plus n'est pas une ligne de diff : c'est une propagation mécanique sur des dizaines de fichiers, et surtout un **risque d'échange silencieux** entre paramètres de même type, le défaut que l'[EPIC #2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483) traite en réduisant l'arité des constructions. Ajouter un booléen à `Passage` allait frontalement contre un chantier en cours.

Un précédent existait, et il était documenté : la migration **V10** avait isolé le matériel du micro dans `passage_equipment` plutôt que d'alourdir `passage`, en écrivant explicitement la raison, « plutôt que d'alourdir l'entité centrale, construite en ~60 endroits ».

## Décision

**Un fait booléen porté par une entité centrale se stocke dans une table latérale de présence**, dont la clé primaire est la clé étrangère vers l'entité :

```sql
CREATE TABLE passage_opportuniste (
  passage_id INTEGER PRIMARY KEY REFERENCES passage(id) ON DELETE CASCADE
);
```

La **présence de la ligne** porte le fait ; son absence porte le cas courant, qui ne coûte alors aucun stockage. Le record et son DAO restent **inchangés**.

Le DAO associé étend `DaoGenerique<Long, Long>` (l'entité *est* la clé, seul le fait d'exister compte) et expose une API d'intention : `marquer` / `demarquer` / `definir(id, bool)` / `estX(id)`, plus un **`tousLesIds()`** de lecture groupée. Ce dernier n'est pas un confort : sans lui, un service qui balaie N entités ferait N requêtes.

Appliqué par **V34** (`passage_opportuniste`) et **V35** (`site_tiers`).

## Conséquences

**En bien.** Les ~110 et ~81 sites de construction sont restés intacts. Le cas courant ne coûte rien. La suppression de l'entité nettoie le marquage (`ON DELETE CASCADE`), sans code.

**En moins bien.** La lecture n'est plus « gratuite » avec l'entité : il faut injecter le DAO là où le fait compte (les règles R3/R4, le décompte du solde, le rendu) et **penser aux lectures groupées**. C'est le prix à payer, et il se paie à chaque nouveau consommateur.

**Limite assumée.** Ce n'est pas une règle universelle : pour une entité construite en cinq endroits, la colonne reste plus simple et plus lisible. Le critère est le **volume de sites de construction** croisé avec la **proportion de lignes concernées**.

## Alternatives écartées

**Une colonne sur le record.** Écartée pour le blast radius mesuré ci-dessus et parce qu'elle aggravait l'arité que l'EPIC #2483 cherche à réduire.

**Un champ dans un JSON existant** (`parametres_acquisition`, `donnees_meteo`). Écartée : ces colonnes transportent des structures opaques que le modèle ne sait pas interpréter ; y loger un fait dont dépendent des **règles métier** (R3/R4) l'aurait rendu invisible au SQL et intestable simplement.

**Un enum de statut élargi.** Écartée : « opportuniste » n'est pas une étape du workflow d'un passage, c'est une qualité orthogonale. Les mélanger aurait multiplié les états et cassé les transitions existantes.

## Journal

- 2026-07-27 : Rédigée à la clôture (passe 3) du chantier #2349, après application dans V34 et V35.
