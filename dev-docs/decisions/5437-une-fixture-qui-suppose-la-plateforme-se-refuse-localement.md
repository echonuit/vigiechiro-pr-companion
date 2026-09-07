---
type: adr
title: "Une fixture qui suppose la plateforme se refuse localement, pas le mardi suivant"
status: stable
article: A2
chantier: "#5433 (onze rouges de plateforme en août, quatre en six jours), lot #5437"
decided_at: 2026-09-07
verification: probable
enforced_by:
  - "scripts/adr/5437-fixture-suppose-la-plateforme.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-09-07
relations:
  complete: ["3802-un-defaut-de-plateforme-se-sonde-il-ne-se-deduit-pas"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-07
---

# Une fixture qui suppose la plateforme se refuse localement, pas le mardi suivant

## Contexte

L'ADR 3802 a établi qu'un comportement de plateforme se sonde, et que le test qui reste doit être
éprouvable partout, donc passer par une couture. Son `enforced_by` ne nommait que
`suite-sous-windows-et-macos.yml`.

Ce workflow **détecte**, et une fois par semaine. Entre deux mardis, une fixture non portable se
fusionne sans que rien ne la voie. Quatre l'ont fait entre le 2026-08-31 et le 2026-09-06, après
onze de même nature corrigées en août : la décision existait, elle était juste, et rien ne
l'appliquait au moment où elle aurait servi.

## Décision

**Un appel POSIX qui peut jeter, dans l'arbre de test, déclare l'exigence qu'il porte.** Un garde
local le refuse à chaque demande, cliquet à zéro.

Quatre formes valent déclaration, et ce sont celles que le dépôt employait déjà :

| Forme | Ce qu'elle dit |
|---|---|
| `@EnabledIf` sur la méthode ou la classe | la forme de #3778, visible dans le rapport |
| `assumeTrue` en tête de méthode | la même exigence, moins visible |
| `try` / `catch (UnsupportedOperationException)` | le code se replie, il n'éprouve rien de la plateforme |
| une aide dont tous les appelants du fichier déclarent | la délégation que #3778 a voulue |

`assumeTrue` est accepté à côté de `@EnabledIf`, alors que #3778 préfère le second. Le mal que cette
ADR nomme est celui d'un `assumeTrue` posé **au milieu** d'une méthode, qui emporte les assertions
n'ayant rien de POSIX. En tête, il protège aussi bien. Refuser la forme ancienne ferait rougir ce
garde sur des cas justes, pour une préférence de style qu'une autre décision porte déjà.

## Pourquoi l'arbre syntaxique, et non un motif de ligne

Mesuré sur l'arbre du 2026-09-06 : un motif textuel retient **sept** fichiers, le garde en retient
**deux**, et ce sont les deux qui étaient réellement cassés. Cinq des sept sont légitimes : une
citation en commentaire, deux `assumeTrue`, une aide qui délègue, une aide qui rattrape l'exception.

Un motif qui se trompe cinq fois sur sept ne peut pas annoncer un zéro qui veuille dire quelque
chose. Le garde lit donc `scripts/_commun/arbre.py`.

Deux des faux positifs sont de plus des `///` du JEP 467, et `tree-sitter` est le seul lecteur du
dépôt qui les rende fidèlement : Spoon les classe en `//` et re-sérialise en `// /`, JavaParser ne
les voit pas sous `getJavadoc()`.

## Ce qui n'a pas été écrit, et pourquoi

**Le second motif du chantier, celui du modificateur clavier, n'existe pas.** Sa population
illégitime est vide : deux occurrences de `KeyCode.CONTROL` dans tout l'arbre de test, l'une dans une
chaîne de caractères, l'autre juste puisque `MainViewTest` y est d'accord avec `MainController`, qui
code `CONTROL_DOWN` en dur.

Un garde qui lirait des centaines de bancs pour rendre zéro sur une règle dont le seul cas réel doit
être exempté ne pourrait jamais être vu rouge, et `verifie_temoins_non_decoratifs.py` le dirait
décoratif. C'est la leçon de l'ADR 5398 : une règle sans population écrit ce qu'on imagine du
problème, et rien ne dément l'imagination.

La portabilité du geste clavier reste donc tenue par une relecture, et le cas vérifiable est nommé
ici plutôt qu'un principe : `MainViewTest` pousse `^F` et a raison de le faire.

## Conséquences

- **La question « ce système porte-t-il POSIX ? » a une seule écriture.** Elle en avait cinq ; quatre
  sont absorbées dans `fixture.SystemeDeFichiers`. Une copie qui diverge de ses soeurs ne fait rougir
  personne.
- **Ce garde ne prouve pas qu'une déclaration est vraie.** Un `@EnabledIf` nommant un prédicat qui
  rendrait toujours vrai passerait : il est suivi par son nom, pas par son corps. D'où
  `verification: probable`.
- **Une aide appelée depuis un autre fichier échappe.** La délégation est suivie dans le fichier
  seulement, comme le garde 5278 le fait depuis #5353.

## Alternatives écartées

- **Amender l'`enforced_by` de la 3802.** Une ADR acceptée ne se réécrit pas ici : elle se complète.
- **Poser le cliquet au-dessus de zéro pour tolérer les deux `assumeTrue`.** Ils sont justes, pas
  tolérés. Un cliquet les aurait comptés comme une dette, et aurait appris à lire un chiffre non nul
  comme normal.
