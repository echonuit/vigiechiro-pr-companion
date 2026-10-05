## Context

`ClientVigieChiro.chercherCarre(numero)` rend la liste des sites qui portent un carré. Trois appelants
la classent, chacun à sa façon (voir proposal.md, Why) :

```
 chercherCarre(n) -> [sites]
     |
     +-- RechercheCarreExistant   bouton « Vérifier »   [] = « déclarez-le ici », SUCCES
     |                                                 [Routier] = « existe déjà, récupérez-le »
     +-- RapatriementCarre        « Récupérer »         [] = Inexistant ; [Routier] = AutreProtocole
     +-- CreerSite (CLI)          creer-site            [Point Fixe] = refus ; sinon rien
```

`SiteVigieChiro.estPointFixe()` dit déjà si un site est en Point Fixe. La modale ne vérifie qu'au clic
(exigence « geste séparé ») ; « Créer » appelle `SiteEditViewModel.enregistrer()`, puis `apresSucces`,
qui rafraîchit « Mes sites », et ferme la modale.

## Goals / Non-Goals

**Goals:**

- Un seul classement du résultat de recherche, que les trois appelants lisent.
- Une seule phrase sur le portail, lue par la vérification, le rapatriement et `creer-site`.

**Non-Goals:**

- Créer le site sur la plateforme : écarté par la décision du 30 septembre, parce que c'est une
  écriture sur la plateforme nationale qui publie aussi une actualité.
- L'édition d'un site existant : la vérification à l'enregistrement ne vaut que pour la création.
- Le conseil du dépôt (`passage/model/SynchronisationParticipation`) : il dit déjà juste, et la règle
  d'architecture interdit à `passage` de citer `sites`.

## Decisions

**La classification vit dans `sites/model`, comme une fonction pure.** Elle prend la liste rendue par
`chercherCarre` et rend l'un de trois cas : `PointFixe(site)`, `AutreProtocole(titres)`, `Absent`.
L'indisponibilité n'en fait pas partie : elle vient de la réponse du portail, pas de la liste, et
chaque appelant la traite déjà. *Écarté* : corriger les seuls messages de la vérification et de
`creer-site` (option B de l'instruction) ; la règle resterait écrite trois fois, et c'est ainsi que
l'écart est né.

**Chaque appelant garde la phrase de son geste.** Vérifier avant de déclarer ne dit pas la même chose
que récupérer : « vous pouvez le déclarer ici » n'a de sens qu'avant, « rien n'a été récupéré »
qu'après. Ce qui est commun est la **phrase sur le portail**, une constante de `sites/model` :
« pour y déposer des nuits, il faudra l'activer en Point Fixe sur le portail Vigie-Chiro (y créer un
point), puis le récupérer ici. » *Écarté* : un message unique pour les trois gestes, qui forcerait
l'un d'eux à dire faux.

**La vérification gagne un verdict `AutreProtocole`**, et « Récupérer ce carré » ne s'offre plus que
sur `PointFixe`. Le bouton apparaissait sur tout « il existe », Routier compris, pour aboutir à « rien
n'a été récupéré ».

**« Créer » vérifie ce qui ne l'a pas été.** Le ViewModel sait si un verdict est affiché pour le
numéro saisi. S'il n'y en a pas, la modale interroge le portail hors du fil JavaFX, le bouton occupé,
puis :

- `PointFixe` : elle n'enregistre pas, affiche le verdict et le bouton « Récupérer ce carré » ;
- tout autre cas, indisponibilité comprise : elle enregistre, puis passe le verdict à l'appelant, que
  `apresSucces` porte désormais au bandeau de retour de « Mes sites ».

Si un verdict est déjà affiché, « Créer » s'en sert sans réinterroger. *Écarté* : vérifier sans
réseau, par un simple rappel ; le portail sait la réponse, et le rappel aurait parlé à tort aux
carrés déjà actifs. *Écarté aussi* : créer quand même un carré déjà en Point Fixe en le disant
(décision du porteur) ; le doublon local ne se rattache pas au dépôt.

**`creer-site` écrit sur sa sortie d'erreur**, comme l'avertissement « non vérifié » qu'elle porte
déjà. Sa sortie standard est l'identifiant du site, que les scripts lisent : `site=$(cli creer-site
…)` dans les `bats`.

## Risks / Trade-offs

- [« Créer » attend désormais un aller-retour réseau quand rien n'a été vérifié] → la recherche ne
  demande qu'une requête, rendue sous la seconde (#3458) ; hors connexion, elle échoue vite et la
  création se fait quand même.
- [Le verdict « absent » passe de succès à avertissement] → c'est voulu : un geste reste à faire
  avant de déposer, et le succès faisait croire le contraire.

## Migration Plan

Aucune donnée ne change. Un site local déjà créé en doublon d'un carré Point Fixe reste ce qu'il est :
le rapatriement sait déjà le rattacher.
