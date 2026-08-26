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

**Ce qui est consigné vit dans `commun.api.JournalEchange`** (#2734) : la sévérité, le résumé, la
cause lisible et la troncature du corps d'un refus. Le transport **émet** et appelle ; il ne décide
plus de la forme du journal. Chercher « comment se fabrique cette ligne » mène donc à `JournalEchange`,
pas au transport.

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

Le menu principal (☰) → **« Ouvrir le dossier des journaux »** (une `ActionMenu` socle du groupe Maintenance, cf.
[Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md)) ouvre `<workspace>/logs/` dans le
gestionnaire de fichiers : l'utilisateur retrouve la trace d'un incident et la joint à un signalement.

## Ce que l'utilisateur lit d'une exception (#3470, #3947)

Le journal reçoit la trace complète ; l'alerte, elle, ne montre **qu'une phrase**, et cette phrase se
compose par [`CauseLisible.messageDe`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/CauseLisible.java)
([ADR 3470](decisions/3470-un-message-d-erreur-ne-montre-jamais-le-nom-de-son-enveloppe.md)),
**des deux côtés** : le filet de l'IHM comme celui de la ligne de commande
([ADR 3947](decisions/3947-un-message-montre-a-l-utilisateur-se-compose-en-un-seul-endroit.md)).

**Le défaut qu'elle ferme.** Un utilisateur a vu, pour tout diagnostic :

> `java.lang.reflect.InvocationTargetException`

La chaîne n'était pas absente, elle était **exacte et sans valeur**. Rien ne rougissait, parce qu'un
texte non vide a l'air d'un message.

**Et ce n'est pas propre à la réflexion.** `RuntimeException(Throwable)` - comme **tous** les
constructeurs `(Throwable)` de la bibliothèque standard - pose comme message le `toString()` de sa
cause. La même chaîne inutile sort de n'importe quelle enveloppe.

**La règle** : on descend la chaîne des causes et l'on retient le **dernier message informatif**, en
écartant celui qu'une enveloppe a fabriqué. Ce n'est **pas** « prendre la cause racine » : la plus
profonde peut être un `NullPointerException` muet, et dérouler jusqu'au bout **appauvrirait** l'alerte
en ayant l'air de la corriger. Quand toute la chaîne est muette, le repli nomme le **type court** et
renvoie au journal, jamais `null` ni un nom pleinement qualifié.

**Deux formes à ne pas réécrire à la main**, parce qu'elles produisent exactement ce que la règle
interdit :

```java
echec.getMessage() != null ? echec.getMessage() : echec.toString();      // « java.lang.XxxException »
echec.getMessage() == null ? echec.getClass().getSimpleName() : ...      // idem, en plus court
echec.getCause() != null ? echec.getCause().getMessage() : ...           // ne déroule que d'un cran
```

Elles sont **comptées** : `scripts/adr/3947-message-enveloppe.py` porte un cliquet, et il ne descend
jamais tout seul.

**Chaque surface passe son « où regarder ».** Le repli qui nomme le journal renvoie vers
`menu principal > Ouvrir le dossier des journaux` à l'écran, et vers le dossier `logs/` en ligne de
commande. Un terminal n'a pas de menu principal : lui en désigner un produirait un message non vide,
donc d'apparence correcte, et inapplicable. C'est le défaut de l'ADR 3470 déplacé d'un cran par sa
propre correction, et c'est pourquoi `OU_REGARDER_IHM` et `OU_REGARDER_CLI` sont deux constantes.

Tout filet qui montre une exception à l'utilisateur passe par `CauseLisible`.

## Quand c'est le MESSAGE qui explose (#3956)

Le cas précédent suppose qu'on puisse **lire** le message. Sous Java 25, une panne d'injection Guice
n'en offre pas : `ProvisionException.getMessage()` cherche les numéros de ligne pour composer son
rapport, lit du bytecode **major 69** avec l'ASM embarqué de Guice 7.0.0, et lève
`IllegalArgumentException: Unsupported class file major version 69`.

**La pile se lit à l'envers de ce qu'on croit.** Guice a d'abord échoué à fournir quelque chose ;
c'est en **racontant** cet échec qu'il a explosé. Le message n'annonce pas le défaut, il annonce la
panne du dispositif qui devait l'annoncer - et le vrai défaut est à trois `Caused by` de là. Guice le
dit une ligne plus haut, et personne ne la lit :

```
AVERTISSEMENT: Failed loading line numbers. ASM is probably out of date.
```

**Ce que le dépôt fait.** `SignalementIncident` garde le chemin riche - il porte la pile entière et
sert dans tous les autres cas - et **retombe** sur une description construite à la main quand le
formatage lève. Chaque cause y est lue défensivement :

```
Exception non capturée sur le fil « JavaFX Application Thread ». Son rapport n'a pas pu être formaté
(java.lang.IllegalArgumentException : Unsupported class file major version 69). La chaîne des causes,
lue sans le formateur :
    → com.google.inject.ProvisionException : message illisible (java.lang.IllegalArgumentException)
        à fr.univ_amu.iut.App.start(App.java:102)
    causé par java.lang.Error : Unresolved compilation problem:
        à fr.univ_amu.iut.audit.view.ActionResetGuide.<init>(ActionResetGuide.java:1)
```

On perd la pile complète ; on garde le **défaut**. Le parcours des causes est borné à douze : une
chaîne n'est pas garantie acyclique une fois que des `initCause` s'en mêlent, et un filet qui
bouclerait ici referait #3700 par un autre chemin.

**À la main, pour aller plus vite.** Le drapeau désarme le formateur de Guice et fait paraître la
cause immédiatement :

```bash
./mvnw javafx:run -Dguice_include_stack_traces=OFF
```

**Le défaut est en amont.** L'ASM de Guice 7.0.0 ne lit pas major 69 ; une version qui le lit
règle tout, et rien de ce qui précède n'est perdu - le repli ne se déclenche que si le formatage
échoue.

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
