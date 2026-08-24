---
type: adr
title: "Un script de migration **publié ne se modifie plus**, et la base s'en souvient"
status: stable
article: A17
chantier: "#2729, lot 1 (#2721) du chantier de dette #2720"
decided_at: 2026-08-03
verification: certaine
enforced_by:
  - "EmpreinteMigrationsTest#script_modifie_apres_coup_fait_refuser"
verified:
  - by: machine:ci
    at: 2026-08-03
---

# Un script de migration **publié ne se modifie plus**, et la base s'en souvient

## Contexte

Une migration appliquée **ne se rejoue jamais** : sa version est inscrite dans `schema_version`, et le
migrateur passe. Modifier son script après coup, ce que fait n'importe quel rebase ou n'importe quelle
correction bien intentionnée, produit donc deux populations de bases : celles qui ont subi la première
version du script, et celles qui naissent avec la seconde. **Elles divergent en silence**, et rien
dans le schéma ne dit laquelle on a sous les yeux.

Le défaut est particulièrement traître parce qu'il ne se manifeste pas chez celui qui modifie : sa
base à lui est neuve, elle reçoit la bonne version. Il se manifeste chez l'utilisateur qui a migré la
semaine d'avant.

## Décision

**Chaque migration laisse son empreinte SHA-256 dans `schema_version`**, écrite dans la transaction
qui l'applique (#2728). Au démarrage, avant d'appliquer quoi que ce soit, une empreinte qui ne
correspond plus à son script est un **refus explicite** qui nomme le fichier.

Trois précisions qui font la décision, et sans lesquelles elle serait fausse :

**L'empreinte porte sur les instructions, pas sur le texte du fichier.** Corriger une faute dans un
commentaire ou changer les fins de ligne ne change rien à ce que la base reçoit. Faire échouer un
démarrage pour cela serait un **refus faux**, et un refus faux use plus vite la confiance qu'une
alerte manquée : l'utilisateur apprend à passer outre.

**Refus, pas avertissement.** Migrer par-dessus une divergence n'y changerait rien, la version étant
déjà enregistrée : le schéma obtenu ne correspondrait à aucune description, et l'application
travaillerait sur une base dont personne ne sait ce qu'elle contient. S'arrêter en le disant est le
seul geste qui laisse une porte de sortie.

**Le refus précède toute application.** Il a lieu avant la première migration en attente : une base
dont un script a dérivé ne doit pas recevoir en plus les migrations suivantes.

## Conséquences

- Un script publié devient **immuable**. Une correction se fait par une **nouvelle** migration.
- Un démarrage peut échouer sur un refus que seul un développeur comprendra. C'est assumé : le
  message nomme le fichier, et cette situation ne se produit que sur une base de développement.
- ⚠️ **Les migrations appliquées avant la mise en place des empreintes n'en ont aucune.** Elles sont
  **étalonnées** au premier lancement, sur le contenu actuel des scripts : l'empreinte fige le
  présent, elle ne juge pas le passé. Si un script avait **déjà** été modifié sur une base existante,
  l'étalonnage enregistrera la version modifiée et personne ne le saura. C'est irrattrapable, rien
  n'ayant gardé trace de ce qui avait été appliqué, et c'est écrit plutôt que tu : une garantie qu'on
  croit plus large qu'elle n'est vaut moins que pas de garantie du tout.

## Alternatives écartées

**Avertir sans refuser.** Un avertissement au démarrage se lit une fois, puis se saute. Et la base
continue de diverger pendant ce temps.

**Empreinte du fichier entier.** Plus simple à écrire, mais elle transforme chaque correction de
commentaire en panne de démarrage. Le premier refus faux aurait suffi à faire désactiver le
mécanisme.

**Rejouer le script modifié.** Impossible sans savoir ce que la base a déjà reçu : rejouer un `ALTER
TABLE ADD COLUMN` déjà appliqué échoue, et rejouer un `UPDATE` déjà passé peut détruire des données
saisies depuis.
