---
type: adr
title: "Une tâche de fond qui calcule mène de front la moitié des cœurs, et laisse l'autre à l'interface"
status: stable
article: A15
heuristiques:
  - "nielsen-1"
chantier: "#6178 (l'interface tient O3 et O5), lot #6175"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/importation/ParallelismeDeLImportTest.java"
  - "src/test/java/fr/univ_amu/iut/commun/model/ParallelismeDeFondTest.java"
verification_note: "le premier compte les transformations en vol dans un vrai import et les veut égales à la borne du poste, ni plus ni moins ; le second tient la règle de calcul, passée à la mutation. Aucun des deux ne regarde une image : que l écran ne gèle plus se lit dans le relevé du banc de l IHM, qui ne tourne pas en CI"
relations:
  amende: ["0044-le-mecanisme-de-parallelisme-suit-la-nature-de-l-attente"]
  amendee_par: ["6256-la-reactivation-mene-la-moitie-des-coeurs-comme-l-import"]
verified:
  - by: machine:ci
    at: 2026-10-13
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# Une tâche de fond qui calcule mène de front la moitié des cœurs, et laisse l'autre à l'interface

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-14** : le point 4 est **amendé** par
    [6256](6256-la-reactivation-mene-la-moitie-des-coeurs-comme-l-import.md) : la réactivation mène
    elle aussi la moitié des cœurs. Seule la compaction garde les siens.

## Contexte

L'[ADR 0044](0044-le-mecanisme-de-parallelisme-suit-la-nature-de-l-attente.md) chiffre la borne d'un
parallélisme sur ce qu'elle protège, et range celle de l'import sous « les cœurs » :
`availableProcessors()`. Une transformation audio occupe un cœur entier. Menées de front sur tous,
elles n'en laissent aucun au fil JavaFX ni à celui qui dessine.

Le banc de l'IHM l'a mesuré le 2026-10-13 (#6175), sur un poste à 16 cœurs, à l'échelle 1, en datant
chaque écart entre deux images. Vingt-six tirs alternés, les traitements de front émulés par les fils
porteurs :

| Traitements de front | Écart max, nuit de 1572 WAV | Écart max, nuit de 20 Gio | Durée à 20 Gio |
|---|---|---|---|
| 16 | 178 à 442 ms | 103 à 258 ms | 13,1 s |
| 14 | 276 et 387 ms | 93 et 318 ms | 13,0 s |
| 12 | 155 et 199,5 ms | 90 et 145 ms | 13,4 s |
| 10 | 125 et 134 ms | 104 et 144 ms | 13,8 s |
| 8 | 65 à 255 ms | 74 et 103 ms | 14,3 s |

Le seuil d'O3 est de 200 ms. Les écarts qui le passent tombent entre le premier et le dernier fichier
terminé. À 8, plus aucun écart de plus de 100 ms ne tombe dans cette phase, et la sonde voit de 44 à
59 images par seconde au lieu de 16 à 42. Les 255 ms de cette ligne viennent d'ailleurs : la première
image de la table de suivi, que le parallélisme ne change pas (#6197).

## Décision

**1. Une tâche de fond qui calcule mène de front la moitié des cœurs du poste**, arrondie au profit de
l'interface, et jamais moins d'un traitement. La règle a un nom et un seul endroit :
`ParallelismeDeFond`.

**2. L'import la lit.** `ServiceImport.PARALLELISME_FICHIERS` vaut `ParallelismeDeFond.duPoste()`, pour
le découpage comme pour la copie protégée.

**3. La table de l'ADR 0044 gagne une ligne, et sa dernière en perd deux noms :**

| Borne | Valeur | Ce qu'elle protège |
|---|---|---|
| `DecoupageParallele`, `PreparationOriginaux` | `ParallelismeDeFond.duPoste()` | la **réactivité de l'interface** pendant l'import |
| `CompacteurDepot` | `availableProcessors()` | les **cœurs** |

**4. La compaction du dépôt et la réactivation gardent leur borne.** Elles calculent sur tous les
cœurs, et rien n'a mesuré ce qu'elles font à l'écran : leur appliquer la règle serait payer un coût
pour un gel qu'on n'a pas vu.

## Conséquences

Sur le code qui porte la règle, la nuit de 20 Gio rend 76 et 134 ms. Dans l'IHM, son import dure
15 % de plus : 15,0 s au lieu de 13,1, là où l'émulation en annonçait 9. `BancImport`, qui ne dessine
rien, ne ralentit pas. Sur un poste à deux cœurs l'import devient séquentiel.

**La règle n'est mesurée dans l'IHM que sur un poste**, à 16 cœurs de trois familles (six à 5,1 GHz,
huit à 4,4, deux à 2,5). Sur le premier poste de la campagne, à 8 cœurs dont 4 physiques, le gel
n'apparaissait pas à la borne d'origine (de 76 à 151 ms sur des nuits de 300 WAV), et la moitié y
donne quatre traitements. `BancImport` y a chiffré le coût le même jour, sans interface, sur la nuit
standard : 37,3 s à 8 fils porteurs, 41,3 et 43,1 s à 4, un tir à froid écarté. Soit 13 % de plus,
dont 2,5 % pour la transformation ; le reste vient de la copie, qui varie de 15,7 à 27,7 s au même
réglage. Si un poste de cette taille paie cher un gel qu'il n'avait pas, la règle se révise ici.

Le banc dessine en logiciel, ce qui charge un cœur de plus que l'application lancée hors des tests :
la borne est prudente pour un utilisateur, et juste pour un poste sans accélération graphique.

## Ce qui a été écarté

**Les deux tiers des cœurs** (10 sur 16). Moins coûteux, et le seuil tient sur quatre tirs. Mais
des écarts de plus de 100 ms restent dans la transformation, et à 12 un tir rend 199,5 ms. La
dispersion d'un tir à l'autre est du même ordre que l'effet : la marge compte plus que la durée.

**Laisser deux cœurs** (14 sur 16). 387 ms à 1572 WAV.

**Regrouper les relais vers le fil JavaFX.** Chaque fichier y poste deux à quatre événements, et
`SuiviLignes.ligne(numero)` parcourt la liste. La mesure l'écarte : bornée à 8, la transformation émet
encore 91 % des événements par seconde, sans plus d'écart.

**Un réglage.** L'ADR 0044 l'a déjà écarté : un arbitrage que l'utilisateur ne peut pas évaluer.
