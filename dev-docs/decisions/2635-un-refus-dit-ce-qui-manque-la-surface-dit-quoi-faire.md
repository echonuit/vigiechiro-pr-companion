---
type: adr
title: "Un refus dit ce qui manque, la surface dit quoi faire"
status: stable
article: A13
chantier: "#2635 (suite de l'EPIC #2554, passe 7)"
decided_at: 2026-07-28
verification: probable
enforced_by:
  - "scripts/adr/2635-refus-sans-surface.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-07-28
---

# Un refus dit ce qui manque, la surface dit quoi faire

## Contexte

Six refus du modèle nommaient le **menu principal (☰)**. Ils n'étaient pas mal écrits : chacun l'avait été à un
moment où une seule surface appelait ce code, et le chemin de menu était alors l'information la plus
utile qu'on pouvait donner.

Le produit a changé sous eux. Les mêmes lectures distantes servent aujourd'hui la reconstruction, la
réactivation, la complétion, l'application **et** la ligne de commande. Le symptôme est apparu à la
parité CLI de #2554 : `vigiechiro reactiver` répondait

> Non connecté à Vigie-Chiro : collez un jeton (**menu principal (☰)** > Se connecter) avant de **reconstruire** un
> passage.

Un menu à qui travaille dans un terminal, et un geste que l'utilisateur ne demandait pas. Deux messages
ont été corrigés à la main pendant ce chantier ; l'audit d'harmonisation en a trouvé quatre autres et,
surtout, a montré que le motif se reproduirait.

## Décision

**Un refus porte ce qui manque ; la surface qui l'affiche ajoute le geste.**

Le modèle lève un [`RegleMetierException`] dont le message énonce le **fait** - ce qui manque, et ce que
ça empêche - et qui porte un [`Besoin`] nommé (`Connexion`, `Fonctionnalite(nom)`) quand le refus vient
de l'environnement. Chaque surface possède son formateur :

| Surface | Ce qu'elle ajoute |
|---|---|
| application | `commun.viewmodel.GesteAttendu` : « menu principal (☰) > Se connecter à Vigie-Chiro » |
| ligne de commande | `cli.GesteAttenduCli` : « vigiechiro connexion --token \<jeton\> » |

**Personne ne perd de guidage**, et c'était la condition : l'utilisateur de l'application - la surface
majoritaire - garde son chemin de menu au mot près. Ce qui change, c'est qu'il ne vient plus du modèle.

Trois conséquences valent d'être notées.

**Le besoin est optionnel.** La plupart des refus portent sur l'état du domaine (« ce passage est déjà
déposé ») et n'appellent aucun geste d'environnement. Ils traversent inchangés.

**Une surface peut l'ignorer.** Un consommateur qui ne connaît pas les besoins affiche l'énoncé seul : un
message juste, seulement moins guidant. C'est le comportement voulu pour un script ou une API.

**Les fonctionnalités font exception côté terminal**, et le message le dit franchement : elles se règlent
dans l'application. Mieux vaut renvoyer à l'endroit qui existe que d'inventer une commande.

## Conséquences

- Le cliquet est à **zéro** : plus aucun `☰` dans une chaîne de `**/model/**`, et le septième message ne
  passera pas. C'est ce qui manquait aux six premiers - rien ne les empêchait de se multiplier.
- Le glyphe `☰` est le seul marqueur retenu, parce qu'il est le seul non ambigu. Un message qui dirait
  « ouvrez la fiche de la nuit » ne serait pas attrapé : c'est du vocabulaire métier autant que
  d'interface, et l'arbitrer demande de lire, pas de compter.
- Un refus qui **renvoie vers un autre écran** sans être un besoin d'environnement (« pour remplacer,
  ouvrez Sons & validation ») perd son chemin : le fait est conservé, le renvoi appartient à la surface.

## Alternatives écartées

- **Réécrire les six messages en neutre**, sans mécanisme. Une demi-journée, aucun type nouveau - mais
  l'utilisateur de l'application, qui est la majorité, y perdait l'indication précise qui rendait ces
  messages actionnables. Le remède aurait coûté plus que le défaut.
- **Traduire côté ligne de commande** : la CLI réécrivant les mentions d'interface au moment d'afficher.
  Le moins invasif, mais c'est un pansement - la CLI devrait connaître les tournures de l'application
  pour les défaire, et la traduction se désynchronise au premier message ajouté.
- **Ne rien faire**, en considérant la ligne de commande comme une surface d'appoint. C'est le statu quo
  qui a produit six occurrences ; la parité CLI ↔ IHM est une règle du dépôt (ADR 0014), pas une faveur.
