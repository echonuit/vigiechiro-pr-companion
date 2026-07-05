# Validation

L'écran **Validation** (la vue audio unifiée « Sons & validation ») sert à **écouter, relire et
corriger** les espèces identifiées par l'outil Tadarida, et à constituer votre **corpus de sons de
référence**. On y arrive depuis plusieurs points : un **passage** (après dépôt, pour valider ses
résultats Tadarida), l'accueil (**Sons & validation**), **Espèces & observations** (les détections d'une
espèce) et **Carte & passages** (un passage ou le lot filtré).

![Sons & validation : table des observations, écoute pleine largeur et barre d'actions.](../assets/captures/apercu-sons-validation.png)

Quelle que soit la source, l'écran présente la **table des observations** (espèce retenue, proposition
Tadarida, statut À revoir / Validée / Corrigée, mesures d'identification…) que vous pouvez **filtrer**,
**trier** et dont vous **choisissez les colonnes**, ainsi qu'un **panneau d'écoute pleine largeur**
(sonogramme + spectrogramme) pour la ligne sélectionnée, où vous **repérez et rejouez le cri** dans la
séquence. Les **colonnes de contexte** (passage, carré, point) s'affichent quand la source couvre plusieurs
passages et se masquent pour un passage unique.

Le **tri** et les **filtres** que vous réglez sont **mémorisés le temps de la session** : si vous quittez
puis rouvrez l'écran, vous retrouvez la revue là où vous l'aviez laissée, sans tout re-régler.

## Filtrer les observations

Une nuit d'enregistrement produit souvent des centaines d'observations : la **barre de filtres** vous aide
à isoler celles que vous voulez revoir. Elle fonctionne « à la manière de Notion » :

- un **champ de recherche** permanent, à gauche, cherche dans le **nom de fichier**, l'**espèce** (taxon
  Tadarida ou votre correction) et le **commentaire** ; la recherche ignore la casse et les accents ;
- un bouton **« + Filtre »** ajoute un critère sous forme de **puce** ; on retire une puce par sa croix.

Les critères disponibles :

| Critère | Ce qu'il garde | Par défaut |
|---|---|---|
| **Statut** | À revoir / Validée / Corrigée | À revoir (le plus utile pour la revue) |
| **Groupe** | un groupe taxonomique présent (Chiroptères, Oiseaux, Orthoptères…) | **Chiroptères** s'il est présent : « chauves-souris uniquement », qui écarte bruit, oiseaux et orthoptères |
| **Espèce** | une espèce précise (taxon retenu) | aucune tant que vous n'en choisissez pas une |
| **Références** | seulement les sons marqués « référence » | (puce booléenne : sa présence suffit) |
| **Proba** | les détections dont la probabilité Tadarida est **≥** au seuil du curseur | 50 % ; les observations **sans** probabilité sont toujours gardées |
| **Heure** | les captures dont l'heure tombe dans la plage « de … à … » | **nuit (21 h → 6 h)** ; la plage gère le passage à minuit, et les captures sans heure sont gardées |

Les puces se **combinent en ET** : « Chiroptères » + « Proba ≥ 80 % » ne garde que les chauves-souris les
plus sûres. Les **compteurs** de la barre de statut (À revoir / Validées / Corrigées) suivent en temps réel
le **sous-ensemble affiché**, pas la nuit entière : vous voyez toujours combien il reste à traiter dans ce
que vous avez sous les yeux.

![La barre de filtres avec la puce « Groupe : Chiroptères » active : la table ne montre plus que les chauves-souris.](../assets/captures/apercu-sons-validation-filtres.png)

## Choisir et organiser les colonnes

La table s'adapte à votre façon de travailler :

- **Trier** : cliquez sur un **en-tête de colonne** (un second clic inverse le sens). Vous pouvez trier par
  date, par fréquence, par espèce…
- **Choisir les colonnes affichées** : un **sélecteur de colonnes** (menu ☰) coche celles à montrer et
  masque le reste, pour ne garder que l'information utile à votre revue.
- **Réordonner** : **glissez** un en-tête pour déplacer sa colonne, comme dans un tableur ou Notion.

Outre l'espèce, le statut et la proposition Tadarida, la table peut afficher : le **nom de fichier** de la
séquence, la **date d'enregistrement**, l'**heure de capture**, la **fréquence médiane**, un **indicateur
de commentaire**, et les mesures d'identification **FME** (fréquence de moindre énergie) et **fréquence
terminale**, calculées sur le cri sélectionné.

![La table avec toutes les colonnes affichées, dont la fréquence médiane et les mesures FME / fréquence terminale.](../assets/captures/apercu-sons-validation-colonnes.png)

Les mesures **FME** et **fréquence terminale** demandent d'analyser le signal du cri : elles se
**remplissent au fil de l'écoute** (un tiret « — » tant que la ligne n'a pas été sélectionnée), pour ne pas
analyser toute la nuit d'un coup.

## Repérer et écouter le cri

Le **panneau d'écoute** montre le **sonogramme** et le **spectrogramme** de la séquence sélectionnée. Comme
les cris de chauves-souris sont des **ultrasons**, le son est **ralenti dix fois** (expansion temporelle
×10) pour devenir audible.

Quand une observation pointe un cri précis dans la séquence, la **fenêtre de ce cri** (entre son début et
sa fin) est **surlignée** sur le sonogramme et le spectrogramme, et la **lecture s'y positionne**
directement : vous entendez le bon cri sans chercher. Le menu ☰ propose deux options d'écoute : la
**lecture automatique** à chaque sélection (activée par défaut) et la **lecture en boucle**.

## Relire et corriger

Pour l'observation sélectionnée, vous pouvez :

- **Valider** : retenir la proposition de Tadarida ;
- **Corriger** : retenir un autre taxon, choisi dans la liste ;
- **Marquer / retirer la référence** : ajouter l'observation à votre corpus de sons de référence, ou l'en retirer.

Un **mode inventaire** permet de propager une validation aux autres détections de la même espèce.

**Éditer un commentaire** : cliquez sur la **case Commentaire** d'une ligne pour saisir ou modifier une note
sur cette observation (l'indicateur de commentaire de la table signale les lignes annotées).

### Aller vite : clavier et lots

La revue est pensée pour **enchaîner** les observations sans quitter le clavier :

- **↑ / ↓** naviguent d'une ligne à l'autre ;
- **Entrée** valide, **R** marque / retire la référence, **N** saute à la prochaine observation « À revoir ».

Vous pouvez aussi **sélectionner plusieurs lignes** (Ctrl+clic, ou Maj+clic pour une plage) et **valider,
corriger ou marquer en référence tout le lot d'un coup**. Une action groupée est **tout ou rien** (si elle
échoue, aucune ligne n'est modifiée) et enregistre la validation en **mode activité** (sans propagation
inventaire, qui n'aurait pas de sens sur une sélection hétérogène).

## Validation d'un passage (Tadarida)

Ouvert sur un **passage** (accessible **après le dépôt** : Vigie-Chiro renvoie les résultats
d'identification 24 à 48 h plus tard, voir le [parcours](../parcours/index.md)), l'écran permet
d'**importer le fichier CSV** de résultats Tadarida, puis d'**exporter** le fichier `_Vu` réinjectable
(avec, en option, la trace du mode de validation). Ces actions propres au passage vivent dans le menu « ☰ ».

Pour importer, vous pouvez soit utiliser le menu « ☰ », soit **glisser-déposer** directement le fichier
CSV sur l'écran : pratique quand la fenêtre de sélection de fichier du système ne s'ouvre pas. À la fin de
l'import, un bandeau confirme le nombre d'observations chargées ; en cas de problème (séquence introuvable,
fichier illisible…), un bandeau rouge explique ce qui s'est passé.

![Validation Tadarida : la table des observations « À revoir » juste après l'import d'un CSV, avec le bandeau récapitulatif.](../assets/captures/apercu-validation-tadarida.png)

L'import est **tolérant** : les observations dont le son n'est pas disponible sont ignorées (le bandeau en
indique le nombre), et les taxons que Tadarida propose hors de la liste de référence sont conservés tels
quels. Vous pouvez ainsi importer un fichier de résultats complet même si vous n'avez gardé qu'une partie
des sons.

## Sons de référence

Depuis l'accueil, l'activité **Sons & validation** ouvre l'écran sur **toutes les observations marquées
« référence »** : vous les **écoutez**, les **validez / corrigez**, **retirez** la référence, et
**exportez la bibliothèque** (un récapitulatif CSV + la copie des fichiers son) vers un dossier de votre
choix.
