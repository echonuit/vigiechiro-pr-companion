# Observabilité

Un incident doit laisser une **trace inspectable**, même quand son message est nul. Avant #1523 ce
n'était pas le cas : slf4j était en `slf4j-nop` (test), les quelques `Logger` du code écrivaient à FINE
(invisibles par défaut) et aucun fichier de log n'était produit - après un plantage, rien à regarder.

## Backend : java.util.logging

Le choix s'est porté sur **java.util.logging (JUL)** plutôt que slf4j+logback :

- **zéro dépendance** ajoutée et **zéro changement du graphe de modules** (JUL vient de la plateforme,
  déjà tiré transitivement par `java.sql`) ;
- cohérent avec le **packaging classpath** (shade + jpackage ; le jlink modulaire est de toute façon
  impossible à cause des modules automatiques de Guice, cf. [CI/CD et release](ci-cd-release.md)).

`ConfigurationJournalisation.configurer(dossierLogs)` installe, **une fois** au démarrage :

- un **fichier tournant** dans `<workspace>/logs/` (5 fichiers de 2 Mo), pour garder la trace **après**
  l'incident, même l'application fermée ;
- le niveau **FINE** sur `fr.univ_amu.iut`, capté par le fichier ; la **console** reste à INFO (pas de
  bruit à l'écran).

Elle est amorcée à `App.main` **et** `Cli.main` (IHM et CLI). L'amorçage est dans `main`, **pas**
`start()` : les tests (qui appellent `start()` directement) n'installent donc aucun fichier de log. La
sortie **console** des tests reste propre elle aussi : de nombreux tests exercent *volontairement* les
chemins d'échec, où `JournalisationTache` émettrait des `SEVERE` + traces d'apparence alarmante mais
normales ; une configuration JUL de test (`src/test/resources/logging-tests.properties`, pointée par
`maven-surefire`) coupe ce seul logger pendant les tests, sans rien changer en production (#1560).

!!! note "Workspace"
    Le dossier est résolu par `Workspace.dossierLogs()` (`<workspace>/logs/`), comme le reste : aucun
    chemin n'est codé en dur ailleurs (cf. [Persistance](persistance.md)).

## Point de passage : ExecuteurTache

Presque toute tâche de fond passe par `ExecuteurTache` (réseau, base ; cf.
[Patterns et principes](patterns.md)). Ses deux implémentations routaient le `Throwable` vers le
callback `echec` **sans le loguer** : un échec à message nul disparaissait. Désormais
`JournalisationTache.consigner(...)` le journalise **avant** l'affichage à l'écran, au **seul** point par
lequel tout passe.

La journalisation **distingue la nature** de l'échec, pour ne pas noyer le signal :

| Nature | Niveau | Trace ? |
|---|---|---|
| Annulation (`OperationAnnuleeException`) | FINE | non |
| Refus métier (`RegleMetierException` : point inconnu, analyse non terminée…) | FINE | non |
| **`Throwable` inattendu (bug)** | **SEVERE** | **oui** |

Les refus et annulations - des issues **normales** d'une opération longue - restent donc discrets, et
seul un **vrai** incident part en SEVERE avec sa trace : exactement la classe de bug qu'on ne voyait
pas. Le filet d'exceptions non capturées d'`App`
(`Thread.setDefaultUncaughtExceptionHandler`) journalise de même **avec la trace**, au lieu d'un
`printStackTrace` perdu en console.

## Les échanges avec l'API (#1845)

Le filet ci-dessus couvre les **tâches** ; il ne voyait pas le **réseau**. Le journal ne portait aucune
ligne mentionnant `participation`, `PATCH` ni le moindre statut HTTP. Face à « l'application dit
*envoyées*, la plateforme n'affiche rien » (#1844), il ne permettait de trancher **aucune** hypothèse :
le diagnostic a dû se faire en lisant les sources du serveur et du front : hors de portée d'un
utilisateur, et impossible sur le terrain.

`TransportVigieChiro` consigne désormais chaque échange : **méthode, chemin, issue, durée**. Le point
d'instrumentation est le filet commun `emettre`, qui voit déjà passer GET, POST, PATCH et PUT et trie
leur issue en `ReponseApi` ([ADR 0007](decisions/0007-retours-http-type-scelle-reponse-api.md)) : un
seul endroit pour tout couvrir. Le dépôt S3, qui ne passe pas par lui (corps binaire, délai long), se
consigne lui-même.

La sévérité se décide **à l'émission**
([ADR 0008](decisions/0008-aucun-echec-silencieux-severite-a-l-emission.md)) :

| Issue | Niveau | Pourquoi |
|---|---|---|
| Succès | FINE | échange nominal : capté par le fichier, absent de la console |
| Non connecté | FINE | appel non émis faute de jeton : ce n'est pas une anomalie |
| Injoignable | WARNING | anomalie, visible sans réglage |
| Refusé | WARNING | idem, **avec le corps de la réponse** |

Le **corps d'un refus** est consigné, tronqué à 300 caractères : c'est l'explication du serveur
(`_issues`, « invalid field »…), l'élément le plus diagnostique qui soit : et précisément ce qui
manquait pour comprendre #1844.

!!! warning "Ce qu'un journal ne doit jamais contenir"
    Le jeton et les en-têtes ne sont **jamais** journalisés, ni le corps envoyé. L'**URL complète**
    non plus : une URL S3 **pré-signée porte sa signature dans sa requête**. On journalise donc le
    **chemin seul** : la fuite est réglée par construction, pas par vigilance. Un journal doit pouvoir
    être joint à un signalement d'anomalie sans divulguer de secret (cf. [Sécurité](securite.md)).

## Accès utilisateur

Le menu ☰ → **« Ouvrir le dossier des journaux »** (une `ActionMenu` socle du groupe Maintenance, cf.
[Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md)) ouvre `<workspace>/logs/` dans le
gestionnaire de fichiers : l'utilisateur retrouve la trace d'un incident et la joint à un signalement.

## Dette soldée

L'audit de suite (#1543, **clos**) a résorbé les points restants : les opérations de fond lourdes
(import et publication VigieChiro, relevé d'analyses, rattachement, lancement du traitement serveur)
montrent désormais un **voile d'occupation** ou un repère « … en cours » (cf.
[Patterns et principes](patterns.md)), et les deux callbacks d'échec muets sont traités - l'un routé
vers le filet d'erreurs de son écran, l'autre *fire-and-forget* assumé à la fermeture de la modale mais
**journalisé** au point de passage.

Cette page a ensuite conclu, pendant un temps, qu'aucune dette d'observabilité ne restait : alors que
la **couche réseau était muette** (§ « Les échanges avec l'API »). La leçon vaut d'être gardée : un
audit d'observabilité ne prouve rien sur ce qu'il n'a pas pensé à regarder. La question utile n'est pas
« mes journaux couvrent-ils mes échecs ? » mais « **face à ce symptôme, le journal me permet-il de
trancher ?** ». Ici, le symptôme était un succès.
