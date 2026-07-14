# Passage

L'écran **Passage** est le **pivot** d'une nuit donnée : il rassemble en une seule page tout ce qui
concerne cette nuit et ouvre les écrans spécialisés (vérification, dépôt, validation, diagnostic).
C'est l'écran sur lequel vous revenez entre chaque étape du [parcours](../parcours/index.md).

## Anatomie de l'écran

![L'écran Passage au statut « Vérifié » : bandeau d'identité, statut du workflow, résumé et cartes d'actions.](../assets/captures/apercu-passage.png)

De haut en bas :

- **En-tête** : l'identifiant de la nuit (carré / point / numéro / année) et les actions sur le
  passage (**Modifier le rattachement**, **Supprimer**). Le bouton **Voir la participation** ouvre
  la participation liée sur le **portail Vigie-Chiro** dans votre navigateur, pour vérifier d'un
  coup d'œil que la nuit est rattachée au bon endroit ; il reste grisé tant que le passage n'est
  pas lié (la participation est créée à l'import connecté, ou au premier dépôt).
- **Bandeau d'identité** : date et plage horaire, enregistreur, **statut** et **verdict**.
- **Statut du workflow** : une frise qui situe la nuit dans sa progression
  (Importé, Transformé, Vérifié, Prêt à déposer, Déposé).
- **Résumé de la nuit** : volumes (bruts et transformés), durée enregistrée, nombre de séquences.
- **Cartes d'actions** : Vérifier l'enregistrement, Diagnostic matériel, Préparer le dépôt et
  Sons & validation. Une seule carte est mise en avant : la **prochaine action recommandée**.

## Le déverrouillage de la validation

La carte **Sons & validation** est **grisée** tant que la nuit n'est pas déposée (voir la capture
ci-dessus, au statut « Vérifié »). Une fois la nuit **déposée**, elle se déverrouille :

![Le même écran au statut « Déposé » : la carte « Sons & validation » est devenue accessible.](../assets/captures/apercu-passage-depose.png)

!!! info "Pourquoi cet ordre ?"
    Vigie-Chiro ne renvoie les résultats Tadarida que 24 à 48 h après le dépôt : la validation des
    espèces vient donc nécessairement **après** le dépôt. Voir le [parcours métier](../parcours/index.md).

## Les sons non identifiés

Tadarida ne retient qu'une partie des enregistrements d'une nuit : les autres (bruit, sons trop faibles,
séquences non classées) existent sur le disque mais **sans identification Tadarida**. L'écran
[Sons & validation](validation.md) d'un passage les affiche désormais **avec** les observations Tadarida ;
une vue dédiée **Sons non identifiés** (onglet au-dessus de la table) permet de n'afficher que
**ces séquences‑là**. Vous pouvez les **écouter** pour vérifier par vous‑même s'il s'y cache une
chauve‑souris que l'algorithme aurait manquée. Leur colonne d'espèce affiche « — » (aucune proposition).

**Valider à la main.** Si vous reconnaissez une espèce, sélectionnez la séquence, choisissez le taxon dans
la liste puis cliquez sur **Corriger** : une observation est créée pour cette séquence, qui passe alors au
statut **Corrigée** avec votre taxon. La séquence **reste** dans la liste (vous voyez ce qui a déjà été
traité) et l'observation rejoint le reste de vos données. Le bouton **Valider** — qui « retient la
proposition Tadarida » — reste inactif ici, faute de proposition à retenir.

## Annuler le dépôt

Sur un passage **déposé**, un bouton **Annuler le dépôt** (barre du haut, visible sur la capture ci-dessus)
permet de revenir en arrière : après confirmation, la nuit repasse du statut « Déposé » à « **Prêt à
déposer** ». Les **validations Tadarida déjà saisies sont conservées** : annuler le dépôt ne touche pas
votre travail de validation, il rouvre seulement l'étape de dépôt (par exemple pour corriger quelque chose
avant de redéposer). Le bouton n'apparaît que lorsqu'il a un sens, c'est-à-dire sur un passage
effectivement déposé.

## Purger les originaux

Le **Résumé de la nuit** affiche le **volume des bruts** : la copie des enregistrements d'origine
conservée sur le disque au moment de l'import. Ces fichiers ne servent **pas** à l'écoute ni à la
validation (celles-ci s'appuient sur les séquences transformées) et peuvent être **volumineux**.

Un bouton **Purger les originaux** (barre du haut) permet, après **confirmation**, de **supprimer** ces
fichiers bruts pour **récupérer de l'espace disque**. Les **séquences d'écoute, la validation et le
dépôt sont conservés** : seul le volume des bruts tombe à **0**. Le bouton n'apparaît que si la nuit
conserve encore des originaux. Cette suppression est **définitive**.

> Pour purger **toutes les nuits** d'un coup, utilisez le menu « ☰ → Purger les originaux importés… »
> (voir [Accueil et navigation](index.md)).

## Archiver un passage : consulter sans écouter

Une nuit d'enregistrement pèse lourd : sur un carré complet, les séquences d'écoute se comptent en
milliers de fichiers. Une fois le passage **déposé** et analysé par la plateforme, vous n'avez plus
forcément besoin de garder cet audio sur votre disque.

Le bouton **Archiver ce passage** supprime, après confirmation, **l'audio local** de ce passage
(séquences d'écoute **et** bruts encore présents). Ce qui fait l'intérêt de la nuit **reste
consultable** :

- les **observations** et vos **validations** (taxons corrigés, références, commentaires, marquages
  « douteux ») ;
- les **résultats Tadarida** (le CSV), le **journal du capteur** et le **relevé climatique** ;
- l'historique du passage : vous voyez toujours vos nuits, même sans en avoir gardé les données
  d'origine.

Ce que vous perdez, c'est **l'écoute** : le passage devient un **passage archivé**.

!!! warning "Ce que la confirmation vous dit, et pourquoi elle insiste"
    L'espace récupéré est **annoncé avant** que vous ne confirmiez. Surtout : pour **réécouter** un
    jour, il faudra **réimporter les mêmes fichiers**. La plateforme Vigie-Chiro **ne rend pas**
    l'audio d'un dépôt au format ZIP (le mode par défaut) : sans vos fichiers d'origine, la perte est
    **définitive**. Archivez donc un passage dont vous avez une **sauvegarde**, ou dont l'audio ne
    vous sert plus.

Le bouton n'est actif que sur un passage **déposé** (avant le dépôt, l'audio est nécessaire à
l'analyse) et qui conserve encore de l'audio. Sinon, il est grisé : **survolez-le**, l'infobulle dit
pourquoi.

### Ce que devient un passage archivé

Dans **Sons & validation**, un bandeau annonce que le passage est archivé, et le lecteur audio est
remplacé par une explication. **Tout le reste continue de fonctionner** : la liste des observations,
les filtres, le tri, les colonnes, les vues mémorisées, les commentaires, le marquage « douteux »,
les corrections et les exports. Vous ne pouvez simplement plus **écouter**.

L'**audit de cohérence** ne s'en alarme pas : un passage archivé volontairement n'est pas un passage
corrompu. Il apparaît en simple **information**, avec le décompte des séquences encore présentes.

## Réactiver un passage : réimporter les fichiers d'origine

Le bouton **Réactiver ce passage** remet l'audio en place à partir d'un dossier que vous désignez
(votre sauvegarde, un disque externe, la carte d'origine…). L'exploration est **récursive** : vous
pouvez pointer la racine d'une sauvegarde.

**Chaque fichier est vérifié avant d'être rebranché.** C'est le cœur de l'affaire : deux jeux de
fichiers peuvent porter **les mêmes noms** sans être les mêmes (une redécoupe, une autre expansion,
une autre nuit du même carré). Rebrancher vos observations sur le mauvais audio produirait un
résultat **faux et silencieux** : vous valideriez un cri en écoutant autre chose. L'application
confronte donc chaque fichier à ce qu'elle sait de la séquence attendue :

- son **empreinte de contenu**, si elle a été enregistrée à l'import (identité certaine) ;
- sinon son **nom**, sa **taille** et sa **durée réelle**, confrontés à l'en-tête du fichier ;
- et les **cris eux-mêmes** : les instants et fréquences des observations sont-ils bien présents dans
  le fichier proposé ?

Un fichier qui échoue à ces contrôles **n'est jamais rebranché en silence** : il est **rapporté**,
avec le motif du refus, et c'est vous qui décidez de la suite.

Le rapport final dit combien de séquences sont **revenues** (et **sur quelle preuve**), combien ont
été **refusées** (et pourquoi), et combien restent **introuvables** dans le dossier désigné. Si tout
est revenu, le passage redevient pleinement écoutable ; s'il en manque, il reste **partiellement**
disponible, et le décompte vous le dit.

!!! tip "Les fichiers de votre sauvegarde ne sont jamais touchés"
    La réactivation **copie** : votre dossier source reste intact, et vos observations comme vos
    validations ne sont jamais recalculées — on rebranche des chemins, rien d'autre.

## Modifier le rattachement

Si une nuit a été rattachée par erreur (mauvaise année ou mauvais numéro de passage), le bouton
**Modifier le rattachement** ouvre une fenêtre permettant de corriger ces informations.

![La fenêtre de modification du rattachement : année et numéro de passage.](../assets/captures/apercu-passage-rattachement.png)

### Importer les observations depuis Vigie-Chiro

Quand la nuit a été **déposée sur Vigie-Chiro**, cette même fenêtre propose **« Importer les
observations »** : la plateforme ayant analysé les sons, ce bouton rapatrie les identifications
Tadarida sans passer par un fichier CSV.

Si l'analyse n'est pas terminée, l'application vous dit **pourquoi** il n'y a rien à importer :

- elle **n'a jamais été lancée** : lancez-la depuis « Préparer le dépôt » (étape ④) ;
- elle est **planifiée** ou **en cours** : il n'y a qu'à patienter (comptez plusieurs dizaines de
  minutes), le suivi étant affiché dans « Préparer le dépôt » ;
- elle a **échoué** : le motif renvoyé par la plateforme est indiqué ;
- elle est **terminée mais ne renvoie aucune observation** : c'est anormal, vérifiez que les fichiers
  sont bien arrivés (« Vérifier le dépôt »).

Le même import reste disponible depuis « Sons & validation » (menu ☰).
