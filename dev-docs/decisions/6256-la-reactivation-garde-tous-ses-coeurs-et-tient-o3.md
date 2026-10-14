---
type: adr
title: "La réactivation garde tous ses cœurs : menée par sa modale, elle tient O3"
status: stable
article: A5
chantier: "#6246 (les suites de #6178), lot #6256"
decided_at: 2026-10-14
verification: humaine
verification_note: "le seuil se lit dans le relevé du banc de l IHM, qui dessine sur l affichage réel et ne tourne pas en CI. Aucun test ne peut refuser un gel qu il ne voit pas : la décision tient par ses chiffres, et se rouvre sur un relevé à 200 ms"
verified:
  - by: machine:ci
    at: 2026-10-14
relations:
  prolonge: ["6175-une-tache-de-fond-qui-calcule-laisse-la-moitie-des-coeurs-a-l-interface"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# La réactivation garde tous ses cœurs : menée par sa modale, elle tient O3

## Contexte

L'import menait de front autant de transformations que de cœurs, et gelait l'écran jusqu'à 442 ms à
20 Gio. L'[ADR 6175](6175-une-tache-de-fond-qui-calcule-laisse-la-moitie-des-coeurs-a-l-interface.md)
lui a donné la moitié des cœurs, et laissé tous les siens à la réactivation : « rien n'a mesuré ce
qu'elle fait à l'écran ». La réactivation d'une nuit hydratée régénère pourtant ses bruts par la même
transformation, en parallèle. Et elle n'avait été éprouvée que par son service, jamais par la modale
de l'application (#6174).

## Ce que la mesure a établi

Le banc de l'IHM réactive maintenant une nuit hydratée par sa modale (`BancIhmReactivationTest`). La
nuit est importée, archivée et réduite à des placeholders, comme une nuit reconstruite ; le banc ouvre
la fiche du passage, désigne la carte sans dialogue, clique « Réactiver ce passage » et date chaque
écart entre deux images parmi les jalons de la modale.

Le 2026-10-14, sur le second poste (16 cœurs, carte Intel), à l'échelle 1, cinq tirs par nuit et par
dessin, alternés :

| Écart maximal entre deux images | Rendu GPU | Rendu logiciel |
|---|---|---|
| 1572 bruts de 5 s, réactivés en 29 s | de 75 à 151 ms | de 45 à 85 ms |
| 8 bruts de 20 min, réactivés en 11 s | de 34 à 64 ms | de 50 à 109 ms |

Aucun tir n'atteint 200 ms. Les écarts de plus de 100 ms tombent au clic, quand la modale s'ouvre
(101 et 151 ms), pendant la régénération des 1572 bruts (110 à 130 ms), et à l'affichage du compte
rendu (109 ms). Chaque tir a tout réactivé, et le compte rendu dit que les séquences ont été
régénérées à partir des bruts.

## Décision

**La réactivation garde `availableProcessors()`.** Elle tient O3 sans la règle de l'ADR 6175, et la
lui appliquer allongerait la régénération pour un gel qu'aucun relevé ne montre.

**Elle se rouvre sur un relevé, pas sur une analogie avec l'import.** Un écart de 200 ms ou plus, daté
par ce banc pendant une réactivation, la rouvre.

## Ce qui n'est pas établi

- **Pourquoi elle tient là où l'import gelait.** La charge de calcul est la même ; ce qui diffère est
  l'écran. Pendant un import, chaque fichier terminé met à jour une ligne de la table de suivi ; la
  modale de réactivation ne porte que deux barres. Une piste, non mesurée.
- **La voie des fichiers transformés**, où le dossier contient déjà les séquences et où la modale
  demande s'il faut les copier : elle ne régénère rien. Écartée par le porteur pour ce lot.
- **Le filet du démarrage**, que le banc ne traverse pas (`App.start`).
- **La compaction du dépôt**, qui calcule elle aussi sur tous les cœurs.
- **Un autre poste**, et un poste à peu de cœurs, où la réactivation laisserait moins de place à
  l'écran.

## Conséquences

- `docs/benchmarks/README.md` porte les vingt relevés.
- Le banc de l'IHM gagne un scénario qui passe par la vraie modale, et une porte de test qui désigne un
  dossier à la fiche du passage sans dialogue natif (`ReactivationDuBanc`).
