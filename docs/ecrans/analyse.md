# Espèces & observations

L'écran **« Espèces & observations »** est la porte d'entrée du prisme **biodiversité** : il **exploite
transversalement** vos observations (toutes nuits confondues) pour répondre à « quelles espèces ai-je
détectées, où, quand et combien ? ». Il complète le prisme **collecte & passages** (sites, import,
validation), qui produit la donnée nuit par nuit.

On l'ouvre depuis la carte **« Espèces & observations »** de l'accueil.

![L'inventaire par espèce : chaque espèce détectée, son groupe, ses compteurs et sa période.](../assets/captures/apercu-analyse.png)

## L'inventaire

Une table récapitule vos espèces. Un sélecteur **Regrouper** propose deux angles (le pivot espèce ↔ lieu) :

- **Par espèce** : une ligne par espèce, avec son **groupe** taxonomique, le nombre de **détections**, de
  **passages**, de **carrés** et de **points** où elle apparaît, et sa **période** d'observation.
- **Par carré** : une ligne par carré, avec sa **richesse spécifique** (nombre d'espèces distinctes) et son
  total de détections — utile pour le rendu Vigie-Chiro.

![L'inventaire par carré : la richesse spécifique (nombre d'espèces) de chaque carré.](../assets/captures/apercu-analyse-carre.png)

L'espèce retenue pour chaque observation est le **taxon validé** par l'observateur s'il existe, sinon la
**proposition Tadarida** ; les pseudo-taxons « bruit » et « oiseau » sont exclus.

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
