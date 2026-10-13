---
type: adr
title: "La taille de police se déclare sur la racine, comme la famille"
status: stable
article: A23
heuristiques: ["nielsen-4"]
chantier: "#5496 (l'hypothèse de plateforme), lot #6191"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "TypographieTest#la_taille_est_declaree_sur_la_racine"
  - "TypographieTest#un_texte_sans_taille_propre_prend_la_taille_declaree"
verified:
  - by: machine:ci
    at: 2026-10-13
relations:
  complete: ["3361-la-typographie-est-embarquee"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# La taille de police se déclare sur la racine, comme la famille

## Contexte

L'[ADR 3361](3361-la-typographie-est-embarquee.md) a embarqué Noto Sans et l'a citée en tête de
`base.css`. La règle `.root` déclarait la famille, et aucune taille.

Un texte sans taille propre prenait donc celle que JavaFX tient du système : 12 px sous Windows,
13 px sous Linux et macOS. La famille était celle du produit, la taille celle de la machine.

Le passage complet sous Windows du 13 octobre 2026 l'a montré sur un cas. « complétude inconnue »,
dans une cellule de table simple de 130 px, mesure 133,3 px à 13 px et se coupe. À 12 px il mesure
123,0 px et tient. Le cas de test qui attendait la coupure passait sous Linux et macOS et rougissait
sous Windows (#6190). Rejoué sous Linux avec `-Dcom.sun.javafx.fontSize=12`, il rendait le même
échec.

Le garde de troncature des captures tourne sous Linux. Son verdict valait à 13 px, et ne disait rien
de ce qu'un utilisateur Windows voyait.

## Décision

**`base.css` déclare `-fx-font-size: 12px` sur `.root`.** Toute scène habillée passe par cette
feuille, celles de l'application comme celles des outils de capture
([ADR 3374](3374-une-fenetre-porte-son-habillage-ou-elle-n-est-pas-le-produit.md)).

12 px est la taille que Windows rendait déjà : le produit y garde son rendu. Sous Linux et macOS, les
textes sans taille propre perdent un pixel, et les marges que Modena exprime en `em` suivent.

Les 115 règles du produit qui fixent leur taille en pixels ne changent pas.

## Conséquences

Toutes les captures sont régénérées : elles étaient rendues à 13 px.

Une comparaison de tournages dont l'avant précède ce changement et l'après le suit relève presque
tous ses cas au-dessus de leur plancher. `compare_tournages.py` les compte et ne refuse pas. La
référence d'après est le premier tournage qui suit.

Le verdict du garde de troncature vaut pour les trois systèmes, là où la taille vient de `.root`.

## Ce que la règle ne tient pas

**Ce que JavaFX mesure hors du graphe de scène.** Un `ChoiceBox` calcule sa largeur avec
`Font.getDefault()`, à laquelle aucune feuille ne s'applique
([ADR 3417](3417-la-galerie-rend-comme-une-machine-accordee-au-produit.md)). Mesuré le 13 octobre
2026 sur une scène habillée, le système étant forcé par `-Dcom.sun.javafx.fontSize` :

| Taille déclarée | Taille du système | Libellé | `ChoiceBox` | `ComboBox` |
|---|---|---|---|---|
| 12 px | 12 px | 124 px | 159 px | 170 px |
| 12 px | 13 px | 124 px | 172 px | 170 px |
| 13 px | 12 px | 134 px | 166 px | 182 px |
| 13 px | 13 px | 134 px | 172 px | 182 px |

Le libellé et le `ComboBox` suivent la taille déclarée. Le `ChoiceBox` reste plus large de 13 px sous
Linux et macOS que sous Windows, et son texte tient dans les quatre cas.

## Ce qui la tient

`la_taille_est_declaree_sur_la_racine` lit la règle `.root` de `base.css` et rougit si la
déclaration en disparaît.

`un_texte_sans_taille_propre_prend_la_taille_declaree` monte une scène habillée et lit la taille d'un
libellé sans style. Il a été vu rouge sur l'état d'avant, où il lisait 13 px sur un poste Linux. Sous
Windows il passerait sans la règle, le système y rendant 12 px de lui-même : c'est le premier cas qui
garde la règle sur les trois systèmes.

## Alternatives écartées

**Déclarer 13 px.** Linux, macOS et les captures publiées ne bougeaient pas, et sous Windows les
textes sans taille propre grossissaient d'un pixel. Le porteur a retenu 12 px le 13 octobre 2026. Le
tableau ci-dessus ne départage pas les deux tailles : l'écart du `ChoiceBox` entre systèmes vaut
13 px à 12 px déclarés, et 6 px à 13 px.

**Régler la taille par la propriété `com.sun.javafx.fontSize`.** Elle règle aussi
`Font.getDefault()`, donc le `ChoiceBox`. Elle est interne à JavaFX et se lit au démarrage du
toolkit : les outils de capture et les tests, qui n'entrent pas par `App`, ne la recevraient pas.
