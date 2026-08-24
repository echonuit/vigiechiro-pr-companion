---
type: adr
title: "Une capture exige son libellé plutôt que de s'abstenir (amende 0025)"
status: stable
article: A4
chantier: "#3053, clôture des suites de #2967"
decided_at: 2026-07-31
verification: probable
enforced_by:
  - "scripts/adr/3053-capture-libelle.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-07-31
---

# Une capture exige son libellé plutôt que de s'abstenir (amende 0025)

## Contexte

L'[ADR 0025](0025-une-capture-passe-par-le-code-de-production.md) §4 pose qu'« un libellé partagé entre la production et une capture vit dans une **constante citée**, jamais dans deux chaînes jumelles », avec la raison exacte : « un libellé recopié se renomme une fois sur deux. »

**Quatre outils de capture ne l'appliquaient pas**, et le pronostic s'est réalisé. Renommer une puce de filtre (#2967) a laissé `CaptureSonsValidationFiltres` chercher « Groupe » dans un menu qui n'offrait plus que « Taxon parent ». Les trois autres n'avaient aucun rapport avec ce renommage : ils attendaient le leur.

Ce que produisait leur abstention n'est pas une capture manquante, c'est une **capture fausse** :

| Outil | Aperçu produit avec un libellé renommé |
|---|---|
| `CaptureSonsValidationFiltres` | la table entière, sous la légende « filtrée sur les chiroptères » |
| `CaptureSonsValidationLieu` | la table entière, sous la légende « filtrée sur un lieu » |
| `CaptureEcranReglages` | quatre onglets devenus **le premier**, chacun sous le nom d'un autre |
| `CaptureListeLieu` | aucune image, mais **en code 0** : la galerie garde la précédente, la CI reste verte |

Le dernier cas mérite d'être distingué : il ne publiait pas de fausse image, mais **rien ne distinguait « rien à refaire » de « le geste n'a pas eu lieu »**.

## Pourquoi la constante partagée ne suffisait pas

Les catalogues de critères (`CriteresAudio`, `CriteresActivite`…) sont **package-private** : un outil de capture vit dans un autre paquet et ne peut pas citer leur constante. Les rendre publics élargirait leur surface pour une raison qui n'est pas la leur - une capture n'est pas un motif d'API.

L'ADR 0025 §4 reste donc la **règle de premier rang** partout où la constante est atteignable. Cette ADR ne la remplace pas : elle en pose le **repli**.

## Décision

**Quand un outil de capture désigne un contrôle par son libellé, il exige de le trouver.** L'abstention (`findFirst().ifPresent(...)`) est proscrite : elle transforme un défaut de code en image publiée.

**Le refus nomme les libellés présents**, et pas seulement celui qu'il cherchait :

```
« Import » introuvable dans les onglets des réglages : la capture montrerait un
ecran sans ce geste. Libelles presents : [Général, Emplacements, Import, Audio, …]
```

C'est ce qui transforme un constat en piste : le libellé a **changé**, il n'a pas disparu, et la correction consiste toujours à lire la liste. Le mécanisme vit dans `ApercuFx.exigerParLibelle`, à côté du refus de capturer un libellé comprimé ([#2049](0025-une-capture-passe-par-le-code-de-production.md)) : même famille, même raison.

**Décliner en code 0 tombe sous la même règle.** Un outil qui renonce proprement laisse la galerie porter l'image précédente sans que rien ne le signale. Il lève.

## Conséquences

- Un renommage de libellé **casse la CI** au lieu de dériver en silence. C'est le but : le coût se paie au moment où on le crée, par celui qui le crée.
- La contrainte est **bornée aux outils de capture**. Ailleurs, `ifPresent` sur une valeur réellement facultative est légitime ; c'est ce qui garde le cliquet exploitable plutôt que bruyant.
- Un outil qui aurait une raison de s'abstenir n'est pas interdit : il **relève le cliquet**, ce qui est une décision écrite plutôt qu'une omission.

## Alternatives écartées

- **Rendre publiques les constantes des catalogues.** Élargit une API pour un besoin d'outillage, et n'empêche que le cas où la production et la capture sont dans le même dépôt au même moment - pas celui où la liste change de contenu.
- **Comparer les captures d'un build à l'autre.** Détecte le changement mais ne dit pas s'il est voulu ; et une capture fausse dès sa première génération n'a pas de témoin.
- **S'en remettre à la passe 8** (revue visuelle). Elle a trouvé ces défauts, mais **après** publication et seulement parce qu'un renommage l'avait mise sur la piste. Une règle qu'un humain seul applique n'est pas une règle.
