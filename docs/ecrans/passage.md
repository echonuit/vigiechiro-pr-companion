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

!!! info "Un passage reconstruit depuis VigieChiro"
    Si ce passage avait été **reconstruit** (récupéré depuis la plateforme sans que vous ayez conservé
    l'audio localement), la réactivation fait une chose de plus : elle **rétablit le lien** entre vos
    observations et la plateforme, pour que vous puissiez de nouveau **publier vos corrections**. C'est
    le bon moment, et le seul utile : puisque vous venez de retrouver l'audio, vous allez pouvoir
    **écouter**, corriger, puis renvoyer. Cette étape interroge la plateforme, elle peut donc prendre un
    moment — et n'a lieu que pour ces passages-là (un passage importé normalement garde ce lien depuis le
    départ).

### Vous n'avez gardé que vos enregistrements bruts ?

C'est le cas le plus fréquent : on garde volontiers sa **carte SD** (ou sa copie), plus rarement les
séquences d'écoute, qui n'en sont qu'un produit dérivé.

**Désignez ce dossier-là, tout simplement.** L'application reconnaît ce qu'il contient : si elle n'y
trouve pas les séquences, mais qu'elle y trouve vos **bruts**, elle **régénère** les séquences à
partir d'eux. Vous n'avez aucun choix à faire ni aucune option à cocher.

Ce qui est régénéré n'est pas cru sur parole : chaque tranche recalculée passe **exactement les mêmes
contrôles** que si vous l'aviez retrouvée telle quelle. C'est possible parce que le découpage est
**reproductible** : à partir du même enregistrement, l'application refabrique des tranches **identiques
au bit près**, dont l'empreinte correspond alors à celle qui avait été relevée avant l'archivage.
Autrement dit, la reproductibilité sert de **preuve**, elle n'est pas une excuse pour ne pas vérifier.

Le brut lui-même est d'abord vérifié (empreinte du fichier entier). **Un brut douteux ne régénère
rien** : il serait absurde de recalculer des séquences à partir d'un fichier dont l'identité n'est pas
établie.

!!! note "Vos bruts ne sont pas recopiés sur le disque"
    Ils servent à recalculer les séquences, puis l'application les oublie. Recopier les gigaoctets que
    vous aviez justement demandé de libérer n'aurait aucun sens : le passage redevient **écoutable**,
    sans que ses bruts reviennent occuper la place.

## Modifier le passage

Le bouton **Modifier le passage** ouvre une fenêtre pour corriger le **rattachement** (année, numéro de
passage, si une nuit a été rattachée par erreur) et saisir les **conditions de dépôt** (relevé météo,
matériel du micro).

![La fenêtre « Modifier le passage » : rattachement et conditions de dépôt.](../assets/captures/apercu-passage-rattachement.png)

Sur un passage **déjà déposé**, l'année et le numéro sont **verrouillés** (ils forment l'identité de la
nuit côté Vigie-Chiro), mais la **météo et le micro restent modifiables**. C'est utile, par exemple, pour
compléter à la main la météo d'un passage **reconstruit** que la plateforme n'a pas rapatriée.

### Importer les observations

L'import des identifications Tadarida ne se fait pas depuis cet écran : il vit dans
**« Sons & validation »** (menu ☰ « Importer depuis Vigie-Chiro », ou glisser-déposer d'un fichier CSV
`_Vu`), là où vous écoutez et validez les sons. Si l'analyse n'est pas encore terminée, l'application vous
dit **pourquoi** il n'y a rien à importer (jamais lancée, planifiée, en cours, ou en échec).
