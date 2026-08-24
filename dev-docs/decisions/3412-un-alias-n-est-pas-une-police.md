---
type: adr
title: "Un alias n'est pas une police"
status: stable
article: A23
chantier: "#3412, suite de l'[ADR 3361](3361-la-typographie-est-embarquee.md)"
decided_at: 2026-08-06
verification: certaine
enforced_by:
  - "PoliceCouvreLIhmTest#aucune_feuille_ne_s_en_remet_a_un_alias"
verified:
  - by: machine:ci
    at: 2026-08-06
---

# Un alias n'est pas une police

## Contexte

L'[ADR 3361](3361-la-typographie-est-embarquee.md) a traité `base.css`, qui nommait trois familles
introuvables et retombait sur `sans-serif`. Elle a cherché les **familles nommées**. Elle n'a donc pas
vu les deux feuilles qui demandaient une police **par l'alias directement** :

| Feuille | Classe | Ce qui s'y affiche |
| --- | --- | --- |
| `lot.css` | `.chemin` | le chemin du dossier de dépôt |
| `importation.css` | `.apercu-valeur` | les valeurs d'aperçu à l'import |

Un alias (`monospace`, `sans-serif`, `serif`) **n'est pas une police** : c'est une demande que chaque
système résout à sa façon. Le défaut de #3361 se rejouait donc à l'identique, sur du texte à chasse
fixe - des chemins de fichiers, ce que l'utilisateur recopie et compare caractère par caractère.

**Mesuré** : le chemin `/home/observateur/VigieChiro/Car040962-2026-Pass1-A1/depot` rend sensiblement
plus large avec `Noto Sans Mono` embarquée qu'avec l'alias, et le paragraphe au-dessus se ré-enroule
en conséquence - **1,93 %** des pixels de l'écran Dépôt.

## Décision

`Noto Sans Mono` est **embarquée** (OFL 1.1, comme `Noto Sans`) et **nommée** dans les deux feuilles,
l'alias restant en dernier :

```css
-fx-font-family: "Noto Sans Mono", monospace;
```

L'alias en queue est un **filet**, pas une demande : si la fonte embarquée manquait du jar, le texte
s'afficherait quand même. Le garde ne l'interdit donc que **seul**.

## Conséquences

- **le garde porte sur l'alias, pas sur les deux cas trouvés.** `PoliceCouvreLIhmTest` refuse toute
  feuille dont un `-fx-font-family` ne cite **aucune** famille entre guillemets. Corriger deux
  occurrences aurait laissé la troisième arriver ;
- la **provenance** des quatre fichiers est écrite dans `src/main/resources/fonts/README.md`, et la
  licence renommée `LICENSE-Noto-OFL-1.1.txt` puisqu'elle couvre désormais deux familles. L'OFL permet
  l'incorporation sans imposer sa licence au logiciel hôte : le produit reste sous GPLv3 ;
- 51 aperçus changent à la première régénération - la mesure de ce qui rendait avec la police du
  système.

## Ce que cette ADR apprend au-delà de son cas

L'ADR 3361 a **cherché ce qu'elle savait chercher** : des noms de familles. Le trou n'était pas dans sa
décision - juste - mais dans son **inventaire**. La leçon vaut pour les deux gardes de typographie
écrits depuis : ils portent tous deux sur la **forme du défaut** (« une police non déterminée »,
« un caractère non couvert »), jamais sur la liste des cas connus au moment de les écrire.
