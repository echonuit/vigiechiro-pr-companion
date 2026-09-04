---
type: adr
title: "La CI garde ses inventaires comme ceux du produit"
status: stable
article: A5
chantier: "#3794, lot 2 des suites #3802"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - ".github/scripts/verifie_inventaires_ci.py"
verified:
  - by: machine:ci
    at: 2026-08-16
relations:
  prolonge: ["3661"]
---

# La CI garde ses inventaires comme ceux du produit

## Contexte

Ce dépôt exige de son **produit** que ses inventaires soient prouvés, et il le fait partout : commandes
CLI contre `dev-docs/cli.md` dans les **deux sens**, surface CLI contre un compteur verrouillé, vues
contre captures contre doc, ADR contre index contre nav, appelants de `Files.walk` contre leur
rattrapage.

De sa **propre description**, il n'exigeait rien. Trois inventaires étaient tenus à la main, et les
trois avaient dérivé - mesuré à la clôture du lot 3 de #3518 :

| Inventaire | Écart |
|---|---|
| tableau des workflows | **2 absents** : `codeql.yml`, `securite-dependances.yml` |
| tableau des gardes autotestées | **6 absents** |
| chemins surveillés ↔ classes jouées | concordait déjà |

**Les deux workflows absents étaient ceux de sécurité.** Ce n'est pas un hasard : un inventaire non
gardé perd d'abord ce qu'on regarde le moins.

**Et le défaut se reproduit pendant qu'on le décrit** : l'issue #3771 annonçait cinq gardes
manquantes, il y en avait **six**. Les cinq avaient été listées à l'œil ; la sixième est sortie d'un
comptage. C'est l'argument qui a fait écrire la garde **avant** les corrections - elle donne la liste
au lieu de la refaire.

## Décision

**Ce que la CI dit d'elle-même est confronté à ce qu'elle fait, par une garde.**
`verifie-inventaires-ci.sh` compare trois paires et rougit sur l'écart, avec son `--auto-test` dans
`lint.yml` comme les autres (ADR 3661).

### Ce qu'elle ne vérifie PAS, et pourquoi c'est écrit dedans

**Le contenu des colonnes.** La colonne « où elle tourne » porte des nuances **vraies** :
`lance-test-filme.sh` vit dans un workflow manuel, `mesure-duree-portail.sh` **avertit** sans bloquer.
Exiger un libellé pousserait à compléter le tableau **en l'aplatissant** - complet et trompeur, ce qui
est pire que lacunaire.

**Qu'une classe mérite d'être surveillée.** Aucun script ne tire cela du code. Que `GestesFichiers` et
`TailleFichier` doivent figurer parmi les chemins du contrat de fichiers est une **décision**, posée à
la main (#3814) - et le dire évite de croire que la garde couvre ce qu'elle ne couvre pas.

### Comparer des fichiers, jamais compter des lignes

`maven.yml` occupe **cinq** lignes du tableau, une par job. Une garde qui compterait rougirait sur un
tableau juste, et se ferait retirer. Le premier cas de l'auto-test est ce **contrôle négatif**.

## Conséquences

- **Le chiffre de l'ADR 3661 n'a plus à être tenu à la main.** Elle disait « dix scripts sur onze » ;
  ils sont dix-huit aujourd'hui. Le nombre n'était pas faux, il **datait** - et c'est exactement ce
  qu'une garde rend sans objet.
- **Deux règles de l'auto-test ne sont attrapées que par la vérification du MOTIF de la sortie**,
  pas du code de retour. Sans elle, retirer la détection d'un workflow fantôme et retirer le refus sur
  section renommée restaient **toutes deux vertes**, le refus tombant de toute façon pour une autre
  raison. Leçon de `veille-plateformes.sh` (#3526), appliquée dès l'écriture.
- **La garde est elle-même un inventaire tenu à la main** : ses **trois** paires. Un quatrième
  inventaire qui apparaîtrait ne serait pas confronté, et rien ne le dirait. La régression est plus
  lente qu'avant, elle n'est pas impossible.

## Alternatives écartées

- **Corriger les trois tableaux sans garde** : c'est exactement ce qui a produit l'état de départ. Ils
  redeviendraient faux au prochain workflow.
- **Une garde par tableau** : trois scripts pour un même invariant - « ce qui existe est décrit » -,
  c'est la duplication qu'une passe 7 est censée retirer.
- **Faire déduire par la garde ce qui mérite d'être surveillé** : donnerait le pire des deux, un faux
  sentiment de complétude sur une décision qu'elle ne peut pas prendre.
