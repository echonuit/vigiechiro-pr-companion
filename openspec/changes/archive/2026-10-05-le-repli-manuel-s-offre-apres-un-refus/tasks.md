## 1. La règle

- [x] 1.1 Test rouge dans `DepotUniteDaoTest` : zéro sans refus, zéro après un échec rejouable, zéro pour
      un refus de droits, compté pour le stockage, le contenu et une ligne sans cause, zéro pour une archive
- [x] 1.2 `DepotUniteDao` et `ServiceLot` rendent le compte, et le test passe
- [x] 1.3 Mutations sur la règle : repli offert trop tôt, repli jamais offert, vues rouges (dix mutations à la
      main, sur la requête, le modèle de vue et la carte, toutes tuées par le cas attendu)

## 2. Le modèle de vue

- [x] 2.1 Test rouge du repli absent puis offert, fil à trois étapes, absent hors connexion et en forme ZIP
      (d'abord dans `LotViewModelTest`, puis dans `DepotViewModelTest` et `EtapeDesArchivesTest` : tâche 5.5)
- [x] 2.2 Le modèle de vue porte le compte du repli, et le test passe
- [x] 2.3 `CompteRenduChiffreDepot` renvoie au repli, et `CompteRenduChiffreDepotTest` le lit

## 3. L'écran

- [x] 3.1 `EtapeDesArchives` distingue « offerte » et « les archives servent » ; `LotDepotConnecteViewTest`
      reste vert
- [x] 3.2 La carte du repli : titre, consigne, place sous le téléversement, lus par `LotRepliManuelViewTest`
      après un téléversement refusé
- [x] 3.3 Aperçu de l'état, ouvert et regardé (`apercu-lot-repli-manuel.png` ; la table vide de la carte y
      disait une phrase fausse en séquences, corrigée et tenue par le scénario)

## 4. La ligne de commande

- [x] 4.1 Test rouge dans `DeposerVigieChiroTest` : le repli nommé pour le stockage et le contenu, absent
      pour les droits
- [x] 4.2 `DeposerVigieChiro` dit le repli, et le test passe

## 5. Le dernier geste du repli, et les deux lectures partielles du plan

- [x] 5.1 Le bouton « Marquer le passage déposé » de la carte : grisé sans archive, ouvert avec, cliqué, et
      absent hors du repli, vu rouge dans `LotRepliManuelViewTest`
- [x] 5.2 `deposer` ne prépare que ce qui ne l'est pas, vu rouge dans `DeposerTest` ; `deposer-vigiechiro`
      nomme la commande
- [x] 5.3 Rechargée depuis le plan, la table garde le caractère définitif d'un refus, vu rouge dans
      `SuiviLignesDepotTest` et à l'écran
- [x] 5.4 Le bilan de récupérabilité ne nomme le serveur que si tout y est, vu rouge dans
      `ServiceRecuperabiliteTest`
- [x] 5.5 Le compte du repli quitte `LotViewModel` pour le modèle de vue du dépôt (plafond `GodClass`), tenu
      par `DepotViewModelTest` et `EtapeDesArchivesTest`

## 6. Ce qui se lit hors du code

- [x] 6.1 `docs/ecrans/lot.md` et la fiche M-Lot du brief décrivent le repli
- [x] 6.2 Un cas de recette le joue (S4-104)
- [x] 6.3 L'ADR dit ce qu'elle change aux ADR 5677 et 5824, et passe `scripts/adr/verifie_okf.py`
