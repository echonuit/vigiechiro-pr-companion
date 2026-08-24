---
type: adr
title: "L'ergonomie se rattache à une heuristique nommée, et le vocabulaire est clos"
status: stable
article: A29
chantier: "#4342 (l'article de l'ergonomie, chantier #4334)"
decided_at: 2026-08-24
verification: probable
enforced_by:
  - "scripts/adr/verifie_okf.py"
ratchet: 71
verified:
  - by: machine:suspects
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# L'ergonomie se rattache à une heuristique nommée, et le vocabulaire est clos

## Contexte

Le corpus porte 202 décisions. Beaucoup portent sur l'usage : ce qu'un écran montre, ce qu'il
refuse, ce qu'il fait attendre, ce qu'il groupe. Chacune déclare son article de la constitution,
qui dit ce que **le dépôt** tient - une preuve, un état, un compte rendu - et jamais ce que
**l'utilisateur** rencontre.

Résultat : on ne sait pas répondre à « quelles décisions traitent de la prévention de l'erreur ».
On peut chercher par mots, et le mot n'est pas le sujet.

## Le défaut

Les décisions qui servent une même préoccupation d'usage se retrouvent sous des articles
différents, et rien ne les rapproche : on les redécouvre une par une.

Le second défaut coûte plus cher : **une préoccupation qu'aucune décision ne couvre est
invisible**. Un corpus ne dit jamais ce qu'il ne contient pas, et sans vocabulaire de référence
l'absence n'a pas de nom.

## Décision

Une décision d'ergonomie déclare, par le champ `heuristiques:` de son en-tête OKF, la ou les
heuristiques qu'elle sert. **Toujours une liste**, même à une seule entrée, et chaque clé est
confrontée séparément au vocabulaire.

Le vocabulaire est **clos** et vit dans [l'annexe](../ergonomie/heuristiques.md) : les dix
heuristiques de Nielsen, l'affordance et le signifiant de Norman, six lois de la Gestalt et cinq
critères WCAG. Vingt-trois clés, avec leurs sources et une version. Une clé nouvelle est une
décision, pas une improvisation.

Rien de tout ceci n'entre dans le corps des ADR : un rattachement qui coûterait cent mots par
décision ne serait pas posé. Rien ne borne aujourd'hui la longueur d'une ADR dans ce dépôt, et
l'en-tête reste donc le seul endroit où le rattachement tienne sans peser.

Trois contrôles, dans `verifie_okf.py`, et leurs trois sévérités sont voulues. Une clé hors
vocabulaire est **refusée** : une faute de frappe qui passerait créerait une vingt-quatrième
heuristique en silence. Une décision rattachée à l'un des huit articles d'usage qui ne déclare rien
est un **suspect sous cliquet**, car un refus sec le jour de la pose rendrait le garde rouge sur
tout le corpus, et il serait désactivé dans la semaine. Une heuristique que rien ne sert est
**dite**, sans faire rougir : c'est un manque à connaître, pas une faute à corriger.

La matrice est **engendrée** par `scripts/methode/matrice-ergonomie.py`, jamais tenue à la main.
Elle annonce **deux nombres**, rattachements et ADR : une décision peut servir plusieurs
heuristiques, et confondre les deux ferait croire à une couverture qui n'existe pas.

Le cliquet part de la mesure : **<!--inv:cliquet-heuristique-->71<!--/inv-->** décisions concernées, dont aucune ne nommait d'heuristique le
jour de la pose. Elles descendront chantier par chantier, chacune ouverte et lue. Jamais par
mots-clés :
sur un problème plus facile - retrouver l'ADR derrière un numéro - la ressemblance de vocabulaire
s'est trompée cinq fois sur soixante-dix-neuf. Cette mesure vient du dépôt jumeau (#4334) et n'a pas
été refaite ici : elle est citée pour ce qu'elle montre, pas comme un relevé local. Ici l'erreur
n'attribuerait pas une adresse, elle attribuerait une **intention**.

## Conséquences

Le groupement sert à **retrouver**, pas à fusionner d'office. Deux décisions qui partagent une clé
restent deux décisions : elles peuvent être voisines de sujet sans dire la même chose. Toute fusion
se décide par paire, et se justifie.

**Le cliquet ouvre à 71, et il ne descendra pas à zéro.** Toutes les ADR rattachées aux huit
articles d'usage sont suspectes le jour de la pose, puisque aucune ne nommait d'heuristique. Elles se
nommeront au fil des chantiers qui les rouvriront, une par une.

Certaines n'en nommeront jamais : une décision rattachée à un article d'usage n'est pas
nécessairement une décision d'usage. Le choix d'un mécanisme de parallélisme ou le découpage d'une
vue trop riche ne servent aucune heuristique, parce qu'ils ne parlent pas d'usage. Leur attribuer une
clé serait leur prêter une intention. Le cliquet s'arrêtera donc au-dessus de zéro, et l'endroit où
il s'arrête sera une mesure, pas un objectif.

**Ce que la mesure a dit du soupçon.** Trois heuristiques étaient soupçonnées de n'avoir aucun
domicile. Deux sont démenties : la prévention de l'erreur est servie par **dix** décisions, la
reconnaissance plutôt que le rappel par **sept**. La troisième est confirmée : **l'aide et la
documentation n'a aucune décision**, et c'est cohérent avec un produit dont l'aide se réduit à des
libellés.

**Et ce que personne n'avait soupçonné.** Cinq des six lois de la Gestalt n'ont aucun domicile ;
seule la proximité en a deux. Le corpus décide beaucoup de **ce qui** est montré et **si** c'est
visible, presque rien de **comment les choses se groupent** à l'œil. C'est le premier angle mort
que ce vocabulaire fait apparaître, et il n'aurait pas de nom sans lui.

## Alternatives écartées

- **Rattacher automatiquement par mots-clés.** Mesuré faux cinq fois sur soixante-dix-neuf sur un
  problème plus simple, dans le dépôt jumeau.
- **Écrire l'heuristique en prose dans le corps.** Une prose ne se compte pas : ni la matrice ni le
  cliquet ne sauraient la lire.
- **Un article de constitution par heuristique.** Ce ne sont pas des règles opposables mais un
  vocabulaire, et dix-huit articles de plus rendraient la page illisible.
- **Un vocabulaire ouvert.** Chacun nommerait la même chose autrement, et le regroupement cesserait
  de fonctionner.

## La jurisprudence du cliquet

Le cliquet de cette décision suit deux ADR antérieures.
[2867](2867-une-dette-se-tient-par-un-cliquet.md) pose qu'une dette se tient par un compteur qui ne
remonte pas, plutôt que par un nettoyage qu'on remet.
[2941](2941-un-cliquet-s-apprend-en-l-appliquant.md) ajoute que sa valeur d'ouverture se mesure, et
que le resserrer est un geste distinct de le poser.

[3540](3540-un-cliquet-qui-compte-n-est-pas-la-preuve-de-la-regle.md) dit la limite : un compteur qui
ne monte pas prouve que rien ne s'ajoute, pas que la règle est comprise. C'est pourquoi le garde rend
des suspects et non des fautes.
