# P7 - Valider les résultats Tadarida ✅

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Persona principal** : Marie / Samuel. **Objectifs qualité visés** : [O4 Exactitude lecture audio](../../Objectifs%20qualites/Objectifs%20qualites/O4.md), [O7 Intégrité](../../Objectifs%20qualites/Objectifs%20qualites/O7.md).

24-48 h après le dépôt sur Vigie-Chiro (parcours [P4](P4%20-%20Preparer%20un%20lot%20pret%20a%20deposer.md)), Tadarida a analysé les séquences d'écoute et restitué un fichier de **résultats d'identification** (CSV listant les espèces détectées dans chaque séquence, avec leur probabilité). Marie veut **passer en revue ces résultats** pour valider ou corriger les classifications avant que les données ne soient consolidées dans la base nationale.

1. Le plus souvent, Marie n'a rien à télécharger : « Actualiser » sur l'écran de lot, ou « **Vérifier le traitement** » sur la fiche du passage, a importé les observations dès que l'analyse s'est terminée ([P4](P4%20-%20Preparer%20un%20lot%20pret%20a%20deposer.md)).
2. Elle ouvre « **Sons & validation** » sur son passage. Si les observations n'y sont pas encore, ☰ ▸ « **Importer depuis Vigie-Chiro…** » les demande à la plateforme ; le même geste, rejoué plus tard, va chercher ce qui a changé, à commencer par l'avis du validateur. En repli, ☰ ▸ « Importer un CSV Tadarida… » lit un fichier de résultats téléchargé sur le portail (formats `Brut` ou `Vu`, R17), que l'on peut aussi déposer sur l'écran.
3. L'application associe chaque ligne à la séquence d'écoute correspondante et affiche la **vue de validation** :
    - la **table des observations** : l'espèce retenue, la proposition de Tadarida, sa probabilité, le statut (À revoir, Validée, Corrigée) en pastille. Marie la trie, la filtre et choisit ses colonnes ;
    - sous la table, le **panneau d'écoute** pleine largeur de la ligne sélectionnée : sonogramme et spectrogramme.
4. Marie sélectionne une observation. La séquence se charge et se joue aussitôt, ralentie dix fois pour devenir audible ; la fenêtre du cri est surlignée, et la lecture s'y positionne. Elle peut agrandir le temps ou la fréquence du **sonogramme** et du **spectrogramme**, opération très fréquente en analyse acoustique, et rejouer en boucle.
5. Marie écoute, regarde, décide :
    - si le taxon Tadarida lui semble correct, elle **valide** d'un geste (`taxon observateur = taxon Tadarida`, R15) ;
    - sinon, elle **corrige** en retenant un autre taxon dans la liste. L'observation passe au statut « Corrigée » (R16) ;
    - elle déclare sa **certitude** (Sûr, Probable, Possible) : c'est la « Confiance observateur » du portail, jamais déduite d'une probabilité ;
    - elle peut marquer l'observation **douteuse** pour y revenir, ou la garder comme **son de référence** ;
    - elle peut ajouter un commentaire libre (« pic 39 kHz, morphologie atypique ») ;
    - en cas de doute, elle **consulte la fiche de l'espèce** proposée (double-clic sur la ligne) : critères acoustiques et répartition s'ouvrent dans son navigateur, sur la fiche du **Plan National d'Actions Chiroptères** pour une chauve-souris, sur une source universelle par nom scientifique sinon. C'est la **troisième source de preuve**, à côté du son et du spectrogramme, et elle est nécessaire précisément parce que la probabilité Tadarida ne tranche pas (voir les notes ci-dessous).

    Ces décisions sont toutes atteignables **depuis la ligne elle-même** (clic droit) et au clavier, sans remonter aux boutons ni au menu de l'écran : la revue se fait au fil de la liste, là où l'œil et le curseur se trouvent déjà. Plusieurs lignes sélectionnées se valident ou se corrigent d'un coup.

6. Marie peut **filtrer** par statut, par taxon parent (« chauves-souris uniquement »), par espèce, par certitude, par lieu, par seuil de probabilité, par plage horaire, et ne garder que les espèces à enjeu, les douteuses, les références ou les séquences sans proposition.
7. Elle peut **quitter et reprendre plus tard** : chaque décision est enregistrée au fil de l'eau, le statut « À revoir » dit ce qui reste, et une touche saute à la prochaine observation à revoir. Le tri et les filtres tiennent le temps de la session ; une vue sauvegardée les retrouve d'un jour sur l'autre. La validation peut s'étaler sur plusieurs jours sans rien perdre.
8. Une fois la revue terminée, ☰ ▸ « **Publier les corrections vers Vigie-Chiro…** » pousse ses décisions vers la plateforme, taxon retenu et certitude, après un récapitulatif de ce qui part et de ce qui reste à quai. En repli, elle exporte le **fichier de résultats validés** (`*_Vu.csv`) et le téléverse sur le portail.

## Notes importantes

- **Les probabilités Tadarida ne sont pas fiables** au sens strict : il arrive régulièrement qu'une observation à 99 % soit fausse et qu'une observation à 20 % soit correcte. La probabilité reste une **heuristique de tri** utile, mais pas un raccourci de validation automatique.
- **Deux modes de validation coexistent** (R18) :
    - **Mode inventaire** : Marie cherche juste à savoir quelles espèces sont présentes sur son site. Une fois une espèce validée avec confiance sur une nuit, les autres détections de la même espèce sur la même nuit, celles qu'elle n'a pas encore touchées, reçoivent la même décision automatiquement : elle n'a plus à les valider une à une, et chacune garde la trace d'avoir été propagée.
    - **Mode activité** : Samuel cherche à quantifier l'activité. Toutes les observations doivent être passées en revue pour produire des statistiques d'activité fiables.

  Le mode se choisit sur l'écran, à tout moment de la revue : « Activité (une par une) » ou « Inventaire (propage l'espèce) ». Une action sur plusieurs lignes s'enregistre toujours en mode activité : propager n'aurait pas de sens sur une sélection hétérogène.

## Ce que la revue a gagné

Les deux enrichissements annoncés ici sont **livrés** (EPIC #2348).

- **Les espèces à enjeu se distinguent dans la table.** Un repère de ligne, un critère de filtre et un compteur dédié permettent d'aller droit aux observations qui comptent, au lieu de les retrouver une par une dans plusieurs milliers de contacts (#2353).
- **Le mode activité est devenu mesurable.** Le parcours distinguait déjà un mode *inventaire* et un mode *activité*, sans que le second ait de restitution. La [synthèse de la nuit](../Maquettes/M-Synthese.md) lui en donne une, et sa bascule « identifications validées seulement » fait apparaître ce que la validation a changé (#2351).

## Le geste qui manque : valider au genre

!!! warning "Cible non livrée"
    Décrit un chantier ouvert. Son état se lit sur l'issue **#3844**, qui se ferme quand il est livré.

Une partie des cris ne permet pas de conclure à l'espèce. Le cas courant est le couple *Plecotus
auritus* / *austriacus*, et il se présente aussi chez les murins et les pipistrelles. Le parcours
laisse aujourd'hui trois gestes, dont aucun ne dit ce que l'observateur sait réellement :

| Ce qu'il peut faire | Ce que la donnée devient |
|---|---|
| choisir une des deux espèces | une identification qui a une chance sur deux d'être fausse |
| poser l'espèce en certitude « Possible » | l'espèce reste affirmée ; la certitude nuance le propos, elle ne le change pas |
| ne rien poser | un contact qui ne remonte pas, alors qu'il est identifiable au genre |

Le protocole accepte pourtant les identifications au niveau du genre, et le référentiel embarqué porte
les codes correspondants (`Plesp`, `Myosp`, `Pipsp`) au même titre que les codes d'espèce. Un
observateur peut donc déjà en poser un : il lui faut connaître le code par cœur et le retrouver dans
une liste de plusieurs centaines d'entrées qui ne distingue pas visuellement un genre d'une espèce.

Le geste attendu est un contrôle à côté du sélecteur de taxon, qui propose le code de genre et ne
propose **rien** quand il n'y a rien à proposer : un genre monospécifique en métropole, ou un code qui
est déjà un genre. La table
[C15 - Groupe taxonomique](../Modele%20conceptuel/C15%20-%20Groupe%20taxonomique.md) porte déjà le niveau
« Genre » et sert de filtre groupé ; l'ossature existe, elle n'est pas reliée à la correction.
