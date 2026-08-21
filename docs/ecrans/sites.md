# Sites

Les écrans **Sites** servent à gérer vos **sites de suivi** : un site correspond à un **carré
Vigie-Chiro** (six chiffres) et à ses **points d'écoute** (par exemple `A1`, `B2`). C'est le point
de départ de tout le reste : vous ne pouvez pas importer une nuit tant qu'un site n'est pas déclaré.

!!! tip "Le parcours en vidéo (16 s)"

    <video controls muted playsinline preload="metadata" width="100%">
      <source src="../../assets/parcours/parcours-declarer-un-carre.mp4" type="video/mp4">
      Votre navigateur n'affiche pas les vidéos. Le film est là :
      <a href="../../assets/parcours/parcours-declarer-un-carre.mp4">parcours-declarer-un-carre.mp4</a>.
    </video>

    Déclarer son premier carré, de l'accueil à la fiche du site : où cliquer, ce que
    demande la fenêtre, et ce qu'on obtient.

## Mes sites

L'écran **Mes sites** liste vos sites sous forme de cartes. Chaque carte indique le numéro de carré,
son nom, sa commune, le nombre de **points d'écoute** avec leurs codes, le nombre de **passages**
enregistrés dans l'année et combien **restent à vérifier**, et un **badge de fraîcheur** rappelant la
date du dernier passage. Quand le carré porte beaucoup de points, les codes sont **résumés** (« A1 ·
B2 · C3 (+ 5 rapatriés) ») plutôt qu'énumérés.

Une carte n'offre qu'un **chevron ›** qui ouvre le détail : c'est de là que partent les actions du
carré, **Importer une nuit** compris. En haut à droite, **+ Nouveau site** et, une fois
[connecté](../connexion-vigiechiro.md), **Récupérer depuis Vigie-Chiro**.

![L'écran « Mes sites » : une carte par site, avec ses statistiques et un badge de fraîcheur.](../assets/captures/apercu-sites-mes-sites.png)

Le badge de fraîcheur passe du **vert** (dernier passage récent) à l'**orange** (plus ancien), au
**gris** (aucun passage). Un clic sur une carte ouvre le **détail du site**.

## Déclarer un site

**+ Nouveau site** ouvre une fenêtre de saisie. Un seul champ est obligatoire : le **numéro de carré**,
six chiffres, tel qu'il figure sur la grille Vigie-Chiro. Le bouton **Créer** reste fermé tant qu'il
n'est pas complet, et le champ rougit dès qu'il est saisi mais incomplet : l'application vous **empêche**
de vous tromper plutôt que de vous le reprocher après coup.

Le **nom convivial**, le **protocole** et le **commentaire** sont facultatifs - vous pouvez déclarer un
carré à la volée sur le terrain et les compléter plus tard, depuis la fiche du site.

![La fenêtre de déclaration d'un site : numéro de carré (obligatoire), nom, protocole et commentaire.](../assets/captures/apercu-sites-modale-site-creation.png)

Si le carré est **déjà déclaré** dans Companion, le motif s'affiche **dans la fenêtre**, sous le champ,
et **votre saisie est conservée** : corrigez le numéro sans tout recommencer.

### Savoir si le carré existe déjà sur Vigie-Chiro

Le bouton **Vérifier sur Vigie-Chiro**, à droite du numéro, pose la question **au portail** avant que
vous ne déclariez quoi que ce soit. Il reste fermé tant que le numéro n'a pas ses six chiffres, et
répond en une seconde. Trois réponses possibles :

- **le carré n'y est pas encore** : vous pouvez le déclarer ici ;
- **le carré y est déjà** : le message **nomme le site trouvé**, protocole compris - un même carré porte
  un site par protocole - et un bouton **Récupérer ce carré** apparaît. Le bouton **Créer** se ferme le
  temps que dure ce verdict : déclarer un carré qui existe déjà là-bas produirait un doublon local sans
  rattachement, et votre dépôt serait refusé plus tard ;
- **la vérification n'a pas pu se faire** (hors connexion, portail injoignable) : c'est dit tel quel.
  Ce n'est **pas** « le carré est libre ».

![La fenêtre de déclaration après vérification : le carré cherché existe déjà sur Vigie-Chiro, et le message dit sous quel protocole et quoi faire à la place.](../assets/captures/apercu-sites-modale-site-carre-existant.png)

Si vous corrigez le numéro après avoir vérifié, la réponse **disparaît** : elle portait sur l'ancien
numéro, et la laisser affichée vous ferait croire que le nouveau a été vérifié. Recliquez pour poser la
question sur le carré corrigé.

### Récupérer un carré qui existe déjà

**Récupérer ce carré** crée le site **chez vous**, rattaché à son homologue Vigie-Chiro, avec **ses points
d'écoute déjà positionnés**. La fenêtre se ferme et vous restez sur **Mes sites** : le carré récupéré y
paraît, et un bandeau dit ce qui vient d'être créé.

![La liste « Mes sites » après une récupération : le carré récupéré est apparu dans la liste, et un bandeau vert en tête d'écran annonce le nombre de points d'écoute positionnés. Il se ferme d'une croix.](../assets/captures/apercu-sites-carre-recupere.png)

C'est ce rattachement qui compte : sans lui, le téléversement d'une nuit se solde par « site non rattaché
à Vigie-Chiro ». Et il ne s'obtenait jusqu'ici que par la synchronisation, qui ne ramène que les carrés
où **une nuit est déjà déposée** - ce que vous ne pouvez pas faire, justement, tant que le site n'est pas
rattaché.

Ce refus ne se contente plus de constater. Il demande à Vigie-Chiro si votre carré y existe, et vous
oriente en conséquence : **le récupérer ici** s'il y est en Point Fixe, ou **l'activer d'abord sur le
portail** s'il n'y est pas encore. Si la plateforme ne répond pas, il vous le dit plutôt que de trancher.

Ce que vous aviez saisi est conservé : si vous aviez écrit un nom convivial ou un commentaire, le site
récupéré les porte. Sinon il prend le titre de la plateforme.

!!! warning "Un carré peut exister sous un autre protocole"
    Le même numéro peut désigner un site **Point Fixe**, **Pédestre** ou **Routier**. Companion ne traite
    que le Point Fixe : si le carré n'existe que sous un autre protocole, il vous le dit et ne récupère
    rien, plutôt que de vous rattacher au mauvais site.

    ![La fenêtre après une récupération refusée : le carré existe en Routier, protocole que l'application ne gère pas.](../assets/captures/apercu-sites-modale-site-autre-protocole.png)

!!! tip "Pourquoi ce détour évite un dépôt manqué"
    Le portail n'autorise pas à activer un carré sans y créer un point. Un carré activé là-bas puis
    **redéclaré ici** donne deux sites pour le même carré, et un point local qui n'est pas celui du
    portail : la nuit déposée ensuite échoue, loin de sa cause. Vérifier d'abord, puis **récupérer**
    plutôt que redéclarer, évite toute cette chaîne - et c'est aussi le chemin le plus court pour
    préparer une nuit **opportuniste** sur le carré de quelqu'un d'autre.

Chaque carte porte aussi, quand l'application est [connectée à Vigie-Chiro](../connexion-vigiechiro.md),
un **badge d'état plateforme** : **« Enregistré sur Vigie-Chiro »** (bleu) quand le carré est relié au
portail, **« Verrouillé sur Vigie-Chiro »** (vert) quand il est en plus verrouillé par le MNHN -
c'est l'état **favorable**, celui qui autorise le dépôt des nuits. Pas de badge : le site n'est pas
encore rattaché (connectez-vous ou synchronisez).

!!! warning "« Trop rapprochés pour le protocole »"
    Chaque carte de point indique la distance **au point le plus proche du même carré**. En dessous de
    **200 m**, elle passe en avertissement et dit pourquoi : deux points si proches ne conviennent pas
    au protocole, et la cause est souvent une **coordonnée saisie de travers** plutôt qu'un vrai
    voisinage. Vérifiez la position sur la carte avant de déplacer quoi que ce soit sur le terrain.

    Au-delà du seuil, la distance est une simple information : elle ne réclame rien.

!!! note "Renommer un site déjà connu de Vigie-Chiro reste local"
    Le **nom convivial** est le vôtre : il vous aide à vous y retrouver dans votre liste. Sur un site
    que le portail connaît déjà - relié ou verrouillé - le modifier **ne remonte rien** : Vigie-Chiro
    continuera d'afficher le nom qu'il a. La modale vous le rappelle sous le champ, pour que l'écart
    entre les deux affichages ne vous surprenne pas.

Le bouton **Récupérer depuis Vigie-Chiro** (en haut à droite) récupère à la demande vos sites et
points déclarés sur le portail : les sites manquants sont créés localement, ceux déjà présents sont
simplement reliés - vos données locales ne sont **jamais écrasées**. C'est la même synchronisation
que celle exécutée automatiquement à la connexion ; un message sous le bandeau récapitule ce qui a
été récupéré (ou signale qu'il n'y avait rien à récupérer, par exemple hors connexion).

Ce message dit **chaque nature séparément** - sites, nuits, taxons - et **sur combien** : « 12 nuit(s)
récupérée(s) sur 55, dont 40 en attente d'analyse Vigie-Chiro ». Un compteur seul serait exact et
pourtant muet sur la part qui reste à venir.

Si l'une des natures n'a pas pu être récupérée, elle le dit **à côté des autres** et non à leur place :
une synchronisation où les sites sont à jour et les nuits injoignables vous montre les deux.

Il rapatrie aussi vos **nuits déjà déposées** sur la plateforme, avec leur **identité** : date, point,
numéro de passage, mais également l'**enregistreur**, la **météo** et le **micro**. Vous retrouvez donc
votre historique lisible dès la première synchronisation - c'est ce qui permet, après une réinstallation
ou sur un nouveau poste, de repartir de la plateforme plutôt que de zéro.

Elle rapatrie enfin leur **contenu** : les identifications faites par Vigie-Chiro et la liste des
fichiers de la nuit. Vos nuits sont donc consultables dès la première synchronisation.

!!! note "Ce que la synchronisation ne peut pas ramener"
    **Les sons.** Ils ne sont pas conservés sur la plateforme : une nuit récupérée se consulte, elle ne
    s'écoute pas. Si vous retrouvez la carte d'origine ou une sauvegarde, ouvrez la nuit et utilisez
    **Réactiver ce passage** : l'application y reconnaît vos fichiers et les rebranche.

    **Les nuits que Vigie-Chiro n'a pas fini d'analyser.** Leurs identifications n'existent pas encore.
    La synchronisation le dit (« N en attente d'analyse Vigie-Chiro ») et les reprendra d'elle-même au
    prochain passage. Si le compte rendu indique plutôt « N non récupérée(s), à réessayer », c'est la
    liaison avec la plateforme qui a manqué, pas l'analyse : relancez la synchronisation.

    Quand cette reprise aboutit, la nuit se remplit **sous vos yeux** : si sa fiche est ouverte, son
    nombre de séquences cesse d'afficher zéro sans que vous ayez à la rouvrir (voir
    [Ce qui est affiché suit vos données](index.md#ce-qui-est-affiche-suit-vos-donnees)).

!!! info "La synchronisation peut être longue"
    Elle va chercher le contenu de **chaque** nuit du compte. Sur un historique fourni, comptez
    plusieurs minutes à la première fois. Une barre indique où elle en est, et **Annuler** l'interrompt
    à tout moment : ce qui a déjà été récupéré est conservé, et la fois suivante reprend le reste.

La synchronisation rapatrie **tous** les points d'un carré (utile pour importer une nuit sur un point
pas encore utilisé). Mais un carré Point Fixe en compte des dizaines, dont un seul sert : les vues ne
**mettent en avant** que les points **utilisés** (au moins un passage) ou que vous avez **ajoutés à la
main**, et **résument** les autres - « + N rapatriés » sur la carte du site, un lien **« Afficher les
points non utilisés »** dans son détail. Rien n'est perdu : les points rapatriés restent disponibles,
juste repliés.

### Premier lancement

À la toute première ouverture, aucun site n'est déclaré : l'écran vous guide vers la création de
votre premier site, et rappelle que le carré et ses points doivent d'abord exister sur le portail
Vigie-Chiro.

![L'état initial de « Mes sites » : invitation à déclarer un premier site.](../assets/captures/apercu-sites-mes-sites-vide.png)

## Détail d'un site

Le **détail d'un site** réunit son identité (numéro de carré, département, protocole, date de
création, dernière nuit importée, nombre de passages), ses **points d'écoute** et la liste de ses
**passages**.

![Le détail d'un site : bandeau d'identité, points d'écoute et tableau des passages.](../assets/captures/apercu-sites-detail.png)

Le tableau porte sept colonnes : **Date**, **Point**, **N° passage**, **Statut**, **Verdict**,
**Enregistreur** et **Déposé le**. Les deux dernières disent d'où vient la nuit et si elle est partie :
`Déposé le` reste vide (« — ») tant que le dépôt n'a pas eu lieu, y compris sur un passage déjà
**vérifié**, ce qui distingue d'un coup d'œil ce qui est prêt de ce qui est parti.

Il se **trie**, se **réorganise** et laisse **choisir ses colonnes** (clic droit ou menu principal (☰)
« outils ») : voir [Personnaliser les tableaux](../personnaliser-les-tableaux.md).

Un **double-clic** sur une ligne ouvre l'écran du passage. Le **clic droit** réunit les actions de cette
nuit : **ouvrir le passage**, ouvrir sa page **Vigie-Chiro** (grisée si le passage n'est pas lié à la
plateforme) et **copier** son code de point :
voir [Agir sur une ligne](../personnaliser-les-tableaux.md#agir-sur-une-ligne-double-clic-et-clic-droit).

Le bouton **Modifier** (bandeau d'identité) ouvre une fenêtre pour **éditer la fiche du site** :
numéro de carré, nom, protocole et commentaire. Pratique pour corriger une saisie ou compléter le
site après coup, sans repasser par sa création.

Le bouton **Ouvrir sur Vigie-Chiro** ouvre la page de ce site sur le **portail Vigie-Chiro** dans
votre navigateur : pratique pour vérifier d'un coup d'œil que le rattachement est le bon. Il reste
grisé tant que le site n'est pas relié au portail (connectez-vous, ou utilisez « Récupérer depuis
Vigie-Chiro » sur l'écran Mes sites).

![La fenêtre d'édition de la fiche site : numéro de carré, nom convivial, protocole et commentaire.](../assets/captures/apercu-sites-modale-site.png)

- **Points d'écoute** : une carte par point, avec sa description, son **statut GPS** et le nombre de
  passages rattachés. Quand les coordonnées sont renseignées, le lien **« GPS : voir sur la carte »**,
  précédé d'une **icône verte de validation**, ouvre la **carte multi-sites centrée sur ce point** (où le
  mode édition permet de corriger sa position). Quand elles **manquent**, le lien
  **« GPS manquant : placer sur la carte »**, précédé d'une **icône d'avertissement**, ouvre cette même
  carte sur le carré du site, **mode édition déjà actif** : le point, affiché au centre de son carré, n'a plus qu'à être
  **glissé** à sa vraie position (puis enregistré). Le bouton **+ Ajouter un point** crée un nouveau
  point. Chaque carte indique aussi la **distance au point le plus proche** du site ; si deux points sont
  **anormalement rapprochés** (sous le seuil de protocole), une **icône d'avertissement** le signale, pour
  repérer une saisie GPS erronée ou des points trop serrés.
- **Publier un point sur Vigie-Chiro** : sur un point que **vous** avez créé, le lien
  **« Publier sur Vigie-Chiro »** l'ajoute aux points du carré sur la plateforme, sans toucher à ceux
  qui y sont déjà. Une fois le point en ligne, la carte l'indique par **« Publié sur Vigie-Chiro »** et
  ne repropose plus le geste. Les points **rapatriés** de la plateforme, eux, ne l'affichent pas : ils y
  sont déjà. Le lien reste **grisé**, avec son motif, tant que vous n'êtes pas connecté, que le carré
  n'est pas enregistré sur la plateforme, ou que le point n'a pas de coordonnées.

    Au moment où vous **créez** un point, la fenêtre propose de le publier dans la foulée : cochez
    **« Publier ce point sur Vigie-Chiro après l'enregistrement »**. La case reste grisée, avec son
    motif, tant que le geste n'est pas possible ; si vous effacez les coordonnées après l'avoir cochée,
    elle se décoche. Le compte rendu s'affiche sur la fiche du carré, une fois la fenêtre refermée.

    !!! warning "Si un point du même code existe déjà, ailleurs"
        Vigie-Chiro n'accepte qu'un seul point par code sur un carré. Si un point porte déjà ce code
        **à une autre position**, Companion ne publie rien et vous dit à quelle distance il se trouve.
        Il ne propose pas de le déplacer, et c'est volontaire : sur Vigie-Chiro, une nuit désigne son
        point **par son code**. Déplacer ce point déplacerait donc toutes les nuits qui s'y rattachent,
        y compris celles d'autres observateurs. À vous de choisir : donner un autre code à votre point,
        ou aligner sa position sur celle de la plateforme.

    !!! note "Si la plateforme refuse"
        Vigie-Chiro peut refuser l'ajout, et Companion ne peut pas le prévoir. Deux raisons : le carré
        est **le vôtre et déjà verrouillé** (un carré verrouillé est figé, seul un administrateur
        Vigie-Chiro peut le rouvrir), ou il appartient à **quelqu'un d'autre** et vous n'êtes pas
        validé sur son protocole. Le message vous dit laquelle vérifier. Dans les deux cas, **rien
        n'est modifié** sur la plateforme.
- **Passages enregistrés** : un tableau récapitulant, pour chaque nuit, sa date, son point, son
  numéro, son **statut** (Transformé, Vérifié, Déposé...), son **verdict** et son enregistreur.

Sur un site qui n'a pas encore de passage, le tableau est simplement vide :

![Le détail d'un site sans passage : le tableau des passages est vide.](../assets/captures/apercu-sites-detail-sans-passage.png)

## Ajouter ou modifier un point d'écoute

L'ajout ou la modification d'un point d'écoute se fait dans une **fenêtre dédiée** : code du point,
description, et coordonnées GPS (facultatives). En création, le formulaire est vierge ; en
modification, il est pré-rempli avec les valeurs existantes.

La fenêtre intègre une **carte-outil** centrée sur le carré du site, **synchronisée dans les deux
sens** avec les champs latitude / longitude (#153) : **glissez le marqueur** sur la carte pour fixer
la position (les champs se remplissent), ou **saisissez les coordonnées** (le marqueur se déplace).

Dès qu'un point géolocalisé est enregistré, sa **commune** est déterminée automatiquement depuis ses
coordonnées (service public de géocodage, connexion requise) et mémorisée : c'est elle que retrouvent
ensuite les recherches (« aix » dans [Carte & passages](multisite.md) ou la recherche globale) et
l'export CSV des observations. Hors ligne, la commune attend simplement une prochaine occasion : une
synchronisation, un nouvel enregistrement du point, ou la commande `rattraper-communes` en ligne de
commande.

Les champs acceptent **deux formats**, au choix : **degrés décimaux** (`43.5298`, la virgule est
tolérée) **ou** **degrés/minutes/secondes** (`43°31'47"N`, `1°34'26.4"W`). Vous pouvez donc **coller**
des coordonnées depuis n'importe quelle source ; elles sont converties automatiquement, et une saisie
hors plage (latitude −90..90, longitude −180..180) est refusée.
Tant qu'aucun GPS n'est renseigné, le marqueur démarre **au centre du carré** en position
**approximative** (anneau pointillé) : un point de départ à caler, pas une position mesurée.

![La fenêtre de création d'un point d'écoute : formulaire vierge.](../assets/captures/apercu-sites-modale-point-creation.png)

![La même fenêtre en modification : les champs sont pré-remplis avec les valeurs du point existant.](../assets/captures/apercu-sites-modale-point.png)
