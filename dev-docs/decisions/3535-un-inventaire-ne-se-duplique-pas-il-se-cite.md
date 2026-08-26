---
type: adr
title: "Un inventaire ne se duplique pas, il se cite"
status: stable
article: A5
chantier: "#3535, clôture du chantier « le cycle et sa vérification » (#3505, #3510)"
decided_at: 2026-08-09
verification: humaine
verification_note: "aucun invariant mécanique ne couvre les quatre formes d'écriture d'une"
verified:
  - by: human:nedseb
    at: 2026-08-09
---

# Un inventaire ne se duplique pas, il se cite

## Contexte

Le 2026-08-08, un même inventaire a divergé **trois fois en une journée**. Deux de ces divergences ont
été introduites par le chantier qui écrivait, le matin même, la règle censée les éviter.

| Inventaire | Copies | Ce qui a divergé |
| --- | --- | --- |
| état des sessions de recette | **3** | S7 écrite (#3517) le jour où deux copies la disaient inexistante |
| liste des cartes d'accueil | **4** | ordre changé (#3521) ; brief et recette annonçaient **5** cartes pour **7** |
| rectangles de masque des aperçus | 1 liste manuelle | 16 déclarés pour 19 réels (#3439) |

## Le défaut

**Une copie périmée se lit exactement comme une copie juste.**

C'est ce qui la distingue d'un lien mort ou d'une référence cassée : il n'existe aucun signal. Le
Markdown est valide, `mkdocs --strict` n'a rien à dire, aucun test ne rougit. Les trois divergences
ci-dessus ont été trouvées par quelqu'un qui **lisait**, jamais par un outil - et deux d'entre elles
l'ont été par hasard, en cherchant autre chose.

Le mode de panne s'aggrave avec l'utilité de la page : plus une liste est reprise à des endroits utiles,
plus elle a de copies, donc plus elle diverge.

## Décision

**Un inventaire a une source, et une seule. Les autres pages la citent.**

Quand la citation est acceptable pour le lecteur, c'est le remède : `cycle-de-chantier.md` et
`CONTRIBUTING.md` renvoient désormais à `recette/index.md` pour l'état des sessions, au lieu de le
répéter.

**Quand la duplication est inévitable parce que les publics diffèrent, elle se DÉRIVE.** Un brief ne
renvoie pas à une page de documentation développeur, et un script de recette doit se lire seul en
séance. Dans ces cas, un garde compare la liste écrite à celle que le code déclare - c'est exactement
ce que fait déjà `DocumentationAJourTest` pour les ADR et les commandes CLI.

### Et souvent, la meilleure réponse est de SUPPRIMER l'inventaire

Elle n'a pas été trouvée en écrivant cette ADR, mais en la **confrontant** : deux personnes ont corrigé
le même défaut le même jour, sans se voir, et la meilleure des deux corrections n'était ni de citer ni
de dériver.

Sur la session de recette S1, l'inventaire disait « 5 cartes (Mes sites, Carte & passages, …) ». La
première correction (celle-ci) le **mettait à jour** : sept cartes, dans l'ordre de chaque prisme.
La seconde (#3522) le **retirait** :

> Les cartes sont **contribuées par les features** : ne pas figer leur liste ici, mais vérifier que
> chacune de celles qui s'affichent porte un intitulé, une destination annoncée, et **ouvre bien ce
> qu'elle annonce**.

C'est meilleur, et le conflit de fusion l'a rendu évident. Une liste mise à jour redeviendra fausse à
la prochaine carte ; une **consigne de comportement** ne se démode pas. La question à poser avant de
recopier une liste n'est donc pas « où est la source ? » mais **« ai-je besoin de la liste, ou de ce
que ses éléments doivent faire ? »**. Quand c'est le comportement qui compte - et pour un script de
recette, c'est presque toujours le cas - l'inventaire n'a pas à exister.

## Ce que cette ADR généralise

L'[ADR 3439](3439-un-masque-se-derive-de-la-scene-il-ne-se-recopie-pas.md) a posé le geste pour un cas
particulier : les rectangles de masque des aperçus se **dérivent de la scène** au lieu d'être recopiés.
Sa conclusion valait déjà au-delà de son objet :

> Un masque est un **renoncement à vérifier**. Tant qu'il est écrit à la main, personne ne sait plus ce
> à quoi il fait renoncer.

Ce qui vaut pour un rectangle vaut pour une **prose** : une liste recopiée est un renoncement à vérifier
qu'elle est encore juste. La présente ADR étend la règle du code à la documentation, au brief et aux
scripts de recette.

## Conséquences

- avant d'écrire une liste dans une page, chercher si elle existe déjà ailleurs - et si oui, **citer** ;
- une liste qui doit exister en plusieurs exemplaires est une **dette déclarée**, qui appelle un garde,
  pas un rappel de vigilance ;
- **la règle ne s'applique pas à un chiffre daté.** L'[ADR 2750](2750-un-chiffre-que-le-code-sait-recalculer-ne-s-ecrit-pas-a-la-main.md)
  a déjà tranché ce cas voisin : une mesure prise un jour donné reste en dur, et c'est juste. C'est
  l'inventaire *courant* qui se cite, pas le relevé *historique*.

## Ce que cette ADR apprend au-delà de son cas

**Une règle écrite le matin ne protège pas l'après-midi.** Celle de la passe 3 - *chercher ce qui est
devenu faux, pas ce qu'on a à ajouter* - a été écrite le 2026-08-08 et violée le jour même, par son
auteur, dans le fichier voisin. Ce n'est pas un défaut de discipline : c'est que la règle demandait une
vigilance là où il fallait un **point de vérité unique**.

Une règle qui repose sur l'attention se paie à chaque relecture ; une source unique se paie une fois.
