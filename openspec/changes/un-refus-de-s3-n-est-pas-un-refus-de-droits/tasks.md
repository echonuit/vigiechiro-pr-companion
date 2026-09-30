Toutes ces tâches réalisent le lot #5598 du chantier #5596, en **une** demande de fusion : elles ne
s'ouvrent pas en issues séparées. Si le lot se révèle en demander deux, il s'ouvre en sous-chantier
(ADR 4712) plutôt que d'absorber.

Chaque tâche commence par son test rouge (article A7).

## 1. La cause selon la provenance

- [x] 1.1 Écrire les cas unitaires de la règle de classement, un par couple statut et provenance
      (`403` API, `401` API, `403` stockage, `401` stockage, `400` stockage, `422` API, `429` et
      `5xx` sans cause). Rouge attendu : `CauseRefus.de` n'accepte pas de provenance, et un `403` du
      stockage rend `AUTHENTIFICATION`.
- [x] 1.2 Ajouter `Provenance` (`API`, `STOCKAGE`) à `commun/api` et la cause `STOCKAGE` à
      `CauseRefus`, puis réécrire sa javadoc : « la cause vient du statut et de la provenance, jamais
      du texte ». Vert : les cas de 1.1 passent.

## 2. La provenance remonte jusqu'au résultat

- [x] 2.1 Écrire le banc de `TeleverseurArchive`, qui n'existe pas : un `403` provoqué à la
      déclaration, au `PUT` d'un seul bloc, à la demande d'URL de partie, au `PUT` d'une partie et à
      la finalisation, avec la cause lue sur le `Resultat`. Rouge attendu : les deux `PUT` rendent
      `AUTHENTIFICATION`.
- [x] 2.2 Faire rendre à `ClientVigieChiro.deposerEnParts` son issue avec la provenance de l'étape en
      échec, et à `TeleverseurArchive` celle du chemin d'un seul bloc. Vert : le banc de 2.1 passe, et
      `ClientVigieChiroTest` comme `SignatureS3DepotTest` restent verts.

## 3. Le réarmement

- [x] 3.1 Étendre `DepotUniteDaoTest` : une unité refusée pour `STOCKAGE` n'est pas réarmée par
      `rearmer(AUTHENTIFICATION, …)`, une unité `AUTHENTIFICATION` l'est. Vert dès 1.2 si le DAO n'a
      pas à changer, ce que ce test doit établir plutôt que supposer.
- [x] 3.2 Couvrir `EchecUnite.seRearmeParUneReconnexion()` : faux pour `STOCKAGE`. Mutation : le
      rendre vrai pour `STOCKAGE` doit faire rougir le test.

## 4. Le conseil, sur les deux surfaces

- [ ] 4.1 Étendre `CompteRenduChiffreDepotTest` : dix refus du stockage ne produisent pas
      « Reconnectez-vous » et nomment la relance puis le dépôt manuel ; un cas mêlé droits et stockage
      nomme les deux gestes avec leur nombre. Rouge attendu avant 4.2.
- [ ] 4.2 Réécrire `CompteRenduChiffreDepot.phraseDesRefus` pour un conseil par cause. Vert : 4.1.
- [ ] 4.3 Même chose pour `DeposerVigieChiro` et `DeposerVigieChiroTest`, avec le cas de parité : le
      même bilan donne les mêmes gestes sur les deux surfaces.
- [ ] 4.4 Étendre l'aperçu `CaptureCompteRenduDepot` d'un refus du stockage, régénérer la capture de
      `docs/ecrans/lot.md` et la relire ; mettre à jour la légende et le texte de la page s'ils
      nomment la reconnexion comme seul conseil.

## 5. Avant la demande de fusion

- [ ] 5.1 PIT sur `CauseRefus`, `EchecUnite` et `CompteRenduChiffreDepot`, survivants lus un par un.
- [ ] 5.2 La batterie locale (`scripts/batterie.py --lance`) sans refus, et `DocumentationAJourTest`
      vert si la page ou la capture a changé.
