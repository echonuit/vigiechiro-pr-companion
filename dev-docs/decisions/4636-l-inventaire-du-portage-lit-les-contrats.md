---
type: adr
title: "L'inventaire du portage lit les contrats, pas les noms de fichiers"
status: stable
article: A3
chantier: "#4636 (lot 3 du portage, EPIC #4634)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "scripts/methode/contrats-des-gardes.py"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# L'inventaire du portage lit les contrats, pas les noms de fichiers

## Contexte

Les apports de `vigiechiro-companion` se portent ici à la main, par lots. L'inventaire de ce qui
reste à porter comparait des **noms de fichiers**, après normalisation de deux conventions : la
ligne d'origine nomme `cliquet-<geste>.py`, ce dépôt `<numéro>-<geste>.py`.

Il rendait donc « présent » pour tout homonyme. #4635 a montré ce qu'il ne voyait pas : une loupe
portée émettait `LOUPE densite-de-commentaire`, quand le rapport lit `^LOUPE (\d+) \| candidats=`.
Le script tournait, trouvait 43 candidats, et le rapport les jetait. Comptée comme portée, elle ne
rapportait rien à personne depuis son arrivée.

**La différence que l'inventaire normalisait pour comparer est celle qui cassait le produit.**

Trois écarts qu'un nom identique ne distingue pas : le **prédicat**, ce qui est refusé ; la
**population**, où l'on compte ; le **seuil**, quel stock est toléré. À quoi s'ajoute le cas de
#4635, le garde dont la sortie n'est pas comprise par la chaîne qui l'appelle.

## Décision

**Un relevé lit ce qu'un garde déclare.** Le geste, sous les deux conventions ; l'ADR, qu'elle soit
nommée par numéro ou par slug ; la population, qu'elle s'importe de `_commun` ou s'écrive en clair ;
le seuil, dans l'en-tête de son ADR ; et le titre de son verdict.

**Il montre sans juger.** Un audit qui ne peut pas trancher rend les deux colonnes côte à côte, et
l'humain lit. Trente-trois écarts sur trente gestes au premier passage, dont la plupart sont connus
et voulus.

**Il ne tourne pas en CI**, parce qu'il lit deux arbres et que l'autre n'existe pas sur le runner.
Seul son auto-test y tourne, et c'est nécessaire : l'outil qui doit voir les gardes hors service
serait sinon le premier à l'être.

## Les deux limites, déclarées

**Le prédicat n'est pas comparé.** Deux motifs d'expression régulière ne se confrontent pas
mécaniquement, et prétendre le faire rendrait un verdict que rien ne fonde.

**L'appariement se fait par le geste.** Deux gardes du même sujet sous deux gestes différents
sortent en deux absences : `apostrophe-en-libelle` ici et `apostrophe_droite` ailleurs, là où un
lecteur voit un écart de population. Les rapprocher automatiquement demanderait un dictionnaire
écrit à la main, soit exactement la liste qui dérive et que ce relevé existe pour remplacer.

## Ce que le premier passage a rendu

Deux gardes absents ici, tous deux déclarés sans objet ailleurs : celui des renvois vers un dépôt
disparu, que la constitution écarte, et celui des ADR citées par numéro, dont l'identité est ici le
numéro. Un troisième, `apostrophe_droite`, est le sujet de #4637.

Les écarts de population disent surtout #4488 : les gardes d'ici lisent les deux arbres Java quand
leurs homologues lisent la production seule.

## Alternatives écartées

**Allonger la liste écrite à la main.** C'est ce qui existait, et elle avait dérivé au point de
compter comme portée une loupe qui ne rapportait rien.

**Comparer les prédicats.** Deux motifs ne se confrontent pas, et un relevé qui l'affirmerait
mentirait sur ce qu'il mesure.

**Le lancer en CI.** L'autre arbre n'y existe pas. Un garde qui ne peut pas lire ce qu'il compare
rendrait un vert vide, ce que l'article A3 refuse.
