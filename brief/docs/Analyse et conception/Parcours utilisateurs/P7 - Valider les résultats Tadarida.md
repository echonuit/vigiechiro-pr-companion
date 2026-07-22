# P7 - Valider les résultats Tadarida ✅

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Persona principal** : Marie / Samuel. **Objectifs qualité visés** : [O4 Exactitude lecture audio](../../Objectifs%20qualités/Objectifs%20qualités/O4.md), [O7 Intégrité](../../Objectifs%20qualités/Objectifs%20qualités/O7.md).

24-48 h après le dépôt sur Vigie-Chiro (parcours [P4](P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)), Tadarida a analysé les séquences d'écoute et restitué un fichier de **résultats d'identification** (CSV listant les espèces détectées dans chaque séquence, avec leur probabilité). Marie veut **passer en revue ces résultats** pour valider ou corriger les classifications avant que les données ne soient consolidées dans la base nationale.

1. Marie télécharge le fichier de résultats depuis le portail Vigie-Chiro et le sauvegarde sur son disque.
2. Dans l'application, elle ouvre la fiche du passage concerné et clique sur « **Importer les résultats Tadarida** ». Elle pointe sur le fichier téléchargé.
3. L'application parse le fichier (formats `Brut` ou `Vu` reconnus, R17), associe chaque ligne à la séquence d'écoute correspondante en base, et affiche la **vue de validation** :
    - liste des observations à gauche (triable par séquence, taxon Tadarida, probabilité, statut)
    - panneau de détail à droite : taxon proposé, probabilité, fréquence médiane, lecteur audio pour la séquence, bouton de validation
4. Marie sélectionne une observation. La séquence d'écoute associée se charge dans le lecteur (déjà ralentie ×10, lecture immédiate). Elle peut aussi visualiser la **forme d'onde** et un **spectrogramme** (avec **zoom variable**, opération très fréquente en analyse acoustique).
5. Marie écoute, regarde, décide :
    - si le taxon Tadarida lui semble correct, elle valide en un clic (`taxon observateur = taxon Tadarida`, R15)
    - sinon, elle saisit un autre taxon dans le sélecteur. L'observation passe en statut `corrigée` (R16)
    - elle peut ajouter un commentaire libre (« pic 39 kHz, morphologie atypique »)
    - en cas de doute, elle **consulte la fiche de l'espèce** proposée (double-clic sur la ligne) : critères acoustiques et répartition s'ouvrent dans son navigateur, sur la fiche du **Plan National d'Actions Chiroptères** pour une chauve-souris, sur une source universelle par nom scientifique sinon. C'est la **troisième source de preuve**, à côté du son et du spectrogramme, et elle est nécessaire précisément parce que la probabilité Tadarida ne tranche pas (voir les notes ci-dessous)

    Ces décisions sont toutes atteignables **depuis la ligne elle-même** (clic droit), sans remonter aux boutons ni au menu de l'écran : la revue se fait au fil de la liste, là où l'œil et le curseur se trouvent déjà.
6. Marie peut **filtrer** par taxon, par groupe taxonomique (« toutes les pipistrelles », « tous les murins »), par seuil de probabilité, par plage horaire.
7. Elle peut **quitter et reprendre plus tard** : son contexte (dernière observation vue, filtres actifs) est restauré. La validation peut s'étaler sur plusieurs jours sans rien perdre.
8. Une fois la revue terminée, Marie exporte le **fichier de résultats validés** (`*_Vu.csv`) et le téléverse sur Vigie-Chiro pour finaliser sa contribution.

## Notes importantes

- **Les probabilités Tadarida ne sont pas fiables** au sens strict : il arrive régulièrement qu'une observation à 99 % soit fausse et qu'une observation à 20 % soit correcte. La probabilité reste une **heuristique de tri** utile, mais pas un raccourci de validation automatique.
- **Deux modes de validation coexistent** (R18) :
    - **Mode inventaire** : Marie cherche juste à savoir quelles espèces sont présentes sur son site. Une fois une espèce validée avec confiance sur une nuit, les autres détections de la même espèce sur la même nuit ne sont plus validées.
    - **Mode activité** : Samuel cherche à quantifier l'activité. Toutes les observations doivent être passées en revue pour produire des statistiques d'activité fiables.

  L'utilisateur choisit le mode au démarrage du parcours (configurable par passage).

## Enrichissements prévus

> Ces évolutions sont **décidées et maquettées, pas encore livrées**. Elles prolongent ce parcours sans en modifier les étapes actuelles.

- **Les espèces à enjeu se distinguent dans la table.** Un repère de ligne, un critère de filtre et un compteur dédié permettent d'aller droit aux observations qui comptent, au lieu de les retrouver une par une dans plusieurs milliers de contacts (#2353).
- **Le mode activité devient mesurable.** Le parcours distingue déjà un mode *inventaire* et un mode *activité* ; ce second mode n'a aujourd'hui aucune restitution. La [synthèse de la nuit](../Maquettes/M-Synthese.md) lui en donne une, et sa bascule « identifications validées seulement » fait apparaître ce que la validation a changé (#2351).
