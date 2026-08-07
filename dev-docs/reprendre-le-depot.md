# Reprendre le dépôt

Cette page répond à une question que le reste de la documentation ne traite nulle part : **que faut-il
savoir pour *tenir* ce dépôt**, et non pour en lire le code ?

Elle existe parce que la mesure était nette : `VIGIECHIRO_TOKEN` n'était cité que dans **un** fichier,
et les mots « rituel » et « bus factor » dans **aucun** (#2753). La doc développeur est riche sur
l'architecture et muette sur les accès.

!!! danger "Aucune valeur secrète ici"
    Cette page dit **quels** secrets existent, **à quoi** ils servent et **où** les régénérer. Jamais
    leur contenu. Un jeton qui apparaîtrait ici serait à révoquer immédiatement : il est déjà dans
    l'historique git (cf. `verifie-jeton.sh`, qui garde ce dépôt contre exactement cela).

## Les secrets, et ce qui tombe sans eux

| Secret | Consommé par | Sans lui |
|---|---|---|
| `VIGIECHIRO_TOKEN` | `api-live.yml` | le contrat API hebdomadaire **ne vérifie plus rien** - le workflow reste vert avec un avertissement, et la veille de fraîcheur rougit au bout de 21 jours (ADR 2748) |
| `DOCS_DEPLOY_TOKEN` | `docs.yml` | les trois sites de documentation ne se déploient plus |
| `WINGET_TOKEN` | `winget.yml` | ⚠️ **il n'existe pas** : le workflow est **inerte**, et c'est voulu. La soumission winget se fait à la main |

`GITHUB_TOKEN` est fourni par GitHub à chaque exécution : rien à créer.

### ⚠️ Le seul secret qui expire : `VIGIECHIRO_TOKEN`

Il vit **14 jours**. Il se récupère dans le navigateur, connecté à la plateforme, sous
`localStorage['auth-session-token']`, puis :

```bash
gh secret set VIGIECHIRO_TOKEN --repo echonuit/vigiechiro-pr-companion
```

Ne pas le renouveler ne casse rien visiblement - c'est tout le problème, et c'est ce que
[l'ADR 2748](decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md) traite.

## Les variables de dépôt

| Variable | Effet |
|---|---|
| `ENABLE_RELEASE` | à `true`, le train de publication publie réellement. Absente, `release.yml` reste dormant |
| `ENABLE_PAGES` | à `true`, les sites se déploient sur Pages |

Ce sont des **interrupteurs**, pas des secrets : `gh variable list` les montre.

## Les accès extérieurs

| Où | Ce qui en dépend | Comment on y accède |
|---|---|---|
| **Plateforme VigieChiro** | le contrat API, les sondes live, l'import et le dépôt réels | compte naturaliste sur le site, rôle Observateur suffisant en lecture |
| **Flathub** | **rien pour l'instant** : le paquet n'y est pas (#2191) | la soumission a été fermée par leur robot, checklist incomplète |
| **winget** | **rien pour l'instant** : le paquet n'y est pas (#2110) | la première soumission est manuelle ; `WINGET_TOKEN` absent |
| **Zenodo** | le jeu de données d'exemple « une nuit » | dépôt public, DOI figé |
| **GitHub Pages** | les trois sites de documentation | `DOCS_DEPLOY_TOKEN` |

## Ce qui se fait à la main, et à quel rythme

C'est la partie que personne ne devine en lisant la CI.

| Geste | Rythme | Ce qui arrive si on l'oublie |
|---|---|---|
| Renouveler `VIGIECHIRO_TOKEN` | tous les **14 jours** | le contrat API cesse de vérifier ; la veille rougit à 21 jours |
| **Vérifier le train de publication** | chaque **mercredi** après 6 h UTC | l'ADR 2744 le déclare humain : les notes doivent **agréger la semaine**, pas un commit |
| Regarder les aperçus régénérés | après une PR qui touche l'IHM | une capture fausse illustre la documentation sans que rien ne rougisse |

!!! warning "Deux canaux de distribution sont outillés et n'ont jamais rien distribué"
    C'est le piège le plus coûteux de cette page, parce qu'il se lit à l'envers : `.github/workflows/`
    porte `winget.yml` **et** `flatpak.yml`, `flatpak/` porte un manifeste versionné et validé. Tout
    donne à croire que les paquets existent et se mettent à jour. **Ils n'existent pas.**

    - **winget** : `Echonuit.VigieChiroCompanion` est absent de `microsoft/winget-pkgs`. Le workflow
      ne sait faire qu'une **mise à jour** - `winget-releaser` refuse une première soumission, et son
      en-tête le dit. Tant que le paquet n'est pas déposé une fois **à la main**, ce workflow n'a rien
      à mettre à jour. Il manque aussi le secret `WINGET_TOKEN` et un fork de `winget-pkgs`. Cf. #2110.
    - **Flathub** : ni `fr.echonuit.VigieChiroCompanion` ni l'ancien identifiant n'ont de dépôt chez
      Flathub. La PR de soumission a été fermée par le robot pour checklist incomplète, et deux points
      manquants sont des **actes humains** - une vidéo de l'application lancée en Flatpak, et une
      attestation personnelle. Cf. #2191.

    ⚠️ L'identifiant Flatpak a **changé** entre-temps (ADR 0047) : la PR fermée soumettait
    `io.github.iutinfoaix_s201.VigieChiro`, le manifeste porte aujourd'hui `fr.echonuit.…`. Reprendre
    la soumission n'est donc pas qu'une formalité de checklist.

!!! warning "Le train mérite un œil, même vert"
    Son premier départ réel a échoué **après** avoir créé le tag et déposé la Release en brouillon :
    `installers` et `publish` ont été sautés, laissant une version **à moitié publiée**. Quand un train
    échoue, on regarde `gh release list` **avant** `gh run view`.

## Ce que la gouvernance n'exige pas, et pourquoi

`main` n'a **aucune protection de branche**, et `CODEOWNERS` attribue tout à un seul compte. Ce n'est
pas un oubli : c'est un choix assumé, écrit dans
[l'ADR 2753](decisions/2753-une-regle-absente-et-assumee-vaut-mieux-qu-une-regle-contournee.md). Un
repreneur doit le savoir avant de s'appuyer sur une garantie qui n'existe pas - **c'est la CI qui tient
ce dépôt, pas une règle de fusion**.
