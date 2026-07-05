# Recherche globale

La **recherche globale** est le champ **« 🔍 Rechercher »** posé en haut à droite du bandeau, présent
sur **tous les écrans**. Elle permet de **sauter directement** à un site, un point d'écoute ou un
passage, sans repasser par l'accueil ni la navigation : on tape quelques lettres, on choisit, on y est.

![La recherche globale ouverte : la saisie « 640380 » liste les résultats groupés en Sites, Points et Passages.](../assets/captures/apercu-recherche.png)

## Comment ça marche

On clique le champ (ou **Ctrl+F** depuis n'importe quel écran) et on saisit une **recherche libre**.
La liste se remplit **au fil de la frappe**, **insensible à la casse et aux accents** (« etang » trouve
« Étang »). Elle interroge :

- les **sites** : par **numéro de carré** ou **nom** ;
- les **points d'écoute** : par **code** ou **description** ;
- les **passages** : par **carré**, **code de point**, **numéro de passage**, **année** ou **date** ;
- les **espèces observées** : par **code**, **nom latin** ou **nom français**.

Les résultats sont **groupés par type** (Sites, puis Points, puis Passages, puis Espèces), chaque groupe
affichant un **en-tête** ; chaque ligne montre un **libellé principal** et un **détail** de contexte. Le
nombre de résultats par type est **borné** pour garder la liste lisible.

Pour une **espèce**, le détail commence par son **taxon parent** (sa catégorie taxonomique, par exemple
« Chiroptères » — la même notion que le filtre « Groupe » de [Sons & validation](validation.md#filtrer-les-observations)),
suivi du carré / point, du numéro de passage et de la date. Ouvrir une entrée espèce mène au **passage** où
elle a été relevée.

## Naviguer au clavier

La recherche se pilote **entièrement au clavier** :

- **↓** depuis le champ entre dans la liste ;
- **Entrée** ouvre l'élément sélectionné (l'écran du site ou du passage concerné) ;
- **Échap** ferme la liste (puis vide la recherche).

La liste se ferme aussi d'elle-même quand le focus quitte la zone de recherche. Chaque résultat est
**exposé aux lecteurs d'écran** (libellé accessible), pour ne pas dépendre du seul repère visuel.

## Sous le capot

La recherche vit dans le **socle** (`commun`) : le chrome (`MainController`) consomme le contrat
`RechercheGlobale` sans dépendre d'une fonctionnalité. L'implémentation (`recherche`) **agrège** les
services des fonctionnalités `sites` (sites et points) et `multisite` (passages), filtre par
correspondance normalisée et borne chaque catégorie. La saisie est **anti-rebondie** : les frappes
rapides sont regroupées en une seule recherche après une courte pause, pour garder le champ fluide.
