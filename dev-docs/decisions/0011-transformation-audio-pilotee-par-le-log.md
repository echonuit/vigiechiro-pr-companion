# ADR 0011 — La transformation audio est pilotée par le log (fréquence réelle), pas par l'en-tête du WAV

- **Statut** : Accepté — rétroactif
- **Chantier** : import Tadarida (unités CSV), bruts PR expansés (`FrequenceAcquisition`)
- **Vérification** : humaine — que la transformation lise la fréquence dans le log et non l'en-tête WAV est un comportement, gardé par les tests de transformation

## Contexte

Les enregistreurs Pettersson (PR) capturent en ultrasons puis écrivent un WAV dont l'**en-tête ment volontairement** : la fréquence d'échantillonnage y est notée à **Fe/10** (expansion temporelle ×10, pour rendre les cris audibles). La vraie fréquence d'**acquisition** (`Fe`), elle, n'est écrite que dans le **journal** de l'enregistreur (`Fe…kHz`). Or c'est cette fréquence réelle qui détermine le **découpage en tranches de 5 secondes réelles** et le nommage horodaté que Tadarida attend. Se fier à l'en-tête produirait des tranches fausses (dix fois trop longues) et des durées erronées.

## Décision

La transformation lit la **fréquence d'acquisition dans le log** (`FrequenceAcquisition`) et **s'en sert**, jamais de l'en-tête du WAV :

- le découpage se fait à **5 s réelles** = `5 × Fe` échantillons ;
- l'en-tête des tranches produites est réécrit à `Fe/10` (convention d'expansion ×10, préservée) ;
- chaque tranche porte l'**heure réelle de son début** (horodatage de l'original décalé de `index × 5 s`).

Cette fréquence réelle est **persistée** (`original_recording.sampling_rate_hz`) : c'est elle, pas l'en-tête, qui permet de **régénérer à l'identique** plus tard (cf. [ADR 0001](0001-reactivation-passage-reconstruit-identite-structurelle.md)).

## Conséquences

- Le découpage et les durées sont **corrects** (une régression historique était justement un facteur ×10 sur `duration_s`).
- La régénération d'un brut est **déterministe** et reproductible : mêmes octets en entrée + même `Fe` du log → mêmes tranches. C'est ce qui fonde la preuve d'identité **structurelle** de la cascade.
- Sans log exploitable, on ne **devine pas** la fréquence : on renonce et on le dit (pas de tranche fabriquée sur une hypothèse).

## Alternatives écartées

- **Se fier à l'en-tête du WAV.** Il porte `Fe/10` par convention : le découpage serait dix fois trop long, les horodatages faux.
- **Demander la fréquence à l'utilisateur.** Le log la porte déjà et sans ambiguïté ; la redemander, c'est risquer une saisie erronée.
