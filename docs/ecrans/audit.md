# Audit de cohérence

Votre travail vit à **trois endroits** : sur le **disque** (les fichiers audio), dans la **base** (ce que
l'application en sait) et sur **Vigie-Chiro** (ce que la plateforme en a reçu). Ces trois-là peuvent
diverger : un disque débranché, un fichier renommé à la main, un dépôt incomplet : et le plus souvent,
**sans rien dire**.

L'écran **Audit de cohérence** existe pour que plus rien ne diverge en silence. Il **confronte** les trois
et vous **énumère** les écarts.

![L'audit de cohérence : trois écarts sur une même nuit. Un audio incomplet (information), un nom de fichier sans le préfixe attendu (erreur), et un département divergent entre le carré 640380 et la commune Saint-Martin-de-Seignanx (information).](../assets/captures/apercu-audit.png)

## Lire un constat

Chaque ligne est un **écart**, et se lit de gauche à droite :

| Colonne | Ce qu'elle dit |
|---|---|
| **Gravité** | **Erreur** (à traiter), **Avertissement** (à regarder), **Information** (normal, mais bon à savoir) |
| **Catégorie** | la nature de l'écart (fichier manquant, préfixe non conforme, dépôt divergent…) |
| **Passage** | la nuit concernée : **double-cliquez** pour l'ouvrir |
| **Cible** | le fichier ou l'élément en cause |
| **Détail** | ce qui ne va pas, en clair |

Le bandeau du haut résume : *« 5 écarts : 1 erreur, 0 avertissement, 4 infos »*.

!!! tip "Tout n'est pas une anomalie"
    Un constat en **Information** ne demande souvent **aucune action** : une nuit **archivée** n'a plus ses
    fichiers sur le disque, et c'est **voulu**. L'audit le dit : parce qu'un audit qui se tairait sur un
    état normal vous laisserait croire que le disque est intact, et un audit qui **crierait** sur un état
    normal finirait par ne plus être lu du tout.

## « Département divergent » : deux lectures d'un même lieu

Le département d'un point se lit de **deux façons**, et elles ne viennent pas du même endroit :

- par le **numéro du carré** : ses deux premiers chiffres (`840962` est dans le Vaucluse) ;
- par la **commune du point** : celle que ses coordonnées GPS ont désignée (Aix-en-Provence est dans les
  Bouches-du-Rhône).

Quand les deux ne concordent pas, l'audit le dit, en **Information** :

> Départements 13 (commune) et 84 (carré) : le point A1 est en Aix-en-Provence, son carré 840962 dit autre chose.

!!! tip "Ce n'est pas forcément une erreur"
    Un carré fait **2 km de côté** et le carroyage national ignore les limites administratives : un
    carré posé sur une frontière de département en chevauche deux, et un point près du bord tombe
    alors de l'autre côté.

    L'application **ne sait pas** distinguer ce cas d'un vrai problème : un GPS relevé au mauvais endroit,
    un numéro de carré mal recopié. Elle vous **montre** l'écart, vous seul connaissez le terrain. Si le
    point n'a rien à faire là, vérifiez ses coordonnées ou le numéro du carré.

Un point dont la commune n'a **pas** été résolue (pas de GPS, ou résolution jamais lancée) ne produit
aucun constat : il n'y a pas de seconde lecture à confronter.

## Filtrer les constats

Un audit de saison mêle des natures très différentes : une carte SD débranchée, un nom de fichier hors
convention, un dépôt inachevé. La **barre de filtres** vous laisse poser la question qui vous occupe :
*« qu'est-ce qui est bloquant ? »*, *« qu'est-ce qui concerne cette nuit-là ? »*. Elle fonctionne comme
celle des autres écrans :

- un **champ de recherche** permanent cherche dans la **Cible** et le **Détail** : les deux colonnes en
  texte libre. La recherche ignore la casse et les accents ;
- un bouton **« + Filtre »** ajoute un critère sous forme de **puce** ; on retire une puce par sa croix ;
- **« Tout effacer »** enlève d'un coup les puces **et** la recherche.

Les <!--inv:criteres-audit-->3<!--/inv--> critères disponibles :

> Les gestes de la barre (recherche, « + Filtre », puces, « Tout effacer »), la façon dont les listes de valeurs s'adaptent aux autres filtres, et le bandeau qui prévient quand un filtre n'a pas pu être remis en place sont décrits une fois pour tous les écrans dans [Personnaliser les tableaux](../personnaliser-les-tableaux.md#filtrer-la-barre-a-puces).

| Critère | Ce qu'il garde |
|---|---|
| **Gravité** | les constats d'une gravité : Erreur, Avertissement, Information, Succès |
| **Catégorie** | les constats d'une nature : fichier absent du disque, préfixe non conforme, dépôt divergent… |
| **Passage** | les constats qui accusent une nuit précise. Un constat qui ne cite **aucune** nuit (un fichier orphelin, un serveur injoignable) n'a rien à y désigner : il n'apparaît pas dans la liste des passages |

Aucune puce n'est posée d'avance : un audit se lit d'abord **en entier**. Vous filtrez ensuite pour
travailler.

!!! warning "Relancer l'audit peut faire disparaître ce que vous filtriez"
    Vos filtres sont **remis en place** quand vous revenez sur l'écran. Mais entre-temps, l'audit a pu
    être relancé : la nuit 42 que vous suiviez n'a peut-être plus de constat du tout. Quand un filtre ne
    peut pas être repris, un **bandeau vous le dit** : l'écran montre alors **plus large** que ce que
    vous aviez demandé, et vous devez le savoir pour ne pas croire l'avoir déjà réduit.

Vous pouvez aussi **enregistrer une vue** (les onglets au-dessus de la barre) pour retrouver d'un clic une
combinaison qui vous sert souvent : par exemple les erreurs de dépôt d'une campagne.

## Aller au passage accusé

Un constat **nomme** la nuit fautive. **Double-cliquez** sur la ligne : la fiche du passage s'ouvre, avec
son contexte (carré, point). Vous n'avez pas à la retrouver à la main.

Un **clic droit** sur le constat propose la même chose sous forme de menu, avec de quoi **copier** le
n° de passage ou le motif du constat pour les recoller ailleurs. Les entrées sont **grisées** quand le
constat ne cite aucun passage : voir [Agir sur une ligne](../personnaliser-les-tableaux.md#agir-sur-une-ligne-double-clic-et-clic-droit).

## Auditer une seule nuit

Après avoir **réparé** une nuit (réimporté des fichiers, réactivé un passage archivé), vous voulez vérifier
**celle-là** : pas relancer l'audit de tout un workspace qui en compte des dizaines.

Sélectionnez un constat qui cite un passage, puis **« Auditer ce passage »**. Le bouton reste **désactivé**
tant qu'aucune nuit n'est sélectionnée, et son infobulle vous dit **pourquoi**. La même action figure au
**clic droit** de la ligne, si vous préférez rester sur le tableau.

## Vérifier en ligne

**« Vérifier en ligne »** ajoute les écarts qui demandent le réseau : ce que Vigie-Chiro a **réellement
reçu** de vos dépôts, et les **points d'écoute** que la plateforme connaît. Hors connexion, l'audit
fonctionne quand même : il se limite au disque et à la base, et vous le dit.

### Les points d'écoute, dans les deux sens

L'audit compare vos points à ceux de la plateforme **dans les deux sens**, mais il ne dit pas la même
chose des deux.

- **Un point d'ici que Vigie-Chiro ne connaît pas** est toujours signalé : vous l'avez créé localement, ou
  il a été supprimé côté plateforme.
- **Une localité que Vigie-Chiro connaît et que vous n'avez pas** ne l'est **pas systématiquement**.
  L'application la **crée** au prochain rapprochement, et c'est **voulu** : c'est exactement ce qui permet
  de tout retrouver après une réinstallation ou un [redémarrage sur base neuve](index.md#repartir-dune-base-neuve).
  Le signaler à chaque fois ne ferait que du bruit.

!!! warning "Sauf quand ce silence cache du travail"
    Si cette localité inconnue **porte des nuits que vous n'avez pas ici**, l'audit le **dit** - et il
    **nomme les nuits**. Le point serait créé sans un mot, et vous ne sauriez jamais qu'il existe, sur la
    plateforme, des nuits entières qui vous manquent : déposées depuis un autre poste, ou avant une
    réinstallation.

    C'est le seul cas où l'absence d'un point raconte vraiment quelque chose. Vous pouvez alors
    **reconstruire** ces nuits (voir [Passage](passage.md)), ou vérifier que vous travaillez bien sur le
    poste que vous croyez.

## En ligne de commande

```bash
./vigiechiro audit-coherence                    # tout le workspace
./vigiechiro audit-coherence --passage 12       # une seule nuit
./vigiechiro audit-coherence --online --json    # avec le réseau, pour un script
```

La commande sort en **`0`** si aucun écart d'erreur n'est trouvé : un script peut donc s'en servir comme
d'un feu vert.

Elle offre les **mêmes filtres** que la barre de l'écran :

```bash
./vigiechiro audit-coherence --gravite ERREUR                   # ce qui bloque
./vigiechiro audit-coherence --categorie DEPARTEMENT_DIVERGENT  # une nature
./vigiechiro audit-coherence --contient "carte sd"              # cible et détail
```

!!! warning "Le code de sortie ne suit **pas** le filtre"
    Filtrer change ce qui s'**affiche**, jamais ce que le code de sortie **juge**. `--gravite INFO` sur
    un dossier de travail abîmé n'affiche aucune erreur, et sort pourtant en **`1`**.

    C'est voulu : sans cela, un script d'intégration qui filtre pourrait conclure que tout va bien sur
    une base cassée. Le `1` répond toujours à « ce dossier a-t-il un problème ? », jamais à « le filtre
    a-t-il trouvé quelque chose ? ».

    Pour que ce `1` ne paraisse pas sorti de nulle part, la commande **dit ce qu'elle a masqué** :

    ```
    1 constat(s) affiché(s) sur 3. L'audit entier compte 1 erreur(s), 0 avertissement(s),
    2 info(s), et c'est lui que juge le code de sortie.
    ```

Un filtre dont la valeur existe mais ne correspond à rien le dit aussi (« Aucun constat ne correspond
aux filtres »), plutôt que d'afficher « aucun écart détecté » : un filtre ne doit jamais faire passer
une panne pour la santé. Une valeur qui **n'existe pas** (`--gravite CATASTROPHE`) est refusée avant que
l'audit ne tourne, en `2` : c'est une faute de frappe, pas un résultat.
