# ADR 3095 - Un domaine de filtre se calcule **sans son propre critère**, et une facette cascade là où un sélecteur ne cascade pas

- **Statut** : Accepté - 2026-08-03
- **Chantier** : #3095, palier 2 du chantier #3092
- **Vérification** : certaine - `FiltresTest#sauf_lui_ignore_le_critere_nomme`

> Les deux autres règles de cette ADR ont leurs propres gardes, dans
> `CritereListeCascadeTest#une_valeur_cochee_devenue_impossible_est_conservee` et
> `MultisiteVueIntegrationTest#la_puce_lieu_ne_s_auto_effondre_pas` : le garde des ADR n'accepte
> qu'une référence, la décision en a trois.

## Contexte

Les puces de filtre offrent une liste de valeurs. Cette liste était **photographiée à la création de la
puce** et ne bougeait plus : elle proposait des valeurs devenues impossibles (on clique, la table se
vide, rien ne dit pourquoi) et ne faisait pas réapparaître celles qui redevenaient disponibles.

Rendre ces domaines **cascadés** paraissait mécanique. Trois décisions s'y sont révélées nécessaires,
dont aucune ne se devine à la lecture du code.

## Décision 1 : le domaine se calcule sur les lignes que les **autres** critères laissent passer

Les fabriques recevaient la `FilteredList` de l'écran, c'est-à-dire la table **déjà filtrée par tous
les critères, y compris celui qu'on peuple**.

Recalculer naïvement fait s'auto-effondrer la puce : une fois « Aix » coché, les lignes restantes ne
parlent plus que d'Aix, donc le menu n'offre plus qu'« Aix », et l'on ne peut **jamais** cocher une
seconde commune. La puce cesse de savoir désigner autre chose que ce qu'elle désigne déjà.

D'où `Filtres.saufLui(nom)` : la conjonction de tous les prédicats **sauf** celui du critère, appliquée
à la **source** et non à la liste affichée. Partir de la liste affichée interdirait par construction de
faire réapparaître une valeur redevenue disponible.

Le recalcul a lieu **à l'ouverture du menu** : seul instant où la liste est regardée, donc seul où son
exactitude compte. Recalculer à chaque changement de filtre coûterait à chaque frappe pour rien.

!!! warning "Ce qui distingue le cascadage d'un simple rafraîchissement"
    Un test qui pose **un seul** critère ne fait pas la différence : lire la liste déjà filtrée y donne
    par hasard la bonne réponse. L'écart n'apparaît que lorsque la puce est **elle-même** cochée.

    Le premier test d'intégration écrit pour ce chantier ne couvrait pas ce cas : il passait aussi bien
    avec le nouveau câblage qu'avec l'ancien, et n'a été démasqué qu'en remettant l'ancien câblage pour
    vérifier qu'il rougissait.

## Décision 2 : une valeur cochée devenue impossible est **conservée**, visible et marquée

Quand le domaine se resserre, une valeur déjà cochée peut ne plus y figurer. La retirer relâcherait le
filtre **en silence** : l'écran montrerait alors plus que ce qu'il annonce, soit exactement le défaut
que #3056 et #3093 ont corrigé sur les chemins de restauration.

Elle reste donc cochée, rendue en fin de liste, et marquée par la classe `valeur-hors-jeu` (grisée,
italique). Cette classe porte une **vraie règle CSS** : une classe de style sans rendu ne montre rien,
et le dépôt en avait déjà un exemple (`entete-groupe-critere`), ce qui rendrait un test vert sur un
marquage invisible.

La garder visible répond aussi à la question qu'on se pose devant une table vide : « pourquoi n'y
a-t-il rien ? ».

## Décision 3 : une **facette** cascade, un **sélecteur** ne cascade pas

Cascader rend un critère plus juste quand il sert à **restreindre**, et plus pénible quand il sert à
**naviguer**.

Le cas qui plaide pour, sur l'écran Activité, un carré à deux nuits dont l'une n'a pas d'orthoptères :
poser « Nuit = 21 juin » laissait le menu « Taxon parent » offrir *Orthoptères*, une entrée qui ne
pouvait rien donner.

Le même mécanisme sur le critère « Nuit » donne l'inverse : poser « Taxon parent = Orthoptères »
retirerait le 21 juin du menu des nuits, alors que le geste suivant est souvent « et le 21 juin, il y
avait quoi ? ». Le cascadage ôte la nuit vers laquelle on voulait aller, et il faut défaire un filtre
pour naviguer.

D'où la ligne de partage :

| Critère | Nature | Cascade |
|---|---|---|
| Lieu, Taxon parent | **facette** : « restreins ce que je regarde » | oui |
| Nuit | **sélecteur** : « montre-moi celle-là » | non |
| Nature de la nuit, Statut | énumération **fixe** | sans objet |

C'est la même distinction que celle retenue sur *Ma saison* (#3103), où année et campagne restent des
contrôles fixes parce qu'elles **structurent** le domaine au lieu de le restreindre.

## Conséquences

- `Filtres` expose `saufLui`, et tout nouveau critère à domaine doit le consommer plutôt que la liste
  filtrée de son écran ;
- un critère présélectionné dont le choix disparaît retombe sur son défaut **et l'annonce**
  (`RetourOperation.choixRemplace`) : l'écran filtre alors sur autre chose que ce qui était demandé, et
  le taire serait le défaut du palier 1 ;
- ajouter un critère impose de le classer : facette, sélecteur, ou énumération fixe. Le classement se
  justifie en commentaire à côté du câblage, pas seulement ici.
