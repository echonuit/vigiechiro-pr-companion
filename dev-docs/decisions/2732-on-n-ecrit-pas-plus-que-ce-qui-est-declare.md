# ADR 2732 - On n'écrit jamais plus que ce qui est **déclaré**, et le taux de compression ne décide de rien

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #2732, lot 2 (#2722) du chantier de dette #2720
- **Vérification** : certaine - `ExtracteurZipQuotasTest#archive_menteuse_arretee_pendant_la_copie`

> Le versant « un contenu très compressible passe » est tenu par
> `ExtracteurZipQuotasTest#un_contenu_tres_compressible_passe` : le garde des ADR n'accepte qu'une
> référence, la décision en a deux.

## Contexte

Décompresser une archive choisie par l'utilisateur est le seul endroit où l'application ouvre un
fichier dont **rien ne garantit la provenance**. La garde « zip-slip » la bornait en **chemins** ; rien
ne la bornait en **ressources** : `transferTo` écrivait jusqu'à la fin de l'entrée ou la saturation du
disque, sur lequel vit aussi la base SQLite du dossier de travail.

Le garde classique contre les bombes ZIP est un **plafond de taux de décompression**. Il a été écrit
ici, puis retiré. Cette ADR existe parce que sans elle, il sera réécrit : c'est le premier réflexe de
quiconque relit ce code.

## Décision

### Deux gardes, l'un sur le déclaré, l'autre sur le réel

**Avant d'écrire le premier octet**, sur l'inventaire que l'archive **annonce** (son répertoire
central, lu sans rien décompresser) : nombre d'entrées, taille de la plus grosse, total, et espace
disque disponible avec marge. Le refus précède la création même du dossier temporaire.

**Pendant la copie**, sur les octets **réellement** écrits : si le cumul dépasse ce que l'archive avait
annoncé, l'extraction s'arrête. Ce second garde n'a besoin d'**aucune constante** : il confronte
l'archive à **sa propre déclaration**, celle-là même sur laquelle l'espace disque vient d'être validé.

Ensemble, ils tiennent une garantie qui se dit en une phrase : **on n'écrit jamais plus que ce qui a
été déclaré, et on n'accepte jamais une déclaration qui ne tient pas.**

Le second n'est pas une ceinture de plus. Une bombe ZIP **ment précisément sur ce que le premier
lit** : un garde qui ne lit que le déclaré se fait berner par la seule archive contre laquelle il
existe.

### Pas de plafond de taux de compression

**Il ne sépare pas les deux populations dans ce domaine.** Deux mesures, faites plutôt que supposées :

| Ce qu'on compresse | Taux observé |
|---|---|
| Une carte SD du **générateur de recette** (`sd-nominale`, 6,9 Mo) | **≈ 140** |
| La nuit **synthétique** des parcours E2E, archive entière | **137** (relevé par la CI, dans le refus lui-même) |

Les deux dépassent le plafond de 100 qui avait été écrit, et ce sont les deux jeux de données les plus
proches d'une vraie carte dont le dépôt dispose. Un enregistrement réellement silencieux fait bien
davantage : de l'audio silencieux et une bombe sont **les mêmes octets**. Placé au-dessus du légitime,
le seuil laisse passer les bombes à un coup ; placé en dessous, il refuse des nuits réelles.

**Et il n'ajoute rien à la garantie.** Un taux énorme ne nuit que s'il aboutit à beaucoup d'octets
écrits - ce que le total annoncé, le contrôle d'espace disque et le second garde bornent déjà. Le
conserver, c'était payer des refus injustifiés pour une protection qu'on avait par ailleurs.

### Des défauts larges, surchargeables, mais pas un réglage

Une nuit de terrain fait quelques milliers de fichiers et une dizaine de gigaoctets : elle doit passer
**sans que personne ait rien à régler**. Chaque borne se surcharge par propriété système, pour
l'archive légitime mais inhabituelle. Il n'y a **pas** de réglage dans l'écran Réglages : un
naturaliste n'a pas à choisir un plafond d'entrées. C'est le **message de refus** qui nomme la limite
atteinte et l'échappatoire.

## Conséquences

- Une archive qui déclare 400 Go et les écrit vraiment est **acceptée** si le disque les accueille et
  si le total reste sous la borne absolue. C'est assumé : l'utilisateur a désigné cette archive, elle
  ne ment pas, et l'import échouera ensuite s'il ne s'agit pas d'une carte SD.
- Le second garde ne constate le dépassement qu'**à un palier près** (4 Mio, la granularité de
  notification de [CopieInterruptible][copie]). Sans effet pour un mécanisme qui vise les facteurs
  mille.
- Les fixtures de test doivent **ressembler à de l'audio** : celles faites d'octets identiques sont,
  au regard de n'importe quel critère de compression, des bombes. Trois l'étaient.

## Alternatives écartées

**Desserrer le plafond de ratio plutôt que le retirer.** À 140 mesuré sur une carte générée et bien
plus sur du silence réel, le seuil « sûr » se situerait au-dessus des bombes à un coup, qu'il
laisserait donc passer. Le pire des deux mondes.

**Ne borner que le déclaré.** C'est le garde qui se fait berner par la bombe, par construction.

**Ne borner que le réel.** Sans contrôle préalable, l'extraction commence, écrit, puis s'arrête : le
disque a déjà encaissé ce que le premier garde évite, et le refus arrive après le dégât.

[copie]: ../patterns.md
