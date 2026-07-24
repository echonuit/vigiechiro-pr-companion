# Patterns et principes

L'architecture (cf. [Architecture](architecture.md)) applique des **patrons de conception** connus,
chacun choisi pour une raison précise et pour faire respecter les principes **SOLID** ainsi que
d'autres principes transverses (loi de Déméter, YAGNI, KISS, DRY… détaillés en fin de page,
[Au-delà de SOLID](#au-dela-de-solid)).

Chaque patron est présenté ainsi : **le problème** qu'il résout, **la solution**, **comment il est
utilisé ici** (avec, selon les cas, un extrait et un lien vers le code), un **diagramme** quand il
clarifie la structure ou le flux, et les **principes** qu'il sert.

!!! abstract "Rappel SOLID"
    **S**RP responsabilité unique · **O**CP ouvert/fermé · **L**SP substitution de Liskov ·
    **I**SP ségrégation des interfaces · **D**IP inversion des dépendances.

---

## MVVM (Model-View-ViewModel)

**Le problème.** Mélanger affichage, logique de présentation et règles métier dans les controllers
rend le code **intestable** (il faut une fenêtre) et **non réutilisable** (tout est lié à JavaFX).

**La solution.** Trois couches : le `model` (métier pur), le `viewmodel` (état **observable** +
logique de présentation), la `view` (FXML + controller) qui **observe** le viewmodel par *data
binding*. Le flux de dépendances va de la vue vers le modèle, jamais l'inverse.

**Dans cette application.** Chaque feature suit ce découpage. La vue ne fait que **lier** des contrôles à des
propriétés ; elle ne calcule rien et ne touche pas la base.

```mermaid
classDiagram
    class View {
        Controller
        FXML
    }
    class ViewModel {
        Property
        ObservableList
    }
    class Model {
        services
        DAO
    }
    View ..> ViewModel : se lie
    ViewModel ..> Model : appelle
```

**Principes.** **SRP** (une responsabilité par couche), **DIP** (la vue dépend d'abstractions
observables, pas de logique concrète). Frontières **garanties par ArchUnit** (`viewmodel_sans_javafx_ui`,
`view_sans_jdbc`).

---

## Objets-valeurs (records immuables)

**Le problème.** Des entités **mutables** (avec setters) se prêtent aux états incohérents, au partage
accidentel d'une instance et aux bugs d'égalité (comparaison par référence).

**La solution.** Modéliser le domaine en **`record` immuables** : champs finaux, égalité **par
valeur**, aucun setter. Pour « modifier », on **crée** une nouvelle instance.

**Dans cette application.** Le domaine est quasi entièrement en records (**plus de 250** : `Passage`, `Site`,
`SequenceDEcoute`, `Observation`…). Les DAO **construisent** ces records ligne par ligne via un
`RowMapper`, et les ViewModels les exposent dans des `ObservableList`.

**Principes.** Immuabilité (sûreté en lecture, raisonnement local) et **SRP** (l'entité ne porte que
ses données). Socle naturel du DAO et du `RowMapper`.

---

## État observé (un statut distant n'est pas un statut du domaine)

**Le problème.** Un système distant expose un état (l'avancement d'un calcul, le verrouillage d'un
site…). La tentation est de l'ajouter à l'énumération de statuts qu'on possède déjà : un seul enum, un
seul stepper, tout le monde est content. Sauf que cet état **ne nous appartient pas**. Il change sans
nous prévenir, il n'est pas forcément **monotone**, et le jour où il recule, notre statut ment.

**La solution.** Le garder **distinct** : une énumération à part, alimentée par lecture, jamais par une
transition locale. Le statut du domaine continue de dire ce que **nous** avons fait ; l'état observé
dit ce que **l'autre** en a fait. Et comme une lecture réseau coûte cher, on **persiste le dernier
relevé** avec sa date : l'écran affiche alors un souvenir, en le disant.

**Dans cette application.** `EtatTraitement` (EPIC #1259) suit l'analyse Tadarida côté serveur (`PLANIFIE →
EN_COURS → FINI/ERREUR/RETRY`) **sans** étendre `StatutWorkflow` : une relance ramène `FINI` à
`PLANIFIE`, si bien qu'un statut local « Traité » deviendrait faux. `DEPOSE` reste terminal (« ma part
est faite »). Le dernier relevé est mis en cache (`participation_traitement`), et `SuiviTraitement` est
le **point de relevé unique** : il interroge **et** mémorise. Même partition que `StatutPlateforme`
(sites).

**Le disque est un autre système que nous ne possédons pas** (EPIC #1297). Les fichiers audio d'un
passage peuvent disparaître sans nous : purge volontaire, disque externe débranché, dossier déplacé.
« Archivé » n'est donc **pas** une valeur de `StatutWorkflow` mais un **constat** :
`DisponibiliteAudio` (`COMPLETE` / `PARTIELLE` / `ABSENTE`), produit par `ServiceDisponibiliteAudio` en
regardant le disque (un `Files.list` par dossier, pas un `exists` par fichier), mis en cache et
invalidé aux gestes qui le changent. Toute l'IHM se règle **là-dessus** : l'écoute se voile, l'audit
informe au lieu de crier, la réactivation s'offre.

Un geste **déclaré** est autre chose qu'un état **observé**. Le projet est passé des premiers au
second : l'audio absent ne se **déclare** plus, il s'**observe**
([ADR 0048](decisions/0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md)) - l'utilisateur
possède ses fichiers, et son absence n'est jamais une corruption.

Ce basculement a rendu les marqueurs inutiles l'un après l'autre. `archived_at` a disparu du code ;
`originals_purged_at` ne gouverne plus l'audit, qui ne contrôle **plus du tout** les bruts : ce sont
des copies **optionnelles** de ré-analyse ([ADR 0036](decisions/0036-la-copie-des-bruts-est-une-option.md)),
absentes de la plupart des nuits, donc leur absence est l'état normal - et un état normal reste
silencieux. Il n'y avait plus rien à distinguer : le disque et la base disent la même chose. Les deux
colonnes devenues mortes (`archived_at`, `originals_purged_at`) ont depuis été **retirées du schéma**
(`V31`, #2429).

**Principes.** SSOT (la source de vérité reste distante : on ne la copie pas, on la **date**),
**honnêteté de l'IHM** (« dernier état connu le… » plutôt qu'une fraîcheur feinte) et **KISS** (pas de
sondage : on relit à l'ouverture, à la demande, ou après une action).

---

## Cascade de preuves (vérification graduée, refuser plutôt que se tromper)

**Le problème.** Rebrancher des fichiers retrouvés sur un passage archivé demande de répondre à : « ce
WAV est-il **bien** celui-là ? ». Le nom ne prouve rien (deux nuits d'un même carré portent des noms
voisins ; un fichier peut être renommé, tronqué, ré-encodé). Une empreinte cryptographique prouve tout,
mais **n'existe pas** pour les passages antérieurs, ni pour un passage reconstruit depuis la plateforme
(#1305) : exiger la preuve forte, c'est exclure exactement les cas où l'on en aurait le plus besoin. Et
la faute à ne pas commettre est claire : **rebrancher silencieusement le mauvais audio** sur des
observations, ce qui fabrique une donnée fausse et indétectable.

**La solution.** Une **cascade** de preuves de force décroissante, où chaque niveau tranche s'il le
peut et passe la main sinon, et où le doute non levé est un **refus**, jamais un « probablement bon » :

1. **empreinte** (SHA-256 des 64 premiers Kio, `Empreintes.empreinteCourte`) : identité certaine, quand
   elle a été capturée ;
2. **structure** : la durée réelle lue dans l'en-tête WAV confrontée à celle qu'on a enregistrée
   (tolérance 0,15 s), et la taille en octets ;
3. **acoustique** (`AnalyseAcoustique`, filtre de Goertzel) : les **cris des observations** rapatriées
   sont-ils réellement présents, aux fréquences et aux instants annoncés ? C'est la preuve qui reste
   quand aucune autre n'existe, et c'est la plus parlante : elle valide l'audio **contre les données
   qu'on s'apprête à y rebrancher**.

Le verdict est un type scellé (`VerdictIdentite` = `Acceptee(NiveauConfiance, preuves)` /
`Refusee(motif)`) : l'appelant ne peut pas confondre « accepté avec certitude » et « accepté sur faisceau
d'indices », et le **niveau de confiance minimal** atteint remonte jusqu'au rapport, donc jusqu'à
l'utilisateur.

**Dans cette application** (#1309, consommé par #1302 et #1305). `VerificationIdentiteAudio` porte la cascade ;
`ServiceReactivationPassage` ne copie **que** les fichiers acceptés, laisse les divergents de côté et les
**énumère** ; un passage sans empreinte reste donc réactivable, mais par la preuve acoustique, pas par la
confiance dans un nom.

**Corollaire : un fichier *reconstruit* est un candidat comme un autre** (#1406). Quand l'utilisateur n'a
gardé que ses **bruts**, les séquences sont **régénérées** (la transformation est déterministe, R11) puis
soumises à la **même** cascade. Si le code de transformation n'a pas changé, l'empreinte de la tranche
régénérée est celle capturée avant l'archivage → **CERTITUDE** ; s'il a changé, la cascade descend d'un
cran au lieu d'accorder une confiance aveugle. C'est le point à retenir : **la reproductibilité est une
preuve, pas un prérequis** - on ne se dispense jamais de vérifier au motif qu'on a fabriqué le fichier
soi-même. Et un **brut refusé ne régénère rien** : recalculer à partir d'un fichier dont l'identité n'est
pas établie, c'est fabriquer du faux.

**Cas limite : le passage *reconstruit* (EPIC #1653).** Un passage reconstruit depuis la plateforme
(#1305) n'a **jamais eu** d'empreinte : ni sur ses originaux (un placeholder `…-reconstruit.wav` tient
lieu d'inventaire, sans fréquence d'acquisition), ni sur ses séquences. La cascade y tomberait donc
directement sur l'acoustique - qui produit des **faux négatifs** sur des cris réels faibles. Mais l'audio
régénéré est, par construction, un **extrait verbatim** du brut **désigné par l'utilisateur** (la
transformation copie le PCM sans rééchantillonnage, prouvé octet à octet) : son identité tient à la
**régénération elle-même**, pas à une empreinte qu'on n'a pas. L'**hydratation** (`HydratationDepuisBruts`,
#1650/#1682) l'accepte donc sur preuve **structurelle** (nom + durée, `FORTE`), et la concordance
acoustique y devient un **indice non bloquant** (`IndiceAcoustique`), jamais un veto. La chaîne :
`InventaireBrutsSource` (#1649, lit la Fe du **log** et énumère les bruts) → régénération → rebranchement
structurel → `AdoptionOriginauxReconstruits` (#1651, remplace le placeholder par les vrais originaux,
déclarés « purgés » puisque connus mais non stockés localement). Détail : `AnalyseAcoustique` mesure
désormais l'énergie **de pointe** sur une courte fenêtre glissée dans celle de l'observation (#1687) - la
moyenne sur **toute** la fenêtre diluait un cri de quelques ms noyé dans plusieurs secondes, d'où des faux
négatifs qui rendaient l'hydratation d'un vrai passage inopérante avant correction. Le *pourquoi* de ces
deux choix est consigné en [ADR 0001](decisions/0001-reactivation-passage-reconstruit-identite-structurelle.md)
(identité structurelle) et [ADR 0002](decisions/0002-detection-acoustique-energie-de-pointe.md) (énergie de pointe).

**Principes.** Fail-safe (ne pas pouvoir prouver = ne pas faire), **honnêteté** (dire *avec quelle
force* on a conclu), et refus de la fausse alternative « preuve parfaite ou rien ».

---

## Issue d'appel triée (le transport ne parle plus par silence)

**Le problème.** Un client HTTP qui « dégrade proprement » convertit tout échec en `Optional.empty()`
ou liste vide. C'est le bon réflexe pour **un seul** cas : « je ne suis pas connecté » (l'application
vit hors ligne). Pour les autres, c'est une perte d'information catastrophique : un `422` devient une
collection vide (l'import mort et muet de #1277, 4806 observations invisibles), un délai réseau
devient « aucun résultat », et une panne au milieu d'une pagination rend un **préfixe silencieux**
pire que le vide. L'appelant ne peut ni informer l'utilisateur, ni décider correctement.

**La solution.** Un type scellé qui rend l'issue **exhaustive à la compilation** :
`ReponseApi<T>` = `Succes(valeur)` / `NonConnecte` / `Injoignable(cause)` / `Refuse(statut, corps)`.
Un `switch` qui oublie une branche ne compile pas — la famille de bugs #1277, c'est « un cas auquel
personne n'a pensé ». Le comportement commun vit dans les variantes par **override** (`enOptionnel`,
`transformer`, `lireAvec`, `puis`, `echec`), jamais par `switch (this)`. Là où le silence reste le
comportement **voulu**, c'est l'appelant qui le choisit, explicitement : `enOptionnel()`.

**Dans cette application** (#1284). `TransportVigieChiro` émet et trie ; `ClientVigieChiro` nomme les
endpoints ; `PaginationEve` est **tout-ou-rien** (l'issue de la page fautive, jamais un préfixe).
Conséquences : la modale de connexion distingue « jeton refusé (401) » de « plateforme injoignable » ;
l'import et le suivi du traitement disent pourquoi ; la **garde anti-purge** des rapprocheurs est
inchangée mais sa cause remonte au bandeau ; la garde anti-relance du dépôt devient **fail-safe** (état
illisible sans `--forcer` = pas de lancement) ; la vérification d'un dépôt hors ligne lève
« vérification impossible » au lieu d'un faux « tout manquant ». Le **contrat live** verrouille
`max_results=1000 → Refuse(422)` : la sonde qui aurait rendu #1277 bruyante par construction.

**Principes.** Honnêteté (une panne n'est pas une donnée), **exhaustivité par le compilateur** plutôt
que par la relecture, fail-safe (ne pas pouvoir prouver qu'une action destructrice est sûre = ne pas
la faire), et un **vocabulaire unique** des messages d'échec (`ReponseApi.echec()`).

---

## Le verdict porte son message (résultat scellé, message par variante)

**Le problème.** Une opération à plusieurs issues renvoie souvent un rapport « à trous » :
`(boolean succes, String motif, Rapport rapport)`, dont l'appelant doit deviner quels champs sont
renseignés dans quel cas. Chaque appelant re-tricote alors le même `if` — et chaque **surface** (IHM,
CLI) invente sa propre phrase pour dire la même chose. Les deux finissent par diverger.

**La solution.** Un type **scellé** dont chaque variante porte **ce qui la caractérise**, et **sait le
dire**. Le message n'est pas dans l'appelant : il est dans le verdict.

```java
public sealed interface ResultatReset {
    int codeSortie();     // 0 fait · 2 refusé (distinct de 1, l'échec d'exécution)
    String enClair();     // ce qu'il faut dire à l'utilisateur

    record Refuse(String motif, BilanRecuperabilite bilan) implements ResultatReset { … }
    record Fait(BilanSauvegarde sauvegarde, Path filet, int passagesReconstruits,
                RapportAudit audit, List<String> aRetablir) implements ResultatReset { … }
}
```

L'IHM **affiche** `enClair()`, la CLI **affiche** `enClair()` et sort sur `codeSortie()`. Aucune des deux
ne traduit un état en phrase : la parité CLI ↔ IHM est **structurelle**, pas maintenue à la main.

**Dans cette application.** `VerdictCarre` (#733 : `Concorde` / `Diverge` / `HorsGrille` / `Indisponible` — dont
le message **vide** exprime le silence hors ligne) et `ResultatReset` (#1419). Même famille que
[l'issue d'appel triée](#issue-dappel-triee-le-transport-ne-parle-plus-par-silence), appliquée aux
**opérations locales** plutôt qu'au transport : exhaustivité par le compilateur, comportement par
**override** et jamais par `switch (this)`.

---

## Refuser avant de détruire (l'ordre des garde-fous est la garantie)

**Le problème.** Une opération destructrice qui vérifie ses conditions **au fil de l'eau** laisse, au
premier obstacle, un état **à moitié détruit** — le pire des deux mondes. Et l'utilisateur, lui, ne
distingue plus « ça a refusé » de « ça a planté en route ».

**La solution.** Tous les refus **avant** la première écriture, et un refus qui **le dit** : *rien n'a été
modifié*. L'ordre des étapes n'est pas une commodité de lecture, c'est **la garantie**.

Le reset guidé (#1419) en est le cas d'école :

1. **dire ce qu'on perdrait** — une nuit dont l'audio n'est ni sur le disque ni sur le serveur est perdue
   pour de bon ; sans acceptation **explicite**, on s'arrête là ;
2. **vérifier que la plateforme répond** — la base neuve se **repeuple depuis le serveur** : le détruire
   alors qu'il est injoignable laisserait un workspace **vide**. Aucune sauvegarde ne rendrait ça
   acceptable, et c'est le garde-fou décisif ;
3. **sauvegarder** ; 4. **base neuve** ; 5. **repeupler** ; 6. **auditer**.

Le pendant, pour une écriture **irréversible** : **le serveur d'abord, la base ensuite** (#1418). Le
message n'est écrit localement qu'**après** que le serveur l'a accepté. L'inverse laisserait, au moindre
refus, un message que l'observateur **croirait envoyé** et que le validateur ne verrait **jamais**.

**Corollaires.**

- Une **confirmation nomme ce qu'on perd** : elle énumère les nuits, ou cite le texte qui va partir. Un
  « êtes-vous sûr ? » générique n'est **pas un consentement** — on ne consent qu'à ce qu'on a lu. C'est ce
  message-là que le test vérifie, pas le fait qu'un dialogue s'ouvre.
- Une **écriture définitive mérite d'être désactivable**. `discuter-validateur` (#1418) est une
  fonctionnalité à part de la lecture du fil : couper l'écriture laisse la lecture intacte. Lire est sans
  conséquence ; écrire ne se retire pas.
- Un **refus a son propre code de sortie** (`2`), distinct du succès (`0`) et de l'échec d'exécution
  (`1`) : un script peut ainsi refuser d'enchaîner.

---

## Un invariant, deux politiques de surface (l'unification d'un geste, #1656)

Quand un même geste métier vit sur **plusieurs surfaces** (IHM, CLI) et se met à diverger, on ne le
recopie pas : on remonte la **règle de fond** à un seul endroit (le service), et chaque surface n'en
porte qu'une **présentation mince**.

Le chantier « importer les observations d'un passage » (#1656) est le cas d'école : la même décision
« un seul jeu par passage » était réimplémentée **cinq fois**, dont deux qui plantaient sur la contrainte
`UNIQUE`. Après unification :

- **la règle** vit une seule fois, dans le **noyau de service** (`NoyauImportObservations`) : hors
  remplacement, refuser **avant l'INSERT** (cf. « Refuser avant de détruire ») ;
- **la surface IHM** la rend par une **question** (`DecisionRemplacementJeu` : détecter → confirmer →
  remplacer | abandonner), partagée par les fronts de « Sons & validation » ;
- **la surface CLI** la rend par un **refus d'usage** (`GardeJeuExistant`, code `2`, « relancez avec
  `--remplacer` »), partagé par les commandes d'import.

Le test à se poser est celui de la **sur-unification** : fondre les deux présentations en une seule
serait une erreur (un dialogue interactif et un code de sortie ne sont pas le même objet). Le bon
découpage : **une règle, deux adaptateurs**. Une capacité présente d'un seul côté (ou rendue
différemment de chaque côté) est une dette invisible ; la règle centralisée est la garantie qu'elles ne
redivergeront pas.

---

## Package-by-feature (tranches verticales)

**Le problème.** Une organisation **par couche** (`controllers/`, `services/`, `dao/`…) éparpille une
même fonctionnalité dans tout le projet : pour modifier un écran, on touche partout.

**La solution.** Regrouper le code **par fonctionnalité** : `sites/`, `passage/`… chacun contenant ses
4 couches. Une feature devient une **tranche verticale** autonome.

**Dans cette application.** Les 10 features sont des paquets autonomes ; le socle `commun/` porte le partagé
(chrome, persistance, DI). On ouvre, modifie ou supprime une feature sans naviguer ailleurs.

**Principes.** **Forte cohésion / faible couplage** ; **OCP** à l'échelle du produit (ajouter une
feature ≈ ajouter un paquet, sans toucher aux autres — garanti par
`pas_de_dependance_inter_feature_vers_la_vue`).

---

## Injection de dépendances + Composition Root

**Le problème.** Si chaque objet **crée** ses dépendances (`new ServiceX()`), le graphe est figé,
impossible à substituer en test, et le câblage est dispersé partout.

**La solution.** Les objets **reçoivent** leurs dépendances (constructeur), et **un seul** endroit, la
*Composition Root*, assemble le graphe complet.

**Dans cette application.** [`RacineInjecteur`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/di/RacineInjecteur.java)
installe un **socle explicite** (`CommunModule`, `PersistenceModule`) puis **auto-découvre** les
modules de feature via `ServiceLoader<ModuleDeFeature>`, en ne gardant que ceux dont la fonctionnalité
est active. Ajouter une feature ne modifie donc **pas** ce fichier (cf. [Injection](injection.md)).
Même les controllers FXML sont injectés (cf. *Factory* plus bas). En test, on substitue une base
jetable via `Modules.override(RacineInjecteur.modules())`, sans changer le code de production.

```java
public static List<Module> modules() {
    List<Module> modules = new ArrayList<>();
    modules.add(new CommunModule());        // socle : toujours explicite
    modules.add(new PersistenceModule());
    // features : auto-découvertes, filtrées par les feature-flags
    ServiceLoader.load(ModuleDeFeature.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(Fonctionnalites.filtreActives())
        .forEach(modules::add);
    return modules;
}
```

Détails et diagramme de séquence : [Injection (Guice)](injection.md).

**Principes.** **DIP** (on dépend d'abstractions, le câblage est externalisé) et **IoC** (« ne nous
appelez pas, nous vous appellerons » : le conteneur instancie).

---

## Singleton (géré par le conteneur)

**Le problème.** Certaines ressources doivent être **uniques** dans toute l'application : une seule
base, un seul service de navigation. Les multiplier créerait des incohérences (deux connexions, deux
historiques).

**La solution.** Plutôt que le Singleton « maison » (constructeur privé + champ statique, difficile à
tester et à substituer), on **délègue l'unicité au conteneur** : `@Singleton` Guice.

**Dans cette application.** `SourceDeDonnees`, `Navigateur`, les `Navigation*` et la **plupart des providers
de DAO et de services** des features sont `@Singleton` (plus de 130 déclarations) : une seule instance par
injecteur, mais **toujours injectée** (donc remplaçable en test).

**Principes.** Évite l'**état statique global** tout en restant **testable** : l'unicité est une
décision de **câblage**, pas une contrainte gravée dans la classe.

---

## Separated Interface (contrats `Ouvrir*`)

**Le problème.** Si `sites` appelait directement `passage.view.NavigationPassage`, les features
seraient **couplées** entre elles — impossible de les faire évoluer indépendamment (et la règle
ArchUnit l'interdit).

**La solution.** Publier une **interface dans le socle**, l'implémenter dans la feature cible :
l'appelant dépend de l'**abstraction**, jamais de l'implémentation. La dépendance est **inversée**.

**Dans cette application.** [`OuvrirPassage`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/OuvrirPassage.java)
(socle) est implémenté par
[`NavigationPassage`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/view/NavigationPassage.java)
(feature `passage`) et **bindé** par `PassageModule`. `sites` injecte `OuvrirPassage`.

```mermaid
classDiagram
    class OuvrirPassage {
        <<interface>>
        +ouvrir(Long, ContexteSite)
    }
    class NavigationPassage
    SiteDetailController ..> OuvrirPassage : injecte
    NavigationPassage ..|> OuvrirPassage : implémente
    note for OuvrirPassage "dans le socle commun.view"
```

**Principes.** **DIP** (les deux features dépendent du contrat, pas l'une de l'autre) et **OCP**
(brancher une nouvelle implémentation sans modifier l'appelant). La **liste de référence** des contrats
`Ouvrir*` (**10**) est maintenue à un seul endroit : [Navigation](navigation.md#ouvrir-une-autre-feature-sans-en-dependre).

---

## Facade (`Navigation*`)

**Le problème.** Ouvrir un écran demande plusieurs gestes : charger le FXML, brancher la
`controllerFactory`, ouvrir le controller sur son contexte, empiler dans le `Navigateur`. Répétés tels
quels chez chaque appelant, ils seraient verbeux et fragiles.

**La solution.** Une **façade** par feature expose une opération **simple** (`ouvrir(...)`) qui
orchestre ces gestes en interne.

**Dans cette application.** [`NavigationPassage`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/view/NavigationPassage.java)
(et ses homologues `Navigation*`) implémente le contrat `Ouvrir*` en **cachant** le `FXMLLoader` et le
`Navigateur` : l'appelant ne voit qu'`ouvrir(idPassage, contexte)`. Le `Navigateur` lui-même est une
façade sur la zone centrale du chrome + l'historique.

**Principes.** **SRP** (la mécanique d'ouverture est encapsulée) et **faible couplage** (l'appelant
ignore FXML / Navigateur).

---

## Plugin / Extension (Multibinder)

**Le problème.** L'accueil affiche une carte pour **certaines** features (et un compteur de tableau de
bord pour d'autres). Si le `MainController` connaissait chacune, ajouter une contribution l'obligerait
à **se modifier** à chaque fois.

**La solution.** Le socle déclare un `Set<T>` que **les features intéressées alimentent** (multibinding
Guice), sans que le socle connaisse les contributeurs. Il injecte l'ensemble et l'agrège.

**Dans cette application.** Quatre points d'extension suivent ce patron, chacun avec un helper du DSL
[`ModuleDeFeature`](injection.md#ce-que-publie-un-module-de-feature) : `ActiviteAccueil` (carte
d'accueil, `activite(...)`), `IndicateurAccueil` (compteur, `indicateur(...)`), `OngletReglages`
(onglet de l'écran Réglages, `ongletReglages(...)`) et `ActionMenu` (entrée du menu ☰, `actionMenu(...)`).
Le contrat est **agnostique de JavaFX** (dans `commun/view`), la feature ne fournit que des données
(un descripteur, un libellé…), et c'est le socle (`MainController`, `EcranReglagesController`,
`ConstructeurMenuOutils`) qui construit les widgets. Exemple : une bascule de menu déclare une
`BooleanProperty` liée à `ReglagesReactifs` ; le socle en fait une `CheckMenuItem`.

```mermaid
classDiagram
    class MainController {
        Set~ActiviteAccueil~ activites
    }
    class ActiviteAccueil {
        <<interface>>
    }
    MainController o-- ActiviteAccueil : agrège
    ActiviteAccueil <|.. ActiviteMesSites
    ActiviteAccueil <|.. ActiviteImporterNuit
```

**Principes.** **OCP** par excellence : le chrome est **fermé à la modification** mais **ouvert à
l'extension** (une nouvelle carte = un nouveau binding, zéro ligne touchée dans le socle).

**Feature = plugin.** Le patron va jusqu'au bout : les modules de feature sont eux-mêmes
**auto-découverts** par `RacineInjecteur` (`ServiceLoader<ModuleDeFeature>`, cf.
[Injection](injection.md#la-racine-de-composition)). Une feature complète (DAO, services, carte,
compteur, réglages, entrée de menu) s'ajoute donc **sans toucher une seule ligne du socle ni de la
racine de composition** — juste un `XxxModule extends ModuleDeFeature` déclaré comme service.

---

## Interfaces de rôle fines (ISP)

**Le problème.** Une grosse interface « écran » avec *garde de sortie + fil d'Ariane + rafraîchissement
+ …* forcerait **chaque** écran à tout implémenter, même ce qu'il n'utilise pas.

**La solution.** De petites interfaces **optionnelles**, à responsabilité unique, qu'un écran
implémente **seulement si** la capacité le concerne. Le `Navigateur` les détecte par `instanceof`.

**Dans cette application.**

| Interface (1 rôle) | Implémentée par les écrans qui… |
|---|---|
| [`GardeQuitter`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/GardeQuitter.java) | ont une **saisie non enregistrée** |
| [`EmplacementNavigation`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/EmplacementNavigation.java) | ont une **place hiérarchique** (fil d'Ariane) |
| [`RafraichirAuRetour`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/RafraichirAuRetour.java) | affichent des **données mutables** |

Un écran lecture seule n'implémente **aucune** des trois.

**Principes.** **ISP** (aucun écran n'est forcé d'implémenter ce qu'il n'utilise pas) et **OCP** (le
Navigateur honore de nouvelles capacités sans connaître les écrans).

---

## DAO (Data Access Object)

**Le problème.** Du SQL `PreparedStatement` mélangé à la logique métier ou à l'IHM est impossible à
tester, à réutiliser, et viole la séparation des couches.

**La solution.** Isoler l'accès aux données derrière des objets dédiés ; le reste du code ignore JDBC
et dialogue avec des **services**.

**Dans cette application.** Chaque entité a son DAO dans `*/model/dao/`. La règle ArchUnit `view_sans_jdbc`
**interdit** à l'IHM de toucher `model.dao` ou `java.sql`.

**Principes.** **SRP** (la persistance est une responsabilité à part) et **DIP** (le métier dépend
d'abstractions de données, pas de l'API JDBC).

---

## Template Method (`DaoGenerique`)

**Le problème.** Tous les DAO réécriraient la même mécanique : ouvrir une connexion, exécuter, itérer
le `ResultSet`, fermer. Beaucoup de **duplication**.

**La solution.** Une classe de base fixe le **squelette** de l'algorithme (`findAll`, `findById`,
`delete`) et **délègue** les détails variables à des méthodes que les sous-classes remplissent.

**Dans cette application.** [`DaoGenerique<T, ID>`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/DaoGenerique.java)
fournit les opérations communes ; un DAO concret donne seulement `table()`, `colonneCle()` et son
`RowMapper`.

```mermaid
classDiagram
    class DaoGenerique {
        <<abstract>>
        +findAll()
        +findById(id)
        +delete(id)
        #table()
        #colonneCle()
        #rowMapper()
    }
    class SiteDao
    class PassageDao
    DaoGenerique <|-- SiteDao
    DaoGenerique <|-- PassageDao
```

**Principes.** **DRY** (la boucle `ResultSet` n'existe qu'une fois), **OCP** (un nouveau DAO **étend**
sans modifier la base) et **LSP** (tout `DaoGenerique` concret est substituable à l'abstraction).

---

## Strategy (`RowMapper`, génération de sélection)

**Le problème.** Une partie d'un algorithme **varie** (comment lire une ligne ? comment choisir des
séquences ?) alors que le reste est stable. Un `if/else` géant serait fragile et fermé.

**La solution.** Encapsuler la partie variable derrière une **abstraction interchangeable**, injectée
ou passée au client.

**Dans cette application.** Deux usages :

- [`RowMapper<T>`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/RowMapper.java)
  (`@FunctionalInterface`) : « transformer **une** ligne en entité » varie par DAO (souvent une
  lambda) ; l'itération reste dans `DaoGenerique`.

  ```java
  @FunctionalInterface
  public interface RowMapper<T> { T mapper(ResultSet rs) throws SQLException; }
  ```

- [`GenerateurSelection`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/qualification/model/GenerateurSelection.java) :
  `selectionner(sequences, methode, taille)` choisit un sous-ensemble selon la `MethodeSelection`
  (répartition temporelle vs aléatoire vs manuel) — une **règle pure**, sans base ni IHM.

```mermaid
classDiagram
    class RowMapper {
        <<interface>>
        +mapper(ResultSet) T
    }
    class DaoGenerique
    DaoGenerique o-- RowMapper : utilise (stratégie)
```

**Principes.** **OCP** (ajouter une stratégie sans modifier l'appelant), **SRP** (chaque stratégie est
une règle isolée, **testable sans persistance ni IHM** — objectif réutilisation O6).

---

## Table de suivi par unité (socle `commun`)

**Le problème.** Trois opérations longues (génération d'archives #820, import par fichier #947, dépôt
VigieChiro #983) doivent montrer l'avancement **de chaque unité de travail** (état coloré + barre),
alimenté depuis des fils d'arrière-plan, parfois dans le désordre (travail parallèle).

**La solution.** Un socle en trois couches, spécialisé par feature :

- `commun.viewmodel` : `EtatUnite` (en attente / en cours / terminée / échec), `LigneSuivi` (ligne
  observable extensible), `SuiviLignes<L>` (pilote générique, ciblage par numéro, tolérant aux
  événements inconnus ou dans le désordre) ;
- `commun.view` : `TableSuivi` (colonnes `#` / spécifiques / Progression, rangées colorées
  `.ligne-suivi.etat-…` dans `design.css`) + `CelluleProgressionUnite` (barre vive ou icône + libellé,
  raison d'échec en infobulle) ;
- côté feature : une interface d'événements métier (`SuiviArchives`, `SuiviFichiers`, `SuiviDepot`,
  chacune avec sa variante `inerte()`), un **relais** qui rejoue chaque événement sur le fil JavaFX
  (`Platform.runLater`), et une spécialisation `SuiviLignesXxx extends SuiviLignes<LigneXxx>` qui
  traduit les événements en mutations observables.

**La règle.** Toute nouvelle opération longue « par unité » réutilise ce socle : définir l'interface
d'événements (+ `inerte()`), la ligne et le pilote spécialisés, le relais fil JavaFX — jamais une
table ad hoc.

## Occupation d'un écran pendant un traitement long (socle `commun`)

**Le problème.** Un traitement lourd (agrégats, inspection de dossier, appel réseau) exécuté
**synchrone sur le fil JavaFX** fige l'IHM sans feedback ; un `setCursor(WAIT)` n'y suffit pas, fil
bloqué. Le patron correct (thread virtuel → travail → `Platform.runLater`) était recopié écran par
écran, avec le piège récurrent des mutations hors fil JavaFX (« clic figé »).

**La solution.** Deux briques de `commun.view`, à composer :

- `ExecuteurTache` (interface `@ImplementedBy` synchrone) : `executer(Supplier travail, Consumer
  succès, Consumer échec)` exécute le travail **hors du fil JavaFX** puis applique résultat/erreur
  **sur** le fil JavaFX. `ExecuteurTacheAsynchrone` (thread virtuel + `runLater`) en production ;
  `ExecuteurTacheSynchrone` (défaut) rend les tests déterministes. Sœur d'`ExecuteurFiche`.
- `IndicateurOccupation` : superpose sur un `StackPane` hôte un voile + roue + libellé « … en
  cours » (`enCoursProperty`, styles `.occupation-*` dans `design.css`), et pilote un `ExecuteurTache`
  via `occuper(libellé, travail, succès, échec)`. Le voile capte les clics le temps du traitement.
- `OccupationChrome` (#1215) : la déclinaison **chrome entier** pour les traitements du menu « ☰ »
  qui ne concernent aucun écran (sauvegarde / restauration de la base, purge des originaux) : voile
  sur la racine de la fenêtre, et **opération critique (#906) posée le temps du travail** (fermer
  l'application en pleine copie déclenche l'avertissement du socle). Installée par le
  `MainController`, consommée par injection dans les `ActionMenu`.
- `DialogueProgression` (#1597) / port `SuiviOperation` (#1622) : la déclinaison **modale à barre de
  progression annulable**, pour les opérations **longues** dont l'utilisateur veut voir l'avancement et
  pouvoir renoncer (reconstruction, réactivation avec ancrage, import des observations). Là où
  `IndicateurOccupation` pose un voile opaque (« ça travaille »), la modale **dit où on en est** (barre
  déterminée + libellé d'étape + ETA) et **laisse annuler** (bouton « Annuler » câblé sur le jeton). Elle
  pilote le même `ExecuteurTache` (progression + annulation ci-dessous). Le port `SuiviOperation` rend le
  geste **testable sans fenêtre** : un double **synchrone** exécute le travail sans ouvrir de `Stage`, si
  bien que le déclenchement s'éprouve **hors du fil JavaFX**.

**Opérations longues « riches » (#1252).** Pour les traitements qui diffusent leur avancement ou
s'annulent, le socle étend `ExecuteurTache` sans toucher aux écrans déjà migrés :

- **progression déterminée** : `relaisProgression(application)` fabrique le `Consumer<Progression>` à
  passer au service ; chaque point revient sur le fil JavaFX (immédiat en test). Pour tout autre
  événement de suivi (table par unité, cf. section précédente), `surFilJavaFx()` fournit l'`Executor`
  du fil JavaFX - les relais de suivi n'ont plus à recopier `Platform.runLater` ;
- **annulation coopérative** : le **jeton appartient à l'appelant** (`commun.model.JetonAnnulation`,
  câblé sur le bouton « Annuler » de l'écran). Deux styles au choix du travail : `leverSiAnnule()`
  lève `OperationAnnuleeException`, que la surcharge `executer(travail, succès, annule, échec)`
  conclut par le callback `annule` (jamais par `échec`) ; ou bien le moteur lit `estAnnule()` /
  `jeton::estAnnule` et **rend un bilan partiel honnête** par le chemin de succès (patron du dépôt
  #1044 : jamais d'unité fantôme, la reprise ne renvoie que le manquant). Jamais d'interruption
  brutale de thread ;
- **désactivation d'un bouton pendant la tâche** : pas d'API dédiée, un binding suffit -
  `bouton.disableProperty().bind(occupation.enCoursProperty())` (patron posé par #1254 sur M-Audit).
  Plus jamais de `setDisable(true/false)` posé à la main autour de l'appel.

**Testabilité de l'annulation en synchrone.** L'exécuteur synchrone n'empêche pas de tester
l'annulation : le jeton appartenant à l'appelant, le test l'**annule avant de déclencher**
l'opération et vérifie l'arrêt propre au premier point de contrôle (callback `annule`, ni succès ni
échec) - c'est le contrat coopératif qui est testé, la simultanéité réelle relevant de l'E2E.

**La règle.** Toute opération longue d'un écran passe par le socle — `IndicateurOccupation` (voile) pour
les traitements brefs, `DialogueProgression` (modale) quand l'avancement mérite d'être montré et
l'opération annulée — l'échec étant routé vers le filet d'erreurs de l'écran (#795), jamais un
`Thread.ofVirtual()` + `runLater` recopié à la main, y compris pour la progression et l'annulation
(surcharges ci-dessus). Le déport écran par
écran (EPIC #793 puis reliquat #1316) est **terminé** : plus aucun `Thread.ofVirtual` ne vit hors du
socle, tout nouvel écran naît avec ce patron.

**Piège capture (#1278).** Les outils de capture doivent lier les exécuteurs **synchrones**
(`ModuleCaptureCommun`) : `ApercuFx` snapshotte immédiatement, l'asynchrone de production capturerait
le voile « Chargement… » à la place du contenu. Le garde-fou `CablageInjecteursCaptureTest` casse la
CI si un injecteur de capture résout un exécuteur asynchrone.

## Une modale de progression : suivre son contenu, et ne jamais se taire (socle `commun`)

**Le problème.** Une modale est dimensionnée **à son ouverture**, sur le contenu visible à cet instant.
Tout ce qui paraît ensuite - une seconde barre de phase, un compte rendu de fin, un bandeau - agrandit la
mise en page sans agrandir la fenêtre : le bas passe sous la ligne de flottaison. Chaque modale s'en était
tirée pour son seul cas connu, si bien que la réactivation poussait toujours ses **boutons** hors de la
fenêtre dès que la barre d'ancrage paraissait (#1931).

**La solution.** `Modales.suivreLaCroissance(racine, revelations…)` prend la racine et les propriétés dont
un changement fait paraître du contenu ; la fenêtre s'ajuste à chacune. À poser à côté de
`Modales.fermerParEchap`, qui répond à la même histoire (un comportement transverse que chaque modale
réinventait).

⚠️ **Ajuster, oui ; figer, non.** L'implémentation appelle `sizeToScene()` et **rien d'autre**. Une version
qui gardait `max(taille avant, taille après)` pour « ne jamais rétrécir » a fait passer le `Stage` en
dimensionnement **explicite** : il cesse alors définitivement de s'ajuster à ses scènes suivantes. Sans
effet pour une modale que l'on jette après usage - mais le `Stage` du harnais TestFX est **partagé par
toutes les classes de test d'un même fork**, et il est resté figé à 600 px pour toutes les suivantes, dont
les noeuds tombaient « hors de la fenêtre » très loin de la cause (#1940).

**Les phases sont des blocs.** Quand une opération enchaîne plusieurs phases, chacune est un bloc : son nom
(`.nom-de-phase`) et son message sur une ligne, sa barre sur **toute la largeur** en dessous. Le nom, la
barre et le message sur une même ligne ont deux défauts : le message dispute sa largeur à la barre, si bien
que la phase au message le plus long se retrouve avec la barre la plus **courte** - or empiler deux barres,
c'est demander à l'oeil de les comparer ; et le nom redit le début du message au prix de la place prise à la
barre (#1935, #1946).

**Et la modale ne se tait jamais.** Un intervalle où le travail continue sans qu'aucune barre ne bouge est
un défaut au même titre qu'une barre figée à 100 % : nommer chaque étape, et **poser le libellé avant** le
geste qu'il annonce, jamais après. Raison d'être et cas vécu : [ADR 0027](decisions/0027-une-attente-porte-toujours-un-nom.md).

## Les dialogues d'une action sont des ports (socle `commun`)

**Le problème.** Un `showAndWait()` **fige** un test TestFX headless (piège connu depuis #798). Toute
action qui ouvre un dialogue est donc, littéralement, **impossible à cliquer dans un test** : le test
s'arrête sur la ligne du dialogue et n'en revient jamais. La conséquence a mis longtemps à être
nommée (#1405) :

> On ne testait que le **grisage** des boutons. Jamais leur **effet**.

Et cela portait précisément sur les gestes qu'on veut couvrir : restaurer la base, supprimer un
passage et sa nuit, réimporter par-dessus les validations de l'observateur. Tous irréversibles, tous
non testés.

**La solution.** Rendre **remplaçable** chaque forme de dialogue. **Quatre** familles, bâties sur le même
triplet - un **contrat neutre**, une **implémentation réelle**, un **porteur injectable** :

| port | ce qu'il demande | implémentation réelle | double en test |
|---|---|---|---|
| `Confirmateur` (#1013) | le **oui/non** | `ConfirmationNavigation` | répond ce qu'on lui dit |
| `Notificateur` (#1404) | le **compte rendu** | `NotificationDialogue` | **capture** ce qui a été dit |
| `SelecteurFichier` (#1425) | la **désignation** d'un fichier / dossier | `SelecteurFichierJavaFx` | répond un chemin, **ou rien** (annulé) |
| `DemandeurDeChoix<T>` (#1431) | le **choix** parmi plusieurs options | `ChoixDansListe` **ou** `ChoixParBoutons` | répond une option, **ou rien** (renoncé) |

Chaque **écran** détient **une** instance de chaque porteur qu'il utilise, champ `final`, exposée à ses
tests par un accesseur package-private. Ses **collaborateurs** (actions extraites, cartes, helpers)
**reçoivent** ces porteurs : ils n'en fabriquent pas. Quand un écran en accumule plusieurs, ils forment
une unité qu'on peut extraire (`DialoguesAudio` en porte trois).

### « Annuler » n'est pas une option : c'est un renoncement

Le dernier port est né d'un dialogue qui semblait **inclassable** : « Enregistrer / Abandonner /
**Annuler** », à la sortie du mode édition de la carte. Trois boutons, donc trois issues - et aucun
contrat existant pour ça. Il aurait fallu inventer un port « à trois branches », taillé pour **un seul
écran**.

C'était une erreur de lecture :

> **« Annuler » n'est pas une troisième décision. C'est le refus de décider.** On reste en édition, rien
> n'est enregistré, rien n'est perdu.

Le dialogue n'a donc pas trois issues : il en a **deux**, plus la possibilité de **renoncer** - ce qui se
lit `Optional.empty()`, exactement comme un sélecteur de fichier qu'on ferme. Un seul contrat suffit, et
il sert **aussi** au choix d'une participation VigieChiro parmi une liste.

Corollaire à retenir : **renoncer n'est pas abandonner**. Les deux ferment le dialogue ; **un seul
détruit** le travail de l'utilisateur. Un test doit les distinguer.

**La présentation, elle, reste à l'appelant** - car elle n'est légitimement pas la même :

- `ChoixDansListe` quand les options sont des **données** (on ignore combien de participations le compte
  contiendra) ;
- `ChoixParBoutons` quand ce sont des **décisions** (enregistrer / abandonner) : deux décisions se lisent
  d'un coup d'œil, une liste déroulante y serait un **recul**.

### Un formulaire n'est pas un dialogue : c'est une vue

Face aux `Dialog<T>` de saisie (créer un site, personnaliser une sélection d'écoute), la tentation était
d'ajouter un cinquième port, générique, rendant `Optional<T>`. **Il ne fallait pas** : l'application avait
déjà le bon patron, et il était testé.

> Un formulaire est une **vue** : FXML + controller + ViewModel + une entrée `ouvrirModale*` sur la façade
> de navigation.

Cinq modales le suivent (`ModalePoint`, `ModaleSite`, `RattachementModale`, `ReconstructionModale`,
`ModaleSelection`), toutes couvertes par un test TestFX. Les `Dialog<T>` bâtis à la main étaient les
**intrus**, et ils cumulaient **trois** défauts liés :

1. le **geste** était injouable (`showAndWait`) ;
2. la **validation** vivait dans la vue, donc n'était pas testable non plus (elle devient un binding
   observable du ViewModel, vérifiable **sans IHM**) ;
3. leur **capture de documentation** était une **réplique** reconstruite à la main (`CaptureDialogues`),
   faute de `.fxml` - et elle **avait dérivé** : elle affichait un protocole « Point fixe » là où la vraie
   valeur est « PointFixeStandard ». La doc mentait, et rien ne pouvait le signaler.

Le refus métier y gagne aussi : il s'affiche **dans** la modale, à côté du champ fautif, **sans perdre la
saisie** - là où l'alerte d'après coup obligeait à tout ressaisir.

```java
// Écran : un champ final par porteur, un accesseur par porteur.
private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();
private final NotificateurModifiable notificateur = new NotificateurModifiable();

if (!confirmateur.confirmer("Supprimer ce site et ses points d'écoute ?")) {
    return;
}
viewModel.supprimerSite();
notificateur.notifier(NiveauNotification.INFORMATION, "Site supprimé", "…");

// Test de vue : le geste devient cliquable, et vérifiable JUSQU'À SON EFFET.
controleur.confirmateur().definir(message -> true);
controleur.notificateur().definir((niveau, entete, message) -> annonces.add(entete));
robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());
assertThat(sitesEnBase()).isEmpty();   // pas « un mock a été appelé » : la ligne a disparu
```

**La règle.** Jamais de `new Alert(...)`, de `FileChooser` ni de `DirectoryChooser` dans un contrôleur
ou une action. Et surtout, la formulation générale - c'est elle qui compte, pas la liste des ports :

> Une action ne devient testable que si **tous** ses dialogues sont remplaçables. Il suffit d'en
> **oublier un** pour que le geste reste hors de portée.

C'est ce qui avait échappé jusqu'à #1425 : le `Confirmateur` et le `Notificateur` ne suffisaient pas à
rendre la sauvegarde testable, parce qu'elle **commence** par un sélecteur natif. Le test s'arrêtait à
la première ligne. Deux ports sur trois, c'est zéro geste testable.

Deux pièges corollaires, tous deux rencontrés :

- **Un porteur que rien n'expose est mort-né.** `CartesPointsSite` fabriquait son propre
  `ConfirmateurModifiable` sans accesseur : le patron était là, mais aucun test ne pouvait le
  remplacer. Un porteur se **partage depuis l'écran**, il ne se recrée pas.
- **Une surcharge « production / test » est un troisième idiome pour le même besoin.** `audio` avait
  `lancer(…)` qui fabriquait le vrai dialogue et `lancer(…, Confirmateur)` pour les tests. Un écran,
  une paire de porteurs, partagée.

- **Un port qu'on croit manquant peut être un port déjà là.** `GestionnaireVues` semblait bloqué par son
  `TextInputDialog` ; il reçoit en fait son demandeur de nom **par constructeur** depuis toujours, et onze
  tests s'en servent avec un stub. Vérifier avant d'abstraire.

**Ce qui reste en dur** (et c'est légitime) : les **implémentations** des ports elles-mêmes
(`ConfirmationNavigation`, `NotificationDialogue`, `SelecteurFichierJavaFx`, `ChoixDansListe`,
`ChoixParBoutons`), et le **filet global** d'`App.java` (exceptions non capturées, #795) - le seul endroit
où le dialogue **est** la fonction.

**Le contre-exemple à connaître.** Un refus **prévenu par l'affordance** n'a pas de notification à
tester - il n'arrive jamais. Sur M-Site-detail, « Supprimer » est grisé quand un point porte des
passages (#789), et **JavaFX n'émet aucune action sur un bouton désactivé** (`Button.fire()` est un
no-op). Le `catch` du refus métier reste comme garde défensive, mais c'est le **grisage** que le test
doit vérifier : *on ne prévient pas après coup ce qu'on a déjà empêché.*

**Et ce qu'aucun test ne verra jamais.** Trois défauts de ce chantier n'ont été trouvés qu'en **regardant
une capture** : un libellé tronqué, un emoji qui ne se rend pas (#700), une réplique de dialogue qui avait
**dérivé du vrai écran**. Un geste testé n'est pas un écran regardé - [rendez la capture, et ouvrez-la](captures.md).

## Écrans de données : densité, badge, filtres (socle design partagé)

**Le problème.** Les onze écrans sont nés à des moments différents, sans référentiel de design commun :
tables plus ou moins denses, statuts tantôt en texte coloré tantôt en pastille, chaque feature
recopiant son CSS. Le chantier #686 a unifié la **famille « écrans de données »** (audio, multisite,
analyse, fiche site, qualification) sur un socle `commun/view`.

**La solution.** Trois briques réutilisables, plus une feuille de style chargée par tous les écrans :

- `commun.view.TableDonnees` : `uniformiser(table)` (et `uniformiserNavigable` pour une table qui
  répond au clavier) applique la classe CSS `table-donnees` (hauteur de ligne, padding, en-tête
  uniques). **Un appel unique** dans le contrôleur garantit la densité partagée ;
- `commun.view.ColonneBadge` : `cellule(Function<S, String> classe)` fabrique une cellule **pastille**
  dont la couleur est **dérivée de la donnée de la ligne** (jamais stockée). Les surcharges
  `classe(StatutWorkflow)` / `classe(Verdict)` couvrent les types de `commun.model` ;
- `commun.view.design.css` : jetons sémantiques (`-badge-succes/avertissement/danger/info/neutre-*`) et
  classes `.badge-*`, **chargée par tous les FXML** (plus de CSS de statut recopié par feature).

**Le piège d'architecture (mapping feature → classe CSS).** `ColonneBadge` vit dans `commun`, qui **ne
doit dépendre d'aucune feature** (règle `ArchitectureTest.features_sans_cycle`). Le socle ne connaît donc
que les enums de `commun.model`. Pour un statut **propre à une feature** (`Fraicheur` côté sites,
`StatutObservation` côté validation…), le mapping statut → classe CSS reste **côté feature**, et l'on
passe cette fonction au générique `cellule(Function)` :

```java
// sites : la vue mappe son enum
colStatut.setCellFactory(c -> ColonneBadge.cellule(LignePassage::statutClasseCss));
// audio : FormatLigneAudio.classeBadgeStatut(StatutObservation) -> "badge-observation-…"
col.statut().setCellFactory(c ->
    ColonneBadge.cellule(ligne -> FormatLigneAudio.classeBadgeStatut(ligne.statut())));
```

Les classes CSS correspondantes (`.badge-observation-*`, `.badge-frais/tiede/froid`) vivent quand même
dans `design.css` : ce ne sont que des **chaînes**, aucune dépendance de code de `commun` vers la feature.

**La règle.** Une nouvelle table de données réutilise `TableDonnees.uniformiser` + `ColonneBadge` ;
jamais une densité ni une pastille ad hoc. Un statut de `commun.model` passe par `ColonneBadge.classe` ;
un statut de feature reçoit un `classeBadge`/`classeCss` **côté feature** (jamais une surcharge dans
`commun`, sous peine de cycle).

## Icônes d'IHM : un pictogramme se pose, il ne s'écrit pas

**Le problème.** Les libellés portaient leurs pictogrammes **en toutes lettres** dans le `text` des
FXML : `♻ Réactivation`, `☰`, `📤 Exporter…`. Un caractère dépend des **polices installées** sur la
machine : selon le système il tombe en rectangle vide, en noir et blanc, ou en emoji couleur pleine
taille qui déséquilibre la ligne. Il ne se **teinte** pas non plus avec le texte, donc il ne peut
suivre aucun état. #700 avait posé la règle ; l'usage littéral était revenu, jusqu'à **35 glyphes sur
17 vues** au moment de #1933.

Le rendu est correct sur la machine qui écrit le code. C'est précisément pourquoi le défaut ne se
signale pas tout seul : il se voit sur les **aperçus régénérés en CI**, où plusieurs pictogrammes du
produit ne s'affichent pas du tout.

**La solution.** Un [`FontIcon`](https://kordamp.org/ikonli/) (pack FontAwesome 5) dans le `<graphic>`
du nœud, et le texte reste du texte :

```xml
<Label text="Reconstruire un passage manquant" styleClass="titre-page">
  <graphic><FontIcon iconLiteral="fas-cloud" styleClass="titre-page-icone"/></graphic>
</Label>
```

**Où passe la frontière.** Ce qui **désigne une action ou un objet** est une icône ; ce qui **vit dans
une phrase** reste un caractère. Mécaniquement, par bloc Unicode : les flèches (U+2190-U+21FF) et les
opérateurs mathématiques (U+2200-U+22FF) sont de la typographie (`A → B`, `≥ 1 mois`), tout le reste
est un pictogramme. Un signe typographique **seul** sur un nœud retombe du côté icône : c'est un
bouton à icône qui n'a pas dit son nom. Le raisonnement complet est dans
[ADR 0035](decisions/0035-un-pictogramme-est-une-icone-pas-un-caractere.md).

**Trois pièges.**

- **`-fx-text-fill` ne colore pas un `FontIcon`** : c'est `-fx-icon-color`. En convertissant un
  caractère en icône dans un contrôle déjà stylé, la **taille** est conservée (une `FontIcon` suit le
  `-fx-font-size` hérité, au même titre que `-fx-icon-size`), mais la **couleur retombe au noir**. La
  substitution compile, les tests passent, et l'icône est noire au milieu d'un contrôle gris ardoise.
  Mesures et garde-fou dans
  [ADR 0035](decisions/0035-un-pictogramme-est-une-icone-pas-un-caractere.md).
- **Une icône se réévalue comme son libellé.** Une entrée de menu dont le texte change d'état (« Se
  connecter… » / « Vigie-Chiro : pseudo ») doit changer d'icône avec lui. `ConstructeurMenuOutils`
  réévalue les deux à chaque `setOnShowing`, en réutilisant le `FontIcon` en place.
- **Un `promptText` est une chaîne**, il n'accueille pas de nœud. Une loupe dans un champ de recherche
  se pose **à côté** du champ, ou pas du tout.

**La règle.** Aucun pictogramme littéral dans un FXML : `PictogrammesFxmlTest` échoue dessus. La
**CLI** est hors sujet — une console ne rend pas de `FontIcon`, `⚠` y est le seul moyen d'écrire un
avertissement. Les libellés bâtis **en Java** (#1564) ont été repris depuis : les glyphes de
**sévérité** par #2036 puis #2188/#2221, qui les font dériver du type plutôt que de les écrire.

**Un pictogramme d'état n'est pas une sévérité, mais il se pose pareil.** La colonne « Écouté » de la
qualification rendait `ecoutee() ? "✓" : "○"` — deux pictogrammes dans une chaîne, qui ne disent
pourtant aucune sévérité (ce ✓ veut dire « écouté », pas « succès »). `MarqueurEcoute` les **pose** par
un `cellFactory`, comme la colonne badge de verdict : la **forme** distingue les deux états (un `CHECK`
plein, un cercle creux), la couleur reste neutre. Choisir un glyphe **différent** de celui de la
sévérité est délibéré — réutiliser `CHECK_CIRCLE` aurait fait lire « succès » là où on dit « fait »
(#2237).

## Actions de ligne d'une table : double-clic et menu contextuel (socle `commun`)

**Le problème.** Les neuf tables de l'application ont grandi séparément, et leurs gestes avaient
divergé. Le **menu contextuel** se réduisait partout à « Colonnes… », sauf la table des espèces de
l'Inventaire qui y ajoutait « Fiche de l'espèce » ; trois tables (dépôt du Lot, Audit, Importation)
n'avaient **aucun** menu. Le **double-clic** ouvrait selon l'écran l'écoute, le passage, ou rien. La
même action s'atteignait par deux gestes différents d'un écran à l'autre, et les actions de ligne
vivaient en boutons ou dans le ☰, jamais sous le curseur. Le chantier #1792 a unifié tout cela.

**La solution.** Quatre briques de `commun/view`, composables avec le `GestionnaireColonnes` existant :

- `DoubleClicLigne.installer(table, action)` : pose une `rowFactory` qui déclenche `action` au
  double-clic **sur une ligne remplie**. Le même geste installe aussi la sélection au **clic droit**,
  qui cible la ligne survolée **sans casser une sélection multiple** en cours ;
- `MenuLigne.item(libelle, table, action)` : un `MenuItem` lié à la sélection, désactivé quand elle est
  vide ;
- `MenuCopier.creer(table, Entree...)` : le sous-menu « Copier ▸ », chaque `Entree(libelle, valeur)`
  extrayant une chaîne de la ligne, déposée dans le presse-papier système (`PressePapier`) ;
- `ActionVigieChiroPassage.item(table, idPassage)` : ouvre la page de la participation du passage de la
  ligne, désactivé avec son motif quand le passage n'est pas lié à la plateforme.

Les items se passent en varargs à `GestionnaireColonnes.installerClicDroit` /
`installer` / `installerEtPersister`, **seul propriétaire des menus contextuels** de production : il les
compose et referme toujours la liste par « Colonnes… ».

**La grammaire du menu, stable d'un écran à l'autre.** L'ordre porte du sens ; on ne le réarrange pas
au gré des écrans :

```
Action principale de la ligne     (miroir du double-clic)
Actions secondaires               (Vigie-Chiro, auditer…)
──────────
Validation ▸                      (sous-menu, si l'écran valide)
Copier ▸
──────────
Colonnes…                         (toujours en dernier)
```

**Le double-clic est le miroir de l'action principale**, jamais une action qu'on ne trouve nulle part
ailleurs : il n'a aucune affordance propre, donc tout ce qu'il déclenche doit rester atteignable par un
chemin visible.

**Le piège (un geste sans état ne peut pas être muet).** Un `MenuItem` montre son état **avant** le
clic : indisponible, il se grise et porte le motif dans son libellé (#789). Un double-clic n'a rien à
montrer avant le geste, donc son silence ne se distingue pas d'une panne - c'est exactement ce qui a
été remonté de l'usage réel (#1834, #1837). Une action ouverte au double-clic doit donc **rendre
compte** quand elle n'aboutit pas : `ActionFicheEspece.ouvrir` rend un booléen, et
`ouvrirOuSignaler(espece, siAucuneFiche)` route le motif vers le canal de l'écran. Voir
[ADR 0021](decisions/0021-double-clic-miroir-qui-rend-compte.md).

**Le véhicule du motif.** `commun.viewmodel.RetourOperation` (texte + `commun.model.Severite`) et
`commun.view.BandeauRetour.installer(...)` rendent ce retour dans un bandeau **non modal** -
véhicule **par défaut** de tout compte rendu, le modal étant réservé à l'irréversible
([ADR 0023](decisions/0023-rendre-compte-bandeau-par-defaut-modal-si-irreversible.md)) :
un double-clic est un geste courant et souvent accidentel, une boîte modale y serait pire que le
silence. Le style vit dans `design.css` sous `.bandeau-retour`, que tous les écrans chargent déjà.

**La règle.** Une nouvelle table de données pose `DoubleClicLigne.installer` sur son action principale,
compose son menu par le `GestionnaireColonnes` dans l'ordre ci-dessus, et donne à toute action ouverte
au double-clic un moyen de dire pourquoi elle n'a rien fait.

## Rendre compte d'une opération

**Le problème.** « Message » est un mot commode qui recouvre trois choses différentes, et un écran qui
les mélange finit par mentir. L'EPIC #1870 a migré onze écrans et en a trouvé les trois formes.

**Les trois natures, et leur véhicule.**

| Nature | Ce que c'est | Véhicule | Se ferme ? |
|---|---|---|---|
| **État** | ce qui *est* (« Passage déposé le… », « Cohérence : corrigez les contrôles ») | libellé permanent, adossé à ce qu'il décrit | non |
| **Retour d'opération** | ce qui *vient de se passer*, en **une phrase bornée** | `RetourOperation` + `BandeauRetour` ou `LibelleRetour` | oui |
| **Compte rendu** | ce qui *vient de se passer*, de manière **extensible** : des constats, leurs détails | `CompteRendu` + `VueCompteRendu`, dans sa propre zone | non, il se remplace |
| **Travail en cours** | ce qui *se passe* | barre de progression, `IndicateurOccupation`, barre de statut | sans objet |

⚠ **Le mot « compte rendu » a changé de sens** avec l'[ADR 0031](decisions/0031-un-retour-n-est-pas-un-compte-rendu.md). Il désignait ici ce qui s'appelle désormais **retour d'opération**. Le critère qui les sépare n'est pas la longueur actuelle mais l'**extensibilité** : un message qui concatène une partie variable est déjà un compte rendu, et un compte rendu n'a **jamais** sa place dans un bandeau - l'y loger revient à le tronquer.

**Comment choisir.** Si ce qu'il y a à dire peut grandir - une liste de refus, de fichiers, de passages -
c'est un compte rendu. Si c'est une phrase dont la forme est connue d'avance, c'est un retour.

Un état et un compte rendu ne partagent **jamais** de propriété, et un compte rendu ne se **déduit**
jamais d'un statut : le même statut est atteint en agissant *et* en ouvrant un écran déjà dans cet
état, et seul le premier mérite d'être rapporté. Voir
[ADR 0028](decisions/0028-un-etat-n-est-pas-un-compte-rendu.md).

**Les sévérités.** Elles vivent dans `commun.model.Severite` - le **modèle**, pas la vue : un constat
d'audit qualifie sa gravité sans rien savoir de l'affichage ([ADR 0038](decisions/0038-l-echelle-de-severite-a-quatre-niveaux.md), amendée par #2159).

`SUCCES` quand l'opération a abouti. `ERREUR` quand elle a échoué ou a été refusée par un service.
`AVERTISSEMENT` quand elle a **abouti** mais que quelque chose mérite l'attention : une nuit déjà
importée qu'on réimporte quand même, un dossier mélangeant deux enregistreurs. `INFO` pour tout le
reste, et ce « reste » est plus large qu'il n'y paraît :

- un **guidage** : l'utilisateur a quelque chose à faire, rien n'est cassé (« saisissez des nombres ») ;
- une **absence d'objet** : « rien à relever », « traitement déjà lancé » - rien n'a raté ;
- un **résultat partiel** : relevé incomplet, dépôt interrompu. Annoncer un succès mentirait sur ce
  qui est acquis, annoncer une erreur nierait ce qui est passé.

**La sévérité ne s'écrit jamais dans le texte.** Le constructeur compact de `RetourOperation` refuse un
message ouvrant par `⚠ ✓ ✗`. La vue la rend **deux fois** - couleur et icône - depuis la valeur, par
`IconesSeverite`, table unique partagée par le bandeau, le compte rendu et le libellé inline. Un glyphe
dans la chaîne la dirait une troisième fois sans garantie d'accord ([ADR 0035](decisions/0035-un-pictogramme-est-une-icone-pas-un-caractere.md) point 5).

**Une confirmation transporte une structure, pas une chaîne.** Le port `Confirmateur` accepte un
`CompteRendu` autant qu'un `String` (`confirmer(CompteRendu)`, #2060) : la modale de confirmation rend
alors la structure de `VueCompteRendu` - un `Label` par détail, le retrait porté par le CSS - au lieu
d'aplatir une liste à puces dans une chaîne. Une puce aplatie perd son alignement dès qu'une ligne
dépasse la largeur du dialogue ; la structure ne le peut pas. Seule l'implémentation qui **rend** le
dialogue (`ConfirmationNavigation`) surcharge la méthode ; les stubs de test se contentent du repli
textuel par défaut.

**L'exception : les surfaces qui n'ont que du texte.** Un pictogramme se rend en icône *là où un
composant le rend*. Là où il n'y a que du texte - la **CLI** ([ADR 0035](decisions/0035-un-pictogramme-est-une-icone-pas-un-caractere.md)
point 6), une **info-bulle** dont l'avertissement ouvre une ligne interne (`Tooltip` n'accepte qu'un
`graphic`, en tête), un message imposé à un port en `String` - le glyphe littéral reste, et c'est
**assumé** (#2036). Le signe distinctif : aucun `Label` ni `FontIcon` ne peut s'y poser. Ce n'est pas un
oubli, c'est la limite de la surface ; on l'écrit à l'endroit du glyphe pour que le prochain lecteur ne
le « corrige » pas.

**Une barre de statut est neutre.** Elle dit *où l'on en est*, pas si c'est bien ou mal : ses phrases
s'écrivent sans marqueur, et ce qui doit alerter passe par un bandeau ou un encart
([ADR 0039](decisions/0039-une-barre-de-statut-est-neutre.md)).

**Le patron `Messages<Ecran>`.** Quand un écran porte un état **et** un compte rendu, les deux vivent
dans une petite classe dédiée plutôt que dans le ViewModel : `MessagesAudio`, `MessagesLot`,
`MessagesRattachement`. Elle expose les propriétés en lecture et des méthodes qui **nomment la
sévérité** (`succes`, `info`, `erreur`, `effacer`). Le ViewModel garde sa responsabilité d'orchestrer,
et le plafond `GodClass` du portail qualité s'en trouve mieux.

**Un collaborateur qui n'émet que des échecs** peut rester agnostique et recevoir un `Consumer<String>`,
la sévérité se décidant au point de jonction. **Un collaborateur qui émet plusieurs natures** reçoit la
messagerie et choisit lui-même : la lui faire deviner ailleurs reviendrait à réinterpréter ses messages
d'après leur texte.

**Le piège à connaître.** Un message de garde placé derrière un contrôle **grisé sur la même condition**
n'est jamais lu par personne. Cinq cas de ce genre existent dans l'application (#1970) : la garde et le
`disableProperty().bind(…)` testent le même prédicat. Aucun test ne le signale - c'est en essayant de
**produire une capture** du message qu'on s'en aperçoit.

## Unit of Work (`UniteDeTravail`)

**Le problème.** Par défaut, chaque écriture DAO s'auto-commit. Mais « créer un passage **et** sa
session » doit être **atomique** : si la 2ᵉ échoue, la 1ʳᵉ ne doit pas rester en base.

**La solution.** Regrouper les écritures dans **une transaction** : tout réussit (commit), ou tout est
annulé (rollback).

**Dans cette application.** [`UniteDeTravail`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/UniteDeTravail.java)
exécute un bloc sur **une seule connexion** :

```java
uniteDeTravail.executer(connexion -> {
    // plusieurs écritures... une exception => rollback
});
```

```mermaid
sequenceDiagram
    participant S as Service
    participant U as UniteDeTravail
    participant DB as Connexion
    S->>U: executer(co -> { écritures })
    U->>DB: autoCommit(false)
    U->>DB: écritures…
    alt tout réussit
        U->>DB: commit()
    else exception
        U->>DB: rollback()
    end
```

**Principes.** **SRP** (la gestion transactionnelle est isolée des DAO) ; garantit l'**intégrité** O7.

!!! note "Quand une seule transaction est impossible : compensation"
    `UniteDeTravail` regroupe des écritures **sur une même connexion**. Mais certaines opérations
    franchissent une frontière qui ouvre sa **propre** transaction - typiquement la reconstruction d'un
    passage (#1522), dont l'import des observations passe par un port qui gère sa transaction à lui (et
    SQLite n'a qu'un seul écrivain). Une transaction unique est alors infaisable. On garde malgré tout la
    règle « mieux vaut ne rien créer que créer à moitié » par **compensation** (*saga*) : si une étape
    échoue, on **défait** ce qui précède - ici via les clés étrangères `ON DELETE CASCADE` (supprimer le
    passage emporte sa session, ses séquences, ses observations, cf.
    [Modèle de données](modele-de-donnees.md)). Le résultat visible est celui d'une transaction : tout,
    ou rien.

---

## Matérialiser tard, consommer, libérer aussitôt

**Le problème.** Certaines étapes ont besoin de fichiers qui **n'existent pas encore** : les tranches
régénérées d'un brut, les archives ZIP d'un dépôt. Tout produire d'abord, tout consommer ensuite est la
lecture naturelle - et c'est celle qui fait exploser le disque, précisément sur les nuits volumineuses
que ces opérations visent. Régénérer une nuit entière pour libérer de la place commencerait par doubler
l'occupation qu'on cherchait à réduire.

**La solution.** Ne matérialiser qu'**au moment de consommer**, et libérer **dès que la consommation
est acquise**. Le pic n'est plus la somme des ressources produites, mais le nombre de ressources
vivantes à un instant donné - un nombre que l'on **borne explicitement**.

La libération n'est donc pas de l'hygiène : **c'est elle qui borne le pic**. C'est ce qui distingue ce
patron d'un simple `try`/`finally` de nettoyage.

**Dans cette application.** Le patron est écrit quatre fois, dans trois features :

| Où | Matérialise | Libère | Ce qui borne le pic |
|---|---|---|---|
| [`DecoupageParallele`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/importation/model/DecoupageParallele.java) | un sous-dossier `.tmp-decoupage/<i>` par original | après le nommage définitif | le `Semaphore` de la campagne |
| [`ReactivationDepuisBruts`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/model/ReactivationDepuisBruts.java) | `DossierTemporaire.creer("vc-regen-…")` | en `finally`, après rebranchement | **un brut à la fois** |
| [`HydratationDepuisBruts`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/model/HydratationDepuisBruts.java) | un temporaire par brut, sur un fil d'`ExecutionParallele` | en `finally` | le `Semaphore` d'`ExecutionParallele` |
| [`SourceArchivesRegenerables`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/lot/model/SourceArchivesRegenerables.java) | une archive ZIP à la résolution | après le commit `DEPOSE` | la fenêtre 2, relayée par `parallelismeMax()` |

```mermaid
sequenceDiagram
    participant M as Moteur
    participant S as Source / temporaire
    participant C as Consommateur
    loop dans la limite de la borne
        M->>S: matérialiser (tard, faillible)
        S-->>M: chemin
        M->>C: consommer
        alt consommation prouvée
            M->>S: libérer
        else échec
            Note over S: reste sur le disque,<br/>la reprise le retrouve
        end
    end
```

**La libération suit la preuve, jamais la tentative.** Dans le dépôt, `source.liberer` est appelé
*après* le commit qui marque l'unité `DEPOSE`, jamais avant : une coupure entre les deux laisserait une
unité ni en ligne ni sur le disque. La reprise la régénérerait, mais on aurait perdu la preuve de
l'envoi.

**Principes.** **SRP** (qui produit la ressource sait la libérer) ; borne le pic disque, objectif O3.

!!! note "Deux politiques d'échec de libération, et c'est voulu"
    Trois des quatre occurrences **avalent** l'échec de libération : ne pas avoir pu rendre de la place
    n'est pas une raison de faire échouer une opération par ailleurs réussie. Le reliquat sera repris
    par « Libérer l'espace disque ». C'est le contrat que portent
    [`DossierTemporaire.supprimer`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/model/DossierTemporaire.java)
    (« efface **au mieux** ») et `SourceDepot.liberer`, formulé deux fois de façon indépendante.

    `DecoupageParallele` **échoue dur**, et c'est la bonne asymétrie : son temporaire n'est pas un
    reliquat mais une **étape du pipeline**. Le nommage définitif lit ce dossier
    ([ADR 0026](decisions/0026-le-nommage-des-tranches-est-une-etape-du-pipeline.md)) ; un temporaire
    survivant fausserait la découpe suivante au lieu de simplement occuper de la place.

    La règle : **avaler si la ressource n'est qu'un coût, échouer si elle porte du sens.**

!!! warning "Ce qui n'est pas ce patron : la ressource de session"
    `ImportationViewModel` extrait aussi un ZIP dans un temporaire, mais celui-ci est un **champ** qui
    survit à plusieurs interactions, se libère sur des **transitions d'écran** et non à la fin d'une
    consommation, et s'accompagne d'un filet anti-fuite balayant les résidus au démarrage.

    C'est une **ressource de session**, gouvernée par le cycle de vie d'un écran. Lui appliquer ce
    patron-ci la libérerait sous les pieds de l'utilisateur. Un patron sans frontière se fait invoquer
    à tort.

Trois ADR décidaient déjà ce patron, chacune pour sa feature et sans se citer :
[0026](decisions/0026-le-nommage-des-tranches-est-une-etape-du-pipeline.md) (temporaire vidé après
chaque brut), [0032](decisions/0032-le-plan-precede-l-ecriture.md) (résolution tardive et faillible,
`liberer` no-op par défaut), [0033](decisions/0033-la-fenetre-borne-le-disque.md) (fenêtre bornée, la
libération suit la preuve). Cette section est ce qui les relie.

---

## Observer (propriétés et *binding* JavaFX)

**Le problème.** Comment garder l'IHM **synchronisée** avec l'état sans que le modèle « pousse » vers
des widgets qu'il ne devrait pas connaître ?

**La solution.** Le sujet (une `Property` / `ObservableList`) **notifie** ses observateurs au
changement ; la vue **s'abonne** par *binding*. Le sujet ignore qui l'observe.

**Dans cette application.** Le viewmodel expose des propriétés ; la vue s'y lie. Quand l'état change, l'IHM se
met à jour **toute seule** : la vue *observe*, elle ne *tire* pas. C'est le moteur de MVVM.

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant P as Property
    participant V as Vue
    VM->>P: set(nouvelle valeur)
    P-->>V: notifie (invalidation)
    V->>V: met à jour le widget
```

**Principes.** **Faible couplage** View↔ViewModel et **DIP** (la vue dépend d'abstractions
observables, pas de logique).

!!! note "API fluente (le « builder » le plus proche)"
    Les liaisons s'écrivent souvent avec l'**API fluente** de JavaFX :
    `Bindings.when(cond).then(a).otherwise(b)`, `Bindings.createStringBinding(...)`. C'est un
    *builder* conditionnel **fourni par la bibliothèque** — pas un patron Builder que nous
    implémentons. Le projet n'a d'ailleurs **pas de Builder maison** : les entités sont des `record`
    immuables (cf. *Objets-valeurs*), qui rendraient un builder superflu.

---

## Factory (`controllerFactory`)

**Le problème.** Par défaut, `FXMLLoader` crée les controllers avec `new` (constructeur vide) : ils ne
peuvent **pas** recevoir de dépendances injectées.

**La solution.** Fournir au loader une **fabrique** qui délègue la création à Guice.

**Dans cette application.** `loader.setControllerFactory(injector::getInstance)` : chaque controller est
instancié **par le conteneur**, donc reçoit ses ViewModels/services par constructeur (cf.
[`App`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/App.java)).
Diagramme de séquence du bootstrap : [Injection](injection.md#des-controllers-fxml-injectes).

**Principes.** **DIP** (le controller ne construit pas ses dépendances) et **IoC**.

---

## Machine à états (`MoteurWorkflowPassage`)

**Le problème.** Le statut d'un passage (`Importé → … → Déposé`) doit avancer **dans l'ordre** : on ne
doit ni sauter une étape (importer puis déposer) ni revenir en arrière. Disséminer ces règles dans les
services serait fragile.

**La solution.** Centraliser les **transitions autorisées** dans un objet dédié : depuis un état, une
seule cible permise (le successeur immédiat).

**Dans cette application.** [`MoteurWorkflowPassage`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/model/MoteurWorkflowPassage.java)
porte l'ordre et expose `suivant(...)` / `estTransitionAutorisee(...)` / `exigerTransitionAutorisee(...)`.
La logique est **isolée** de l'énum
[`StatutWorkflow`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/StatutWorkflow.java)
(simple porteur de libellés).

```mermaid
stateDiagram-v2
    [*] --> IMPORTE
    IMPORTE --> TRANSFORME
    TRANSFORME --> VERIFIE
    VERIFIE --> PRET_A_DEPOSER
    PRET_A_DEPOSER --> DEPOSE
    DEPOSE --> [*]
```

**Principes.** **SRP** (les règles de transition ne polluent ni l'énum ni les services) et un **point
de vérité unique** pour l'avancement d'une nuit.

---

## Synthèse : où vit chaque principe SOLID

| Principe | Incarné surtout par |
|---|---|
| **S**RP | MVVM (couches), DAO, `UniteDeTravail`, `MoteurWorkflowPassage`, Facade, objets-valeurs |
| **O**CP | Contrats `Ouvrir*`, Multibinder d'accueil, Template Method, Strategy |
| **L**SP | Sous-types de `DaoGenerique` substituables |
| **I**SP | Interfaces de rôle fines (`GardeQuitter`, `RafraichirAuRetour`, `EmplacementNavigation`) |
| **D**IP | Injection + Composition Root, contrats `Ouvrir*`, *binding* observable, Factory |

## Au-delà de SOLID

SOLID n'est pas seul : l'architecture respecte aussi plusieurs principes transverses, eux aussi
visibles dans le code.

### Loi de Déméter (« ne parle qu'à tes amis proches »)

Un objet ne devrait appeler que les méthodes de **lui-même**, de ses **paramètres**, de ce qu'il
**crée** et de ses **champs directs** — pas de chaîne `a.getB().getC().faire()`.

**Ici.** La vue se lie à `vm.titreProperty()` (un collaborateur **direct**), jamais à
`vm.modele().site().nom()`. Les contrats `Ouvrir*` reçoivent un `ContexteSite` / `ContextePassage`
(données **passées en paramètre**) plutôt que de fouiller dans l'écran appelant. Et `view_sans_jdbc`
interdit à la vue de « traverser » les couches jusqu'à la base.

### YAGNI (« vous n'en aurez pas besoin »)

Ne pas construire de **généricité spéculative**.

**Ici.** Pas d'ORM (des DAO `PreparedStatement` directs) ; le workflow est une simple `List` ordonnée
(`suivant()` = `index + 1`), pas un moteur d'états générique ; `DaoGenerique` n'offre que les
opérations **réellement** communes (lecture/suppression), les `insert`/`update` n'étant écrits que là
où on en a besoin ; l'application étant mono-utilisateur, `idUtilisateurCourant` est simplement le
premier utilisateur (aucune machinerie d'authentification construite « au cas où »).

### KISS (« reste simple »)

**Ici.** SQLite fichier (pas de serveur), tests headless **en mémoire** (pas de `xvfb`), capture par
`Scene.snapshot()` (pas d'orchestration lourde).

### DRY (« ne te répète pas »)

**Ici.** `DaoGenerique` (Template Method) et `RowMapper` (Strategy) factorisent la boucle `ResultSet`
écrite **une seule fois** ; les sections communes de doc renvoient à une source unique.

### Tell, Don't Ask

Demander à un objet d'**agir**, plutôt que de lire son état pour décider à sa place.

**Ici.** `MoteurWorkflowPassage.exigerTransitionAutorisee(actuel, cible)` **vérifie et lève** si la
transition est interdite, au lieu d'exposer l'ordre pour que chaque appelant le re-teste.

### Composition plutôt qu'héritage

**Ici.** Le chrome **compose** des capacités via de petites interfaces optionnelles (ISP) détectées à
l'exécution, et l'injection **compose** le graphe d'objets — au lieu d'une hiérarchie de classes
profonde. (`DaoGenerique` reste un héritage **assumé**, limité au Template Method.)

### Convention plutôt que configuration

**Ici.** Les `.fxml`/`.css` à côté de leur controller, les paquets de test en **miroir** de la
production, le `captures.manifest`, les noms `Capture*` / `Navigation*` / `*Module` : autant de
conventions qui évitent de la configuration.

### Fail-fast

**Ici.** `exigerTransitionAutorisee` lève **tôt** ; `Objects.requireNonNull(...)` garde les
constructeurs ; `DataAccessException` remonte une erreur SQL sans la masquer.
