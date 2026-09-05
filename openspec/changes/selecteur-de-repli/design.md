## Context

Voir `proposal.md` pour la motivation. Ce qu'il faut savoir de l'état actuel, et qui commande
l'approche :

Le port `SelecteurFichier` porte trois méthodes et rend `Optional<Path>`. Il est **synchrone**. Son
implémentation réelle, `SelecteurFichierJavaFx`, ouvre un `DirectoryChooser` ou un `FileChooser`
bloquant.

**Le choix d'implémentation n'existe nulle part.** Il est écrit douze fois, en dur, sous la forme
`new SelecteurFichierModifiable(new SelecteurFichierJavaFx(fenetre))`, dans douze contrôleurs et
actions. Compté sur `src/main/java` le 2026-09-06. Il n'y a donc rien à modifier pour changer de
dispositif : il y a douze endroits.

**Le patron d'une modale dessinée derrière un port synchrone existe déjà** :

```
     LE PORT                  L IMPLEMENTATION REELLE          LE CONTENU
+------------------+     +---------------------------+   +--------------------+
| ChoixSauvegarde  |<----| ChoixSauvegardeJavaFx     |-->| ContenuChoixSauve. |
|                  |     | Stage + WINDOW_MODAL      |   | eprouve SANS       |
|                  |     | showAndWait() vit ICI     |   | fenetre            |
+------------------+     +---------------------------+   +--------------------+
```

`showAndWait` est proscrit dans un écran, et **autorisé dans l'implémentation réelle d'un port** :
c'est le cas à huit endroits du socle. Le contrat synchrone peut donc rester tel quel.

## Goals / Non-Goals

**Goals.** Une couture unique où le dispositif se choisit. Une implémentation qui réutilise le patron
existant plutôt que d'en inventer un. Un contenu éprouvable sans fenêtre.

**Non-Goals.** Changer la signature du port, donc toucher aux vingt-cinq appels. Rendre le port
asynchrone. Remplacer le dialogue du système. Élargir ce que le bac à sable autorise.

## Decisions

### Une fabrique, et non une liaison dans l'injecteur

Les douze constructions ont besoin d'un `Supplier<Window>` propre à leur écran, que l'injecteur ne
connaît pas. Une liaison Guice devrait donc passer la fenêtre après coup, ce qui rend la liaison
sans intérêt.

**Retenu** : une fabrique, appelée là où les douze constructions vivent aujourd'hui. Elle prend la
fenêtre et le réglage, et rend le porteur injectable comme aujourd'hui.

*Écarté* : la liaison Guice, pour la raison ci-dessus. *Écarté* : router par un singleton lu
globalement, qui rendrait le dispositif impossible à remplacer en test sans toucher à un état
partagé.

### Le dispositif ne s'appelle pas « repli » dans le code

`ChoixSauvegardeJavaFx` porte déjà un paramètre `repli`, qui rend la main **au sélecteur natif**.
Celui-ci va en sens inverse. Deux « replis » opposés à trois classes d'écart se confondraient.

**Retenu** : nommer par ce que le dispositif **est** et non par son rôle. Le natif reste
`SelecteurFichierJavaFx` ; celui de l'application prend un nom qui dit qu'il est une fenêtre de
l'application. Le mot « repli » reste au réglage, côté utilisateur, où il n'entre en collision avec
rien.

### Le contenu se sépare de la fenêtre

Comme `ContenuChoixSauvegarde`. C'est ce qui rend la mise en page éprouvable sans ouvrir de fenêtre
bloquante, et c'est la seule manière de tenir les exigences de la spec sans un banc filmé.

### Le réglage rejoint l'onglet des emplacements

`OngletReglagesEmplacements` porte déjà les emplacements et **appelle** le sélecteur. C'est le seul
écran où le geste et son réglage se lisent ensemble.

*À confirmer à la réalisation* : par quel service il persiste. `ServiceEmplacements` et
`ConfigurationAmorcage` sont les deux candidats lus le 2026-09-06 ; le choix ne change ni les
exigences ni le découpage.

## Risks / Trade-offs

**Un écran oublié ignore le réglage en silence.** Douze endroits à router, et un manqué ne casse
rien : il ouvre simplement le dialogue du système. → Un garde qui refuse `new SelecteurFichierJavaFx`
ailleurs que dans la fabrique. Sans lui, le défaut se découvre par hasard.

**Deux modales empilées pour un seul geste**, ce que le socle refuse (#2642). Un écran qui a déjà une
modale ouverte et demande un fichier en empilerait une seconde. → Reprendre le geste de
`ChoixSauvegardeJavaFx`, qui **ferme** sa fenêtre avant d'ouvrir le sélecteur.

**Le film montrera une configuration qui n'est pas le défaut.** C'est la contrepartie assumée du
réglage. Elle ne tombe pas sous l'ADR 3788, qui vise une mise en page qu'on ne livre pas : ici la
configuration est livrée, et un utilisateur peut la choisir. → L'écrire sur la page qui porte le
film, plutôt que de laisser le lecteur le supposer.

**Un dialogue dessiné promet plus qu'il ne peut sous bac à sable.** Montrer un arbre de fichiers
suggère qu'on peut tout atteindre. → L'exigence du refus explicite existe pour cela : un chemin hors
d'atteinte se dit, il ne se tait pas.

## Migration Plan

Aucune migration de données. Le défaut reproduit le comportement actuel, donc une installation qui
ne touche pas le réglage ne voit rien changer. Revenir en arrière consiste à remettre le réglage sur
le dialogue du système.

## Open Questions

**Le dialogue de l'application doit-il montrer les fichiers cachés ?** Le natif le propose. La
réponse ne change ni les exigences, ni l'approche, ni le découpage.
