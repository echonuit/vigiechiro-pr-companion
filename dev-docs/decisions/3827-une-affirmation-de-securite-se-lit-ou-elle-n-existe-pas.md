# ADR 3827 - Une affirmation de sécurité se lit, ou elle n'existe pas

- **Statut** : Accepté - 2026-08-16
- **Chantier** : #3827, lot 3 des suites #3802
- **Prolonge** : [ADR 3507](3507-l-amorcage-s-ecrit-d-un-seul-coup.md)
- **Vérification** : certaine - `EcritureAtomiqueTest#creation_restreinte`

## Contexte

Deux affirmations vivaient dans le dépôt depuis des mois, l'une dans un doc-comment, l'autre dans
`dev-docs/securite.md`, et **aucune n'était vérifiée** :

- que `Files.move(ATOMIC_MOVE)` se comporte différemment sous Windows quand la cible est **ouverte** -
  c'était le **motif fondateur** du chantier #3518, et il est resté découvert jusqu'ici ;
- que le jeton VigieChiro « reste protégé par les **ACL du profil utilisateur** ». L'audit de dette du
  2026-07-28 l'avait déjà relevé : le code « se repose sur les ACL du profil **sans les contrôler
  explicitement** ».

⚠️ Les deux portaient sur le **chemin d'amorçage** de l'application, et sur le fichier qui contient le
**jeton**.

## Ce que les sondes ont mesuré, et qui contredit les deux issues

Deux sondes jetables, dispatchées sous **Windows Server 2025**, écrites pour **rapporter** et non pour
juger. Dans les deux cas, elles ont contredit ce que l'issue supposait.

**#3777 redoutait un cas irreproductible.** Elle prévenait que Java ouvre avec
`FILE_SHARE_READ | WRITE | DELETE`, donc qu'un simple flux pourrait ne rien bloquer, et qu'un test
écrit ainsi serait « vert sur les deux plateformes sans rien prouver ». Mesuré : **les quatre** façons
de tenir la cible provoquent l'`AccessDeniedException`, y compris `Files.newInputStream`.

**#3778 soupçonnait une protection absente.** Mesuré : exactement **trois** entrées `ALLOW` - le
propriétaire, `NT AUTHORITY\SYSTEM`, `BUILTIN\Administrators`. C'est l'équivalent de `600` sous POSIX,
où **root** lit aussi. **La doc disait vrai.**

⚠️ Ce qu'on ignorait n'était donc pas si la protection existe : c'est qu'**on n'en savait rien**.

## Décision

**Une affirmation de sécurité s'exprime comme une propriété qu'un test peut lire, ou elle ne compte
pas.** `ProtectionFichier.restreinteAuProprietaire` dit « aucun autre compte ne peut lire ce fichier »
- permissions POSIX ici, ACL là-bas. Le conditionnel quitte le test pour vivre dans un seul endroit
éprouvé.

⚠️ **Ni POSIX ni ACL fait lever**, plutôt que rendre `true`. Annoncer une protection depuis une
**ignorance** serait exactement le faux vert que ce dispositif existe pour empêcher.

### Et l'écriture insiste, plutôt que d'échouer ou de renoncer

Cinq tentatives espacées de 150 ms, ~600 ms - assez pour traverser une analyse antivirus, trop court
pour qu'un utilisateur croie l'application figée. Au-delà, un refus qui **nomme** la cause.

Trois options étaient sur la table ; le porteur a tranché pour la reprise après que la mesure a montré
que le cas n'est pas rare mais **ordinaire** sous Windows.

⚠️ **La reprise ne regarde pas le système d'exploitation.** Sous POSIX, une `AccessDeniedException` est
un vrai refus de droits : elle coûtera le butoir avant d'échouer. C'est le prix assumé pour ne pas
déduire un comportement d'un nom de plateforme - ce que [ADR 3738 / `CouleurCli`] a appris à ne plus
faire.

### Trois `assumeTrue` disparaissent, dont un qui mentait sur sa nature

L'un d'eux vivait **dans un helper**, pas en tête de test : sous Windows, `creation_restreinte`
s'interrompait à l'appel de `permissions()`, **emportant l'assertion suivante** - qui n'avait rien de
POSIX. Un test **partiel** qui se présentait comme **sauté**.

Le cas qui exige vraiment POSIX - sa fixture *crée* un fichier `rw-rw-rw-` - porte désormais
`@EnabledIf` : déclaratif, visible au rapport, et **incapable d'interrompre un test au milieu**.

## Conséquences

- **La garantie est éprouvée chaque mardi** sous Windows, au lieu d'être écrite.
- **Le refus de l'écriture est actionnable** : il nomme la tenue au lieu de parler de droits.
- ⚠️ **La branche ACL est inatteignable sous Linux** : PIT y laisse 8 mutants sans couverture, comme le
  repli de lecture du verrou (#3714). C'est structurel, pas une lacune - la branche est exercée par le
  passage sous Windows, et le dire évite qu'on la croie non testée.
- ⚠️ **Un test de câblage ne prouve pas la plateforme.** Le cas qui traverse le vrai déplacement avec un
  vrai lecteur passe sur les deux systèmes : son vert ne dit **pas** que Windows a emprunté la branche
  de reprise. C'est la **sonde** qui l'établit, et elle est citée dans le doc-comment pour qu'on n'ait
  pas à la refaire.

## Alternatives écartées

- **Laisser remonter l'échec** : honnête, mais un antivirus qui passe au mauvais moment devient une
  erreur que l'utilisateur ne peut ni comprendre ni corriger, sur le chemin de démarrage.
- **Retomber sur un remplacement non atomique** : sacrifie ce que la classe existe pour tenir. Un
  `connexion.json` tronqué se lit « non connecté », soit une déconnexion inexpliquée plutôt qu'une
  erreur.
- **Garder les `assumeTrue`** : un saut est honnête, mais il laisse la propriété **non vérifiée** là où
  elle compte le plus - et celui du helper ne se présentait même pas pour ce qu'il était.
