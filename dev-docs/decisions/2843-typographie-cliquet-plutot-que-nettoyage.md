# ADR 2843 — Une convention typographique se tient par un cliquet, pas par un nettoyage

- **Statut** : Accepté — 2026-07-29
- **Chantier** : #2843, suite de la clôture du chantier #2348 et de #2813
- **Vérification** : probable — `scripts/adr/2843-tiret-cadratin.py` (cliquet : 1036)

## Contexte

La règle « pas de tiret cadratin » est écrite **deux fois** dans le dépôt :

- `CONTRIBUTING.md` : « pas de tiret cadratin dans la doc et les commentaires : tiret simple ou deux-points » ;
- `dev-docs/ajouter-une-fonctionnalite.md`, mot pour mot.

Et elle n'était appliquée par rien. Elle a été enfreinte pendant la clôture du chantier #2348, dans un message de commit et un corps de PR. L'écart n'a été vu que parce que quelqu'un a relu, et il aurait tout aussi bien pu ne pas l'être.

Une convention que seule la relecture applique n'est pas une convention, c'est un souhait.

La mesure, à l'ouverture : **1132 occurrences** dans les sources Java, réparties sur 481 fichiers ; 545 dans la documentation développeur, 80 dans la documentation utilisateur, 9 dans le brief.

## Décision

**1. Un cliquet, pas un nettoyage.** Un correctif d'un seul tenant toucherait la moitié du dépôt pour un gain nul le jour où il fusionne, et rendrait illisible tout `git blame` sur ces fichiers. Le dépôt cesse de dériver ; chaque fichier ouvert pour une autre raison peut être assaini au passage.

**2. Le périmètre est celui des sources Java**, `src/main/java` et `src/test/java`.

C'est un choix contre l'alternative apparemment plus généreuse d'un compteur unique couvrant aussi la documentation. Deux populations dans un seul nombre peuvent se **masquer** : un nettoyage de vingt lignes de documentation compenserait vingt régressions dans le code, le total resterait stable, et le verdict resterait vert. Le garde mentirait alors dans le sens rassurant, ce qui est pire que pas de garde du tout.

Une seule population, un seul nombre, aucun angle mort. Si la documentation doit être tenue de même, elle aura **son propre cliquet**, avec sa propre marge.

**3. La vérification reste « probable ».** Un tiret cadratin peut être cité légitimement : un commentaire qui explique la règle, une chaîne qui reproduit un texte externe. Aucun motif ne sait faire cette différence, c'est un humain qui tranche, extrait en main.

## Conséquences

Le script compte les **commentaires**, contrairement à la plupart de ses voisins qui les retirent avant de mesurer. Ce n'est pas un oubli : la règle vise explicitement « la doc et les commentaires », qui sont ici la matière et non le bruit.

Cette ADR contient elle-même des tirets cadratins, à commencer par son titre et sa ligne de vérification, dont le format est imposé par `_commun.py`. Elle n'est pas dans le périmètre, et il n'y a là aucune contradiction : la règle porte sur ce qu'on écrit dans le code, pas sur la mécanique qui la fait respecter.

Le cliquet ne se resserre pas tout seul. Quand la mesure passe sous la marge, le script le signale (`verdict=a-resserrer`) : un cliquet qu'on ne resserre jamais redevient un tapis sous lequel on pousse.

La marge se mesure sur `main` **à jour**. La première version de cette ADR déclarait 1067, mesuré quelques heures plus tôt ; une fusion intervenue entre-temps a ajouté une ligne, et la CI a fait rougir la PR qui introduisait le cliquet. L'incident est le bon signe, mais il vaut d'être connu : un cliquet posé sur une base périmée échoue à son premier passage.
