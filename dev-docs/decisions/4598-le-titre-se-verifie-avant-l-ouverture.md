---
type: adr
title: "Le titre de PR se vérifie avant l'ouverture, et les titres d'issue ne s'alignent pas"
status: stable
article: A24
chantier: "#4598"
decided_at: 2026-08-26
verification: certaine
enforced_by:
  - "scripts/methode/verifie-controle-du-titre.py"
verified:
  - by: machine:ci
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Le titre de PR se vérifie avant l'ouverture, et les titres d'issue ne s'alignent pas

## Contexte

Le 2026-08-26, quatre PR ouvertes le même jour ont rougi sur le garde `titre`, pour la même cause :
un espace avant les deux-points.

Le dépôt pratique deux conventions à un caractère d'écart. Mesure du jour, sur les 100 derniers
titres de chaque famille :

| Titres | Avec espace avant `:` | Sans | Hors format |
|---|---:|---:|---:|
| Issues | **62** | 15 | 23 |
| PR fusionnées | **0** | **100** | 0 |

La seconde ligne est propre parce que `titre-pr.yml` refuse, pas parce que la main écrit juste.
L'ADR 0040 dit pourquoi le refus existe : dans `feat(scope): sujet`, le deux-points est un jeton de
syntaxe, et un espace rend le sujet illisible pour semantic-release, qui cesse de publier sans rien
faire rougir.

Écrire la règle une fois de plus ne changerait rien. `CONTRIBUTING.md` la porte déjà dans un bloc
`[!IMPORTANT]`, avec sa raison et son coût vécu, et les quatre PR l'ont manquée quand même.

## Où le défaut entre, mesuré

Les quatre PR sont #4570, #4588, #4589 et #4591, seuls renommages de titre sur les 60 dernières.
Leurs sujets de commit de branche étaient **conformes**, tous les quatre. Sur 297 sujets de branche
hors `main`, 286 le sont et aucun n'est hors Conventional Commits : le défaut n'entre pas au commit.

Trois des quatre titres sont le sujet du commit retapé avec ses accents, le quatrième est le titre
de l'issue #4574 recopié tel quel, espace compris. Le défaut entre à la frappe du titre. En écrivant
du français correct, la main lui applique la typographie française, et l'espace avant le deux-points
vient avec.

Ce qui décide de la frappe est le nombre de commits. `gh pr create --fill` reprend le sujet du
commit quand la branche n'en porte qu'un, et titre la PR avec le **nom de branche** au-delà, forme
que le garde refuse. Mesure sur les 30 dernières PR fusionnées : 20 tenaient en un commit, 10 en
plusieurs. Les quatre rouges avaient toutes trois commits ou plus, donc un titre qui ne pouvait pas
être rempli et devait être écrit.

## Décision

**Le titre se vérifie en local, avant `gh pr create`**, en lançant le garde qui existe déjà :

```bash
./.github/scripts/verifie-titre-pr.sh "<le titre>"
```

Aucune règle nouvelle, aucun second lieu où la convention s'écrirait. Trois documents nomment cette
commande au moment où le titre s'écrit : `CONTRIBUTING.md` §4, et les deux copies de la compétence
`clore-une-issue`, dont l'étape 4 de la fonction de garde disait « VERIFIER le titre » sans dire
comment.

**Les titres d'issue ne s'alignent pas sur ceux des PR.** C'est une décision, et elle se mesure :
aligner aurait coûté soixante-deux renommages pour couvrir un des quatre défauts, le seul recopié
depuis une issue. L'espace avant les deux-points est la typographie française correcte, et aucun
analyseur ne lit un titre d'issue. La convention des PR n'est pas meilleure, elle est contrainte par
`semantic-release` ; ce qui est contraint reste là où la contrainte s'applique.

**Le contrôle ne descend pas au commit.** Un hook `commit-msg` entretiendrait un corpus déjà à 286
sur 297 et manquerait l'instant où le défaut naît. Le dépôt fusionne en squash et écarte les sujets
de branche : les garder dépasserait la cause mesurée.

## Conséquences

Le coût d'un titre fautif passe d'une PR à ré-éditer plus une vérification à relancer, à une
commande qui rend un verdict avant l'ouverture. Ce remède repose encore sur un geste, git n'offrant
aucun point d'accroche à l'ouverture d'une PR, et c'est assumé : la commande remplace un réflexe
typographique par un verdict, ce que la relecture ne fait pas.

`CONTRIBUTING.md` §4 documentait `gh pr create --fill` comme le geste d'ouverture, sans dire qu'il
ne convient qu'à une branche d'un seul commit. Les deux cas y sont maintenant écrits.

## Vérification

`scripts/methode/verifie-controle-du-titre.py`, bloquant dans `lint.yml`. Il exige que les trois
documents nomment `verifie-titre-pr.sh`, et il **relance** ce script sur deux titres témoins, l'un
fautif et l'autre conforme. Une méthode qui nommerait une commande devenue permissive vaudrait moins
que rien, son vert étant rassurant ; le témoin conforme tient l'autre bord, un script qui refuserait
tout ne prouvant rien par ses rouges.

Son auto-test l'éprouve sur un arbre sain puis sur sept états cassés, dont le desserrage du script
cité et son durcissement.
