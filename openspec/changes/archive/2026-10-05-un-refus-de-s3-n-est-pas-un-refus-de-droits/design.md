## Context

Voir `proposal.md` pour le pourquoi. L'état du code, relevé sur `main` à `b88c4b14f` :

- `CauseRefus.de(reponse)` décide la cause du **statut seul** : `401`/`403` donnent
  `AUTHENTIFICATION`, les autres `4xx` définitifs `CONTENU`. Sa javadoc range « droits S3 manquants,
  URL signée expirée » dans `AUTHENTIFICATION`, et pose que « la cause vient du statut, jamais du
  texte ».
- La cause a **un seul** point de construction en production : `TeleverseurArchive.Resultat.echec`,
  appelé depuis `DepotVigieChiro` (l. 258), qui la recopie dans `EchecUnite` et dans
  `depot_unite.cause_refus` (`TEXT`, V41). Aucun code ne relit cette colonne en objet : elle ne sert
  qu'au `WHERE` de `DepotUniteDao.rearmer`.
- Le dépôt d'un seul bloc connaît son étape en échec (`TeleverseurArchive.televerser` : déclaration,
  `PUT` S3, finalisation). Le dépôt en parties ne la connaît pas : `ClientVigieChiro.deposerEnParts`
  rend la **première** issue en échec, qu'elle vienne de la demande d'URL de partie ou de la
  finalisation (API) ou du `PUT` d'une partie (S3).
- Le conseil s'écrit deux fois : `CompteRenduChiffreDepot.phraseDesRefus` (écran) et
  `DeposerVigieChiro` (CLI), tous deux sur `EchecUnite.seRearmeParUneReconnexion()`.
- Les séquences WAV passent par le même `TeleverseurArchive` : ce changement les couvre sans code de
  plus.

## Goals / Non-Goals

**Goals :**

- Une provenance décidée à l'émission, portée jusqu'à la cause, sans relire aucun texte.
- Un conseil par cause, identique sur les deux surfaces.

**Non-Goals :**

- Lire le code XML rendu par S3 (`SignatureDoesNotMatch`, `AccessDenied`, `RequestTimeTooSkewed`)
  pour affiner le conseil. Voir la décision 3.
- Réarmer automatiquement un refus du stockage. Aucun événement observable par l'application ne dit
  que la cause est levée.
- Toucher la ventilation du compte rendu (déposées, en échec, restantes) : elle compte des
  catégories, pas des causes, et la règle A14 qu'elle tient n'est pas concernée.

## Decisions

### 1. La provenance remonte par le dépôt en parties, pas par `ReponseApi`

`deposerEnParts` rend son issue **avec** la provenance de l'étape qui l'a produite : un petit type de
`commun/api` (une issue et une `Provenance` `API` ou `STOCKAGE`). `TeleverseurArchive` fournit
lui-même la provenance sur le chemin d'un seul bloc, où il sait déjà quelle étape a échoué.
`Resultat.echec(raison, reponse, provenance)` la transmet à `CauseRefus.de(reponse, provenance)`.

Options écartées :

- **Un composant « origine » dans `ReponseApi.Refuse`.** Toute réponse porterait sa provenance, ce
  qui est séduisant, mais le record est déconstruit par position dans 14 sites, dont 13 en production
  (compté par `grep -rnE "Refuse<[^>]*>\((int|var|Integer)"`), et `transformer`, `lireAvec` et `puis`
  devraient tous la propager. Le coût tombe sur des chemins qui n'ont rien à en faire, pour une
  information que seul le dépôt consomme.
- **Deviner la provenance depuis l'hôte de l'URL ou le corps de la réponse.** C'est relire du texte,
  ce que `CauseRefus` s'interdit pour de bonnes raisons (#3689) : la même panne s'écrit de trop de
  façons.

### 2. `STOCKAGE` est une troisième cause, pas une variante d'`AUTHENTIFICATION`

`CauseRefus` gagne `STOCKAGE`. Le réarmement passe déjà une cause en dur
(`rearmer(CauseRefus.AUTHENTIFICATION, …)`) : une nouvelle valeur en est exclue sans toucher au DAO,
et `EchecUnite.seRearmeParUneReconnexion()` reste vrai pour `AUTHENTIFICATION` seulement.

Option écartée : **un booléen « réarmable » à côté de la cause.** Deux champs dont l'un se déduit de
l'autre finissent par se contredire, et c'est précisément la forme du défaut de #3961, où une règle
écrite deux fois avait une copie inerte qui se lisait comme l'autorité.

### 3. Le conseil du stockage se tient à ce qui est vérifié

Pour `STOCKAGE`, le compte rendu dit que se reconnecter n'y changera rien, conseille de relancer le
téléversement, puis le dépôt manuel si le refus persiste. Les deux gestes sont vérifiés :

- la relance retente l'unité (`restantes()` rend « tout sauf déposé », et rien n'écarte une unité
  sur son drapeau `definitif`, #3946), et chaque tentative redéclare le fichier, donc redemande des
  URL signées neuves : une URL expirée se répare ainsi ;
- le dépôt manuel depuis le dossier est ce que Samuel a fait avec succès le 26 juillet (#3469) et
  le 14 septembre (#5596).

Option écartée pour ce lot : **lire le code S3** pour dire « réessayer n'y changera rien » face à un
`SignatureDoesNotMatch`. Ce serait plus précis, mais cela amende la règle « la cause vient du
statut, jamais du texte », et une règle se change par une décision, pas au détour d'un correctif.
La question reste ouverte pour une issue séparée si le besoin se confirme.

### 4. La règle amendée s'écrit en ADR à la clôture

La javadoc de `CauseRefus` passe de « la cause vient du statut » à « la cause vient du statut et de
la provenance, jamais du texte ». Le pourquoi durable va en ADR 5598 à la passe 11 de la clôture du
chantier ; cette note la prépare.

## Risks / Trade-offs

- [Des unités refusées par S3 **avant** ce changement portent `AUTHENTIFICATION` en base] → Une
  reconnexion les réarmera une dernière fois. C'est sans danger : elles redeviennent « à déposer »,
  et une relance les aurait retentées de toute façon. Aucune migration de données : réécrire ces
  lignes supposerait de deviner leur provenance depuis `message_erreur`, ce que la décision 1 exclut.
- [Un `403` de finalisation multipart peut venir d'une URL de partie périmée côté serveur] → Il reste
  classé **droits**, parce que c'est l'API qui a répondu. C'est la règle de provenance appliquée à la
  lettre, et le conseil de reconnexion y est plausible.
- [Deux textes du conseil, écran et CLI, qui peuvent dériver] → Un test par surface sur le même
  bilan, et le scénario de parité de la spécification. Mettre les deux phrases en commun est tentant ;
  la CLI énumère les unités quand l'écran les compte, ce que ce lot ne change pas.

## Migration Plan

Aucune migration de schéma : `cause_refus` est un `TEXT` qui accepte la nouvelle valeur. Une version
antérieure ouverte sur une base écrite par celle-ci ne relit pas la colonne en objet, donc ne
rencontre pas la valeur inconnue. Le retour arrière est un simple retour de version.
