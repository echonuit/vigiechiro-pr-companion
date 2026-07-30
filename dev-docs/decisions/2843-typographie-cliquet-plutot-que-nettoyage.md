# ADR 2843 - Une convention typographique se tient par un cliquet, pas par un nettoyage

- **Statut** : Accepté - 2026-07-29
- **Chantier** : #2843, suite de la clôture du chantier #2348 et de #2813
- **Vérification** : probable - `scripts/adr/2843-tiret-cadratin.py` (cliquet : 506)

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

Une seule population, un seul nombre, aucun angle mort.

**4. Une zone nettoyée passe du cliquet à la tolérance zéro.** Cette ADR annonçait d'abord que la documentation aurait « son propre cliquet ». C'était mal formulé : un cliquet sur une zone déjà au plancher est inutilement faible, et il resterait masquable tant qu'une autre zone comptée avec elle serait, elle, loin du plancher. Une zone nettoyée n'a plus besoin d'une marge, elle a besoin d'un refus.

`docs/` et `brief/` sont nettoyées depuis #2365 : la vérification y compte **zéro** cadratin de prose, et le script échoue à la première rechute.

Les zones nettoyées vivent dans une **liste déclarative** du script (`ZONES_NETTOYEES`). Ajouter une tranche revient à ajouter **une ligne**, et c'est délibéré : chaque tranche du chantier touche ce même script, donc une insertion d'une ligne se résout sans réfléchir là où un bloc de code aurait sérialisé les tranches.

**5. Ce qui est cité n'est pas de la prose.** Un cadratin entre **guillemets français** ou entre **chevrons de code** est une citation : le glyphe de valeur absente que la documentation décrit, ou un libellé de l'application qu'une fiche d'écran reproduit fidèlement. Une seule règle couvre les deux, là où deux listes d'exceptions auraient dérivé séparément.

C'est aussi ce qui a révélé que **`docs/` n'est pas indépendante des chaînes Java** : `docs/ecrans/sites.md` cite le libellé `« GPS manquant, placer sur la carte »`, écrit tel quel dans `CartesPointsSite`. Corriger la documentation seule l'aurait fait diverger du produit. Ce cadratin partira avec la tranche des chaînes Java, où le libellé et sa documentation changeront ensemble.

**3. La vérification reste « probable ».** Un tiret cadratin peut être cité légitimement : un commentaire qui explique la règle, une chaîne qui reproduit un texte externe. Aucun motif ne sait faire cette différence, c'est un humain qui tranche, extrait en main.

## Conséquences

Le script compte les **commentaires**, contrairement à la plupart de ses voisins qui les retirent avant de mesurer. Ce n'est pas un oubli : la règle vise explicitement « la doc et les commentaires », qui sont ici la matière et non le bruit.

Cette ADR contient elle-même des tirets cadratins, à commencer par son titre et sa ligne de vérification, dont le format est imposé par `_commun.py`. Elle n'est pas dans le périmètre, et il n'y a là aucune contradiction : la règle porte sur ce qu'on écrit dans le code, pas sur la mécanique qui la fait respecter.

Le cliquet ne se resserre pas tout seul. Quand la mesure passe sous la marge, le script le signale (`verdict=a-resserrer`) : un cliquet qu'on ne resserre jamais redevient un tapis sous lequel on pousse.

La marge se mesure sur `main` **à jour**. La première version de cette ADR déclarait 1067, mesuré quelques heures plus tôt ; une fusion intervenue entre-temps a ajouté une ligne, et la CI a fait rougir la PR qui introduisait le cliquet. L'incident est le bon signe, mais il vaut d'être connu : un cliquet posé sur une base périmée échoue à son premier passage.
