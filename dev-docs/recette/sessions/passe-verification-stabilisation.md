# Passe de coutures · les correctifs tiennent-ils **ensemble** ?

> **Écrans traversés** : importation, Rattachement, passage, audit. · **Statut : à jouer.**
> Ce n'est **pas** une session propriétaire, et elle ne vérifie aucune capacité pour elle-même : chaque
> capacité a sa case dans **sa** session (S1, S2, S6, S7). Ici on ne regarde que ce qui se passe **entre**
> elles. Retour à la [méthode](../index.md).

## Ce que cette passe est, et ce qu'elle n'est pas

La stabilisation qui précède la campagne 2 (#3424) a livré onze correctifs. Chacun a ses tests, et
chacun a désormais sa case dans sa **session propriétaire** : c'est la passe 6b du
[cycle](../../cycle-de-chantier.md), et c'est là que vit le procédé de vérification d'une capacité.

Une question reste, qu'aucune de ces sessions ne pose. Une session déroule **un écran** en entier ; six
des onze correctifs vivent sur **un même parcours** qui en traverse quatre :

```
importer → rattacher → déposer → supprimer → auditer → retirer
```

Un correctif vérifié seul peut être défait par son voisin sans que rien ne rougisse : le test du voisin
est vert, et celui du correctif défait ne couvre que le chemin qui vient de changer. **Les coutures sont
l'angle mort du découpage par écran**, et cette passe ne regarde qu'elles.

> Une case ici ne demande jamais « est-ce que X marche ? » - sa session le demande déjà. Elle demande
> « **est-ce que X marche encore quand on y arrive par Y ?** ».

Cette passe ne remplace pas la campagne 2 : elle en est la **condition**. Jouer une campagne
d'acceptation sur une base dont on ne sait pas si elle tient produirait des constats qu'on ne saurait
pas attribuer.

## Où sont les cases par capacité

| Capacité livrée | Session propriétaire |
| --- | --- |
| Accueil, modale de connexion, menu ☰ (#1373, #3433) | [S1, étape 6](s1-premier-contact.md) |
| Import, rattachement, suppression d'un passage (#3448, #3449, #3482) | [S2, étape 6](s2-importer.md) |
| Audit : constats chiffrés, retrait des orphelins (#3490, #3482) | [S6, étape 8](s6-exploiter-piloter.md) |
| Réglages, « conserver les originaux », habillage des dialogues (#3471, #1499, #3437) | [S7](s7-reglages.md) |

Deux livraisons ne sont **pas** en recette, et c'est délibéré :

- la **parité CLI** (#1383) est couverte par `cli-surface.bats` et `cli.bats`, qui lancent le **vrai
  fat-jar** et vérifient jusqu'aux codes de sortie. La rejouer à la main serait du travail en double,
  moins fiable que ce qui existe ;
- la **fidélité des aperçus** et la **typographie** (#3439, #3483, #3361) sont tenues par des gardes qui
  comparent des pixels. Un œil humain y apporte du doute, pas de la preuve.

## Environnement

Base **jetable**, jamais un workspace de travail : le parcours supprime définitivement.

```bash
env -u DISPLAY ./mvnw -q test-compile exec:java@generer-sd \
  -Dexec.args="recette/fixtures/spec /tmp/recette-sd"
```

Une seule carte suffit : **`sd-nominale`**. Le parcours n'exerce pas de cas dégradé - c'est l'objet de
S2 - mais son **enchaînement**.

**Lancer l'application SANS `-Dvigiechiro.workspace`.** Cette propriété gagne en silence sur le
réglage persisté, et c'est précisément ce qui a produit un faux positif lors de l'instruction de #3459 :
le mécanisme était sain, le harnais le contredisait. Un dispositif de vérification fait partie de ce
qu'il faut vérifier.

Il faut un **site avec au moins un point** et une **connexion à la plateforme** : les cas marqués 🔌
n'ont pas de sens hors ligne, et c'est justement la moitié du parcours que les tests couvrent le moins.

## Le script

Une case = **un fait observable**. Le parcours se joue **d'un trait**, sans redémarrer l'application :
c'est la continuité qui est sous examen, et redémarrer entre deux cases effacerait précisément l'état
qu'on veut éprouver.

### Étape 1 · Importer, connecté

- [ ] **PC2-01** 🔌 · Importer `sd-nominale` sur un point renseigné : le compte rendu annonce une
      participation créée, **et** « Voir la participation » l'ouvre réellement sur la plateforme.
- [ ] **PC2-02** · Sans quitter l'écran, revenir à l'accueil : le compteur de passages a **augmenté**.

> PC2-01 est la couture entre l'import et la plateforme : l'écran annonçait une création qui n'avait pas
> eu lieu (#3448). Le fait observable est **sur la plateforme**, jamais dans le message.

### Étape 2 · Rattacher, dans la foulée du même import

- [ ] **PC2-03** · Ouvrir le rattachement sur la nuit qu'on vient d'importer : le compte rendu **chiffre
      les séquences renommées**.
- [ ] **PC2-04** 🔒 · Se déconnecter, refaire un rattachement qui renomme : le compte rendu dit **à la
      fois** le renommage réussi **et** l'échec de l'envoi.

> PC2-04 est la couture entre deux moitiés d'une même opération : la seconde échouait et effaçait la
> première du compte rendu (#3449), laissant l'utilisateur sans savoir s'il pouvait relancer.

### Étape 3 · Déposer la nuit importée

- [ ] **PC2-05** 🔌 · Se reconnecter et déposer cette nuit : la date et l'heure visibles **sur la
      plateforme** correspondent à l'heure du **site**, pas à celle du poste.

> PC2-05 se lit sur la plateforme, jamais dans l'application : une conversion relue avec la même zone
> est juste sous tout fuseau, y compris quand la donnée déposée est fausse (#3450).

### Étape 4 · Supprimer la nuit qu'on vient de déposer

- [ ] **PC2-06** · Supprimer ce passage : la confirmation dit que les **fichiers audio restent sur le
      disque**, et affiche **où**.
- [ ] **PC2-07** · Confirmer, puis regarder le disque : le dossier de la nuit est **toujours là**.
- [ ] **PC2-08** · Revenir à l'accueil : le compteur de passages a **diminué**.

> PC2-08 n'est pas un doublon de PC2-02 : une liaison qui ne suivrait que les ajouts passerait pour
> vivante tant qu'on ne retire rien.

### Étape 5 · L'audit voit ce que la suppression a laissé

- [ ] **PC2-09** · Ouvrir l'audit et relancer : un constat « **Dossier sans session** » désigne
      **exactement** le dossier de PC2-07, et le bouton de retrait s'active.

> PC2-09 est la couture, et cette passe s'arrête là. La suppression laisse un dossier sur un écran, et
> un **autre** écran le ramasse : aucune des deux sessions propriétaires ne peut vérifier ce lien seule,
> puisqu'il relie deux écrans que **deux sessions différentes** possèdent.
>
> Ce que le retrait fait ensuite - le libellé chiffré, la confirmation, le refus qui ne détruit rien, le
> compte rendu - appartient à l'écran d'audit, donc à
> [S6, étape 8](s6-exploiter-piloter.md). Le rejouer ici serait la duplication que la règle d'unicité
> interdit. Ce qui se vérifie ici, c'est que l'audit **voit** ce que la suppression a laissé, et le
> désigne sans se tromper de dossier.

## Ce qu'on fait des résultats

Une case rouge **n'ouvre pas une issue tout de suite**. Elle se qualifie d'abord :

1. **le correctif est défait** - régression franche, à traiter avant la campagne 2 ;
2. **le correctif tient, mais un voisin a bougé** - le verdict que cette passe existe pour produire :
   c'est un chaînon que personne ne gardait, et il mérite un test automatisé, pas seulement un
   correctif ;
3. **le cas était mal écrit** - il demandait deux regards, ou supposait un état qu'on n'avait pas.

Le troisième verdict est fréquent et ne vaut pas aveu : il corrige le script, pas le produit. Les deux
premiers ouvrent une issue qui **cite la case**, pour que le prochain qui la rejoue sache ce qu'elle a
déjà attrapé.
