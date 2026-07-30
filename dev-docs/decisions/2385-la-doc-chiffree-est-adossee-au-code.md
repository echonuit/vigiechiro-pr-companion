# ADR 2385 - Les chiffres et les relations de la documentation sont adossés au code

- **Statut** : Accepté - 2026-07-24
- **Chantier** : #2385 (adosser les chiffres) ; étendu par #2386 (règles métier, statut des ADR) ; clôture de l'EPIC #2367
- **Vérification** : certaine - `DocumentationAJourTest#chaque_chiffre_balise_egale_l_inventaire_reel`

## Contexte

L'audit d'obsolescence documentaire du 22 juillet 2026 a confronté 225 fichiers Markdown au code. Un motif revenait, invisible à la relecture : un **chiffre écrit plus petit que la réalité**. « Les 10 fonctionnalités » ne cloche pas dans une phrase ; le socle en comptait 15. La même dérive frappait les contrats `Ouvrir*`, les états du workflow, les sous-commandes CLI, les tables, les migrations. La liste des `Ouvrir*` était même recopiée à l'identique dans trois fichiers, chacun se présentant comme la référence : une duplication littérale produit une dérive triple.

`mkdocs build --strict` couvre les liens internes, pas les **faits**. `DocumentationAJourTest` vérifiait qu'une commande CLI a sa ligne, mais jamais qu'un **nombre** est juste, ni le **statut** d'une ADR, ni le sens inverse d'un tableau. Corriger les chiffres sans garde, c'est se garantir de recommencer : ce sont exactement les valeurs qui bougent à chaque évolution du socle.

## Décision

**Là où un fait chiffré ou relationnel de la doc décrit le code, il est confronté au code par un test.** Le point de comparaison n'est jamais une liste tenue à la main (c'est ce qui dérive) mais la **vérité du câblage**.

1. **Balise d'inventaire** : un nombre qui décrit le code s'écrit `<!--inv:clé-->N<!--/inv-->`. Le `N` reste visible (un commentaire HTML ne s'affiche pas), la doc se lit normalement, et le test relit `N` pour le comparer au décompte réel (features, `Ouvrir*`, `StatutWorkflow`, sous-commandes câblées, workflows de CI, migrations). Poser une balise n'est **pas** obligatoire partout : la règle est que **là où un chiffre est balisé, il est juste**, et qu'une clé connue reste ancrée par au moins une balise.
2. **Sens inverse** : le tableau CLI ne décrit aucune commande qui n'existe plus.
3. **Règles métier** : chaque `**Rn**{ #rn }` est défini une fois, gras et ancre concordent, la numérotation ne saute pas, et chaque renvoi `[Rn](#rn)` du brief vise une règle réelle.
4. **Statut des ADR** : une ADR déclarée `**Amende** X` impose que X porte la mention réciproque dans son statut, et le premier mot du statut appartient à un vocabulaire fixé.
5. **Liens hors site** : les liens des fichiers racine (`README`, `CONTRIBUTING`, `TESTING`), que `mkdocs --strict` ne voit pas, pointent vers un fichier et une ancre qui existent.

La convention de balisage est décrite dans [Tests et qualité](../tests-et-qualite.md).

## Conséquences

- Un chiffre, une règle ou un statut qui dérive **fait rougir la CI**, avec la vraie valeur dans le message : la doc ne peut plus mentir en silence sur ce que le code sait recalculer.
- Le coût est une **discipline légère** : quand on ajoute une feature, une migration, un contrat `Ouvrir*`, on met à jour le chiffre balisé (le test dit lequel). C'est le prix d'une doc qui reste vraie.
- Les ancres fragiles (un titre à emoji n'a pas de slug stable) se remplacent par une **ancre explicite** `<a id="...">`, immunisée, plutôt que par un slug deviné.

## Alternatives écartées

- **Imposer un chiffre balisé partout.** Transformerait chaque page en gabarit. La garde vérifie l'exactitude là où un chiffre est écrit, pas sa présence.
- **Geler les chiffres dans une seule page et renvoyer.** Réduit la duplication mais laisse la page unique dériver sans garde ; adosser au code est plus sûr que centraliser.
- **Un slug maison pour valider les liens vers des titres à emoji.** Un slug approché qui diverge de GitHub produit un faux vert (le lien casse en ligne, le test passe). Les ancres explicites suppriment le problème à la source.
