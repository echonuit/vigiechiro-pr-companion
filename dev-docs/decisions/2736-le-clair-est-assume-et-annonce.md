# ADR 2736 - Les secrets locaux restent en clair, **assumé et annoncé**, plutôt que protégés à côté de ce qui compte

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #2736, lot 2 (#2722) du chantier de dette #2720
- **Vérification** : humaine - une décision de périmètre ne se prouve pas par un scan ; ce qu'elle
  produit, en revanche, l'est : le garde `SecretsEcritsProtegesTest` tient l'écriture du jeton (#2735)
  et l'avertissement de sauvegarde a son issue d'implémentation.

## Contexte

L'EPIC « Sécurité des secrets locaux » (#1140) a été fermé le 12 juillet 2026 avec son périmètre de
**durcissement** non réalisé : stockage du jeton dans un coffre système, chiffrement éventuel au
repos. La page sécurité y renvoyait encore comme à un suivi vivant. Depuis, plus rien ne traçait ce
reste : c'est le défaut de gouvernance relevé par l'audit v2.112.0.

Ce qui a changé depuis, et qui rend l'arbitrage possible :

- le jeton n'est plus écrit dans un fichier permissif puis restreint (#2735) : il n'atterrit que dans
  un fichier créé déjà à `600`, remplacé de façon atomique ;
- les URL de stockage sont vérifiées avant d'être suivies (#2734) ;
- la décompression est bornée en ressources (#2732).

Restent deux questions, et une exigence.

## Décision

### 1. Le jeton reste un fichier `600`. Pas de coffre système.

**Le coffre protégerait le mauvais actif.** La page sécurité pose la règle la plus importante du
produit, et elle n'est pas technique : *ne jamais exposer la localisation d'espèces protégées*. Or ces
coordonnées vivent dans `vigiechiro.db`, **en clair, dans le même dossier que le jeton**. Un attaquant
local qui lirait `connexion.json` lit tout aussi bien la base d'à côté. Mettre le jeton au coffre
pendant que les gîtes restent lisibles déplacerait la serrure sans déplacer la porte.

**Le jeton est de faible valeur relative** : session de ~14 jours, révocable côté plateforme, portée
d'un compte Observateur. Il permet d'écrire sur la plateforme, ce que la base locale ne permet pas -
c'est le meilleur argument contre cette décision, et il ne renverse pas les deux autres.

**Le coût est réel et récurrent.** Java n'offre aucune API de coffre : il faudrait du natif par
système (Keychain, Credential Manager, Secret Service par le portail sous Flatpak), embarqué dans un
runtime `jlink`, testé sur trois plateformes, et maintenu. Pour un gain qui ne change pas ce qu'un
attaquant local obtient.

### 2. Les sauvegardes et exports ne sont pas chiffrés. Ils annoncent ce qu'ils contiennent.

La **sauvegarde complète** copie la base *et* les dossiers de son, en clair, là où l'utilisateur la
range : disque externe, clé USB, dossier synchronisé sur un nuage. C'est l'exposition la plus large du
produit, et elle est aujourd'hui **silencieuse**.

**Chiffrer poserait la question de la clé, sans bonne réponse.** Dérivée d'un mot de passe, elle
transforme la sauvegarde en piège : mot de passe oublié, sauvegarde perdue - au moment précis où elle
sert, c'est-à-dire quand tout le reste a échoué. Stockée à côté de l'archive, elle ne protège de rien.
Confiée au coffre système, elle rouvre le chantier écarté au point 1, et rend la sauvegarde illisible
depuis une autre machine, ce qui est justement son usage.

**Le remède est de rendre visible, pas de décider à la place.** Au moment de sauvegarder ou
d'exporter, l'application dit ce que l'archive emporte - localisations comprises - et invite à la
ranger en conséquence. C'est la ligne de l'[ADR 0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md) :
l'utilisateur possède ses fichiers, l'application observe et informe.

### 3. La page sécurité dit ce qui est protégé, ce qui ne l'est pas, et pourquoi.

Sans cette troisième partie, les deux premières se lisent comme des omissions. La page doit porter la
liste des protections **et** celle des non-protections assumées, chacune avec son motif - et ne plus
renvoyer à un durcissement « pour bientôt » qui n'arrivera pas.

## Conséquences

- Le périmètre resté ouvert à la fermeture de #1140 est **clos par une décision**, pas par un oubli.
- Un avertissement au moment de la sauvegarde et de l'export reste à écrire : c'est la seule
  implémentation que cette ADR engage (#3212).
- Un utilisateur dont le poste est compromis perd ses données locales et son jeton. C'est assumé, et
  c'est écrit : le produit vise le poste personnel d'un naturaliste, pas un terminal partagé en
  environnement hostile.
- Si la plateforme délivrait un jour un jeton de longue durée, ou si le produit stockait un secret
  ré-utilisable ailleurs (mot de passe, clé d'API personnelle), **le point 1 serait à rouvrir** : c'est
  la faible valeur relative du jeton qui le soutient, pas une opposition de principe au coffre.

## Alternatives écartées

**Coffre système pour le jeton.** Écartée pour les trois raisons du point 1. À noter : elle ne devient
intéressante que si la base est elle aussi protégée - le sujet à rouvrir serait alors « chiffrer le
workspace », pas « ranger le jeton ».

**Chiffrement optionnel des sauvegardes.** Écartée pour la question de la clé, et parce qu'une option
de sécurité que personne n'active protège surtout celui qui l'a écrite. L'avertissement touche tout le
monde, y compris ceux qui n'auraient pas coché la case.

**Ne rien changer et laisser #1140 pour référence.** C'est l'état que l'audit a relevé : une page qui
renvoie à un suivi mort. Une décision, même négative, vaut mieux qu'un renvoi qui ne mène nulle part.
