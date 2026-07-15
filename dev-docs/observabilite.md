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
`start()` : les tests (qui appellent `start()` directement) n'installent donc aucun fichier de log et
restent silencieux.

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

## Accès utilisateur

Le menu ☰ → **« Ouvrir le dossier des journaux »** (une `ActionMenu` socle du groupe Maintenance, cf.
[Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md)) ouvre `<workspace>/logs/` dans le
gestionnaire de fichiers : l'utilisateur retrouve la trace d'un incident et la joint à un signalement.

## Dette connue

Quelques opérations de fond lourdes restent **sans progression** (barre) et deux callbacks d'échec ne
rendent **aucun message** à l'écran - l'incident est tout de même tracé. Suivi dans l'issue d'audit
#1543.
