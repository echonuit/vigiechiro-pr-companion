---
type: adr
title: "Muter un garde de méthode passe par son point d'entrée, pas par la fin du fichier"
status: stable
article: A2
chantier: "#4770 (suites de #4713)"
decided_at: 2026-08-29
verification: certaine
enforced_by:
  - "scripts/methode/temoins-de-methode-non-decoratifs.py"
verified:
  - by: machine:ci
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Muter un garde de méthode passe par son point d'entrée, pas par la fin du fichier

## Contexte

L'[ADR 4490](4490-un-temoin-se-prouve-par-mutation-mecaniquement.md) rend l'article A2 mécanique :
un garde est neutralisé, la suite doit rougir. Elle ne couvrait que `scripts/adr/`. Les gardes de
`scripts/methode/`, dont onze bloquants, avaient chacun un `--auto-test` que rien n'obligeait à
détecter quoi que ce soit.

Étendre paraissait mécanique. Ce ne l'était pas.

## Le défaut

**La neutralisation ajoutée en fin de fichier n'agit que si le fichier est importé.** Le harnais des
gardes d'ADR fait exactement cela : il les charge comme modules, tout le fichier s'exécute, puis il
appelle leurs fonctions. Un garde de méthode se lance autrement - `python3 <garde> --auto-test` - et
son `raise SystemExit` part **avant** d'atteindre une neutralisation posée à la fin.

Mesuré en écrivant ce lot : cinq gardes ont paru décoratifs, dont **deux qui avaient été vus rougir
sur un vrai défaut le même jour**. Le résultat était plausible, et entièrement faux.

## Décision

**La neutralisation s'insère avant le point d'entrée, et la fonction d'auto-test en est épargnée.**

Trois règles, chacune tirée d'une mesure :

**L'insertion, non l'ajout.** Le marqueur est `if __name__ == "__main__":`, et la neutralisation se
pose juste avant. C'est le seul endroit qui garantit que les fonctions sont remplacées quand
l'auto-test les appelle.

**L'exemption de l'auto-test, dérivée du nom.** Toute fonction dont le nom porte « auto » et
« test » est épargnée. Deux gardes du corpus nomment la leur `auto_test`, sans souligné : la
neutraliser les faisait rougir parce que leur point d'entrée rendait une liste au lieu d'un entier,
non parce qu'ils avaient cessé de détecter. Deux verdicts sur sept ne voulaient rien dire.

**Le refus, non le saut.** Six gardes exécutent leur corps au niveau du module, sans point d'entrée.
Ils sont nommés à chaque passage plutôt que passés en silence, et leur sort est une décision à part
(#4788).

## Conséquences

**Le corpus se dérive de `lint.yml`**, comme celui de l'ADR 4490 se dérive des appels du harnais, et
pour la même raison qu'elle donne : un glob vieillit, et un garde neuf passerait au travers. La
dérivation a été vérifiée à l'usage - un garde ajouté par un autre chantier une heure après la
livraison est entré seul dans le corpus, qui est passé de sept à huit éprouvés.

**Le garde est dans son propre corpus, et c'est sain.** Muté, son auto-test rougit : il se prouve
lui-même. Mesuré, parce qu'une auto-référence se vérifie au lieu de se raisonner.

**La mutation porte sur un arbre jetable**, l'acquis de #4700 : `scripts/` copié, le reste lié.
Étendre la mutation à un second corpus ne multiplie donc pas le risque de laisser un garde
neutralisé derrière soi - il n'y a plus rien à restaurer.

**Ce que cette décision ne couvre pas.** Un auto-test peut rougir sous mutation sans rien prouver de
plus qu'un plantage : la neutralisation casse ce qu'elle touche, et « rouge » ne distingue pas
« a détecté » de « s'est effondré ». Le défaut que ce garde chasse est l'inverse - un auto-test qui
reste **vert** quoi qu'il arrive - et c'est celui-là qu'il attrape.

## Alternatives écartées

- **Deviner le début du corps principal** pour les six gardes sans point d'entrée. Couverture
  complète, au prix d'une heuristique qui se tromperait un jour en silence - exactement l'erreur
  commise cinq fois en écrivant ce lot, et qu'un garde ne doit pas industrialiser.
- **Réutiliser le garde des ADR tel quel.** Sa neutralisation par ajout ne prend pas sur un script
  exécuté ; l'appliquer aurait rendu vert sur un corpus qu'il ne mutait pas.
- **Donner un point d'entrée aux six.** Six fichiers touchés pour une raison qui n'est pas leur
  défaut. C'est peut-être le bon geste, et il mérite d'être posé plutôt que glissé dans ce lot.
