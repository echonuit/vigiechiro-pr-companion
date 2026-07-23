# ADR 1038 - La seule configuration qui vive hors de la base est celle qui dit où la base se trouve

- **Statut** : Accepté - 2026-07-22
- **Chantier** : #1038 (choisir où vit la base `.db`, sans déménager l'audio), dernier lot de l'EPIC #2258
- **Vérification** : certaine — `WorkspaceTest#resolu_lit_la_configuration_persistee`
- **Suit** : [ADR 0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md), qui a réduit ce chantier à la base seule
- **Absorbe** : #2187 (les drapeaux de fonctionnalités sont lus avant les migrations)

## Contexte

Tous les réglages de l'application vivent dans la table `app_setting`, donc **dans la base**. C'est la
bonne place pour tout ce qui se règle : un seul endroit, sauvegardé avec le reste, restauré avec le
reste.

Un réglage échappe à cette règle par construction : **où se trouve la base**. On ne peut pas demander à
la base où elle est, et aucun degré d'ingéniosité ne referme cette boucle.

Le besoin est apparu par élimination. #1038 demandait à l'origine de rendre le dossier de travail
configurable, pour les gros volumes. L'[ADR 0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md)
a montré que ce dossier est un **cache** - les bruts se réimportent, les transformés se recalculent - et
que l'audio pouvait se **pointer** là où il vit plutôt que d'être rapatrié. Ce qui restait à relocaliser
n'était plus des centaines de gigaoctets, mais le seul artefact irremplaçable : la base.

Il existait déjà une surcharge, `-Dvigiechiro.workspace`. Elle ne suffit pas :

- elle ne **survit pas** au lancement, donc elle ne se règle pas, elle se relance ;
- elle est **inaccessible** à qui démarre l'application par son icône, c'est-à-dire à tout le monde ;
- sous Flatpak, elle ne peut désigner qu'un chemin déjà accordé au bac à sable, ce qui la vide de son
  intérêt (cf. `flatpak/README.md`).

## Décision

**Une configuration d'amorçage, dans un fichier, hors de la base, lue avant tout le reste.** Elle porte
exactement deux clés : `espace-de-travail` et `base`.

Elle vit là où le système le veut, et pas ailleurs :

| Système | Dossier |
|---|---|
| Windows | `%APPDATA%\vigiechiro\` |
| Ailleurs | `$XDG_CONFIG_HOME/vigiechiro/`, repli `~/.config/vigiechiro/` |

Honorer `$XDG_CONFIG_HOME` n'est pas de la courtoisie envers une convention : sous Flatpak, `~/.config`
est un chemin **masqué**, et cette variable désigne le dossier privé réellement accessible. Un
`~/.config` codé en dur donnerait une application incapable de se configurer une fois empaquetée, ce qui
est précisément le cas qu'on cherche à débloquer.

**L'ordre de résolution est : propriété système, puis configuration persistée, puis défaut.** La
propriété garde la priorité parce qu'elle sert aux tests et aux lancements ponctuels : un emplacement
demandé pour *cette* exécution ne doit jamais être repris par un réglage écrit une fois pour toutes.

**Aucun chemin n'est validé au démarrage.** Un fichier absent, illisible ou abîmé rend une configuration
**vide**, et l'application s'ouvre sur ses emplacements par défaut. Refuser de démarrer laisserait
l'utilisateur sans recours, puisque le seul outil pour corriger le fichier serait l'application
elle-même. L'accessibilité d'un emplacement se vérifie au moment où on le **choisit**, là où l'on peut
encore proposer autre chose.

**`Workspace` porte le chemin de sa base au lieu de le calculer.** Un second champ, un constructeur à
deux arguments, et le constructeur à un argument qui garde le comportement d'avant. Aucune signature
publique ne change - décision prise sur une mesure : `SourceDeDonnees` est construit **4 fois** dans
`src/main` et **96 fois** dans `src/test`. Un remède qui touche sa signature se paie en 96 modifications
de test sans valeur, et rend la revue illisible.

## Conséquences

**L'application gagne une phase d'amorçage explicite** : lire la configuration, migrer, puis composer
l'injecteur. C'est cette phase qui referme #2187 **par la cause** - les drapeaux de fonctionnalités ne
peuvent plus être lus avant les migrations, parce qu'il existe désormais un moment où l'on sait quelle
base ouvrir et où l'on n'a encore rien ouvert.

**Le dossier de travail devient configurable lui aussi**, ce que l'ADR 0048 avait rendu secondaire mais
pas indésirable. Le débloquer coûte une clé de plus dans un fichier qu'on lit déjà.

**Les sauvegardes ne changent pas de nature.** Leur emplacement était déjà **demandé** à chaque fois
(`choisirDossier` côté écran, `--dossier` côté CLI) ; `<workspace>/sauvegardes` n'en est que la
proposition initiale, et elle suit désormais le dossier choisi.

**Une configuration de test s'impose.** Sans redirection, la suite lirait la configuration réelle de qui
lance le build et passerait selon la machine. `vigiechiro.config` est posée par Surefire vers `target/`.

**Un chemin devient falsifiable là où il ne l'était pas.** La règle de placement est isolée dans une
fonction pure qui reçoit le système et les variables d'environnement, parce qu'on ne pose pas de
variable d'environnement depuis un test Java : sans cela, la règle Windows ne serait vérifiée sur aucune
machine du projet.

## Alternatives écartées

**`java.util.prefs`** (registre Windows, `~/.java` ailleurs), qui était la piste de la version d'origine
de #1038. Sous Flatpak, `~/.java` est masqué comme `~/.config` : le réglage disparaîtrait sans erreur, ce
qui est le pire des deux mondes.

**Une seconde base SQLite pour l'amorçage.** Rigoureux, et absurde : on ouvrirait une base pour savoir
où ouvrir une base, et la question de l'emplacement se reposerait d'un cran.

**Un fichier à côté de l'exécutable.** Cassé par toutes les distributions qui installent en lecture
seule, c'est-à-dire toutes celles qu'on publie.

**Déplacer la base soi-même quand l'utilisateur change l'emplacement.** Écarté : déplacer un fichier de
l'utilisateur sans le lui demander est exactement ce que l'ADR 0048 vient de retirer à l'application.
L'écran dira quoi faire ; il ne le fera pas à sa place.
