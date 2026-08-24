# Les clips tournés contre la plateforme

Ces clips-là montrent l'application en train de parler à la **vraie** plateforme Vigie-Chiro, et non à
ses fixtures. Ils vivent à part, sur la pré-version
[`clips-connectes`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-connectes),
et cette page dit pourquoi et comment les lire.

!!! warning "Un lecteur vide dit « pas encore tourné », jamais « le produit est cassé »"

    La pré-version se peuple par un **tournage**, qui est manuel : tant qu'aucun tournage connecté n'a
    eu lieu depuis qu'un scénario existe, son lecteur reste vide. C'est la même règle que pour les deux
    autres pages, et `PageDesClipsTest` garde la correspondance entre les cas et les adresses, pas la
    présence des fichiers.

## Ce qu'un clip connecté prouve, et qu'un autre ne peut pas

L'[ADR 4142](../decisions/4142-un-cas-dit-ou-se-lit-son-verdict.md) a mesuré que sur les 360 cas de
recette non couverts, **environ 220** vivent dans des sessions dont l'objet est **hors de
l'application** : le dépôt reçu par la plateforme, la nuit rapatriée du serveur, les résultats
Tadarida.

Filmé contre un bouchon, un cas pareil donne un clip **convaincant et creux**. Il ne devient pas faux :
il devient **muet sur son propre objet**, ce qui est pire, parce qu'on le regarde en croyant savoir.

C'est mesurable plutôt qu'affirmé. Le bouchon `stub_vigiechiro.py` fait 135 lignes et ne sert que la
ressource `sites` : il n'exige aucun `If-Match`, ne rejette jamais un `max_results` au-delà de 100, et
ne connaît ni les dates RFC 1123 ni le refus de `numero`. Le vert des scénarios d'aujourd'hui dit donc
que notre client parle à **notre idée** de la plateforme.

## Pourquoi ils ne sont pas rangés avec les autres

⚠️ **Un clip connecté ne se compare pas.**

Son écran dépend de **données vivantes**. Deux tournages du même commit peuvent différer parce qu'une
nuit a été traitée entre les deux, et le plancher de bruit établi en #4287 - médiane 0,008 %, pire cas
0,809 % - mesurerait alors la plateforme au lieu du rendu. Le plancher par cas n'y répond pas non plus :
un cas connecté aurait un bruit énorme et se retrouverait durablement en bas du classement, y compris
le jour où il change pour une vraie raison.

`comparer-tournages.yml` **refuse** donc cette source, en le disant. Un outil qui accepte une source
dont il ne sait rien conclure rend un résultat qui a l'air juste.

| | `clips-recette` | le tag `vX.Y.Z` | `clips-connectes` |
|---|---|---|---|
| ce qu'on y trouve | le dernier tournage sur fixtures | le tournage de cette version-là | le dernier tournage **contre la plateforme** |
| à quoi ça sert | regarder la recette du jour | comparer, et garder une trace | **regarder ce qu'un bouchon ne montre pas** |
| se compare ? | oui | oui | **non**, et l'outil le refuse |

## Ce qu'on y trouve, et qui l'écrit

L'énumération n'est **pas** tenue sur cette page, et c'est délibéré : une liste écrite à la main
annoncerait des clips qui n'ont pas été tournés. Elle vient de l'`index.md` que le tournage verse à côté
de ses pièces, à chaque passage :

[**L'index du dernier tournage connecté**](https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-connectes/index.md)

L'adresse d'un clip se déduit de son nom, comme ailleurs :

```
https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-connectes/<Classe>.<test>.mp4
```

⚠️ Les pièces ne portent **aucun préfixe**, contrairement à celles d'un tag de version. La séparation
est portée par la pré-version elle-même : un préfixe sert à distinguer deux populations rangées au même
endroit, et il n'y en a qu'une ici.

## Les clips

<!-- Les adresses sont gardées par `PageDesClipsTest` : chacune doit désigner un test qui existe, et
     tout test citant un cas doit figurer sur l'une des pages de clips. Une adresse morte rend un
     lecteur vide, et un lecteur vide se lit comme un défaut du produit. -->

### S8-05 · l'avancement paraît dans la modale

> L'avancement paraît **dans** la modale de connexion, sans seconde fenêtre, et « Fermer » y est grisé.

Ce que ce clip montre et qu'un clip bouchonné ne montrerait pas : la progression suit une **vraie**
latence réseau, celle du `GET /moi` puis des rapprocheurs. Bouchonnée, elle n'est qu'une temporisation
choisie pour qu'il y ait quelque chose à filmer.

<video controls width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-connectes/ScenarioConnecteConnexionTest.l_avancement_parait_dans_la_modale.mp4"></video>

### S8-06 · la modale annonce l'identité

> À la fin, la modale de connexion annonce l'identité **et** le résumé de ce qui a été récupéré.

Le pseudo affiché est celui que la plateforme a rendu. Le résumé, lui, dépend de ce que le compte de
tournage contient : c'est la première chose que ce clip apprendra.

⚠️ Ce cas n'asserte que l'**identité**. Le résumé fait partie de ce que la case demande, mais son
contenu dépend du compte : l'asserter aujourd'hui figerait une attente qu'aucune mesure ne soutient
encore.

<video controls width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-connectes/ScenarioConnecteConnexionTest.la_modale_annonce_l_identite.mp4"></video>

## ⚠️ Ce que ces clips publient, et le compte que cela engage

`S8-06` montre l'identité que la plateforme a rendue. Un clip connecté porte donc, **en clair et sur
une pré-version publique**, le nom du titulaire du compte employé et le résumé de ce que ce compte
contient - combien de sites, combien de nuits, combien en attente d'analyse.

Ce n'est pas un effet de bord : c'est **exactement ce que la case demande**, et un clip qui le
masquerait ne montrerait plus son cas.

**La décision (#4345) : le compte du mainteneur EST le compte de tournage dédié.** Son nom figure déjà
dans chaque commit de ce dépôt, donc la publication n'ajoute rien à ce qu'il révèle déjà de cette
personne-là.

⚠️ **Et la propriété ne tient qu'à ce choix.** Le jour où un tournage connecté emploierait le compte de
quelqu'un d'autre - un étudiant, un observateur qui prête son accès - le clip publierait un nom que
rien n'oblige à être public, et le résumé décrirait des données qui ne sont pas les nôtres. Poser
`VIGIECHIRO_TOKEN_TOURNAGE` avec le jeton d'un tiers **n'est donc pas** un geste anodin, et rien dans
le dispositif ne s'y oppose : c'est une règle, pas un garde.

## Comment en produire

Le flux **tournage de recette** (`tournage-recette.yml`, `workflow_dispatch`), avec le drapeau
**`connecte`** coché.

Il exige le secret `VIGIECHIRO_TOKEN_TOURNAGE` et **refuse de partir** sans lui : sinon il rendrait des
clips d'un écran hors ligne, convaincants et muets sur leur objet.

## Ce que le jeton ne peut pas atteindre

Trois barrières, et chacune tient quand la précédente a manqué.

**Il n'entre pas par l'écran.** Le champ du jeton est un `TextField` et non un `PasswordField` : ce
qu'on y colle se lit, et le banc photographie le graphe de scène. Le jeton passe donc par
`StockageConnexion`, déposé **sans profil**, ce qui fait revérifier la modale à son ouverture sans
geste (#1369). Seul le geste de **coller** reste infilmable.

**Il ne dépasse pas son pas.** Posé dans l'`env:` d'un job, un jeton serait offert à toute la suite de
tests, que `ConnexionModule` pointe alors sur la production. `verifie-portee-des-secrets.sh` le refuse
ailleurs qu'au pas qui filme. Et le banc lui-même **lie sa propre source de jeton** : un scénario qui a
demandé une connexion factice reste factice, même dans un tournage connecté (ADR 4134).

**Il meurt avec le run.** `POST /logout` en fin de tournage retire ce jeton et lui seul de la carte du
compte. Sans cela, sa fenêtre d'exposition serait de quatorze jours pour des clips destinés à être
publiés.

Le détail de ces mesures vit dans
[le spike](../spikes/tournage-contre-la-plateforme.md).

## Ce qu'ils ne remplacent pas

⚠️ Le contrat d'API (`api-live.yml`, hebdomadaire) éprouve le **contrat** et rougit quand il dérive ;
un tournage connecté éprouve le **parcours** et produit une image. Le premier fait foi, le second se
regarde. Deux dispositifs, deux objets.
