# ADR 4021 - Un aperçu montre ce que l'action produira, pas ce qu'elle écartera

- **Statut** : Accepté - 2026-08-20
- **Chantier** : #4021, trouvé en filmant le parcours du mélange pour #4013
- **Vérification** : certaine - `RattachementImportViewModelTest#l_apercu_ne_montre_pas_un_fichier_ecarte`

## Contexte

L'assistant d'importation affiche, avant d'agir, l'**aperçu du préfixe appliqué aux fichiers** : le
futur nom d'un enregistrement, pour que l'utilisateur voie ce que l'import va produire.

Sur une carte qui mélange deux enregistreurs, il montrait le nom d'un fichier que l'import **n'allait
jamais écrire**.

```
Aperçu : Car640380-2026-Pass1-A1-PaRecPR1648011_20260422_204010.wav
Après  : « 3 / 6 importés » - les trois ignorés sont ceux de la série 1648011
```

Les deux règles étaient justes séparément, et ne se parlaient pas :

- [`TriParSerie`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/importation/model/TriParSerie.java)
  retient les enregistrements de la **série du journal** et écarte les autres (#1492) ;
- `RattachementImportViewModel.majApercu` prenait `nomsOriginaux.get(0)`, sans savoir lequel serait
  retenu.

⚠️ **Ce n'est pas un hasard de fixture.** « 1648011 » précède « 1925492 » dans l'ordre des noms :
l'aperçu tombait **systématiquement** sur la série écartée. Une carte mélangée sur deux le fera.

⚠️ **Et l'écran avait l'air juste.** Le carré, le passage, le point, le préfixe : tout était correct.
Seul le fichier d'exemple ne l'était pas. Sur le seul écran où l'utilisateur a besoin de comprendre
ce qui sera pris et ce qui sera laissé, l'aperçu désignait précisément ce qui serait laissé.

## Décision

**1. Un aperçu prend son exemple parmi ce que l'action retiendra.** Le rattachement reçoit deux
listes : tous les originaux - dont l'avertissement de discordance (#111) a besoin, puisqu'il porte
sur l'ensemble du dossier - et ceux que l'import gardera.

**2. Si rien n'est retenu, l'aperçu ne promet aucun nom.** Montrer le futur nom d'un fichier qu'on
n'écrira pas est le défaut d'ici ; le faire quand on n'écrira **rien** serait le pire des deux.

**3. La règle de tri ne se réécrit pas : elle s'appelle.** `RapportInspection.nomsRetenus()` passe
par `TriParSerie`, celle-là même que `ServiceImport` emploie. Deux copies d'une règle de tri finissent
par diverger, et c'est l'écran qui le dit en dernier - trop tard.

⚠️ **La méthode vit sur le RAPPORT, pas sur le sous-VM d'inspection.** L'y poser a fait basculer
`InspectionImportViewModel` en God Class au portail PMD (WMC 48). Le rapport connaît déjà ses
originaux et son journal : c'est chez lui que la question a sa réponse.

## Conséquences

Un écran qui annonce un résultat doit dériver son annonce de la **règle qui produira ce résultat**,
pas d'une approximation commode. « Le premier de la liste » est une approximation commode.

Cette décision ne dit rien de la **discordance** de préfixe - le cas où l'import est bloqué parce que
les fichiers portent le préfixe d'un autre rattachement. Elle est traitée ailleurs, et illustrée par
`apercu-import-rattachement-avertissements.png`.

## Ce qui a été écarté

**Filtrer `nomsOriginaux` à la source**, en ne transmettant que les retenus. L'avertissement de
discordance (#111) porte délibérément sur **tout** le dossier ; le restreindre aurait corrigé un
défaut en en créant un autre, plus discret.

## Le rapport avec l'ADR 0042

[ADR 0042](0042-un-apercu-qui-ment-est-refuse.md) s'intitule « un aperçu qui ment est refusé », et sa
décision porte entièrement sur la **déformation** : un libellé tronqué, un texte comprimé par une
scène trop courte. Le cas d'ici est d'une autre espèce - l'aperçu était parfaitement **rendu**, et
sémantiquement faux. Le titre de 0042 le couvre ; sa décision non. Les deux se lisent ensemble.
