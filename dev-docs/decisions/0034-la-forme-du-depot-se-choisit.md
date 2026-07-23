# ADR 0034 — La forme du dépôt se choisit, elle ne se déduit pas de la place disponible

- **Statut** : Accepté — 2026-07-19
- **Chantier** : #1991 (lots #1993 à #1999)
- **Vérification** : humaine — que la forme du dépôt se choisisse (réglage) plutôt qu'elle ne se déduise de la place est une règle de comportement

## Contexte

Le mode de dépôt n'était pas un choix, c'était une conséquence :

```java
return sequences; // repli WAV : l'espace disque ne permet pas de créer les archives ZIP
```

Les archives ZIP étaient privilégiées, et le repli sur les séquences WAV n'intervenait que si le disque
ne permettait pas de les créer. Tant que le dépôt générait tout le lot d'un coup, ce repli se
déclenchait pour de vrai sur les grosses nuits.

L'[ADR 0033](0033-la-fenetre-borne-le-disque.md) a supprimé cette contrainte : deux archives suffisent
désormais. Le ZIP devient donc pratiquement toujours possible, et **le repli ne se déclencherait plus
jamais**. Sans décision explicite, le chantier aurait supprimé en silence la seule route par laquelle
l'IHM produisait un dépôt WAV.

Or ce mode n'est pas une question de vitesse ni de place. D'après l'analyse de #1244 :

| Mode | Côté serveur | Audio récupérable ? |
|---|---|---|
| WAV déposés directement | le `fichier` créé survit au traitement | oui |
| ZIP | l'archive est **supprimée** après extraction, les WAV extraits ne sont pas montés sur S3 | non |

C'est exactement ce que dit le verrou `relanceBloquee` : « relancer le calcul effacerait ses
observations sans pouvoir les recalculer ». Ce verrou est une **conséquence du mode ZIP**, pas une
propriété de la plateforme.

Le constat de recette S4-C06 l'avait déjà relevé : le choix « n'existe ni en IHM ni en Réglages ;
l'IHM impose le ZIP, WAV n'est atteignable qu'en CLI ; or ce choix détermine si l'audio reste
récupérable côté serveur ».

## Décision

**1. Le mode est un réglage** (`depot.mode`, Réglages ▸ Dépôt), relu à chaque dépôt comme le plafond
d'archive.

**2. Son aide énonce la conséquence, pas la performance.** Elle dit que le ZIP fait perdre l'audio en
ligne et interdit de relancer la participation, et que le WAV les conserve au prix d'un dépôt plus long.
Une option muette ne vaudrait guère mieux qu'un choix subi : le point du lot n'est pas d'offrir un
bouton de plus.

**3. Le défaut reste le ZIP.** C'est le comportement établi et le plus rapide. Mais c'en est maintenant
un que l'on choisit.

**4. Un mode ZIP demandé mais impossible échoue explicitement**, en nommant les deux issues : libérer de
l'espace, ou passer en WAV. Il ne bascule plus tout seul, puisque ce basculement changerait ce qu'il
advient de l'audio de l'utilisateur.

**5. Un réglage absent ou corrompu retombe sur le ZIP.** Un réglage illisible ne doit pas empêcher de
déposer.

## Conséquences

La CLI et l'IHM partagent enfin le même défaut. `--archives` et `--wav` restent, et priment sur le
réglage pour un dépôt ponctuel : c'est l'IHM qui rattrape la CLI, pas l'inverse.

Le constat de recette S4-C06 peut être coché. Ce qui reste de #1515 est l'arbitrage produit lui-même —
faut-il recommander le WAV par défaut ? — qui demande un retour de terrain que ce chantier n'a pas.

`ChoixSourceDepot` ne fait plus qu'**appliquer** un mode et refuser ce qu'il ne peut pas honorer. La
place disque a cessé d'être une politique déguisée en contrainte.

## Ce qui a été écarté

**Conserver le repli automatique, rebasé sur le seuil de la fenêtre.** C'était le plus petit changement.
Mais le repli serait devenu pratiquement inatteignable, laissant un mécanisme mort dans le code et le
choix toujours subi.

**Traiter le choix hors de ce chantier, dans une issue rattachée à #1515.** Cela aurait laissé une
fenêtre pendant laquelle le mode WAV était inatteignable en IHM sans que rien ne le dise — une
régression fonctionnelle introduite par un chantier de performance, ce qui est précisément le genre de
dette qu'on ne voit qu'une fois livrée.

**Un choix par dépôt, dans l'écran plutôt qu'en réglage.** Plus fin, et défendable : la conséquence est
per-nuit. Mais elle est aussi *systématique* pour un même observateur, et un choix répété à chaque nuit
serait vite cliqué sans être lu. À reconsidérer si le terrain montre des usages mixtes.
