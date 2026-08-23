# Les clips tournés contre la plateforme

Ces clips-là montrent l'application en train de parler à la **vraie** plateforme Vigie-Chiro, et non à
ses fixtures. Ils vivent à part, sur la pré-version
[`clips-connectes`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-connectes),
et cette page dit pourquoi et comment les lire.

!!! warning "La pré-version est encore vide, et ce n'est pas une panne"

    Aucun scénario n'appelle `BancDeRecette.connecteALaPlateforme()` à ce jour : le dispositif existe,
    les scénarios viennent avec #4307. Un lecteur vide sur cette page signifie donc « pas encore
    tourné », jamais « le produit est cassé ».

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
