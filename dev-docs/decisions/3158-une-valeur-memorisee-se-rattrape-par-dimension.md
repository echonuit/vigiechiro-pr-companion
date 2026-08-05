# ADR 3158 - Une valeur mémorisée se rattrape **par dimension**, jamais par ressemblance

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #3158, lot 1 du chantier #3151
- **Vérification** : certaine - `CritereLieuTest#un_carre_memorise_nu_se_replace_malgre_ses_points`

> Le garde d'abstention - deux candidates ne cochent rien - est tenu par
> `CritereLieuTest#deux_entrees_candidates_ne_rattrapent_rien` : le garde des ADR n'accepte qu'une
> référence, la décision en a deux.

!!! note "Sa jumelle : la **clé**, quand celle-ci porte sur la **valeur**"
    Une vue sauvegardée persiste deux choses, et deux ADR nées la même semaine en traitent une chacune :
    la **clé** du critère (`lieu`, `statut`) est un contrat de sérialisation
    ([ADR 3096](3096-une-cle-de-critere-est-un-contrat-de-serialisation.md), chantier #3092), et la
    **valeur** cochée se rattrape par dimension (celle-ci, chantier #3151). Écrites en parallèle, elles
    ne se citaient pas ; le rapprochement a été fait à la clôture de #3151. Les lire ensemble avant de
    toucher à `vue_sauvegardee`.

## Contexte

Une vue sauvegardée persiste les valeurs cochées **en clair** : c'est par ce texte que
`CritereListe.restaurerValeurs` retrouve l'entrée à recocher. **Requalifier** une entrée rend donc
introuvables toutes les valeurs enregistrées avant elle.

Ce n'est pas une hypothèse. #2992 a écrit le point sous la forme « 640380 · A1 » sans rattraper les
vues d'alors, et [ADR 3157](3157-un-carre-a-un-identifiant-et-une-etiquette.md) allait faire de même
pour le carré. Depuis #3093 la perte est **annoncée**, ce qui vaut mieux qu'un élargissement
silencieux ; elle le serait néanmoins à chaque rejeu, pour un changement dont l'utilisateur n'est pas
l'auteur.

## La première règle, et pourquoi elle ne pouvait pas marcher

La règle d'abord retenue cherchait l'entrée dont **un segment quelconque** égale la valeur mémorisée.
Elle paraissait suffire, et elle est fausse pour le cas principal.

Le point est qualifié **par** son carré. « 640380 » se lit donc aussi en tête de « 640380 · A1 » :
toute valeur de carré trouve **deux** prétendants, le carré et chacun de ses points. Le rattrapage
s'abstenait, systématiquement. Il tenait sa promesse pour les points et **jamais** pour les carrés,
c'est-à-dire pour le cas même que la requalification venait de créer.

Le défaut ne s'est pas vu à la relecture : il s'est vu à un test existant devenu muet
(`MemoireRevueAudioTest#filtre_non_replace_est_signale` ne signalait plus rien, la valeur n'ayant
jamais été cochée à l'aller).

## Décision

**Le socle demande, le critère répond.** `CritereListe.Rattrapage` ne reçoit plus la liste des
entrées : il reçoit la valeur mémorisée et rend ce qu'elle désigne. Ce qui distingue
« 640380 · Vallon » de « 640380 · A1 » n'est pas leur texte, c'est la **dimension** dont chacune vient,
et le socle ne la connaît pas.

**Chaque dimension déclare où vivait son ancienne écriture** :

| Dimension | Écriture ancienne | Exemple |
|---|---|---|
| Carrés | `EN_TETE` | « 640380 » a donné « 640380 · Vallon » |
| Points | `EN_QUEUE` | « A1 » a donné « 640380 · A1 » |
| Communes | aucune | elles n'ont jamais changé d'écriture |

**Deux candidates ne donnent rien.** Sur un écran qui couvre la saison, « Z1 » existe dans presque tous
les carrés : cocher le premier venu filtrerait sur un lieu que l'utilisateur n'a pas choisi, et l'écran
aurait l'air juste. #3093 dit alors ce qui n'a pas été replacé, et l'utilisateur tranche.

**La comparaison porte sur des segments entiers.** « 6403 » ne désigne pas « 640380 · Vallon » : une
valeur mémorisée est un lieu qui a existé, pas une amorce de recherche. C'est ce qui distingue ce
rattrapage de la correspondance partielle de `--lieu`, où l'on tape à l'aveugle et où deviner aide.

## Conséquences

- Une requalification future devra **déclarer son côté**, faute de quoi le rattrapage ne s'appliquera
  pas - un oubli visible, puisque les vues se plaindront.
- Le socle reste ignorant de la forme des valeurs, ce qui laisse chaque critère libre de la sienne. Un
  critère qui ne déclare rien garde le comportement d'avant : texte exact, ou rien.
- Le rattrapage **propose**, le menu **dispose** : une entrée que le domaine courant n'offre pas est
  écartée, si bien qu'aucun rattrapage ne peut cocher un lieu absent.

## Alternatives écartées

- **Ne rien rattraper**, en s'appuyant sur le bandeau de #3093. Honnête, mais il aurait averti à chaque
  rejeu de chaque vue existante, pour un changement subi.
- **La correspondance partielle de `FiltresLieu`.** À l'écran, la valeur vient d'une liste fermée et non
  d'une frappe : accepter un fragment transformerait une restauration en recherche approximative.
- **Migrer les vues stockées** en réécrivant leur contenu en base. Irréversible, et faux dès qu'un site
  est renommé après coup ; le rattrapage se recalcule à chaque ouverture sur le domaine réel.
