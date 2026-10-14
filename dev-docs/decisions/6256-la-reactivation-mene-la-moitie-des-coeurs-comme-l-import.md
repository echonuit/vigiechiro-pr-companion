---
type: adr
title: "La réactivation mène la moitié des cœurs, comme l'import"
status: stable
article: A15
heuristiques:
  - "nielsen-1"
chantier: "#6246 (les suites de #6178), lot #6256"
decided_at: 2026-10-14
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/passage/model/ParallelismeDeLaReactivationTest.java"
verification_note: "le test refuse une borne de réactivation qui ne serait pas celle de ParallelismeDeFond ; vu rouge sur le code d avant (16 au lieu de 8). Que l écran ne gèle pas se lit dans le relevé du banc de l IHM, qui ne tourne pas en CI"
relations:
  amende: ["6175-une-tache-de-fond-qui-calcule-laisse-la-moitie-des-coeurs-a-l-interface"]
verified:
  - by: machine:ci
    at: 2026-10-14
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# La réactivation mène la moitié des cœurs, comme l'import

## Contexte

L'[ADR 6175](6175-une-tache-de-fond-qui-calcule-laisse-la-moitie-des-coeurs-a-l-interface.md) a
donné à l'import la moitié des cœurs : à tous, il gelait l'écran jusqu'à 442 ms. Son point 4 laissait
tous les leurs à la compaction et à la réactivation, faute d'avoir vu ce qu'elles font à l'écran. La
réactivation d'une nuit hydratée régénère pourtant ses bruts par la même transformation, en
parallèle, et n'avait été éprouvée que par son service (#6174).

## Ce que la mesure a établi

Le banc de l'IHM réactive maintenant une nuit hydratée par sa modale (`BancIhmReactivationTest`) :
nuit importée, archivée, réduite à des placeholders ; fiche du passage ouverte, carte désignée sans
dialogue, « Réactiver ce passage » cliqué, chaque écart entre deux images daté. Le 2026-10-14, second
poste, échelle 1, rendu GPU, nuit de 1572 bruts de 5 s :

| | Écart maximal entre deux images | Régénération | Réactivation entière |
|---|---|---|---|
| 16 cœurs, tous (5 tirs) | de 75 à 151 ms | 8,3 à 8,6 s | 29,3 à 29,7 s |
| 16 cœurs, la moitié (5 tirs) | de 48 à 85 ms | 8,5 à 8,9 s | 26,2 à 26,6 s |
| restreint à 4 cœurs, tous (3 tirs) | de 29 à 58 ms | 8,5 à 8,6 s | 25,5 à 25,9 s |
| restreint à 4 cœurs, la moitié (3 tirs) | de 23 à 26 ms | 11,3 à 15,3 s | 28,2 à 33,1 s |

Le poste à 4 cœurs est émulé par `taskset`, que la JVM voit (« 16 total, 4 available »). Les tirs à
4 cœurs sont alternés ; ceux à 16 cœurs, tous puis la moitié, sont à une demi-heure d'écart.

**À tous les cœurs, aucun tir ne gèle l'écran**, même restreint à quatre. La famine que le porteur
craignait n'apparaît pas pour notre fil JavaFX sur ce poste.

## Décision

**La réactivation mène la moitié des cœurs** : `ServiceReactivationPassage.PARALLELISME` vaut
`ParallelismeDeFond.duPoste()`, la règle de l'ADR 6175. Décision du porteur du 2026-10-14.

Trois raisons, la mesure faite :

- **la marge** : les écarts sont plus courts dans chaque configuration, et la règle ne repose pas sur
  la machine la plus confortable ;
- **le reste du poste** : à tous les cœurs, les autres applications de l'utilisateur n'ont plus de
  processeur pendant la régénération, et le banc ne voit que notre fenêtre ;
- **une seule règle** pour les tâches de fond qui calculent, plutôt qu'une exception par geste.

**Son prix est écrit** : rien sur un poste de seize cœurs, de 10 à 30 % de durée en plus sur un poste
de quatre, où la régénération passe de 8,5 s à 11 à 15 s.

## Ce qui n'est pas établi

- **Ce que voient les autres applications** pendant une réactivation : rien ne le mesure.
- **Un vrai poste à peu de cœurs**, plus lent par cœur que le second poste restreint.
- **La voie des fichiers transformés**, qui ne régénère rien, écartée par le porteur pour ce lot ; le
  **filet du démarrage**, que le banc ne traverse pas ; la **compaction**, qui garde tous ses cœurs.

## Conséquences

- `ParallelismeDeLaReactivationTest` refuse une borne qui redeviendrait celle du poste entier.
- `docs/benchmarks/README.md` porte les relevés ; le banc de l'IHM gagne un scénario qui passe par la
  vraie modale (`ReactivationDuBanc`).
