# Espèces & observations

L'écran **« Espèces & observations »** est la porte d'entrée du prisme **biodiversité** : il **exploite
transversalement** vos observations (toutes nuits confondues) pour répondre à « quelles espèces ai-je
détectées, où, quand et combien ? ». Il complète le prisme **collecte & passages** (sites, import,
validation), qui produit la donnée nuit par nuit.

On l'ouvre depuis la carte **« Espèces & observations »** de l'accueil.

![L'inventaire par espèce : chaque espèce détectée, son taxon parent, ses compteurs et sa période.](../assets/captures/apercu-analyse.png)

## L'inventaire

Une table récapitule vos espèces. Un sélecteur **Regrouper** propose deux angles (le pivot espèce ↔ lieu) :

- **Par espèce** : une ligne par espèce, avec son **taxon parent** (sa catégorie taxonomique, par exemple
  « Chiroptères »), le nombre de **détections**, de **passages**, de **carrés** et de **points** où elle
  apparaît, et sa **période** d'observation.
- **Par carré** : une ligne par carré, avec sa **richesse spécifique** (nombre d'espèces distinctes) et son
  total de détections : utile pour le rendu Vigie-Chiro.

> Un **bouclier violet** devant un nom d'espèce signale une espèce **prioritaire** au sens du
> [Plan National d'Actions Chiroptères](https://plan-actions-chiropteres.fr/) 2016-2025 : dix-neuf
> espèces sur les trente-six de métropole. L'onglet **« Espèces prioritaires »** ne garde qu'elles, et
> la puce du même nom se combine avec les autres filtres. L'absence de bouclier ne veut pas dire « sans
> intérêt » : la plupart des taxons détectés ne relèvent tout simplement pas de ce plan.

> Le filtre **« Nature de la nuit »** sépare les nuits du protocole des **participations
> opportunistes** (celles réalisées sur le carré d'un tiers, qui ne comptent pas dans votre solde de
> saison). Les deux restent comptées ensemble par défaut : c'est à vous de demander la séparation.

> Le **taxon parent** est la catégorie taxonomique (Chiroptères, Oiseaux, Orthoptères…) qui coiffe l'espèce. Le filtre porte ce nom sur tous les écrans.

La barre du haut porte <!--inv:criteres-analyse-->5<!--/inv--> critères, plus une recherche libre :

    La recherche libre couvre les colonnes en texte de cet écran : le **taxon** (retenu, latin,
    vernaculaire), le **n° de carré** et le **nom** que vous lui avez donné, la **commune** et le **code
    du point**.

> Les gestes de la barre (recherche, « + Filtre », puces, « Tout effacer »), la façon dont les listes de valeurs s'adaptent aux autres filtres, et le bandeau qui prévient quand un filtre n'a pas pu être remis en place sont décrits une fois pour tous les écrans dans [Personnaliser les tableaux](../personnaliser-les-tableaux.md#filtrer-la-barre-a-puces).

| Critère | Ce qu'il garde |
|---|---|
| **Statut** | les observations selon leur état de revue (à revoir, validée, corrigée) |
| **Taxon parent** | une catégorie taxonomique présente (Chiroptères, Oiseaux, Orthoptères…) |
| **Lieu** | les observations d'un ou plusieurs lieux à cocher : communes, carrés et points présents dans l'inventaire, le carré portant son nom quand vous lui en avez donné un |
| **Nature de la nuit** | les nuits du protocole, ou les participations opportunistes |
| **Espèces à enjeu** | seulement les espèces prioritaires du Plan National d'Actions Chiroptères |

![L'inventaire par carré : la richesse spécifique (nombre d'espèces) de chaque carré.](../assets/captures/apercu-analyse-carre.png)

L'espèce retenue pour chaque observation est le **taxon validé** par l'observateur s'il existe, sinon la
**proposition Tadarida** ; les pseudo-taxons « bruit » et « oiseau » sont exclus.

Les tableaux de cet écran (l'inventaire et le détail des observations) se **trient**, se **réorganisent**
et laissent **choisir leurs colonnes** (clic droit sur le tableau ou menu ☰ « outils ») :
voir [Personnaliser les tableaux](../personnaliser-les-tableaux.md).

Le **clic droit** réunit aussi les actions de la ligne visée : la **fiche de l'espèce**, et sur le détail
des observations **Écouter** et **Ouvrir le passage**. Un sous-menu **Copier ▸** dépose dans le
presse-papier le **nom latin** ou le **nom vernaculaire** de l'espèce (le **n° de carré** sur le détail),
pour les recoller dans un tableur :
voir [Agir sur une ligne](../personnaliser-les-tableaux.md#agir-sur-une-ligne-double-clic-et-clic-droit).

## Le détail : les observations d'une espèce

L'écran est en **maître-détail**. En sélectionnant une espèce dans l'inventaire (mode *Par espèce*), le
panneau du bas liste **toutes ses observations à travers les passages** : date et n° de passage, carré,
point, **commune**, **proposition Tadarida** (avec sa probabilité), **votre taxon** (la saisie de l'observateur, ou
`—` si la séquence n'a pas encore été revue) et le **statut**. C'est la réponse à « où et quand ai-je
détecté cette espèce ? », toutes nuits confondues.

Sélectionnez une observation, puis :

- **« Écouter / valider »** ouvre l'écran de validation Tadarida du passage **droit sur cette
  détection** : la séquence est prête à être **réécoutée**, **validée** ou **corrigée**, sans avoir à la
  retrouver dans la liste. C'est l'écoute transverse : depuis n'importe quelle espèce, on saute à la bonne
  séquence. Au retour sur l'écran, l'inventaire reflète vos décisions.
- **« Ouvrir le passage → »** (ou un double-clic sur la ligne) ouvre l'écran du passage concerné pour en
  voir le contexte complet.

## Consulter la fiche d'une espèce

Un **double-clic** sur une espèce de l'inventaire (ou sur l'une de ses observations dans le détail) ouvre
directement, dans votre **navigateur**, la fiche d'information de l'espèce ; un **clic droit** propose la
même **« Fiche de l'espèce »** sur les deux tables. Pratique pour vérifier un critère avant de trancher une
identification. Comme dans [Sons & validation](validation.md#consulter-la-fiche-dune-espece), la source
s'adapte au taxon : **Plan National d'Actions Chiroptères** pour les chauves-souris, **GBIF** ou
**Wikipédia FR** (selon le réglage du menu ☰ du bandeau) pour les autres.

Toutes les lignes n'ont pas de fiche : un pseudo-taxon (**Bruit**, **Oiseau**) ou un couple d'espèces n'en
a aucune. Dans le menu, l'entrée est alors **grisée** avec la mention « aucune fiche disponible » ; au
**double-clic**, rien ne s'ouvre et un **bandeau** vous le dit (« Aucune fiche disponible pour « Bruit » »).
Le geste ne reste jamais sans réponse.

## La carte de répartition

Le bouton **« Carte »** bascule la zone du haut entre le **tableau** et une **carte**. Par défaut, la
carte est une **choroplèthe de richesse** : chaque **carré** est d'autant plus **vert** qu'il abrite
**d'espèces distinctes** (une légende « faible → élevée » le rappelle). Le survol d'un carré affiche ses
mini-statistiques (espèces, détections, période).

![La carte de répartition : richesse par carré (choroplèthe verte) avec sa légende.](../assets/captures/apercu-analyse-carte.png)

Quand une **espèce est sélectionnée** (mode *Par espèce*), la carte montre sa **répartition** : les carrés
**où elle est présente** gardent leur **teinte de richesse** (un vert plus ou moins soutenu selon le nombre
d'espèces du carré), les autres sont **atténués**. On répond ainsi d'un coup d'œil à « **où est ma
biodiversité la plus riche ?** » et « **où ai-je vu cette espèce ?** ». Le filtre **Statut** recolore la
carte ; **« Tableau »** revient à la liste.

## Filtrer par niveau de confiance

Le filtre **Statut** restreint l'inventaire selon l'état de revue des observations :

- **Validée** : l'observateur a confirmé la proposition Tadarida ;
- **Corrigée** : l'observateur a saisi une autre espèce ;
- **Non touchée** : proposition Tadarida non encore revue.

Sans filtre (**Tous les statuts**), tout est pris en compte. Tant qu'aucune nuit n'a été importée et
validée (résultats Tadarida), l'écran invite à le faire.

## Filtrer et exporter

Le champ **Filtrer** restreint la table à la volée (insensible à la casse et aux accents) : par **nom ou
code d'espèce** en mode *Par espèce*, par **numéro de carré, nom de site ou commune** en mode *Par
carré* (la commune est celle déduite des coordonnées du point, voir [Mes sites](sites.md)). Le
bouton **« Exporter… »** enregistre l'inventaire **affiché** (tel que filtré) en **CSV**, prêt pour un
tableur ou un partage.

### Restreindre à un lieu

La puce **« Lieu »** coche un ou plusieurs lieux parmi ceux **présents dans ce que vous regardez**,
groupés par nature : les **communes**, les **carrés** et les **points d'écoute**. Cocher plusieurs
valeurs les cumule, et une observation est retenue dès qu'**un** de ses lieux figure parmi ceux cochés.

Un carré paraît sous ses deux étiquettes quand vous lui avez donné un nom, « 640380 · Étang de la
Tuilière » : le numéro et le nom désignent le même lieu, et la liste ne vous fait pas choisir entre deux
entrées identiques. Un point paraît toujours **précédé de son carré**, « 640380 · A1 », parce qu'un même
code de point se retrouve sur presque tous les carrés et ne désignerait rien de précis tout seul.

![La liste ouverte de la puce « Lieu » : trois groupes nommés (Communes, Carrés, Points), les carrés paraissant sous la forme « 640380 · Étang de la Tuilière » et les points sous la forme « 640380 · A1 ».](../assets/captures/apercu-analyse-lieu.png)

Une commune peut couvrir **plusieurs carrés** : la cocher les retient tous, ce qui répond à « qu'ai-je
entendu sur cette commune ? » sans avoir à savoir combien de carrés elle recouvre.

!!! tip "Le même inventaire en ligne de commande"
    `vigiechiro lister-especes` rend la table *Par espèce*, `vigiechiro lister-carres` la table *Par
    carré*, avec les **mêmes cinq critères** que la barre à puces : `--statut`, `--taxon-parent`,
    `--lieu` (répétable), `--nature` et `--a-enjeu`. `--format json` remplace le CSV, et `--sortie
    <fichier>` écrit à la place de l'écran - dans les deux formats.

    Une différence utile à connaître : à l'écran vous **cochez** dans une liste, en ligne de commande
    vous **tapez** un fragment, qui correspond partiellement (« chirop » retient « Chiroptères »).
    Le **point d'écoute** ne s'y filtre pas : un code seul, « A1 », se retrouve sur presque tous les
    carrés et ne désignerait rien de précis.

## Vues sauvegardées

Une combinaison de filtres utile peut être **enregistrée sous un nom** pour être rejouée d'un clic. Les
vues enregistrées s'affichent comme des **onglets** au-dessus de l'inventaire : cliquer sur le nom d'un
onglet **rejoue** sa combinaison de filtres. Le bouton **« + Vue »**, au bout de la barre d'onglets,
enregistre les filtres **courants** sous un nouveau nom. Sur chaque onglet, le **crayon** le renomme et
la **croix** le supprime.
