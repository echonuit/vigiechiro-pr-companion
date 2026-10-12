## Context

`RefletDuJeton` publie un booléen « un jeton est disponible », lu par trois écrans à travers le port
`EtatConnexion`. Il ne change que par `relire()`, et `relire()` n'avait qu'un appelant, l'écouteur de
`jetonEnregistreProperty` dans la modale de connexion. Trois choses changent pourtant la réponse sans
passer par là : l'horloge (`StockageConnexion` périme le jeton après 14 jours), la plateforme (un `401`
reçu en cours de travail, rendu comme une valeur), et l'ouverture de la modale, dont le modèle de vue
naît avec `jetonEnregistre` à faux et ne déclenche donc pas l'écouteur sur un jeton périmé.

## Decisions

**Effacer au transport, et nulle part ailleurs.** `lire` et `ecrire` sont les deux seules émissions qui
portent le jeton. Un `401` de l'une d'elles se signale à la source du jeton, avec le jeton qui est parti.
Écarté : effacer dans chaque écran, qui ferait autant de copies que de gestes, et laisserait la ligne de
commande et le dépôt renvoyer le jeton mort. Le stockage S3 (`deposer`, `telecharger`) ne passe pas par
là : son `401` refuse une URL signée, pas le jeton.

**La source décide, et seulement pour son jeton.** `FournisseurToken.jetonRefuse(jeton)` ne fait rien
par défaut. `StockageConnexion` oublie le jeton s'il est celui du fichier (`oublierSi`). La source liée
par l'application relaie au stockage : un jeton ponctuel refusé y est reconnu comme étranger, et la
connexion enregistrée reste. La même condition couvre une reconnexion survenue pendant la requête.

**Relire au retour du geste, sur le fil JavaFX.** `EtatConnexion.relire()` entre au port, avec un défaut
vide pour les états constants. Les écrans l'appellent dans les retours de succès, d'échec et
d'annulation de leurs gestes, jamais dans la tâche (règle de #6139). Relire est idempotent : appeler à
chaque retour coûte une lecture de fichier, et évite de deviner quels retours parlent d'un jeton.

**Relire à l'ouverture de la modale.** Une ligne après `rafraichir()` dans `initialize`.

**Le dépôt range « non connecté » en droits.** Le premier `401` d'un dépôt efface le jeton ; les unités
suivantes reviennent « non connecté » sans requête. Sans cause, elles étaient comptées comme refus de
contenu, avec le conseil de régénérer, et ne se réarmaient pas. Rangées en droits, elles se réarment et
le compte rendu dit « Reconnectez-vous ».

**Le geste et l'endroit, pour les écrans seulement.** Une phrase, `GesteDeReconnexion.A_L_ECRAN`, nomme
l'entrée de menu que les infobulles de blocage nomment déjà. Les messages que la ligne de commande lit
aussi (`RapportSynchro.enClair`, le rapatriement d'un carré) disent seulement « reconnectez-vous ».

## Risks / Trade-offs

Un `401` passager d'un serveur mal configuré effacerait un jeton valide. La vérification `/moi` faisait
déjà ce choix ; le porteur l'étend à toute l'API.

La ligne de commande qui reçoit un `401` sur le jeton enregistré déconnecte l'application. C'est le même
jeton, et il est mort.

Un dépôt lancé sans aucun jeton est désormais classé en droits et réarmé à la reconnexion, là où il ne
portait aucune cause.
