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
  total de détections — utile pour le rendu Vigie-Chiro.

> Le **taxon parent** est la même notion que le filtre **« Groupe »** de la vue [Sons & validation](validation.md#filtrer-les-observations) : la catégorie taxonomique (Chiroptères, Oiseaux, Orthoptères…) qui coiffe l'espèce.

![L'inventaire par carré : la richesse spécifique (nombre d'espèces) de chaque carré.](../assets/captures/apercu-analyse-carre.png)

L'espèce retenue pour chaque observation est le **taxon validé** par l'observateur s'il existe, sinon la
**proposition Tadarida** ; les pseudo-taxons « bruit » et « oiseau » sont exclus.

Les tableaux de cet écran (l'inventaire et le détail des observations) se **trient**, se **réorganisent**
et laissent **choisir leurs colonnes** (clic droit sur le tableau ou menu ☰ « outils ») :
voir [Personnaliser les tableaux](../personnaliser-les-tableaux.md).

## Le détail : les observations d'une espèce

L'écran est en **maître-détail**. En sélectionnant une espèce dans l'inventaire (mode *Par espèce*), le
panneau du bas liste **toutes ses observations à travers les passages** : date et n° de passage, carré,
point, **proposition Tadarida** (avec sa probabilité), **votre taxon** (la saisie de l'observateur, ou
`—` si la séquence n'a pas encore été revue) et le **statut**. C'est la réponse à « où et quand ai-je
détecté cette espèce ? », toutes nuits confondues.

Sélectionnez une observation, puis :

- **« 🎧 Écouter / valider »** ouvre l'écran de validation Tadarida du passage **droit sur cette
  détection** : la séquence est prête à être **réécoutée**, **validée** ou **corrigée**, sans avoir à la
  retrouver dans la liste. C'est l'écoute transverse : depuis n'importe quelle espèce, on saute à la bonne
  séquence. Au retour sur l'écran, l'inventaire reflète vos décisions.
- **« Ouvrir le passage → »** (ou un double-clic sur la ligne) ouvre l'écran du passage concerné pour en
  voir le contexte complet.

## Consulter la fiche d'une espèce

Un **clic droit** sur une espèce de l'inventaire propose **« Fiche de l'espèce »** : elle ouvre dans votre
**navigateur** la fiche d'information de l'espèce, pratique pour vérifier un critère avant de trancher une
identification. Comme dans [Sons & validation](validation.md#consulter-la-fiche-dune-espece), la source
s'adapte au taxon : **Plan National d'Actions Chiroptères** pour les chauves-souris, **GBIF** ou
**Wikipédia FR** (selon le réglage du menu ☰ du bandeau) pour les autres.

## La carte de répartition

Le bouton **« 🗺️ Carte »** bascule la zone du haut entre le **tableau** et une **carte**. Par défaut, la
carte est une **choroplèthe de richesse** : chaque **carré** est d'autant plus **vert** qu'il abrite
**d'espèces distinctes** (une légende « faible → élevée » le rappelle). Le survol d'un carré affiche ses
mini-statistiques (espèces, détections, période).

![La carte de répartition : richesse par carré (choroplèthe verte) avec sa légende.](../assets/captures/apercu-analyse-carte.png)

Quand une **espèce est sélectionnée** (mode *Par espèce*), la carte montre sa **répartition** : les carrés
**où elle est présente** gardent leur **teinte de richesse** (un vert plus ou moins soutenu selon le nombre
d'espèces du carré), les autres sont **atténués**. On répond ainsi d'un coup d'œil à « **où est ma
biodiversité la plus riche ?** » et « **où ai-je vu cette espèce ?** ». Le filtre **Statut** recolore la
carte ; **« 📋 Tableau »** revient à la liste.

## Filtrer par niveau de confiance

Le filtre **Statut** restreint l'inventaire selon l'état de revue des observations :

- **Validée** : l'observateur a confirmé la proposition Tadarida ;
- **Corrigée** : l'observateur a saisi une autre espèce ;
- **Non touchée** : proposition Tadarida non encore revue.

Sans filtre (**Tous les statuts**), tout est pris en compte. Tant qu'aucune nuit n'a été importée et
validée (résultats Tadarida), l'écran invite à le faire.

## Filtrer et exporter

Le champ **Filtrer** restreint la table à la volée (insensible à la casse et aux accents) : par **nom ou
code d'espèce** en mode *Par espèce*, par **numéro de carré ou nom de site** en mode *Par carré*. Le
bouton **« 📤 Exporter… »** enregistre l'inventaire **affiché** (tel que filtré) en **CSV**, prêt pour un
tableur ou un partage.

## Vues sauvegardées

Une combinaison de filtres utile peut être **enregistrée sous un nom** pour être rejouée d'un clic. Les
vues enregistrées s'affichent comme des **onglets** au-dessus de l'inventaire : cliquer sur le nom d'un
onglet **rejoue** sa combinaison de filtres. Le bouton **« + Vue »**, au bout de la barre d'onglets,
enregistre les filtres **courants** sous un nouveau nom. Sur chaque onglet, **« ✎ »** le renomme et
**« ✕ »** le supprime.
