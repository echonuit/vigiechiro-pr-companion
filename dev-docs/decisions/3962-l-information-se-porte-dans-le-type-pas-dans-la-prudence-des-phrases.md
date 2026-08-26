---
type: adr
title: "L'information se porte dans le type, pas dans la prudence des phrases"
status: stable
article: A16
chantier: "#3962, clôture du lot #3900"
decided_at: 2026-08-18
verification: certaine
enforced_by:
  - "CompteRenduChiffreDepotTest#un_refus_definitif_n_est_plus_promis"
verified:
  - by: machine:ci
    at: 2026-08-18
relations:
  prolonge: ["3854", "3689"]
---

# L'information se porte dans le type, pas dans la prudence des phrases

## Contexte

Le compte rendu de dépôt annonçait, dès qu'il y avait des échecs :

> N archive(s) ne sont pas en ligne : « Reprendre le dépôt » ne renverra que celles-là.

et la ligne de commande, dans le même cas :

> Relancez la commande pour ne reprendre que les manquants.

Si les échecs sont des **refus définitifs**, ni l'un ni l'autre ne les reprendra. Côté écran c'est
pire que faux : `EtapeTeleverserUI` lie le libellé du bouton à `resteAReprendre`, que #3687 a corrigé.
Dans cet état précis, le bouton s'intitule « Téléverser sur Vigie-Chiro ». **Le compte rendu nommait
donc un bouton absent de l'écran.**

La cause racine tient en une ligne. `DepotVigieChiro` faisait, sur **tout** échec :

```java
cumul.echecs().add(unite.identifiantUnite());
```

alors que `resultat.definitif()` **et** `resultat.cause()` sont disponibles à cette ligne même, portés
par `TeleverseurArchive.Resultat` depuis #3688 et #3689. `BilanDepot.echecs` était une
`List<String>` : l'information était jetée **à l'endroit exact où elle existait**.

C'est la **même forme** que le défaut de #3688, où le PUT S3 d'un seul bloc perdait le statut de sa
réponse. Corrigé une fois en amont, le motif survivait en aval - et deux surfaces le payaient.

## Décision

**Quand deux surfaces disent une chose fausse à partir d'une donnée appauvrie, on enrichit la donnée.**

`EchecUnite(identifiantUnite, raison, definitif, cause)` remplace le nom seul, et `BilanDepot` rend
`reprenables()` et `refusesDefinitivement()`.

### Pourquoi pas rendre les deux phrases prudentes

C'était l'autre option, et elle est moins chère. Elle laisse l'information **jetée là où elle
existe**, et le prochain consommateur du bilan refait l'erreur - pour la troisième fois. Une phrase
prudente protège la phrase ; un type protège tous ses lecteurs, y compris ceux qui n'existent pas
encore.

### Corollaire : un geste se nomme pour la PART qu'il répare

L'[ADR 3854](3854-un-refus-ne-conseille-que-ce-qu-il-a-verifie.md) demande de ne nommer qu'un geste
vérifié applicable. Ma première rédaction en avait tiré « ne rien dire dès qu'une seule archive n'est
pas réparable » : sur un lot mêlé - deux refus de droits, un contenu refusé - elle se taisait
entièrement, alors que se reconnecter réparait deux archives sur trois.

**Se taire quand le geste s'applique à une part, c'est perdre un geste vérifié.** La phrase dit donc
le compte : « 2 d'entre elles tenaient à vos droits : reconnectez-vous ».

**Ce cas-là ne s'est pas trouvé en relisant le code.** Il s'est vu **en ouvrant l'aperçu**, à la
passe 8, sur une bande où seize tests étaient verts.

## Le titre disait « déposée » quand il manquait trois archives

Même aperçu, même passe, second défaut. `titre(plan)` valait « Nuit déposée sur Vigie-Chiro » dès que
le plan n'était pas **interrompu** : une nuit à 11/14 dont trois refusées s'annonçait « déposée », en
gras, juste au-dessus d'une ventilation qui disait le contraire.

Le titre lit désormais le bilan : « **Dépôt incomplet** », le mot que la ligne de commande employait
déjà pour le même état. Deux surfaces, une désignation.

## Conséquences

- **Un aperçu manquant est un défaut, pas une lacune de documentation.** Aucun des quatorze aperçus de
  l'écran de lot ne montrait l'état où la reprise cesse d'être offerte : celui, précisément, où
  l'utilisateur a le plus besoin d'être renseigné. L'ajouter a révélé les deux défauts ci-dessus.
- **Le garde de captures dit pourquoi la galerie compte** : « une capture qui n'y figure pas n'est
  jamais regardée ». Il a refusé le premier commit, qui déclarait l'aperçu au manifeste sans l'y
  présenter.
- **Vu rouge** en re-jetant la distinction dans `BilanDepot` : six tests tombent, trois de chaque côté.

## Ce que la décision NE couvre pas

Le réarmement d'un **contenu** refusé après régénération de l'archive reste non câblé
([#3946](https://github.com/echonuit/vigiechiro-pr-companion/issues/3946)). Réarmer sur autre chose
que la cause ramènerait le bouton que #3687 a fait taire.
