# Tourner les clips contre la plateforme VigieChiro

Chantier : [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291).

Le banc filmé tourne aujourd'hui contre des fixtures. La demande est de pouvoir lancer une session de
tournage dans un mode où l'application parle à la **vraie** plateforme, en fournissant au job un jeton
VigieChiro jetable.

L'enjeu n'est pas le confort. L'[ADR 4142](../decisions/4142-un-cas-dit-ou-se-lit-son-verdict.md) a
mesuré que sur les 360 cas de recette non couverts, **environ 220** vivent dans des sessions dont
l'objet est hors de l'application : le dépôt reçu par Vigie-Chiro, la nuit rapatriée du serveur, les
résultats Tadarida. Filmer ceux-là avec une frontière bouchonnée donne un clip convaincant et creux.
Un seul cas cité porte aujourd'hui `HORS_APPLICATION`, et c'est parce que ce qui a été couvert
jusqu'ici est précisément ce qui se filme sans réserve.

Un mode connecté est donc la porte d'entrée de ces 220 cas. Cette page mesure ce qu'il coûterait,
avant que quiconque écrive une ligne de YAML.

## Ce qui a été mesuré, et comment

Toutes les mesures datent du 2026-08-23, sur `echonuit/vigiechiro-pr-companion`, dépôt **public**.

| Question | Comment | Résultat |
|---|---|---|
| Une entrée de dispatch se lit-elle sans compte ? | `GH_TOKEN="" gh api repos/…/actions/runs/32645724859 --jq .display_title` | `comparaison clips-java → v2.189.0 (banc java)` |
| Les journaux d'un run se téléchargent-ils sans compte ? | `GH_TOKEN="" gh api repos/…/actions/runs/32645724859/logs -i` | `HTTP 200`, `logs_88457756936.zip` |
| Les artefacts se téléchargent-ils sans compte ? | `GH_TOKEN="" gh api repos/…/actions/artifacts/9494835940/zip -i` | `HTTP 200` |
| Le champ du jeton est-il masqué à l'écran ? | `ConnexionModale.fxml:67` | `<TextField fx:id="champToken" …>` |
| Par où un jeton entre-t-il sans passer par l'écran ? | `ConnexionModule.jetonPonctuel()` | propriété `vigiechiro.token`, sinon `VIGIECHIRO_TOKEN` |
| Vers quel serveur pointe-t-on par défaut ? | `ConnexionModule.urlDeBase()` | la **production**, sauf `vigiechiro.url` / `VIGIECHIRO_URL` |
| Quelle est la forme d'un jeton ? | `vigiechiro/xin/auth.py:212-213` | `[A-Z0-9]{32}`, tiré au hasard |
| Un jeton se révoque-t-il ? | `vigiechiro/xin/auth.py:187-199` | `POST /logout`, `$unset` du jeton |
| Existe-t-il un rôle sans écriture ? | `vigiechiro/settings.py:23-28` | `Lecteur`, qui n'inclut que `Lecteur` |

Les quatre dernières lignes sont lues dans le code de la plateforme (backend Python-Eve), pas déduites
de son comportement.

## 1. Une entrée de `workflow_dispatch` n'est pas un secret

C'est vérifié, et le verdict est net : **un jeton ne peut pas transiter par une entrée de dispatch.**

Trois raisons, dans l'ordre de gravité.

**Le `run-name` publie l'entrée.** `tournage-recette.yml` porte
`run-name: "tournage ${{ inputs.session }} sur ${{ inputs.plateforme }}"`, et le `display_title` qui en
résulte se lit **sans authentification** sur un dépôt public. Ajouter une entrée `jeton` sans toucher
au `run-name` ne la publierait pas par ce canal, mais la propriété tiendrait alors à une ligne de YAML
que personne ne relit en ajoutant une entrée.

**Rien ne masque une entrée.** Le masquage de GitHub s'applique aux valeurs enregistrées comme secrets,
et la documentation le décrit comme une rédaction **dans les journaux** (« redacted from logs »). Une
entrée de dispatch n'est enregistrée nulle part : toute étape qui l'affiche l'imprime en clair, et
`::add-mask::` posé à la première étape arrive après que la valeur est déjà dans les métadonnées du run
et dans le contexte `github.event.inputs`.

**Et les journaux sont publics.** Mesuré ci-dessus : `HTTP 200` sans jeton d'API. Le seul lecteur qu'on
imaginait, celui qui a accès au dépôt, n'est pas le seul lecteur.

⚠️ La documentation GitHub ne porte **aucun avertissement** sur ce point. L'absence d'avertissement se
lit facilement comme une autorisation ; c'est pour cela que cette page l'écrit.

### Les options de transport, comparées

| Option | Ce qui l'amène au job | Qui la pose | Ce qu'elle coûte | Verdict |
|---|---|---|---|---|
| **Entrée de `workflow_dispatch`** | `inputs.jeton` | quiconque peut lancer le workflow | rien à construire, mais la valeur est en clair dans les métadonnées, non masquée, et les journaux du run sont publics | **exclue** |
| **Secret de dépôt** (`secrets.VIGIECHIRO_TOKEN`) | `env:` du pas | un mainteneur, par `gh secret set` | déjà en place pour `api-live.yml` ; masqué dans les journaux ; mais **partagé** avec le contrat hebdomadaire, qu'un jeton de tournage écraserait | possible, au prix d'un **second** secret |
| **Secret d'environnement + règle de protection** | `environment:` déclaré par le job | un mainteneur, plus une règle d'approbation | isole le jeton au seul job qui déclare l'environnement, et un relecteur obligatoire fait du tournage connecté un geste approuvé ; aucun workflow du dépôt ne déclare `environment:` aujourd'hui, c'est donc de l'infrastructure neuve | **recommandée** |
| **`secrets: inherit` en `workflow_call`** | l'appelant | l'appelant | commode le jour où le train appellera ce mode ; mais `inherit` passe **tous** les secrets, `FLATPAK_GPG_KEY` et `WINGET_TOKEN` compris, à un job qui exécute les tests du produit | à éviter ; déclarer le secret **nommément** sous `on.workflow_call.secrets` |
| **Jeton court par échange OIDC** | `id-token: write` puis échange | personne, aujourd'hui | ce serait la bonne réponse : plus de secret au repos, une durée de vie de quelques minutes. La plateforme ne le permet pas (cf. ci-dessous) | **impossible sans travail côté plateforme** |

**Recommandation : un secret d'environnement, nommé distinctement de celui du contrat, déclaré
nommément à l'appel, et révoqué en fin de run.**

L'argument tient en trois points.

Le **secret de dépôt seul** ne suffit pas parce que `VIGIECHIRO_TOKEN` est déjà pris : il alimente
`api-live.yml`, dont la veille de fraîcheur rougit au bout de trois semaines sans vérification réelle.
Un tournage qui poserait son jeton au même endroit rendrait cette veille inintelligible, et son
expiration ferait passer le contrat hebdomadaire pour sauté. Deux usages, deux secrets.

L'**environnement** apporte ce que le secret de dépôt ne peut pas donner : le jeton n'est visible que du
job qui déclare l'environnement, et la règle de protection oblige à une approbation avant que ce job ne
démarre. Un tournage connecté n'est pas une opération de routine ; qu'il demande un geste explicite est
une propriété, pas une friction. Le coût est réel et il faut le dire : c'est le premier `environment:`
du dépôt, donc une notion de plus à comprendre pour le prochain qui lira les workflows.

La **révocation** est ce qui rend le jeton réellement jetable, et la plateforme la donne (cf. § suivant).

### Ce que la plateforme permet, et qui ferme la porte OIDC

Lu dans `vigiechiro/xin/auth.py` :

- un jeton est **32 caractères** tirés dans `A-Z0-9`, avec une expiration à `TOKEN_EXPIRE_TIME`, soit
  **14 jours** ;
- il n'est frappé qu'au retour du parcours OAuth (`/login/github`, `/login/google`), rendu au frontal
  par une redirection `…/#/?token=…`. Il n'existe **aucune** route qui échange des identifiants
  machine, une assertion OIDC ou un JWT contre un jeton ;
- un compte porte une **carte de jetons** (`tokens.<jeton>: expiration`), donc plusieurs jetons
  coexistent. Un jeton frappé pour un tournage est **indépendant** de celui du navigateur.

Conséquence : l'échange OIDC n'est pas une option qu'on aurait négligée, c'est une option qui n'existe
pas de notre côté. La proposer supposerait un travail côté MNHN/CESCO, et il faudrait le demander.

⚠️ La contrepartie du parcours OAuth est qu'un jeton **s'obtient à la main**, dans un navigateur. Le
secret devra donc être reposé régulièrement, comme celui du contrat, et ce mode ne sera jamais
entièrement automatique.

### Ce que la plateforme offre en revanche : la révocation

`POST /logout`, authentifié par le jeton lui-même, fait un `$unset` de ce jeton et de lui seul. Deux
propriétés en découlent, et elles sont exactement ce qu'il faut ici :

- **révoquer est une seule requête**, donc une étape `if: always()` en fin de tournage borne
  l'exposition à la durée du run, quelles que soient les fuites en aval ;
- **révoquer n'affecte pas les autres jetons du compte**, donc la personne qui a posé le secret ne se
  fait pas déconnecter de son navigateur.

C'est ce qui rend « jeton jetable » autre chose qu'une formule.

## 2. Le jeton peut finir à l'écran

Ce risque est **propre au tournage**. Les autres workflows manipulent un secret dans un journal, que
GitHub masque. Le banc, lui, produit une image, et une image ne se masque pas.

### La surface, et elle est unique

Un balayage des vues et des FXML ne rend qu'un seul endroit où le jeton peut paraître :
`ConnexionModale.fxml:67`, `<TextField fx:id="champToken">`. C'est un `TextField` et non un
`PasswordField` : ce qui y est collé se lit. Le contrôleur le vide après une connexion réussie
(`ConnexionModaleController:297` et `:326`), mais il le vide **après**, et le banc filme entre-temps.

Le reste est propre, et c'est vérifié plutôt que supposé : le jeton stocké n'est jamais réinjecté dans
le champ (la re-vérification de #1369 passe le jeton au réseau, pas à l'écran), aucune infobulle ni
aucun bandeau ne le cite, et `dev-docs/observabilite.md` interdit déjà au journal de porter le jeton,
les en-têtes et le corps envoyé.

### Où va ce que le banc enregistre

| Destination | Qui peut la lire | Combien de temps |
|---|---|---|
| Artefact du run (`clips-<session>-<plateforme>`, `tournage.log`) | **tout le monde**, sans compte (mesuré) | 14 jours |
| Pré-version roulante `clips-recette` | tout le monde | jusqu'au tournage suivant |
| Tag de version, clips préfixés `java-` | tout le monde | **pour toujours**, le tag ne bouge pas |

⚠️ L'en-tête de `tournage-recette.yml` dit « il ne PUBLIE pas, les clips restent en artefact ». C'est
vrai du versement, et faux de la confidentialité : sur un dépôt public, un artefact est public. La
phrase mérite d'être précisée le jour où ce mode existera.

### Les parades, dans l'ordre où elles doivent être posées

**Le jeton n'entre pas par l'écran.** `ConnexionModule.jetonPonctuel()` lit la propriété système
`vigiechiro.token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, et ce jeton ponctuel
**l'emporte** sur la connexion enregistrée. Un tournage connecté doit donc l'injecter par là, jamais
par le champ.

⚠️ **Ce que cette parade coûte, et il faut l'assumer** : le cas qui vaudrait le plus, celui de la
connexion elle-même (coller un jeton et voir le badge passer au vert), devient précisément celui qu'on
ne peut pas filmer connecté. Un tournage connecté montre l'application **déjà** connectée. La connexion
reste filmée avec un jeton bouchonné, et son clip garde alors sa réserve.

**Le jeton a une forme, contrairement à ce que nous croyions.** `verifie-jeton.sh` explique qu'un jeton
VigieChiro est « une chaîne opaque, sans préfixe distinctif, qu'aucun catalogue de fournisseur ne
connaît », et c'est vrai des catalogues. Mais le code de la plateforme le tire dans `A-Z0-9` sur
exactement 32 caractères : `[A-Z0-9]{32}` est une forme, et elle ne ressemble ni aux empreintes
SHA-256 du dépôt (minuscules, 64 caractères) ni aux identifiants Mongo (hexadécimal minuscule, 24). Un
garde qui balaie les **textes** de l'artefact avant publication est donc possible, et bon marché.

⚠️ Il ne protège pas l'image. Un garde qui rougirait sur `tournage.log` en laissant passer un clip
serait exactement le dispositif rassurant que ce dépôt s'interdit : s'il est écrit, il doit dire ce
qu'il ne couvre pas.

**Et l'on révoque à la fin.** `POST /logout` en `if: always()` : après cette étape, ce qui aurait fui ne
vaut plus rien. C'est la seule parade qui tienne même si les deux premières ont manqué.

## 3. Écrire sur la plateforme réelle

`api-live.yml` est en lecture seule, et c'est un choix documenté. Le tournage doit s'y tenir, pour des
raisons plus fortes que celles du contrat hebdomadaire.

### Pourquoi la lecture seule, ailleurs

Les probes d'écriture demandent **trois verrous distincts** (`-Dvigiechiro.write=true`, puis une
participation de rebut, puis `-Dvigiechiro.message=true`), et le troisième existe parce que
`PUT …/messages` empile par `$push` : aucune route ne retire ni ne modifie un message, sur des données
que lit un validateur du MNHN. Ce qui est écrit **reste**.

Et le dépôt a déjà détruit des données. Le spike du 2026-07-13 l'a établi en lisant le code de la
plateforme : un dépôt en ZIP fait extraire les WAV côté serveur **sans `s3_id`**, puis
`delete_fichier_and_s3(zippj)` **détruit le ZIP sur S3**. Ni WAV, ni `.ta`, ni `.tc` ne survivent ;
seul le CSV d'observations reste. Une relance de traitement sur une participation réelle n'est pas une
opération sans conséquence.

### Le danger que personne ne verrait

C'est le point le plus important de cette section, et il ne se voit pas en lisant un YAML.

`ConnexionModule.jetonPonctuel()` lit `System.getenv("VIGIECHIRO_TOKEN")`. Les forks surefire
**héritent de l'environnement** du job : c'est ainsi que le job `fuseau-alternatif` passe `TZ`, et
c'est écrit dans [CI/CD](../ci-cd-release.md). Et `ConnexionModule.urlDeBase()` vaut la **production**
par défaut.

Autrement dit : poser `VIGIECHIRO_TOKEN` dans l'`env:` du **job** de tournage ne l'offre pas au
scénario visé, il l'offre à **toute** la suite filmée, pointée sur la production. Le profil `-Papi-live`
n'y change rien : il ne gouverne que la propriété système `vigiechiro.token` (`pom.xml:1472-1476`), pas
la variable d'environnement.

Un mode connecté n'arme donc pas un scénario. Sans précaution, **il arme la suite**.

### Les parades

- **`env:` au pas, jamais au job.** La portée d'un secret est la première décision, pas la dernière.
- **`VIGIECHIRO_URL` déclarée explicitement**, même quand elle vaut la production. Un défaut implicite
  qui pointe sur la production est un défaut qu'on oublie de relire.
- **Un compte dédié, rétrogradé au rôle `Lecteur`.** `ROLE_RULES` donne à `Lecteur` le seul rôle
  `Lecteur`, et toutes les routes d'écriture relevées portent `@requires_auth(roles='Observateur')` au
  minimum. Un jeton de `Lecteur` rend l'écriture impossible **par le serveur**, et non par notre
  discipline. C'est la seule parade qui survive à une erreur de câblage.

⚠️ Deux réserves honnêtes : rétrograder un compte demande un administrateur de la plateforme, donc une
démarche auprès du MNHN/CESCO ; et le relevé des rôles est lu dans un code que nous ne contrôlons pas
et qui peut changer. Une sonde qui vérifie qu'une écriture est bien refusée vaut mieux qu'une lecture
de source.

### Si des écritures devenaient nécessaires

Elles ne le sont pas pour tourner en lecture. Le jour où elles le deviendraient, la règle existe déjà
et n'a pas à être réinventée : compte dédié, **participation de rebut** jamais une réelle, et le
nettoyage écrit **avant** le tir, en sachant qu'une correction posée et un fichier déclaré ne se
retirent pas. Une relance destructrice a déjà coûté les ZIP côté S3 ; ce n'est pas une crainte, c'est
un antécédent.

## 4. Le déterminisme, et où les clips connectés doivent aller

Trois dispositifs supposent aujourd'hui que deux tournages sont comparables. Le mode connecté les
touche tous les trois, et pas au même endroit.

| Dispositif | Ce qu'il suppose | Ce que le mode connecté lui fait |
|---|---|---|
| L'oracle du banc (« Chaque cas de la session a sa ligne ») | le nombre de cas attendus est dérivé du classpath par `CorrespondanceRecetteTest` | rien : le compte des cas ne dépend pas des données. Ce garde **survit** |
| Le plancher de bruit de `compare-tournages.sh` | deux tournages du même commit diffèrent de ≤ 0,01 % à 5 % de tolérance | des données vivantes changent l'écran sans qu'aucun commit ait bougé. Le plancher mesure alors la plateforme, pas le rendu |
| La comparaison « dernière version contre tournante » | les deux côtés montrent le même produit sur les mêmes données | un cas « bouge » parce qu'une nuit a été traitée entre les deux tournages. Le signal le plus fiable, la **présence** du cas, tient encore ; l'image finale et la durée ne veulent plus rien dire |

⚠️ Le plancher par cas de #4287 aggrave le problème plutôt qu'il ne le règle : il classe chaque cas par
le rapport de son écart à son propre bruit. Un cas connecté aurait un bruit énorme et se retrouverait
durablement en bas du classement, c'est-à-dire invisible, y compris le jour où il change pour une vraie
raison.

**Verdict : les clips connectés ne vont pas au même endroit que les autres.**

Ni sur `clips-recette`, qui est la référence roulante de la comparaison, ni sur le tag d'une version à
côté des clips `java-`. Une destination distincte, et un préfixe distinct, pour la raison même qui a
fait préfixer `bash-` et `java-` : deux dispositifs qui publient au même endroit se contredisent tôt ou
tard, et ici ils se contrediraient **en silence**, puisque les deux produisent des clips valides.

Le mode connecté est un instrument de **recette**, pas un instrument de **comparaison**. Confondre les
deux coûterait la comparaison, qui marche.

## 5. Les pièges d'API : déjà absorbés, jamais éprouvés à l'écran

C'est le meilleur argument en faveur du mode connecté, et il tient à ce que le bouchon **ne reproduit
pas** les pièges que le client absorbe.

| Piège | Où il est absorbé | Ce que le bouchon en montre |
|---|---|---|
| `numero` refusé à l'écriture | `CorrespondanceParticipation` | rien : il ne sert que des sites |
| Dates en RFC 1123 UTC | `CorrespondanceParticipation.rfc1123Utc` | rien |
| `_etag` requis en `If-Match` pour `PATCH`/`PUT`/`DELETE` | `TransportVigieChiro.ecrire` et son `Rejeu` | rien : le bouchon n'exige aucun `If-Match` |
| `max_results > 100` rejeté en 422, listes vides en silence | `PaginationEve` plafonne, `CatalogueApi` explique, `ApiLire` refuse | il annonce `max_results: 100` sans jamais rejeter |
| L'instant du dépôt dépendait du fuseau du poste | **clos** : `FuseauDuPoint` dérive le fuseau de la commune du point (#3406, #3442) | sans objet |

Le bouchon `stub_vigiechiro.py` fait 135 lignes et ne sert que la ressource `sites`. Le vert des E2E
d'aujourd'hui ne dit donc **rien** de ces cinq lignes : il dit que notre client parle à notre idée de
la plateforme.

⚠️ Et c'est exactement pourquoi le mode connecté ne remplace pas `api-live.yml`. Le contrat
hebdomadaire éprouve le **contrat** et rougit quand il dérive ; un tournage connecté éprouve le
**parcours** et produit une image. Le premier fait foi, le second se regarde. Deux dispositifs, deux
objets.

## 6. Ce que les gardes du dépôt exigeraient

| Garde | Ce qu'elle imposerait à ce mode |
|---|---|
| `verifie-butoirs.sh` | un `timeout-minutes` sur tout job ajouté, y compris un job de révocation |
| `verifie-permissions.sh` | pas de plancher en écriture : le job qui filme reste en `contents: read` |
| `verifie-epinglage.sh`, `verifie-fraicheur-actions.sh` | toute action neuve épinglée au SHA, avec son commentaire de version |
| `verifie-noms-d-etapes.sh` | aucun `#` non cité dans un nom d'étape |
| `verifie-apt.sh`, `installer-paquets.sh` | si une installation s'ajoute, elle passe par la porte et câble son cache |
| `verifie-conditions-booleennes.sh` | un drapeau « connecté » en `type: boolean` s'écrit `if: ${{ inputs.connecte }}`, **jamais** `== 'true'` |
| `verifie-conditions-de-job.sh` | un job de révocation appuyé sur `needs` nomme l'état qu'il attend, sinon il sera sauté le jour où il servira le plus |
| `verifie-inventaires-ci.sh` | le tableau des workflows et celui des gardes autotestées de [CI/CD](../ci-cd-release.md) |
| `DocumentationAJourTest` | le compteur balisé `inv:workflows-ci`, qui ne bouge que si un **fichier** de workflow s'ajoute |
| `verifie-jeton.sh` | rien de neuf, et c'est le point : son motif cherche `vigiechiro.token` ou `auth-session-token` **affectés dans un fichier versionné**. Il ne regarde ni les artefacts, ni les clips, ni les journaux de run |

⚠️ Un mode porté par un **drapeau** sur `tournage-recette.yml` ne fait pas bouger le compteur des
workflows : celui-ci compte des fichiers. Ce n'est pas un oubli du compteur, c'est ce qu'il mesure.

## 7. La décision à écrire

Une décision structurante est en jeu, et elle ne se déduit d'aucun des points ci-dessus pris
séparément : **un clip tourné contre la plateforme réelle n'est pas du même genre que les autres, et il
ne se range pas avec eux.**

Elle trancherait trois choses :

1. **la destination**, distincte, et le préfixe qui l'accompagne, pour que la comparaison de tournages
   ne mélange jamais deux populations de clips ;
2. **la portée du secret**, au pas et non au job, parce que la portée par défaut arme la suite entière
   contre la production ;
3. **la lecture seule structurelle**, tenue par le rôle du compte et non par la discipline de celui qui
   écrit le YAML.

Elle s'écrira en passe 10, comme le veut le cycle, et portera le numéro de son chantier : **ADR 4291**.

⚠️ Ce que cette page **ne dit pas**, faute de l'avoir mesuré : combien de cas parmi les ~220 hors
application deviendraient réellement filmables avec un jeton en lecture seule. La lecture donne les
sites, les participations, les résultats et le journal de traitement, donc S8 (« récupérer une nuit
déposée ») semble à portée et S4 (« déposer et suivre ») non, puisque déposer est une écriture. Ce tri
demande de dérouler les deux scripts de session, cas par cas, et il n'a pas été fait ici.
