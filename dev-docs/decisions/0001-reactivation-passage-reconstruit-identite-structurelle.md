# ADR 0001 — Identité d'un passage reconstruit : régénération structurelle, l'acoustique en indice

- **Statut** : Accepté — 2026-07-17
- **Chantier** : EPIC #1653 (réactiver un passage reconstruit depuis les bruts)
- **Vérification** : humaine — l'identité d'une tranche reconstruite (régénération structurelle vs empreinte) est une règle de reconstruction, vérifiée en revue et par les tests de réactivation

## Contexte

La réactivation d'un passage archivé « depuis les bruts » (#1302) rebranche les séquences retrouvées en confrontant chaque tranche à l'**empreinte** capturée à l'import (#1299) : identité **certaine**, bit à bit.

Un passage **reconstruit** depuis la plateforme (#1305) n'a jamais eu cette empreinte. Il ne porte qu'un **placeholder** (`reconstruit.wav`, sans fréquence ni `sha256`) et des séquences nommées d'après les titres du CSV distant. Quand l'utilisateur veut en récupérer l'audio depuis sa carte SD, on **régénère** les tranches en rejouant la transformation de l'import sur le brut qu'il désigne. Mais il n'y a **rien** à quoi comparer une empreinte : le passage n'en a jamais eu. La cascade de vérification ordinaire (#1309) tombe alors sur son dernier cran, l'**acoustique** (le cri attendu est-il présent ?), qui produit de nombreux **faux négatifs** sur des cris réels faibles - au point de tout refuser (le cas réel : 1 séquence retenue sur 134).

## Décision

Pour un passage reconstruit, l'identité d'une tranche régénérée repose sur la **régénération structurelle**, pas sur l'empreinte :

- la tranche est un extrait **verbatim** (octet à octet) du brut que l'utilisateur a désigné, produit par la **même transformation déterministe** que l'import - c'est **cela**, la preuve ;
- son **nom** doit correspondre à une séquence attendue (le nommage ne dépend que du nom de l'original et de la fréquence d'acquisition) ;
- la **concordance acoustique** est **mesurée et rapportée en indice**, jamais opposée en veto.

## Conséquences

- L'audio d'un passage reconstruit **revient** depuis la carte SD, même sans aucune empreinte stockée. Sur le cas réel : de 1 séquence sur 60 bruts à **134 récupérées, 121 confirmées à l'acoustique**.
- Les originaux **adoptés** après hydratation (#1651) sont déclarés « **purgés** » : connus et prouvés par régénération, mais non stockés localement (l'utilisateur garde ses bruts) - le même état qu'un passage archivé par purge.
- L'identité obtenue est **structurelle** (nom + extrait verbatim), pas au niveau CERTITUDE (empreinte). C'est un choix assumé : l'utilisateur a désigné le brut, et l'extrait en est byte-exact.
- L'acoustique restant un indice, une nuit dont les cris sont faibles n'est pas refusée à tort ; l'indice est tout de même remonté à l'utilisateur (« N/M concordantes »).

## Alternatives écartées

- **Exiger une empreinte.** Impossible : un passage reconstruit n'en a pas. Résultat : 0 séquence récupérée, et le message trompeur « N introuvables » alors que les fichiers sont là.
- **Garder le veto acoustique.** Les faux négatifs sur cris réels faibles refusaient l'essentiel des tranches pourtant authentiques (1/134 sur le cas réel). La concordance acoustique dépend en outre du détecteur, lui-même corrigé par [ADR 0002](0002-detection-acoustique-energie-de-pointe.md).
