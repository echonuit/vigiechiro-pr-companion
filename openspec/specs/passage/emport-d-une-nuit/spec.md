# emport-d-une-nuit Specification

## Purpose

Ce que l'application garantit quand une nuit part vers un autre poste pour y être relue, et quand
l'avis du relecteur revient. Ce que le paquet emporte, ce qu'il refuse d'emporter, comment l'avis est
attribué, et ce qu'il ne décide pas.

## Requirements

### Requirement: Le paquet emporte la sélection d'écoute, et elle est figée

Le paquet SHALL contenir les séquences de la sélection d'écoute en cours, ses métadonnées et les
verdicts déjà posés. Il ne SHALL PAS contenir les autres séquences de la nuit, ni les enregistrements
bruts.

La sélection SHALL être figée pour le relecteur : les deux jugent le même échantillon, faute de quoi
les deux avis porteraient sur des séquences quasi disjointes et ne se compareraient pas.

*Vérifié par* : `ServiceQualificationTest.une_nuit_venue_d_un_paquet_refuse_la_regeneration`, dont le
refus tombe si on le retire, et `PaquetAllerRetourTest`, qui écrit un paquet depuis une sélection de
deux séquences tirées d'une nuit qui en porte cinq, le rouvre, et retrouve les deux avec leurs
métadonnées et leurs verdicts, sans les trois autres ni le brut. La provenance est une valeur de
`MethodeSelection`, si bien que le `switch` exhaustif désigne lui-même les endroits à traiter.

#### Scenario: la régénération est fermée sur une nuit venue d'un paquet
- **WHEN** un relecteur ouvre un paquet et demande une nouvelle sélection d'écoute
- **THEN** l'application refuse en disant que la sélection est celle de l'expéditeur, plutôt que de
  produire un échantillon que personne ne pourra confronter au sien

#### Scenario: les bruts restent au poste d'origine
- **WHEN** un paquet est écrit pour une nuit dont les enregistrements bruts sont présents
- **THEN** le paquet ne les contient pas, et son plan le dit avant l'écriture

### Requirement: Le volume s'annonce avant que rien ne soit écrit

L'application SHALL présenter un plan d'export avant d'écrire le moindre octet. Le plan SHALL porter
le volume estimé, ventilé par nature de contenu, et la liste de ce qui sera écrit.

*Vérifié par* : `PlanDePaquetTest` et `ActionsEmportTest` pour le plan lui-même ; le cas de recette
S3-45, filmé, pour l'annonce à l'écran ; et `cli.bats`, où `emporter-nuit` sans `--oui` annonce le
volume et n'écrit rien.

#### Scenario: le plan précède l'écriture
- **WHEN** un utilisateur demande à emporter une nuit
- **THEN** l'application montre le volume estimé et ce qu'elle écrira, et n'écrit rien tant qu'il n'a
  pas confirmé

### Requirement: L'avis du relecteur est attribué à une personne nommée

Le paquet SHALL porter l'identité du relecteur, apposée à l'ouverture du paquet et non au moment du
jugement. Chaque verdict revenu SHALL porter le pseudo de qui l'a posé.

Le pseudo est celui de la connexion de la plateforme, déjà persisté localement et lisible hors
connexion. L'apposer à l'ouverture évite qu'une identité périmée rende un avis anonyme.

*Vérifié par* : `OuvertureDePaquetTest`, qui éprouve que le pseudo est capturé et non relu, et tombe
si l'ouverture capture l'identifiant de plateforme au lieu du pseudo ; les cas de recette S3-51 et
S3-52, filmés, pour l'identité que le compte rendu nomme et celle que le manifeste renvoyé signe.

*Reste sans dispositif* : la pose d'un verdict au travers d'un paquet ouvert après péremption. Ce
flux n'existe pas encore, donc l'horloge injectée qui l'éprouverait n'a rien à éprouver. Le second
scénario ci-dessous décrit une garantie **visée**, non une garantie tenue.

#### Scenario: un relecteur dont la connexion a expiré depuis l'ouverture
- **WHEN** un relecteur ouvre un paquet alors que sa connexion est valide, puis pose un verdict plus
  de quatorze jours après
- **THEN** le verdict porte le pseudo relevé à l'ouverture, et non une identité vide

#### Scenario: un paquet ouvert sans connexion valide
- **WHEN** un relecteur ouvre un paquet alors qu'aucune identité valide n'est disponible
- **THEN** l'application refuse d'ouvrir le paquet en nommant la cause, plutôt que de recueillir des
  verdicts anonymes

### Requirement: L'avis du relecteur se range à côté du nôtre, jamais dessus

À l'import d'un avis, l'application SHALL conserver le verdict de l'expéditeur inchangé et ranger
celui du relecteur à côté. Elle ne SHALL PAS fondre les deux, ni en dériver un troisième.

La sélection étant figée, un verdict qui désigne une séquence hors d'elle ne peut venir que d'un
paquet qui ne correspond pas à la nuit visée. L'application SHALL alors refuser l'import en nommant
la séquence en cause, plutôt que d'écarter le verdict en silence ou de l'accueillir sans ligne où le
ranger.

*Vérifié par* : `RepriseAvisTest`, dont les trois cas couvrent le rangement à côté du verdict de
l'expéditeur, le refus qui n'écrit rien, et le second avis qui attend la confirmation ; les cas de
recette S3-48 et S3-49, filmés, pour la question qui nomme ce qui serait perdu.

#### Scenario: les deux ont jugé la même séquence
- **WHEN** un avis revient avec un verdict sur une séquence que l'expéditeur avait déjà jugée
- **THEN** les deux verdicts sont visibles côte à côte, chacun avec le pseudo de qui l'a posé

#### Scenario: un verdict désigne une séquence hors de la sélection figée
- **WHEN** un avis revient avec un verdict sur une séquence que l'expéditeur n'avait pas tirée
- **THEN** l'application refuse l'import en nommant la séquence en cause, car le paquet ne correspond
  pas à la nuit visée

#### Scenario: un second avis arriverait par-dessus un premier
- **WHEN** un avis est importé alors qu'un avis d'un autre relecteur est déjà présent
- **THEN** l'application dit ce qui sera remplacé et demande confirmation avant d'écrire

### Requirement: L'avis du relecteur ne pèse pas sur le verdict du passage

Le verdict d'un passage SHALL continuer de se dériver des seuls verdicts de l'expéditeur. L'avis du
relecteur s'affiche, il ne vote pas.

C'est une décision de ne pas faire, et elle est délibérée : vingt-trois classes consomment le verdict
d'un passage, du tableau multisite au solde de saison. Décider qu'un relecteur y pèse est une
décision de domaine, hors de ce chantier.

*Vérifié par* : `AgregationVerdictTest`, qui SHALL rester vert sans être modifié.

#### Scenario: un avis divergent n'altère pas le verdict du passage
- **WHEN** un avis revient avec des verdicts qui contrediraient le verdict dérivé du passage
- **THEN** le verdict du passage reste celui que les verdicts de l'expéditeur dérivent
