# P2 - Importer une nuit d'enregistrement 📥

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Marie / Karim / Samuel. **Objectifs qualité visés** : [O3 Tenue dans la durée](../../Objectifs%20qualités/Objectifs%20qualités/O3.md) (jusqu'à 40 Go), [O7 Intégrité](../../Objectifs%20qualités/Objectifs%20qualités/O7.md), [O8 Confidentialité](../../Objectifs%20qualités/Objectifs%20qualités/O8.md).

Marie vient de récupérer la carte SD de son enregistreur après une nuit d'enregistrement. Elle veut importer cette nuit dans l'application. Le PR a déposé sur la SD un journal du capteur (`LogPR<n>.txt`), un relevé climatique (`PaRecPR<sn>_THLog.csv`) et plusieurs centaines de fichiers d'enregistrement.

1. Marie ouvre l'application. Elle clique sur « **Importer une nuit** » depuis la vue des sites (parcours [P1](P1%20-%20Déclarer%20un%20site%20de%20suivi.md)) ou directement depuis la barre principale.
2. Elle pointe sur le **dossier source** (sélecteur ou glisser-déposer sur la modale). L'application **inspecte le dossier** sans rien y écrire et affiche un premier récapitulatif :
    - journal du capteur détecté ✅, **n° de série de l'enregistreur** extrait
    - relevé climatique détecté ✅ (ou non, signalé)
    - N enregistrements originaux détectés, taille totale, plage horaire couverte
    - paramètres d'acquisition (Fe, gain, bande de fréquence) extraits du journal du capteur
    - **état du nommage** des fichiers : sans préfixe (cas neuf), tous déjà préfixés `CarXXXXXX-AAAA-PassN-YY-` (cas d'un dossier déjà passé chez LupasRename ou d'un re-import), ou mélangé (cas d'un import précédent partiellement corrompu).
3. La modale d'import demande à Marie de **rattacher la nuit à un site, un point, une année et un n° de passage**. Le pré-remplissage de ces 4 champs dépend de l'état du nommage :
    - **Cas « sans préfixe » (le plus courant)** :
        - **Site et point** : combobox parmi les sites déclarés (parcours [P1](P1%20-%20Déclarer%20un%20site%20de%20suivi.md)). Si l'enregistreur (n° de série) a déjà été utilisé sur un site/point auparavant, la **dernière association connue est présélectionnée** (modifiable). Si aucune association ni aucun site n'existe, un raccourci « **+ Créer un nouveau site** » ouvre le formulaire de [P1](P1%20-%20Déclarer%20un%20site%20de%20suivi.md) à la volée puis revient à la modale d'import.
        - **Année** : préremplie avec l'année courante.
        - **N° de passage** : pré-rempli en **auto-incrément** sur la base des passages déjà enregistrés pour ce point cette année (max + 1, ou 1 si aucun). **Modifiable librement** par Marie si elle souhaite saisir une autre valeur (cas d'un protocole non standard ou d'un rattrapage).
    - **Cas « déjà préfixés »** (re-import ou dossier déjà passé chez LupasRename) : l'application **extrait le quadruplet `(carré, année, n° passage, point)` directement du préfixe** présent sur les fichiers et le présélectionne. Si le carré ou le point ne correspond à aucun site déclaré, le raccourci « + Créer un nouveau site » est proposé. Marie peut valider tel quel, ou modifier un champ ; dans ce dernier cas, l'application détecte l'**incohérence préfixe ↔ saisie** et propose, après confirmation, de **réaligner les noms de fichiers sur la saisie** (re-renommage de tous les fichiers à l'étape 4).

    *Cas dégradé : si le dossier mélange fichiers préfixés et non préfixés, l'application bloque l'import avec le message « Le dossier contient un mélange de fichiers nommés et non nommés. Nettoyez le dossier puis réessayez ».*
4. Marie valide « Importer ». L'application **copie de manière protégée** tous les fichiers depuis la SD vers son espace de travail (R9 : aucune écriture sur les originaux SD). Les fichiers copiés **reçoivent ou conservent** le préfixe `CarXXXXXX-AAAA-PassN-YY-` (R6, R7, R8).
5. Une fois la copie terminée, l'application **transforme** automatiquement chaque enregistrement original en séquences d'écoute (découpage en tranches de 5 s réelles, puis expansion ×10, R10). Une barre de progression détaillée informe Marie de l'étape en cours et de l'étape suivante.
6. À la fin, l'application affiche un récapitulatif : nombre de séquences d'écoute produites, durée totale enregistrée, durée écoulée. Le passage est créé en base avec le statut `Transformé`. L'**association enregistreur → site/point** est mémorisée pour faciliter les imports suivants. Marie est invitée à enchaîner sur la **vérification d'enregistrement** (parcours [P3](P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md)).

## Notes importantes

- **Aucun fichier n'est touché tant que le quadruplet `(carré, année, n° de passage, point)` n'est pas confirmé** par l'utilisateur à l'étape 3. L'inspection initiale du dossier source (étape 2) est en lecture seule. Si Marie change d'avis avant de cliquer « Importer », rien ne s'est passé sur disque.
- Une fois la copie effectuée et les fichiers renommés, **changer rétroactivement** le n° de passage ou le point d'un passage déjà importé est possible mais déclenche un **re-renommage** de tous les fichiers du passage (à valider explicitement par l'utilisateur via une action dédiée « Modifier le rattachement »).
- L'application doit **tenir des nuits jusqu'à 40 Go** sans freezer l'IHM (cas Samuel en haute saison). L'import et la transformation se font de préférence en arrière-plan, avec possibilité de fermer la fenêtre de progression sans annuler l'opération.
- Si l'utilisateur lance un import alors qu'un autre est en cours, l'application le met en file d'attente plutôt que de refuser ou de paralléliser (préservation des perfs).
- Les **identifiants observateur et participation** présents dans les noms de fichiers ou les CSV sont conservés en local mais ne sont jamais transmis à un service distant (R8 implicite, [SC2](../../Objectifs%20qualités/Scénario/SC2.md)).

## Enrichissements prévus

> Ces évolutions sont **décidées et maquettées, pas encore livrées**. Elles prolongent ce parcours sans en modifier les étapes actuelles.

- **La fin d'import rend des comptes en chiffres.** Le rapport final énumère aujourd'hui ce qui est passé et ce qui a été écarté ; le [compte rendu chiffré](../Maquettes/M-CompteRendu.md) le restitue en proportions (part importée, part ignorée, part rejetée avec ses motifs), annonce le volume écrit sur le disque, et se termine par l'action suivante plutôt que par un acquittement (#2358).
