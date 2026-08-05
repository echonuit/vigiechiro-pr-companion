# Interface en ligne de commande (CLI)

À côté de l'IHM JavaFX, VigieChiro Companion expose un **point d'entrée sans interface graphique** :
[`fr.univ_amu.iut.cli.Cli`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/Cli.java).
Il répond au besoin de **scriptabilité** (parcours A10 : enchaîner des imports/exports sans clics,
pour les utilisateurs avancés). La CLI **n'a pas de logique propre** : elle orchestre les **services
métier existants** (`ServiceImport`, `ServiceLot`, `ServiceValidation`, DAO multi-features).

!!! abstract "Principe : réutiliser, pas réimplémenter"
    La CLI et l'IHM sont **deux façades** sur le même cœur métier. Tout ce que fait la ligne de
    commande, l'application graphique le fait aussi, *via les mêmes services*. C'est l'intérêt d'avoir
    isolé le métier des vues (cf. [Architecture](architecture.md)) : on peut lui greffer une seconde
    surface sans le dupliquer.

## Comment elle s'assemble (injecteur enfant)

La CLI a besoin de **tout le graphe applicatif** (socle + features) **plus** quelques aides de lecture
qui lui sont propres. Plutôt que de modifier la composition racine, elle crée un **injecteur enfant** :

```java
RacineInjecteur.creer().createChildInjector(new CliModule());
```

L'enfant **hérite** de tous les bindings du socle et des features (dont les services et DAO), et y
**ajoute** les aides CLI sans rien retirer ni remplacer. C'est le patron *injecteur enfant* de Guice,
détaillé dans [Injection (Guice)](injection.md).

```mermaid
flowchart LR
    main["Cli.main()"] --> child
    subgraph child["Injecteur enfant"]
        CliModule["CliModule<br/>(RegistrePassages)"]
    end
    child -.hérite de.-> root["RacineInjecteur.creer()<br/>socle + features"]
    child --> svc["ServiceImport · ServiceLot<br/>ServiceValidation · DAO"]
```

[`CliModule`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/di/CliModule.java)
n'apporte qu'une chose :
[`RegistrePassages`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/model/RegistrePassages.java),
une **lecture transverse** qui croise les DAO de `passage` et `sites` pour reconstituer le contexte
« carré / point » de chaque passage. La dépendance va `cli → <feature>.model.dao` (jamais vers une
`view`/`viewmodel`) : c'est l'unique entorse autorisée par la règle ArchUnit assouplie, et `cli` reste
un **puits** (aucune feature ne dépend de lui), donc le graphe reste acyclique.

## Les sous-commandes

!!! note "Ce tableau est tenu par un test"
    `DocumentationAJourTest` confronte ce tableau aux sous-commandes **réellement câblées** dans
    l'annotation `@Command` de `CommandeRacine` : ajouter une commande sans lui écrire sa ligne **fait
    rougir la CI** (#1458). Le tableau a compté jusqu'à **22 commandes sur 29** avant qu'on s'en aperçoive.

| Commande | Options | Parcours | Service |
|---|---|---|---|
| `creer-site` | `--carre <n> [--nom ..] [--protocole ..] [--commentaire ..]` | A10 | `ServiceSites.creerSite` |
| `ajouter-point` | `--site <id> --code <c> [--lat ..] [--lon ..] [--description ..]` | A10 | `ServiceSites.ajouterPoint` |
| `lister-sites` | `[--json]` | A10 | `ServiceSites` (lecture) |
| `lister-sites-vigiechiro` | `[--portee mes\|plateforme] [--pages <n> \| --tout] [--point <code>] [--carre <n>] [--recenser] [--json] [--token <jeton>]` | #3003 | `ClientVigieChiro.sitesPlateforme` / `ClientVigieChiro.mesSites`. Interroge le **catalogue de la plateforme** (20 517 sites) ou vos sites. `--recenser` compte les sites par **code de point** : c'est ainsi qu'on établit qu'un code comme `Z1` est porté par des centaines de carrés (#2993). Chaque sortie porte son **dénominateur** (« 300 lus sur 20517 annoncés, 3 pages sur 206 ») et signale les sites **sans point ponctuel** (transects routiers, dont les localités sont des lignes) : un échantillon ne doit jamais passer pour un recensement. Les filtres sont appliqués **chez nous**, ce backend ignorant `where=` en silence |
| `lister-participations-vigiechiro` | `[--json] [--token <jeton>]` | #3005 | `ClientVigieChiro.mesParticipations`. Vos nuits déposées, **avec leur identifiant** : c'est lui que réclament `importer-vigiechiro --participation` et `reconstruire-passage --participation`, et qu'aucune commande ne donnait. `reconstruire-passage` sans argument ne liste que les participations **orphelines** ; une nuit déjà rattachée n'apparaissait nulle part |
| `api` | `lire --chemin <chemin> [--page <n>]`, `ressources [--sonder]` | #3006 | **Interrogation brute de l'API, en lecture seule.** Groupe volontairement discret : ses sous-commandes ne sont pas détaillées ici (elles parlent le langage de l'API, pas celui du produit) - le détail vit dans [api-vigiechiro.md](api-vigiechiro.md). `lire` refuse **avant d'émettre** les deux pièges qui ne préviennent pas (`max_results` au-delà de 100, `where=` que le serveur ignore) ; `ressources` affiche la carte des lectures et sait la confronter au serveur. Aucune écriture : [ADR 3006](decisions/3006-le-groupe-api-est-borne.md) |
| `creer-campagne` | `--nom <n> [--annee N] [--commentaire ..]` | #2355 | `ServiceCampagne.creerCampagne` : crée une campagne (regroupement **facultatif** de passages). Année par défaut = année courante. Feature `campagne` (désactivable) |
| `lister-campagnes` | `[--json]` | #2355 | `ServiceCampagne` (lecture) : les campagnes de suivi, de la plus récente à la plus ancienne |
| `modifier-campagne` | `--campagne <id> --nom <n> --annee N [--commentaire ..]` | #2355 | `ServiceCampagne.modifierCampagne` : corrige nom, année et commentaire. Sans elle, corriger une campagne obligeait à la supprimer, donc à détacher tous ses passages |
| `rattacher-campagne` | `--passage <id> [--campagne <id>]` | #2355 | `ServiceCampagne.rattacherPassage` : rattache un passage à une campagne, ou l'en **détache** si `--campagne` est omis. Supprimer la campagne détache aussi (`ON DELETE SET NULL`) |
| `supprimer-campagne` | `--campagne <id>` | #2355 | `ServiceCampagne.supprimerCampagne` : supprime le **regroupement**, jamais les nuits, les passages rattachés sont détachés (`ON DELETE SET NULL`) |
| `lister-passages` | `[--carre <n>] [--lieu <texte>…] [--annee <a>] [--statut <s>] [--verdict <v>] [--analyse <e>] [--campagne <nom>] [--json]` | P5, #3269 | `RegistrePassages` (lecture). Les **sept** filtres sont ceux de l'écran « Carte & passages » et passent par le **même prédicat** (`FiltresMultisite`, un record dont `accepte` ignore les critères non renseignés) ; `--lieu` lit `FiltresLieu`, comme `lister-observations`. Aucune règle n'est réécrite. ⚠️ `--analyse` porte un état **déduit** (`EtatAnalyse.deduire`) et non lu : « Déposé » sans relevé vaut `JAMAIS_RELEVE`, « Importé » vaut `SANS_OBJET`. Une base sans aucun passage le dit **avant** tout filtrage : sinon `--lieu` refuserait le lieu (ADR 3082) là où la vérité est qu'il n'y a rien. Un filtre qui ne retient rien le **dit** aussi |
| `solde-saison` | `[--annee N] [--campagne <nom>] [--lieu <texte>] [--reste-a-faire] [--format texte\|csv\|json]` | #2356 | `ServiceSoldeSaison` (lecture) : ce qu'il reste à faire, point par point, pour une saison (règles R3/R4 restituées). `--lieu` et `--reste-a-faire` sont les **deux filtres de l'écran** « Ma saison » (#3103), portés ici à la clôture de #3092 et lus sur la même écriture (`FiltresSaison`). Ils ne touchent **que la liste des points** : l'en-tête continue d'annoncer le solde de la **saison entière**, comme à l'écran. Un filtre qui ne retient rien le **dit**. Même décompte que l'écran « Ma saison » (service partagé, parité IHM/CLI). `texte` par défaut, `csv`/`json` scriptables. Les trois sorties **montrent le nom du carré** (#3289), par lequel `--lieu` sait déjà chercher : le texte le qualifie (`640380 · Vallon`), le CSV a sa colonne « Nom du carré », le JSON sa clé `nom_site`. L'écran, lui, lui donne une **colonne** : qualifié dans « Carré », le nom s'y faisait tronquer. La **commune** suit le même chemin (#3313) : colonne à l'écran, `Commune` au CSV, `commune` en JSON, entre parenthèses en texte, et `--lieu` la retient |
| `statut-passage` | `--passage <id> [--json]` | M-Passage, #1878 | `ServicePassage.detailPassage` + `ResultatsIdentificationDao` + `ServiceConditionsPassage.heuresProuvees` (lecture). La ligne « Nuit » dit l'**origine** de ses heures - `[attestées par les enregistrements]` ou `[déclarées, modifiables]` -, et le JSON porte `heuresProuvees` : un script sait ainsi **avant** d'essayer si `metadonnees-passage --heure-debut` sera refusé, au lieu de l'apprendre en échouant |
| `verifier-maj` | *(aucune option)* | #2109 | `VerificateurMiseAJour` (lecture seule, réseau) : indique si une version plus récente est publiée. Pendant CLI de l'annonce au démarrage de l'IHM. **Trois codes de sortie**, parce qu'un script ne pilote pas « à jour » et « je n'ai pas pu savoir » de la même façon : `0` à jour, `10` mise à jour disponible, `1` vérification impossible (hors ligne, ou version locale inconnue car lancée hors d'un artefact publié). Se tait dès qu'un doute existe plutôt que d'annoncer à tort ; désactivable avec la feature `maj` |
| `diagnostiquer` | `--passage <id> [--json] [--csv <serie\|anomalies>]` | P6, #1672 | `ServiceDiagnostic.diagnostiquer` (lecture seule) : bilan matériel d'une nuit (climat R20, T° début nuit, cohérence horaire #548, GPS du point, anomalies/évènements R19). Parité CLI de M-Diagnostic. `--csv` exporte série ou anomalies via `ExportDiagnostic` (P6-CA6) |
| `importer` | `--source <chemin> --point <id> [--annee N] [--passage N] [--conserver-originaux \| --sans-originaux] [--ecraser]` | P2 | `ServiceImport`. Le mode de conservation suit le **réglage** `import.conserver-originaux` (par défaut : **pas de copie**) ; les deux options le forcent pour un import ponctuel et s'excluent. Auparavant la commande conservait **en dur**, quel que soit le réglage (#2064). La sortie rapporte aussi le **doublon de nuit** et les **anomalies du journal** : l'écran les montre depuis #2044, la commande les taisait (#2004). **Collision de numéro** (#2278) : la perte est chiffrée (séquences, validations Tadarida) et, **sans `--ecraser`, rien n'est importé**, sortie `2`. Avec, `ServiceImport.ecraserEtImporter` sauvegarde d'abord, puis remplace la nuit existante. **Parité #2350** : la sortie dit aussi les **volumes lus / écrits** (#2358, les mêmes chiffres que la bande de l'écran, sans les barres) et la **participation Vigie-Chiro créée** (#1488), une écriture distante ne se découvre pas ailleurs. **Parité #3195** : `--source` accepte aussi une **archive .zip**, décompressée sous le dossier de travail (jamais sous `/tmp`, souvent un tmpfs en RAM) puis effacée, refus compris ; les bornes de ressources de #2732 valent donc aussi pour la ligne de commande, et un refus y sort en `2` |
| `importer-transformes` | `--dossier <dir> --point <id> [--annee N] [--passage N] [--referencer]` | #2433 (EPIC #2258) | `ServiceImportReference`. Crée un passage à partir d'un dossier de séquences **déjà transformées**, sans rejouer la transformation : l'original est un **placeholder** (pas de brut), l'identité (empreinte + taille) est calculée à l'inscription pour que la réactivation la revérifie au réveil. Par défaut **copie** les WAV dans l'espace de travail ; **`--referencer`** les laisse en place, la base pointe l'emplacement externe et aucun octet audio n'est recopié ([ADR 2433](decisions/2433-un-dossier-de-transformes-s-importe-par-reference.md)). Série et date **déduites des noms** de fichiers. Refus métier (dossier introuvable, aucun WAV, point inconnu, quadruplet déjà pris) en sortie `2`, rien n'est créé, l'état est intact |
| `importer-tadarida` | `--passage <id> --csv <fichier> [--remplacer]` | P6 | `ServiceValidation.importer` / `reimporter` |
| `qualifier` | `--passage <id> --verdict <ok\|utilisable\|inexploitable> [--commentaire ..]` | R13 | `ServicePassage.poserVerdict` (alias `douteux`/`a-jeter` rétro-compatibles) |
| `qualifier-fichier` | `--passage <id> --sequence <id> --verdict <bon\|mauvais\|inexploitable>` | #1512 | Verdict **par fichier** d'une séquence de la sélection d'écoute ; recalcule le verdict final proposé. Parité CLI de M-Qualification |
| `lister-selection` | `--passage <id> [--json]` | #1512 | Sélection d'écoute d'un passage : **verdict par fichier** de chaque séquence + **verdict final proposé** (dérivé). Parité CLI de M-Qualification, lecture seule |
| `pre-check` | `--passage <id> [--json]` | #1512 | **Pré-check consultatif** d'une nuit (3 feux : couverture horaire, nombre de fichiers, renommage) + résumé des anomalies. Parité CLI de M-Qualification, lecture seule, jamais bloquant (R13) |
| `constituer-selection` | `--passage <id> [--methode <reparti\|aleatoire\|manuel>] [--taille <n>]` | R12 | (Re)constitue la sélection d'écoute (échantillon à écouter). Remplace la sélection existante et **efface** ses verdicts. Parité CLI de « Personnaliser… » / « Régénérer » |
| `exporter-lot` | `--passage <id>` | P4 | `ServiceLot` |
| `deposer` | `--passage <id>` | P8 | `ServiceLot.preparerLot` + `marquerDepose` (marquage **manuel**) |
| `recuperer-vigiechiro` | `[--token <jeton>]` | #1181, #1866 | rejoue les `RapprochementVigieChiro` (taxons, sites/points) après un `GET /moi` de contrôle ; `0` ssi connecté. Le geste ne fait que **recevoir**, d'où le verbe ([ADR 0022](decisions/0022-le-verbe-dit-le-sens-de-l-echange.md)) ; **alias** `synchroniser-vigiechiro`, conservé pour les scripts |
| `deposer-vigiechiro` | `--passage <id> [--token <jeton>] [--archives\|--wav]` | #1043 | `DepotVigieChiro.deposer` (moteur **reprenable** #982, téléversement **parallèle** #984). Défaut = `ChoixSourceDepot.pour`, **le même choix que M-Lot** : ZIP si présentes, sinon invite à les générer (étape 2), sinon repli WAV. `--archives`/`--wav` forcent l'un ou l'autre. **Parité #2350** : depuis le réessai gradué (#2354) une coupure momentanée n'échoue plus, elle **attend** ; la ligne `~ <unité>, nouvelle tentative dans N s` le dit, là où le silence faisait passer la temporisation pour un blocage |
| `lancer-traitement-vigiechiro` | `--passage <id> [--token <jeton>] [--forcer]` | #984, #1261, #1265 | `DepotVigieChiro.lancerTraitement` (`POST /participations/{id}/compute`), équivalent du bouton « Lancer la participation ». `0` **dès lors que le traitement est en route** (accepté **ou** déjà en cours : la commande est idempotente), `1` si le serveur a refusé la relance, `2` pour un refus métier en amont (dépôt indisponible, aucune participation liée au passage, rendu par le handler central, #2294). Une nuit **déjà analysée n'est pas relancée** : le serveur détruirait ses observations pour les recalculer, sans pouvoir les régénérer (audio absent d'un dépôt en archives, #1244), `--forcer` lève cette garde, typiquement après un échec |
| `etat-traitement-vigiechiro` | `--passage <id> [--token <jeton>]` | #1265 | `SuiviTraitement.relever` (lecture seule : `GET /participations/{id}` → bloc `traitement`, et **mise à jour du cache local** #1262). Codes **faits pour un script** : `0` terminé, `3` planifié/en cours/nouvel essai, `1` en échec côté serveur, `4` jamais lancé, `2` **indisponible** (on n'a pas pu demander : nuit non déposée, jeton absent, plateforme injoignable, refus métier rendu par le handler central, #2294) |
| `traiter-passages` | `--action <preparer-depot\|televerser\|importer-resultats\|declencher-calcul> --passage <id> [--passage <id>…] [--json]` | #2357 | `MoteurTraitementGroupe` sur les quatre [`ActionGroupee`](patterns.md), équivalent du menu « Traiter la sélection » de « Carte & passages ». Ce qu'une boucle shell ne sait pas faire : **savoir à l'avance lesquels sont éligibles** (les règles lisent l'état du dépôt, le rattachement et la présence de résultats, rien de tout cela n'étant dans `lister-passages`). Les écartés sont annoncés avec leur motif **sans être tentés**, un échec **n'arrête pas** les suivants, chaque passage a sa ligne. **Ni `--forcer` ni `--remplacer`** : les actions groupées ne les exposent pas, un recalcul détruisant les observations d'un dépôt en archives et un remplacement touchant à ce que l'observateur a validé, vingt d'un coup ne serait pas un service. `0` si aucun échec (les **écartés** en font partie : rejouer un lot déjà traité est le cas **idempotent**, comme `lancer-traitement-vigiechiro` sur « déjà en cours »), `1` si au moins un passage a échoué, `2` pour un refus en amont (fonctionnalité désactivée, identifiant inconnu) |
| `reinitialiser-depot` | `--passage <id>` | #984 | `ServiceLot.reinitialiserDepot` (efface le plan `depot_unite`, retour « Prêt à déposer » ; **local**, archives ZIP et lien de participation conservés) : équivalent du bouton « Réinitialiser le dépôt » |
| `supprimer-passage` | `--passage <id> [--confirmer]` | #2278 | `ServicePassage.supprimer`, équivalent du bouton « Supprimer » de la fiche passage. **Destructif** : chiffre d'abord la perte (séquences, validations Tadarida menacées) puis, **sans `--confirmer`, ne touche à rien et sort en `2`**. Un passage **déposé** ou introuvable est refusé par le métier (code `2` : état intact). La cascade s'arrête à la base : les fichiers de la nuit restent sur le disque |
| `verifier-depot-vigiechiro` | `--passage <id> [--token <jeton>]` | #1132 | `VerificationDepot.verifier` (lecture seule : journal de traitement + titres des `donnees` vs plan `depot_unite` ; `0` ssi tout est retrouvé) |
| `importer-vigiechiro` | `--passage <id> [--remplacer] [--participation <objectid>] [--token <jeton>]` | #1181, #1838 | `ImportVigieChiro.importerRapide` (résultats Tadarida depuis l'API ; `--participation` = rattachement préalable). Au **premier import**, prend le **CSV d'observations** d'un coup (#1565) quand la plateforme l'expose, avec **repli** sur la pagination `donnees`. Avec `--remplacer`, reste sur les `donnees` : un ré-import va chercher ce qui a changé côté serveur (avis du validateur, fils), que le CSV effacerait. Le CSV ne porte ni ancrage ni fils de discussion : la **publication** les acquiert ensemble quand elle en a besoin ([ADR 0019](decisions/0019-ancrage-acquis-quand-il-sert.md)) |
| `publier-corrections-vigiechiro` | `--passage <id> [--token <jeton>]` | #723, #1838 | `PublicationCorrections.publier` (un PATCH par observation publiable : taxon + certitude + ancrage ; idempotente ; code `1` si au moins un envoi est refusé par le serveur (échec partiel de publication), code `2` si la publication est indisponible dans ce contexte, fonctionnalité désactivée (refus métier, état intact)). **Acquiert d'abord l'ancrage qui manque** (#1838) quand la nuit est rattachée à une participation : une nuit importée par CSV (#1565) n'en porte pas, ses corrections seraient sinon toutes écartées. Le rapatriement peut durer ; son avancement va sur la **sortie d'erreur**, la sortie standard restant réservée au bilan. Une nuit déjà ancrée n'en paie pas le coût |
| `reactiver` | `--passage <id> --source <dir> [--referencer] [--json]` | #1302, #1406, #1571, #2255 | `ServiceReactivationPassage.reactiver` : rebranche les fichiers retrouvés, **fichier par fichier**, via la [cascade de preuves](patterns.md#cascade-de-preuves-verification-graduee-refuser-plutot-que-se-tromper). Le dossier est **reconnu**, pas déclaré : s'il ne contient que les **bruts**, les séquences sont **régénérées** (transformation déterministe) puis vérifiées comme n'importe quel candidat, la voie empruntée est dite (champ `voie` en JSON). Non destructive et **idempotente**. `0` si l'audio redevient complet, `1` s'il reste partiel (les écarts sont énumérés). Sur un passage **reconstruit** (observations sans ancrage), acquiert en plus l'**ancrage plateforme** par ré-import des `donnees` (#1571) pour rendre les corrections publiables, **le seul cas où `reactiver` touche le réseau**. Avec **`--referencer`** (#2255), **rien n'est copié** : la base pointe les fichiers là où ils vivent (NAS, disque externe, dossier de travail). La nuit devient muette si ce support n'est plus joignable et redevient écoutable quand il revient, l'identité étant revérifiée (#2254). Les tranches **régénérées** depuis les bruts restent copiées : elles sont produites par l'application, pas par l'utilisateur |
| `reconstruire-passage` | `[--participation <objectid>] [--json]` | #1305, #1565 | `ServiceReconstructionPassages` : sans argument, **liste** les participations VigieChiro sans équivalent local (nuits déposées depuis un autre poste, ou avant l'application) ; avec `--participation`, en **reconstruit** une en passage archivé (séquences recréées + observations rapatriées). La reconstruction bascule sur le **CSV d'observations** téléchargé d'un coup (#1565, quasi instantanée), avec **repli** sur la pagination `donnees` si le CSV n'est pas exposé. Les **lacunes** sont imprimées avec le rapport |
| `metadonnees-passage` | `(--passage <id> \| --tout) [--recuperer] [--envoyer] [--enregistreur <serie>] [--heure-debut <HH:mm> --heure-fin <HH:mm>] [--confirmer] [--json]` | #1861 | Parité CLI de la modale « Modifier le passage » : `--recuperer` rapatrie météo/micro/n° de série depuis la participation, `--envoyer` réécrit les métadonnées locales dessus (les heures y sont **réalignées sur les enregistrements**, #1878), `--enregistreur` et les heures écrivent en local sans réseau. **`--tout` est le rattrapage de saison** : sans lui, les correctifs #1814/#1828/#1844 ne réparent que la nuit sur laquelle on repasse. Comme il écrit sur la plateforme, il exige `--confirmer` ; sans, il **énumère ce qu'il ferait**. Best-effort par nuit, compte rendu nuit par nuit, code `1` s'il reste des nuits ignorées |
| `retro-empreintes` | *(aucune option)* | #1299 | `BackfillEmpreintes` : pose les empreintes manquantes sur **toutes** les nuits importées avant V23. Rejouable sans risque (ne touche que ce qui manque) |
| `rattraper-communes` | *(aucune option)* | #2791 | `ServiceCommunes` : comble la commune des points en attente (GPS présent, commune absente) via l'API Géo. Rejouable sans risque ; hors ligne, les points restent en attente |
| `exporter-vu` | `--passage <id> --sortie <fichier>` | P7 | `ServiceValidation` |
| `exporter-observations` | `--passage <id> --sortie <fichier>` | #149 | `ProjectionsAudioDao.lignesAudioDuPassage` + `ExportObservationsCsv` |
| `exporter-sons` | `(--passage <id> \| --espece <code>) --sortie <zip> [--lieu <lieu>]… [--proba-min <0..1>]` | #2795 | `ProjectionsAudioDao.lignesAudioDuPassage` / `lignesAudioDeLEspece` + `ExportObservationsEtSons` (#2792). Parité CLI du geste « Exporter les observations et les sons » de la vue audio (#2793) : archive `observations.csv` + `sons/<session>/<fichier>`, sons introuvables comptés sans bloquer. `--passage` couvre le même sous-ensemble qu'`exporter-observations` (CSV identique) et refuse un passage inconnu (code 2) ; `--espece` couvre l'espèce sur tous les passages de l'utilisateur, tous statuts, et une espèce sans observation produit une archive au CSV d'en-têtes seuls |
| `exporter-activite` | `(--passage <id> \| --tout) --sortie <fichier> [--tranche 15\|30\|60] [--format csv]` | #2352 | `ServiceActivite.contactsDuPassage` + `AgregationActivite.parEspece` + `ExportActiviteCsv`. Facette **données** de la courbe d'activité (pendant CLI de l'export image de l'IHM), rattachée à la nuit biologique. Une ligne par **(carré, point, nuit, espèce, tranche)** : chaque ligne porte son lieu, sans quoi un export couvrant plusieurs nuits ne se recouperait pas (#2613). `--tout` couvre la vue transverse de l'écran. **Non gouvernée par la fonctionnalité `activite-nuit`** : l'agrégation est une capacité stable de `analyse`, celle-ci ne gouvernant que l'accès à la vue |
| `audit-coherence` | `[--passage <id>] [--gravite <niveau>] [--categorie <nature>] [--contient <texte>] [--json] [--online] [--token <jeton>]` | #1133, #1254, #1347 | `ServiceAuditCoherence` : confronte **disque, base et serveur**. `--gravite` et `--categorie` sont les **deux puces** de l'écran « Audit de cohérence » (#3100), portées ici à la clôture de #3092 ; chacune retient une valeur **exacte**. ⚠️ Elles filtrent **l'affichage seul** : le **code de sortie continue de juger le rapport entier**, sans quoi `--gravite INFO` sur un workspace abîmé rendrait `0` et un script d'intégration conclurait que tout va bien. Sans `--passage`, audite tout le workspace ; avec, une seule nuit (utile après l'avoir réparée). `--online` ajoute les constats qui demandent le réseau (dépôts, points). `0` ssi aucun constat d'erreur |
| `sauvegarder` | `[--complet] [--dossier <dir>]` | #148, #1346 | `ServiceSauvegarde` : instantané cohérent de la base (`VACUUM INTO`). `--complet` emporte **aussi l'audio** (dossiers de session), c'est la **seule sauvegarde qui protège vraiment**, la plateforme ne rendant pas l'audio d'un dépôt en archives. Le bilan **dit ce qui n'a pas pu être copié** (carte SD non montée) et sort en `2` : une sauvegarde qu'on croit complète et qui ne l'est pas vaut moins que pas de sauvegarde |
| `restaurer` | `<chemin> [--complet] --confirmer` | #148, #1346 | `ServiceSauvegarde.restaurer` / `restaurerComplet` : remet la base (et, avec `--complet`, les dossiers de son, **remis là où ils étaient**, avec les `root_path` corrigés et un compte rendu de ce qui a changé de place, #2727). **Écrase l'état local** : `--confirmer` est obligatoire. La base courante est mise de côté (`vigiechiro.db.avant-restauration`) |
| `lister-sauvegardes` | `[--dossier <d>] [--json]` | #3197 | `InventaireSauvegardes.lire`. Ce que `sauvegardes/` contient : nom, date, taille, et le **total**. L'application y écrit un filet complet avant **chaque** migration de schéma et n'en supprime jamais aucun - délibérément (ADR 0048 : le filet appartient à l'utilisateur), mais jusqu'ici sans que rien ne dise combien il y en avait ni ce qu'ils pesaient. La commande **observe** : elle ne purge rien et ne conseille rien. Les sauvegardes complètes étant des **dossiers**, leur taille est celle de leur contenu - un inventaire qui ne verrait que les fichiers mentirait là où le chiffre compte |
| `supprimer-sauvegarde` | `--nom <nom> [--dossier <d>] --confirmer` | #3197 | Le ménage que la doc conseille, **explicitement demandé**. Même parade que `supprimer-passage` : sans `--confirmer`, la commande **chiffre la perte et ne touche à rien**, sortie `2`. Un nom inconnu est une **erreur d'usage**, pas un succès silencieux : croire avoir supprimé ce qu'on n'a pas touché est précisément ce qu'on veut éviter |
| `reset-guide` | `[--json] [--executer --confirmer [--accepter-perte] [--sauvegarde <dir>]]` | #1151, #1419 | Sans `--executer` : **lecture seule**, ce que deviendrait l'audio de chaque nuit si l'on repartait d'une base neuve (disque / serveur / **perdu**), code `2` dès qu'une nuit est en « perdu », pour qu'un script puisse refuser d'enchaîner. Avec `--executer` : `ServiceReset` mène la procédure (sauvegarde complète → base neuve → repeuplement depuis VigieChiro → audit final). Il **refuse de démarrer** si la perte n'est pas acceptée, **ou si VigieChiro ne répond pas**, une base neuve qu'on ne peut pas repeupler est une destruction sèche. Dans les deux cas, la base reste **intacte** |
| `synthetiser-passage` | `--passage <id> [--carre ..] [--milieu ..] [--validees-seulement] [--format csv\|json] [--sortie <f>]` | #2351 | **Facette CLI de M-Synthese.** Contacts par espèce et **classe d'activité** au regard du référentiel ACTICHIRO, avec les quantiles retenus. Même `ServiceSynthese` que l'écran : mêmes règles de repli, même résultat, une synthèse qui différerait d'une surface à l'autre serait pire qu'absente. Le **contexte, l'avertissement et la citation** sont écrits en tête du CSV (lignes `#`) et dans un objet `contexte` en JSON : la source est libre d'usage **avec citation obligatoire**, et un avertissement resté à l'écran ne prévient personne qui ouvre le fichier |
| `lister-observations` | `--passage <id> [--statut ..] [--taxon ..] [--douteux] [--reference] [--a-enjeu] [--certitude ..] [--json] [--lieu <lieu>]… [--proba-min <0..1>]` | #1311 | **La surface de découverte de la revue.** Liste les observations d'un passage avec leur **identifiant**, l'avis de Tadarida, le vôtre, celui du validateur, le statut et les drapeaux. Sans elle, les gestes de revue (et `discussion`) sont aveugles : rien ne donnait les identifiants. Ses filtres sont **exactement** ceux des gestes (`SelectionObservations` partagé) : ce qu'elle montre est ce qu'ils toucheraient. `--a-enjeu` (#2353) ne garde que les **espèces prioritaires** du Plan National d'Actions Chiroptères, et le drapeau `enjeu` les signale dans la sortie (champ `aEnjeu` en JSON) |
| `lister-especes` | `[--statut <s>] [--taxon-parent <t>] [--lieu <texte>…] [--nature protocole\|opportuniste] [--a-enjeu] [--format csv\|json] [--sortie <f>]` | #3269 | **Facette CLI de l'inventaire par espèce** d'« Espèces & observations ». Même `AgregationAnalyse.parEspece` que l'écran et **mêmes colonnes que son export** : le tableau ne peut pas dire une chose ici et une autre là. Les cinq critères sont ceux de la barre à puces, mais posés en **fragments tapés** (partiels, insensibles à la casse et aux accents) là où l'écran fait cocher dans une liste : c'est l'écart assumé de `FiltresAnalyse` avec le catalogue de la vue. Le **point** n'est pas filtrable (un code seul désigne autant de lieux qu'il y a de carrés). Une base sans aucune observation se constate **avant** tout filtrage : sinon `--lieu` refuserait le lieu (ADR 3082) là où la vérité est qu'il n'y a rien |
| `lister-carres` | `[mêmes filtres que lister-especes] [--format csv\|json] [--sortie <f>]` | #3269 | **Facette CLI de l'inventaire par carré** du même écran : la **richesse** en espèces et les détections, par carré prospecté. Répond à « où » quand sa jumelle répond à « quoi » - deux commandes plutôt qu'une à `--regrouper`, parce que leurs colonnes n'ont rien en commun et qu'un script ne doit pas avoir à deviner celles qu'il recevra. La richesse suit la sélection, comme à l'écran : `--a-enjeu` donne la richesse **en espèces prioritaires**, `--statut VALIDEE` celle qu'on peut défendre |
| `valider-observations` | `(--observation <ids> \| --passage <id> [filtres] [--confirmer])` | R15, #1311 | **Accepte la proposition de Tadarida**, en lot atomique (mode **Activité** : traite exactement les lignes visées ; le mode *Inventaire*, qui **propage** à d'autres lignes, reste à l'écran - propager dans un script toucherait des lignes que l'utilisateur ne voit pas) |
| `corriger-observations` | `--taxon <code> (--observation <ids> \| --passage <id> [filtres] [--confirmer])` | R16, #1311 | **Retient un autre taxon**, en lot atomique. Le taxon doit exister au référentiel : un code inconnu arrête tout **avant** la moindre écriture |
| `marquer-douteux` | `[--retirer] (--observation <ids> \| --passage <id> [filtres] [--confirmer])` | #160, #1311 | Lève (ou baisse) le drapeau **« douteuse »**. Ce drapeau ne dit **rien** du taxon : il dit « je ne sais pas », une **troisième** réponse qui n'est ni valider ni corriger. Réversible |
| `marquer-reference` | `[--retirer] (--observation <ids> \| --passage <id> [filtres] [--confirmer])` | P10, #1311 | Verse (ou retire) les observations dans la **bibliothèque de sons de référence** - la source `References` de l'écran, et la matière de son export |
| `poser-certitude` | `(--certitude <SUR\|PROBABLE\|POSSIBLE> \| --effacer) (--observation <ids> \| --passage <id> [filtres] [--confirmer])` | #1139, #1311 | Déclare la **certitude observateur**. Il faut **choisir explicitement** : elle ne se déduit **ni** de la probabilité Tadarida **ni** d'une validation, et reste **vide par défaut**. C'est un jugement, que la plateforme exigera avec le taxon (#723) et qu'un naturaliste lira comme la parole de l'observateur |
| `discussion` | `--observation <id> [--message <texte> --confirmer]` | #1417, #1418 | Le **fil d'échange avec le validateur** du MNHN. Sans `--message`, le **lit** (le fil vient de la base, rafraîchi à chaque import). Avec, **y répond**, ⚠️ **écriture définitive** : le serveur ajoute par `$push` et n'offre aucune route de suppression. `--confirmer` est donc obligatoire, et le message n'est écrit localement **qu'après** que le serveur l'a accepté |
| `emplacements` | `[--definir-travail <dir>] [--definir-base <dir>] [--reinitialiser] [--json]` | #1038 | Parité CLI de l'onglet « Emplacements » ([ADR 1038](decisions/1038-la-configuration-d-amorcage-vit-hors-de-la-base.md)) : `ServiceEmplacements`. Sans option, **affiche** où vivent le dossier de travail et la base (et leurs défauts). `--definir-*` **sonde** chaque dossier (un fichier ou un dossier non inscriptible est refusé, code `2` : rien n'est écrit) puis **écrit** le choix ; `--reinitialiser` l'efface. Ne déplace **rien** : change le pointeur lu au prochain démarrage, pas les données - une base pointée vers un dossier vide démarre neuve. `--reinitialiser` et `--definir-*` sont exclusifs (code `2`) |
| `--help` / `-h`, `--version` / `-V`, ou aucun argument | — | — | — |

### Socle : registre de commandes picocli (#614)

Le CLI repose sur **[picocli](https://picocli.info) 4.7.7** : chaque commande est une classe annotée
`@Command` de `cli.commande` (`ListerPassages`, `Importer`, `ExporterLot`, `ExporterVu`) déclarant son nom,
ses `@Option` (types convertis automatiquement) et son aide. La **commande racine**
[`CommandeRacine`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/commande/CommandeRacine.java)
liste les sous-commandes ; l'**aide, l'usage et la liste des commandes sont générés** par picocli (plus de
texte d'aide maintenu à la main). Les commandes restent des **façades** : aucune logique propre, elles
appellent les services.

- **Instanciation par Guice** :
  [`FabriqueGuice`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/FabriqueGuice.java)
  (une `IFactory` picocli) fait **construire chaque commande par l'injecteur**, pour que ses services
  `@Inject` soient fournis ; picocli renseigne ensuite les champs `@Option`. Le module étant un
  `open module`, aucun `opens ... to info.picocli` n'est nécessaire.
- **Migration** : `Cli.executer` migre la base (idempotent) **avant** d'exécuter une sous-commande (pas
  pour l'aide seule), via une `IExecutionStrategy`.
- **Sortie `--json`** : convention uniforme pour les commandes de lecture (scriptabilité), sérialisée par
  [`FormatJson`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/FormatJson.java)
  (écrivain JSON minimal, sans dépendance supplémentaire).
- **Erreurs** : les erreurs de parsing (commande inconnue, argument requis manquant) sont reformulées en
  français et sortent en code `2` ; une
  [`ErreurUsage`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/cli/model/ErreurUsage.java)
  levée dans la logique (ex. point introuvable) sort aussi en `2` ; un **refus métier**
  (`RegleMetierException` ou l'`IllegalArgumentException` des validateurs) sort en `2` (état intact) ; toute
  autre exception (échec inattendu, état incertain) en `1` (message seul, jamais la trace).

### Workspace surchargeable

Comme l'IHM, la CLI travaille dans un **workspace** (qui contient la base `vigiechiro.db`). L'option
globale `--workspace <dir>` est consommée par `main()` **avant** de bâtir l'injecteur (elle positionne
la propriété système `vigiechiro.workspace`, lue par `CommunModule`). Sans elle, le workspace par
défaut est `<Documents>/VigieChiro-Companion`.

### Codes de sortie

| Code | Signification |
|---|---|
| `0` | succès |
| `1` | échec d'exécution : accès aux données, E/S, incident inattendu (état **incertain**) |
| `2` | mauvaise invocation (commande inconnue, argument requis manquant ou mal formé) **ou refus** (règle métier, ou garde destructive : état **intact**, rien n'a été fait) |

**`2` dit aussi « j'ai refusé, je n'ai rien fait ».** Les commandes **destructives** exigent un drapeau
explicite (`--confirmer`, `--ecraser`) : sans lui, elles **chiffrent la perte** et sortent en `2` sans
rien toucher. C'est volontairement **distinct de `1`** : après un `1`, l'état est incertain ; après un
`2`, il est **intact**, et un script peut s'arrêter sans avoir à vérifier quoi que ce soit. Le message
de refus part sur **stderr**, pour ne pas se mêler au compte rendu.

Suivent cette règle : `supprimer-passage`, `importer --ecraser`, `restaurer`, `reset-guide --executer`,
`discussion`, `sauvegarder` (incomplète), `emplacements` (dossier refusé par la sonde, options
exclusives : rien n'est écrit). `restaurer` rendait `1` sur stdout jusqu'à #2294, la
convention n'était écrite nulle part, et c'est ainsi qu'elle a dérivé.

**Les refus de la couche persistance en font partie depuis #3146** : un fichier qui n'est pas une
base, une sauvegarde écrite par une version plus récente, un manifeste abîmé, un dossier de travail
occupé. Tous sont émis **avant** la moindre écriture, et sortaient pourtant en `1`, avec une pile.
Ils portent maintenant `RefusAvantEcriture`, que le gestionnaire d'erreurs classe avec les autres
refus. Une `DataAccessException` ordinaire, elle, reste un incident : sa pile est l'information
utile.

`deposer-vigiechiro` étend la convention : `0` **seulement si le dépôt est complet** ; `1` si des
fichiers restent à reprendre (relancer la même commande ne re-téléverse que les manquants). Le jeton
vient de `--token`, sinon de la variable d'environnement `VIGIECHIRO_TOKEN`, sinon de la **connexion
enregistrée** dans l'application (préférer la variable d'environnement : `--token` laisse le jeton dans
l'historique du shell).

Ces codes rendent le **cycle de dépôt complet scriptable** (#984). Le dépôt **ne déclenche pas** le
traitement serveur : il faut l'appeler explicitement.

```bash
export VIGIECHIRO_TOKEN=…
vigiechiro deposer-vigiechiro --passage 9 \
  && vigiechiro lancer-traitement-vigiechiro --passage 9 \
  && vigiechiro verifier-depot-vigiechiro --passage 9   # après le calcul serveur
```

Le calcul serveur dure des dizaines de minutes (Tadarida tourne sur une ferme de calcul distante).
L'application ne **surveille** jamais la plateforme d'elle-même : le site officiel ne le fait pas
davantage : mais un script, lui, peut l'interroger à son rythme. C'est le rôle des codes de retour
d'`etat-traitement-vigiechiro`, le `3` signifiant « patiente » :

```bash
# attendre la fin du calcul, puis importer les observations
until vigiechiro etat-traitement-vigiechiro --passage 9; [ $? -ne 3 ]; do sleep 300; done
vigiechiro importer-vigiechiro --passage 9
```

En cas de dépôt à refaire de zéro (unités marquées déposées à tort), `reinitialiser-depot --passage 9`
efface le plan local et ramène le passage à « Prêt à déposer » : les archives et la participation sont
conservées, le dépôt suivant re-téléverse tout.

`executer(...)` **ne fait pas** `System.exit` (il *renvoie* le code) : c'est ce qui le rend testable.
Seul `main()` traduit le code en `System.exit`. La base est **migrée au démarrage** (idempotent) avant
toute commande, donc une première invocation crée le schéma si besoin.

## Désigner les observations d'un geste de revue (#1311)

C'était la **vraie difficulté** de la parité : l'écran raisonne par **sélection dans une table**, la ligne
de commande **n'a pas de table**. Les gestes de revue offrent donc **deux** manières de désigner, exclusives
et obligatoires (on ne pose pas un geste sans dire sur quoi), portées par la classe **partagée**
`CiblesRevue` :

```bash
# 1. Chirurgical : sur des lignes qu'on a LUES.
./vigiechiro lister-observations --passage 3 --statut NON_TOUCHEE
./vigiechiro valider-observations --observation 12,13,14

# 2. Scripté : sur un sous-ensemble DÉCRIT, avec les MÊMES filtres que lister-observations.
./vigiechiro corriger-observations --taxon Pippip --passage 3 --taxon-tadarida Pipkuh
```

**La garantie** : c'est le **même** `SelectionObservations` qui choisit pour lister et pour agir. Ce que
`lister-observations --passage 3 --statut NON_TOUCHEE` **montre** est **exactement** ce que
`valider-observations --passage 3 --statut NON_TOUCHEE` **touche**. On regarde, puis on agit - et c'est
mécanique, pas promis.

!!! note "`--proba-min` : l'échelle, et la détection sans probabilité"
    Garde les détections dont la probabilité Tadarida **atteint** le seuil, et **conserve celles qui
    n'en ont pas** : une absence de probabilité n'est pas une mauvaise probabilité, et l'écarter
    perdrait précisément une ligne à revoir. C'est la règle de la puce « Proba », reprise telle quelle.

    **L'échelle est 0 à 1, pas le pourcentage de l'écran.** `lister-observations` imprime déjà
    `probTadarida` brut (`0.74`) : entrée et sortie d'un même appel parlent ainsi la même langue.
    `--proba-min 90`, réflexe du pourcentage, est **refusé** (code 2) avec l'unité rappelée, plutôt que
    borné en silence, ce qui rendrait zéro ligne sans dire pourquoi.

    Un résultat **vide** n'est pas un refus ici, contrairement à `--lieu` : un seuil est un nombre, il
    ne peut pas désigner ce qui n'existe pas, et « aucune détection au-dessus de 0,99 » est une réponse.

    Mais une réponse **muette** : c'est le seul filtre qui puisse légitimement tout écarter sans rien
    dire de ce qu'on a raté. Quand le seuil vide le lot, la commande nomme donc la **meilleure
    probabilité présente** (« la plus sûre du lot est à 0,74 : abaissez le seuil pour l'atteindre »),
    ce qui apprend du même coup que le lot n'était pas vide et de combien descendre.

!!! note "`--lieu` : ce qu'il couvre, et ce qu'il ne couvre pas"
    Répétable, il retient les observations dont la **commune** ou le **carré** correspond, en
    correspondance **partielle** et insensible à la casse comme aux accents : `--lieu aix` trouve
    « Aix-en-Provence ». À l'écran on coche dans une liste fermée, en ligne de commande on tape à
    l'aveugle, sans rien pour rappeler l'orthographe.

    Le **nom convivial** du carré n'est pas une valeur de plus : c'est l'autre étiquette du même lieu
    ([ADR 3157](decisions/3157-un-carre-a-un-identifiant-et-une-etiquette.md)). `--lieu 640380` et
    `--lieu vallon` retiennent donc le même carré, et le **refus** le nomme d'un seul tenant,
    « 640380 · Vallon », comme l'écran l'affiche - pour que la valeur suggérée se recopie telle quelle.
    Personne n'a pour autant à taper le point médian.

    **Le point n'en fait pas partie**, contrairement à la puce « Lieu » de l'écran. Le schéma pose
    `UNIQUE(site_id, code)` : un code seul (« A1 », « Z1 ») désigne autant de lieux qu'il y a de carrés.
    L'écran s'en tire en l'affichant qualifié (« 640380 · A1 »), ce qui suppose une liste sous les yeux ;
    `--lieu A1` rouvrirait le défaut sans que rien ne le montre. Le point restera atteignable par un
    croisement `--carre` / `--point`.

    Un lieu **sans correspondance** est un **refus** (code 2) qui nomme les lieux présents, jamais une
    archive vide en code 0 : un script enchaînerait sans voir la faute de frappe, et l'expert recevrait
    une archive creuse.

!!! warning "Viser un passage sans filtre exige `--confirmer`"
    `--passage 3` **seul**, c'est **toutes** les observations du passage - des centaines. Un filtre oublié
    dans un script transforme « corrige ces trois lignes » en « corrige la nuit entière », et **rien** dans
    la commande ne distinguerait l'un de l'autre. Ce cas exige donc `--confirmer`.

    Et une sélection qui ne retient **rien** **lève**, au lieu de répondre « 0 observation traitée » : un
    geste qui ne touche rien est presque toujours une **erreur de filtre**.

Les filtres booléens (`--douteux`, `--reference`) sont **ternaires** : posés, ils ne gardent que les lignes
concernées ; **absents**, ils laissent passer **les deux** - ils ne veulent **pas** dire « seulement les
non-douteuses ».

## Lancer la CLI

Il n'y a pas encore de lanceur empaqueté : on l'exécute via `exec-maven-plugin` (même mécanique que le
[banc de performance](performance.md)), avec le **JDK 25 standard** (comme la CI) :

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
./mvnw -q -DskipTests compile
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="-cp %classpath fr.univ_amu.iut.cli.Cli --workspace /tmp/vigiechiro-cli lister-passages"
```

`Cli.main(String[])` existe et reste le point d'entrée naturel pour un futur lanceur natif (jpackage).

## Tests

La CLI est couverte par
[`CliTest`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/test/java/fr/univ_amu/iut/cli/CliTest.java)
(dispatch, codes de sortie, aide),
[`CliImportTest`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/test/java/fr/univ_amu/iut/cli/CliImportTest.java)
et
[`CliExportVuTest`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/test/java/fr/univ_amu/iut/cli/CliExportVuTest.java).
Ils positionnent `vigiechiro.workspace` sur un `@TempDir` et capturent les flux `sortie`/`erreur` :
aucun JavaFX, donc des tests **rapides et déterministes**.
